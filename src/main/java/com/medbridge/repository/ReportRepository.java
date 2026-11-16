package com.medbridge.repository;

import com.medbridge.entity.Report;
import com.medbridge.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByOrderByUploadedAtDesc();
    long countByStatus(ReportStatus status);
}
