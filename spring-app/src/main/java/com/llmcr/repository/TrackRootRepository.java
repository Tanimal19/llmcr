package com.llmcr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.llmcr.entity.TrackRoot;

public interface TrackRootRepository extends JpaRepository<TrackRoot, Long> {
    @Query("SELECT t FROM TrackRoot t WHERE t.path = :path")
    public TrackRoot findByPath(String path);

    @Query("SELECT t.id FROM TrackRoot t")
    public List<Long> findAllIds();
}