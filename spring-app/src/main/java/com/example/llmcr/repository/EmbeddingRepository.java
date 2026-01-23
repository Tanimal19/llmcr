package com.example.llmcr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.llmcr.entity.Embedding;
import com.example.llmcr.entity.Embedding.EmbeddingContentType;

@Repository
public interface EmbeddingRepository extends JpaRepository<Embedding, Long> {

    @Query("SELECT e FROM Embedding e JOIN FETCH e.source WHERE e.id IN :ids")
    List<Embedding> findByIdIn(@Param("ids") List<Long> ids);

    List<Embedding> findByContentType(EmbeddingContentType contentType);
}
