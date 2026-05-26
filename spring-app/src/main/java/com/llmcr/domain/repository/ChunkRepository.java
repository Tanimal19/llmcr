package com.llmcr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.domain.entity.Chunk;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {
}
