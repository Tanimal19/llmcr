package com.example.llmcr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.llmcr.entity.Chunk;

@Repository
public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    List<Chunk> findByIdIn(List<Long> ids);

    List<Chunk> findAllByIndexFiles_Name(String indexFileName);

    List<Chunk> findByType(Chunk.ChunkType type);
}
