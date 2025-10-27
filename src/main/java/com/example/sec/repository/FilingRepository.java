package com.example.sec.repository;

import com.example.sec.Entity.Filing;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FilingRepository extends CrudRepository<Filing, Long> {
}