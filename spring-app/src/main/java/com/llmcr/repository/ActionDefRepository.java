package com.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmcr.entity.contextImpl.ActionDef;

public interface ActionDefRepository extends JpaRepository<ActionDef, Long> {
}