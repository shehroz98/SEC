package com.example.sec.Entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("filing")
public class Filing {

    @Id
    private Long id;

    private String title;
    private String link;
    private String summary;

    // DB will generate these values; read-only in Java
    @ReadOnlyProperty
    @Column("row_creation")
    private Instant rowCreation;

    @ReadOnlyProperty
    @Column("updated_at")
    private Instant updatedAt;

    // --- Constructors ---
    public Filing() {}

    public Filing(String title, String link, String summary) {
        this.title = title;
        this.link = link;
        this.summary = summary;
    }

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Instant getRowCreation() { return rowCreation; }
    public void setRowCreation(Instant rowCreation) { this.rowCreation = rowCreation; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
