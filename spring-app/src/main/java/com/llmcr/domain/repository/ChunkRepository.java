package com.llmcr.domain.repository;

import com.llmcr.domain.entity.Chunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

  @Query("SELECT c.id FROM Chunk c WHERE c.context.id IN :contextIds")
  List<Long> findIdsByContextIdIn(@Param("contextIds") List<Long> contextIds);
}
