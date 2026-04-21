package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.entity.contextImpl.GuidelineContext;

public interface GuidelineContextRepository extends JpaRepository<GuidelineContext, Long> {
}