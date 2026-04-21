package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.entity.contextImpl.DocumentContext;

public interface DocumentContextRepository extends JpaRepository<DocumentContext, Long> {
}