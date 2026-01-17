package com.example.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.llmcr.entity.IndexFile;

@Repository
public interface IndexFileRepository extends JpaRepository<IndexFile, Long> {
    public IndexFile findByName(String name);
}
