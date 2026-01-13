package com.example.llmcr.repository;

import com.example.llmcr.entity.ClassNode;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for managing ClassNode entities.
 */
@Repository
public interface ClassNodeRepository extends JpaRepository<ClassNode, String> {

    /**
     * Find all ClassNodes by their IDs.
     */
    List<ClassNode> findByIdIn(List<String> ids);

    /**
     * Find all ClassNodes that haven't been processed yet.
     */
    List<ClassNode> findByProcessedFalse();

    /**
     * Find all ClassNodes that have been processed.
     */
    List<ClassNode> findByProcessedTrue();

    /**
     * Find ClassNodes by signature containing a keyword.
     */
    List<ClassNode> findBySignatureContainingIgnoreCase(String keyword);

    /**
     * Find ClassNodes by code text containing a keyword.
     */
    List<ClassNode> findByCodeTextContainingIgnoreCase(String keyword);
}
