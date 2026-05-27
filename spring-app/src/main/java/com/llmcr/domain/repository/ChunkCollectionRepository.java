package com.llmcr.domain.repository;

import com.llmcr.domain.entity.ChunkCollection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkCollectionRepository extends JpaRepository<ChunkCollection, Long> {
  Optional<ChunkCollection> findByName(String name);
}
