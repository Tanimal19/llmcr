package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.entity.contextImpl.UsecaseContext;

public interface UsecaseContextRepository extends JpaRepository<UsecaseContext, Long> {
}