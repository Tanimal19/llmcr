package com.example.llmcr.repository;

import com.example.llmcr.entity.DocumentParagraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for managing DocumentParagraph entities.
 */
@Repository
public interface DocumentParagraphRepository extends JpaRepository<DocumentParagraph, String> {

    /**
     * Find DocumentParagraphs by keyword in content.
     */
    List<DocumentParagraph> findByContentContainingIgnoreCase(String keyword);

    /**
     * Find DocumentParagraphs by source.
     */
    List<DocumentParagraph> findBySource(String source);

    /**
     * Find all DocumentParagraphs that haven't been processed yet.
     */
    List<DocumentParagraph> findByProcessedFalse();

    /**
     * Find DocumentParagraphs by content containing keyword and source.
     */
    List<DocumentParagraph> findByContentContainingIgnoreCaseAndSource(String keyword, String source);
}
