package com.example.sec.Service;

import com.example.sec.Entity.Filing;
import com.example.sec.repository.FilingRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.*;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class FilingService {

    private final FilingRepository repository;

    public FilingService(FilingRepository repository) {
        this.repository = repository;
    }

    private static final String FEED_URL =
            "https://www.sec.gov/cgi-bin/browse-edgar?action=getcurrent&CIK=&type=144&output=atom";

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; SecBot/1.0; +mailto:contact@example.com)";

    private volatile String lastEtag = null;
    private volatile String lastModified = null;

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Multi-threaded scheduler
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    // Thread pool for async saving
    private final ExecutorService filingExecutor = Executors.newFixedThreadPool(10);

    @PostConstruct
    public void start() {
        log("Starting SEC Filing Poller...");
        scheduler.scheduleAtFixedRate(this::pollOnce, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void pollOnce() {
        long start = System.currentTimeMillis();
        log("Starting pollOnce()...");

        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(FEED_URL))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/atom+xml, application/xml;q=0.9, */*;q=0.8");

            if (lastEtag != null) rb.header("If-None-Match", lastEtag);
            if (lastModified != null) rb.header("If-Modified-Since", lastModified);

            client.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(resp -> {
                        resp.headers().firstValue("etag").ifPresent(et -> lastEtag = et);
                        resp.headers().firstValue("last-modified").ifPresent(lm -> lastModified = lm);

                        if (resp.statusCode() == 200) {
                            try {
                                parseAndSaveAsync(resp.body());
                            } catch (Exception e) {
                                log("Error parsing feed: " + e.getMessage());
                            }
                        }
                    })
                    .join();

        } catch (Exception e) {
            log("Error in pollOnce(): " + e.getMessage());
        } finally {
            long end = System.currentTimeMillis();
            log("pollOnce() completed in " + (end - start) + " ms");
        }
    }

    private void parseAndSaveAsync(InputStream in) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isCoalescing", true);

        try (in) {
            XMLEventReader reader = factory.createXMLEventReader(in);

            boolean inEntry = false;
            StringBuilder title = new StringBuilder();
            StringBuilder summary = new StringBuilder();
            String altLink = "";
            String current = null;

            while (reader.hasNext()) {
                XMLEvent ev = reader.nextEvent();

                if (ev.isStartElement()) {
                    StartElement se = ev.asStartElement();
                    String name = se.getName().getLocalPart();

                    if ("entry".equals(name)) {
                        inEntry = true;
                        title.setLength(0);
                        summary.setLength(0);
                        altLink = "";
                    } else if (inEntry) {
                        current = name;
                        if ("link".equals(name)) {
                            Attribute rel = se.getAttributeByName(new QName("rel"));
                            Attribute href = se.getAttributeByName(new QName("href"));
                            if (href != null && ("alternate".equals(rel != null ? rel.getValue() : "") || rel == null)) {
                                altLink = href.getValue();
                            }
                        }
                    }
                } else if (ev.isCharacters() && inEntry && current != null) {
                    Characters ch = ev.asCharacters();
                    if (!ch.isWhiteSpace()) {
                        switch (current) {
                            case "title" -> title.append(ch.getData());
                            case "summary" -> summary.append(ch.getData());
                        }
                    }
                } else if (ev.isEndElement()) {
                    EndElement ee = ev.asEndElement();
                    String name = ee.getName().getLocalPart();

                    if ("entry".equals(name) && inEntry) {
                        // Create Filing without manually setting timestamps
                        Filing filing = new Filing();
                        filing.setTitle(title.toString().trim());
                        filing.setLink("https://www.sec.gov" + altLink);
                        filing.setSummary(decode(summary.toString().trim()));

                        // Save asynchronously
                        CompletableFuture.runAsync(() -> repository.save(filing), filingExecutor);

                        inEntry = false;
                        current = null;
                    } else if (name.equals(current)) {
                        current = null;
                    }
                }
            }
        }
    }

    private String decode(String s) {
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private void log(String msg) {
        String ts = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now());
        System.out.println("[" + ts + "] " + msg);
    }

    @PreDestroy
    public void shutdown() {
        log("Shutting down executor services...");
        filingExecutor.shutdown();
        scheduler.shutdown();
    }
}
