package com.llmcr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.llmcr.entity.Context;

public interface ContextRepository extends JpaRepository<Context, Long> {

    public List<Long> findAllIds();

    public List<Long> findAllIdsByType(Context.ContextType type);

    @Query("SELECT c FROM Context c JOIN c.chunks ch WHERE ch.id = :chunkId")
    public Context findByChunkId(@Param("chunkId") Long chunkId);

    @Query("SELECT DISTINCT c FROM Context c JOIN c.chunks ch WHERE ch.id IN :chunkIds")
    public List<Context> findAllByChunkIds(@Param("chunkIds") List<Long> chunkIds);
}
