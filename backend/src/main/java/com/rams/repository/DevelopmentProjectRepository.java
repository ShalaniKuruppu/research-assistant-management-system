package com.rams.repository;

import com.rams.domain.DevelopmentProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DevelopmentProjectRepository extends JpaRepository<DevelopmentProject, Long> {

    List<DevelopmentProject> findByAssistantIdOrderByUpdatedAtDescIdDesc(Long assistantId);

    List<DevelopmentProject> findAllByOrderByUpdatedAtDescIdDesc();
}
