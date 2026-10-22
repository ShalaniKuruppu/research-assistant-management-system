package com.rams.repository;

import com.rams.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findBySupervisorIdOrderByIdAsc(Long supervisorId);

    @Query("""
            select p from Project p
            where p.id in (
                select a.projectId from ProjectAssistant a
                where a.assistantId = :assistantId and a.active = true
            )
            order by p.id
            """)
    List<Project> findVisibleToAssistant(@Param("assistantId") Long assistantId);
}
