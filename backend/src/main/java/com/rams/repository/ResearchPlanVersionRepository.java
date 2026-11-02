package com.rams.repository;

import com.rams.domain.ResearchPlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResearchPlanVersionRepository extends JpaRepository<ResearchPlanVersion, Long> {

    List<ResearchPlanVersion> findByProjectIdOrderByCreatedAtDescIdDesc(Long projectId);
}
