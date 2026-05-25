package com.llmcr.repository;

import com.llmcr.entity.ChunkCollection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkCollectionRepository extends JpaRepository<ChunkCollection, Long> {
    Optional<ChunkCollection> findByName(String name);
}
