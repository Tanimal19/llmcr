package com.llmcr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.llmcr.entity.TrackRoot;

public interface TrackRootRepository extends JpaRepository<TrackRoot, Long> {
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TrackRoot t WHERE t.path = :path")
    public boolean existsByPath(String path);

    @Query("SELECT t.id FROM TrackRoot t")
    public List<Long> findAllIds();
}