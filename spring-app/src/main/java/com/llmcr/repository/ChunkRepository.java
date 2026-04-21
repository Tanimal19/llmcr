package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.entity.Chunk;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {
}