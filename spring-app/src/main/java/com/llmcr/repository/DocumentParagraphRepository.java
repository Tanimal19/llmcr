package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import com.llmcr.entity.DocumentParagraph;

import java.util.UUID;

/**
 * Repository interface for managing DocumentParagraph entities.
 */
@Repository
public interface DocumentParagraphRepository extends JpaRepository<DocumentParagraph, UUID> {
}
