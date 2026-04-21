package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.entity.Source;

public interface SourceRepository extends JpaRepository<Source, Long> {
}