package com.example.llmcr.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.llmcr.entity.IndexSet;

@Repository
public interface IndexSetRepository extends JpaRepository<IndexSet, Long> {
    public IndexSet findByName(String name);
}
