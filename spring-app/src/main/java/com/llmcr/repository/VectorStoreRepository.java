package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.entity.VectorStore;

public interface VectorStoreRepository extends JpaRepository<VectorStore, Long> {
}