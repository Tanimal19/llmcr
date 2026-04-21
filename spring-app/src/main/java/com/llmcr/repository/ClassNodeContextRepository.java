package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.entity.contextImpl.ClassNodeContext;

public interface ClassNodeContextRepository extends JpaRepository<ClassNodeContext, Long> {
}