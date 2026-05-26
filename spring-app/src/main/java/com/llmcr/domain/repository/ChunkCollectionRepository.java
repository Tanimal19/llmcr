package com.llmcr.domain.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.domain.entity.ChunkCollection;

public interface ChunkCollectionRepository extends JpaRepository<ChunkCollection, Long> {
    Optional<ChunkCollection> findByName(String name);
}
