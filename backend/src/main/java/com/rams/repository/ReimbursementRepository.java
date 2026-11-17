package com.rams.repository;

import com.rams.domain.Reimbursement;
import com.rams.domain.ReimbursementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReimbursementRepository extends JpaRepository<Reimbursement, Long> {

    List<Reimbursement> findByProjectIdOrderByExpenseDateDescIdDesc(Long projectId);

    List<Reimbursement> findByProjectIdAndAssistantIdOrderByExpenseDateDescIdDesc(Long projectId, Long assistantId);

    List<Reimbursement> findByProjectIdIn(Collection<Long> projectIds);

    List<Reimbursement> findByProjectIdInAndAssistantId(Collection<Long> projectIds, Long assistantId);

    long countByProjectIdInAndStatus(Collection<Long> projectIds, ReimbursementStatus status);

    long countByProjectIdInAndAssistantIdAndStatus(
            Collection<Long> projectIds,
            Long assistantId,
            ReimbursementStatus status
    );
}
