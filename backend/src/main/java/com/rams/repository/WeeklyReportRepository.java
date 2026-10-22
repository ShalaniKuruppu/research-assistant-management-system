package com.rams.repository;

import com.rams.domain.WeeklyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    List<WeeklyReport> findByProjectIdOrderByWeekStartDateDescIdDesc(Long projectId);

    List<WeeklyReport> findByProjectIdAndAssistantIdOrderByWeekStartDateDescIdDesc(Long projectId, Long assistantId);
}
