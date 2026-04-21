package com.llmcr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.llmcr.entity.contextImpl.ClassNodeContext;

public interface ClassNodeContextRepository extends JpaRepository<ClassNodeContext, Long> {
    @Query("""
                SELECT c
                FROM ClassNodeContext c
                WHERE (c.functionalEnrich IS NULL OR TRIM(c.functionalEnrich) = '')
                  AND (c.relationshipEnrich IS NULL OR TRIM(c.relationshipEnrich) = '')
                  AND (c.usageEnrich IS NULL OR TRIM(c.usageEnrich) = '')
            """)
    List<ClassNodeContext> getAllUntransformed();
}