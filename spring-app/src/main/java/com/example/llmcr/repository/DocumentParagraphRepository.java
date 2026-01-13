package com.example.llmcr.repository;

import com.example.llmcr.entity.DocumentParagraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository interface for managing DocumentParagraph entities.
 */
@Repository
public interface DocumentParagraphRepository extends JpaRepository<DocumentParagraph, UUID> {
}
