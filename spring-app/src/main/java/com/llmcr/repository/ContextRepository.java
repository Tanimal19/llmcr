package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.entity.Context;

public interface ContextRepository extends JpaRepository<Context, Long> {
}