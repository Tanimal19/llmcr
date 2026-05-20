package com.llmcr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.llmcr.entity.ChunkCollection;

public interface ChunkCollectionRepository extends JpaRepository<ChunkCollection, Long> {
    Optional<ChunkCollection> findByName(String name);

    @Query("SELECT c FROM ChunkCollection c LEFT JOIN FETCH c.havedTrackRoots WHERE c.name = :name")
    Optional<ChunkCollection> findByNameWithTrackRoots(@Param("name") String name);
}