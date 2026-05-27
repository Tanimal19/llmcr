package com.llmcr.domain.repository;

import com.llmcr.domain.entity.TrackRoot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrackRootRepository extends JpaRepository<TrackRoot, Long> {
  @Query("SELECT t FROM TrackRoot t WHERE t.path = :path")
  public TrackRoot findByPath(String path);

  @Query("SELECT t FROM TrackRoot t WHERE t.path IN :paths")
  public List<TrackRoot> findByPaths(Iterable<String> paths);

  @Query("SELECT t.id FROM TrackRoot t")
  public List<Long> findAllIds();
}
