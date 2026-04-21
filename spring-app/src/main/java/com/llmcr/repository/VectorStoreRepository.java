package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.entity.ChunkCollection;

public interface VectorStoreRepository extends JpaRepository<ChunkCollection, Long> {
}