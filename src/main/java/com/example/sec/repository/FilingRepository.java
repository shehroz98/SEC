package com.example.sec.repository;

import com.example.sec.Entity.Filing;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface FilingRepository extends CrudRepository<Filing, Long> {

    // Find filing by link (to detect duplicates)
    Optional<Filing> findByLink(String link);

    // Update only the updated_at timestamp for an existing filing
    @Modifying
    @Transactional
    @Query("UPDATE filing SET updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void touch(Long id);
}
