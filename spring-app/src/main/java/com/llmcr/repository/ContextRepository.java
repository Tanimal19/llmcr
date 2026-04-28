package com.llmcr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.llmcr.entity.Context;

public interface ContextRepository extends JpaRepository<Context, Long> {

    @Query("SELECT c.id FROM Context c")
    public List<Long> findAllIds();

    @Query("SELECT c.id FROM Context c WHERE c.source.id = :sourceId")
    public List<Long> findAllIdsBySourceId(@Param("sourceId") Long sourceId);

    @Query("SELECT c.id FROM Context c WHERE c.chunkLoaded = false")
    public List<Long> findAllUnloadedIds();

    @Query("SELECT c FROM Context c JOIN c.chunks ch WHERE ch.id = :chunkId")
    public Context findByChunkId(@Param("chunkId") Long chunkId);

    @Query("SELECT DISTINCT c FROM Context c JOIN c.chunks ch WHERE ch.id IN :chunkIds")
    public List<Context> findAllByChunkIds(@Param("chunkIds") List<Long> chunkIds);
}
