package com.llmcr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.entity.ChunkCollection;

public interface ChunkCollectionRepository extends JpaRepository<ChunkCollection, Long> {
    Optional<ChunkCollection> findByName(String name);
}