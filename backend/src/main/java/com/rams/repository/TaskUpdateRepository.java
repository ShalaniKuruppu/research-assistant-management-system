package com.rams.repository;

import com.rams.domain.TaskUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskUpdateRepository extends JpaRepository<TaskUpdate, Long> {

    List<TaskUpdate> findByTaskIdOrderByCreatedAtAscIdAsc(Long taskId);
}
