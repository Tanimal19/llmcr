package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import com.llmcr.entity.ClassNode;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing ClassNode entities.
 */
@Repository
public interface ClassNodeRepository extends JpaRepository<ClassNode, UUID> {

    /**
     * Find all ClassNodes that haven't been processed yet.
     */
    List<ClassNode> findByProcessedFalse();

    /**
     * Find all ClassNodes that have been processed.
     */
    List<ClassNode> findByProcessedTrue();
}
