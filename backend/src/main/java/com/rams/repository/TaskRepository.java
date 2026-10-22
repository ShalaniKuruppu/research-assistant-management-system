package com.rams.repository;

import com.rams.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectIdOrderByIdAsc(Long projectId);

    List<Task> findByProjectIdAndAssignedToOrderByIdAsc(Long projectId, Long assignedTo);

    List<Task> findByProjectIdIn(Collection<Long> projectIds);

    List<Task> findByProjectIdInAndAssignedTo(Collection<Long> projectIds, Long assignedTo);
}
