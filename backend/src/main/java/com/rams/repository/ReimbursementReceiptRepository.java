package com.rams.repository;

import com.rams.domain.ReimbursementReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReimbursementReceiptRepository extends JpaRepository<ReimbursementReceipt, Long> {

    List<ReimbursementReceipt> findByReimbursementIdOrderByUploadedAtAscIdAsc(Long reimbursementId);

    List<ReimbursementReceipt> findByReimbursementIdInOrderByUploadedAtAscIdAsc(Collection<Long> reimbursementIds);

    Optional<ReimbursementReceipt> findByIdAndReimbursementId(Long id, Long reimbursementId);

    long countByReimbursementId(Long reimbursementId);
}
