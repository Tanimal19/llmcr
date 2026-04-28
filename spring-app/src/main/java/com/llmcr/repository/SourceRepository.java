package com.llmcr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.llmcr.entity.Source;

public interface SourceRepository extends JpaRepository<Source, Long> {

    @Query("SELECT s FROM Source s WHERE s.path = :path")
    public Source findByPath(String path);

    @Query("SELECT s.id FROM Source s")
    public List<Long> findAllIds();

    @Query("SELECT s FROM Source s WHERE s.trackRoot.id = :trackRootId")
    public List<Source> findAllByTrackRootId(@Param("trackRootId") Long trackRootId);

    @Query("SELECT s.id FROM Source s WHERE s.extracted = false")
    public List<Long> findAllUnextractedIds();

    @Modifying
    @Query("UPDATE Source s SET s.extracted = true WHERE s.id IN :ids")
    public int setExtractedByIds(@Param("ids") List<Long> ids);
}