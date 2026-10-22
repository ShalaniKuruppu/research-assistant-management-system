package com.rams.repository;

import com.rams.domain.ProjectAssistant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectAssistantRepository extends JpaRepository<ProjectAssistant, Long> {

    Optional<ProjectAssistant> findByProjectIdAndAssistantId(Long projectId, Long assistantId);

    List<ProjectAssistant> findByProjectIdOrderByIdAsc(Long projectId);

    List<ProjectAssistant> findByProjectIdInOrderByIdAsc(Collection<Long> projectIds);

    boolean existsByProjectIdAndAssistantIdAndActiveTrue(Long projectId, Long assistantId);
}
