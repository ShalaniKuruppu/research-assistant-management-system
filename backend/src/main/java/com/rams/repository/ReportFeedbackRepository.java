package com.rams.repository;

import com.rams.domain.ReportFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportFeedbackRepository extends JpaRepository<ReportFeedback, Long> {

    List<ReportFeedback> findByReportIdOrderByCreatedAtAscIdAsc(Long reportId);

    Optional<ReportFeedback> findFirstByReportIdOrderByCreatedAtDescIdDesc(Long reportId);
}
