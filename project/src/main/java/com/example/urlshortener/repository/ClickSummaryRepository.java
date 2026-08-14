package com.example.urlshortener.repository;

import com.example.urlshortener.domain.ClickSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickSummaryRepository extends JpaRepository<ClickSummary, String> {
}
