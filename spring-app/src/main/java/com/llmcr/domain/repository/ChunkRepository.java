package com.llmcr.domain.repository;

import com.llmcr.domain.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {}
