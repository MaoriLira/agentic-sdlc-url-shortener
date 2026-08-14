package com.example.urlshortener.repository;

import com.example.urlshortener.domain.ClickEventDlq;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventDlqRepository extends JpaRepository<ClickEventDlq, Long> {
}
