package com.example.llmcr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.llmcr.entity.Chunk;
import com.example.llmcr.entity.Chunk.ChunkContentType;

@Repository
public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    @Query("SELECT e FROM Chunk e JOIN FETCH e.source WHERE e.id IN :ids")
    List<Chunk> findByIdIn(@Param("ids") List<Long> ids);

    List<Chunk> findByContentType(ChunkContentType contentType);
}
