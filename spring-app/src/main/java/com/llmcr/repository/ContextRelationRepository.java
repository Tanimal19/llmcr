package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.entity.ContextRelation;

public interface ContextRelationRepository extends JpaRepository<ContextRelation, Long> {
}