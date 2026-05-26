package com.llmcr.database.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.database.entity.Chunk;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {
}
