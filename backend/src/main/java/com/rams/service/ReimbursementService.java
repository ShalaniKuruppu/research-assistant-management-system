package com.rams.service;

import com.rams.domain.Project;
import com.rams.domain.Reimbursement;
import com.rams.domain.ReimbursementReceipt;
import com.rams.domain.ReimbursementStatus;
import com.rams.domain.UserAccount;
import com.rams.domain.UserRole;
import com.rams.repository.ReimbursementReceiptRepository;
import com.rams.repository.ReimbursementRepository;
import com.rams.repository.UserRepository;
import com.rams.service.ReimbursementFileService.StoredFile;
import com.rams.web.ApiException;
import com.rams.web.Text;
import com.rams.web.dto.ReimbursementDtos.CreateReimbursementRequest;
import com.rams.web.dto.ReimbursementDtos.ReceiptResponse;
import com.rams.web.dto.ReimbursementDtos.ReimbursementResponse;
import com.rams.web.dto.ReimbursementDtos.ReviewReimbursementRequest;
import com.rams.web.dto.ReimbursementDtos.UpdateReimbursementRequest;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReimbursementService {

    private final ReimbursementRepository reimbursements;
    private final ReimbursementReceiptRepository receipts;
    private final ProjectService projects;
    private final UserRepository users;
    private final ReimbursementFileService files;

    public ReimbursementService(
            ReimbursementRepository reimbursements,
            ReimbursementReceiptRepository receipts,
            ProjectService projects,
            UserRepository users,
            ReimbursementFileService files
    ) {
        this.reimbursements = reimbursements;
        this.receipts = receipts;
        this.projects = projects;
        this.users = users;
        this.files = files;
    }

    @Transactional
    public ReimbursementResponse create(UserAccount user, Long projectId, CreateReimbursementRequest request) {
        projects.requireActiveAssistant(user, projectId);
        Reimbursement claim = new Reimbursement();
        claim.setProjectId(projectId);
        claim.setAssistantId(user.getId());
        claim.setTitle(Text.required(request.title(), 200, "Title"));
        claim.setDescription(Text.optional(request.description(), 20000, "Description"));
        claim.setAmount(requireAmount(request.amount()));
        claim.setCurrency(normalizeCurrency(request.currency()));
        claim.setExpenseDate(request.expenseDate());
        claim.setStatus(ReimbursementStatus.DRAFT);
        reimbursements.save(claim);
        return toResponse(claim, List.of(), user.getName());
    }

    public List<ReimbursementResponse> list(UserAccount user, Long projectId) {
        Project project = projects.requireVisible(user, projectId);
        List<Reimbursement> found = project.getSupervisorId().equals(user.getId())
                ? reimbursements.findByProjectIdOrderByExpenseDateDescIdDesc(projectId)
                : reimbursements.findByProjectIdAndAssistantIdOrderByExpenseDateDescIdDesc(projectId, user.getId());
        return toResponses(found);
    }

    public ReimbursementResponse get(UserAccount user, Long reimbursementId) {
        Reimbursement claim = requireVisible(user, reimbursementId);
        return toResponses(List.of(claim)).get(0);
    }

    @Transactional
    public ReimbursementResponse update(UserAccount user, Long reimbursementId, UpdateReimbursementRequest request) {
        Reimbursement claim = requireAuthorEditable(user, reimbursementId);
        if (request.title() != null) {
            claim.setTitle(Text.required(request.title(), 200, "Title"));
        }
        if (request.description() != null) {
            claim.setDescription(Text.optional(request.description(), 20000, "Description"));
        }
        if (request.amount() != null) {
            claim.setAmount(requireAmount(request.amount()));
        }
        if (request.currency() != null) {
            claim.setCurrency(normalizeCurrency(request.currency()));
        }
        if (request.expenseDate() != null) {
            claim.setExpenseDate(request.expenseDate());
        }
        if (claim.getStatus() == ReimbursementStatus.REJECTED) {
            claim.setStatus(ReimbursementStatus.DRAFT);
            claim.setSubmittedAt(null);
            claim.setReviewedAt(null);
            claim.setReviewerNote(null);
        }
        reimbursements.save(claim);
        return toResponses(List.of(claim)).get(0);
    }

    @Transactional
    public ReimbursementResponse uploadReceipt(UserAccount user, Long reimbursementId, MultipartFile file) {
        Reimbursement claim = requireAuthorEditable(user, reimbursementId);
        if (receipts.countByReimbursementId(claim.getId()) >= 10) {
            throw ApiException.badRequest("A claim can have at most 10 receipt files");
        }
        StoredFile stored = files.store(claim.getId(), file);
        ReimbursementReceipt receipt = new ReimbursementReceipt();
        receipt.setReimbursementId(claim.getId());
        receipt.setOriginalFilename(stored.originalFilename());
        receipt.setContentType(stored.contentType());
        receipt.setFileSize(stored.fileSize());
        receipt.setStorageUrl(stored.storageUrl());
        receipts.save(receipt);
        if (claim.getStatus() == ReimbursementStatus.REJECTED) {
            claim.setStatus(ReimbursementStatus.DRAFT);
            claim.setSubmittedAt(null);
            claim.setReviewedAt(null);
            claim.setReviewerNote(null);
            reimbursements.save(claim);
        }
        return toResponses(List.of(claim)).get(0);
    }

    @Transactional
    public ReimbursementResponse deleteReceipt(UserAccount user, Long reimbursementId, Long receiptId) {
        Reimbursement claim = requireAuthorEditable(user, reimbursementId);
        ReimbursementReceipt receipt = receipts.findByIdAndReimbursementId(receiptId, reimbursementId)
                .orElseThrow(() -> ApiException.notFound("Receipt not found"));
        receipts.delete(receipt);
        files.deleteIfOwned(receipt.getStorageUrl());
        if (claim.getStatus() == ReimbursementStatus.REJECTED) {
            claim.setStatus(ReimbursementStatus.DRAFT);
            claim.setSubmittedAt(null);
            claim.setReviewedAt(null);
            claim.setReviewerNote(null);
            reimbursements.save(claim);
        }
        return toResponses(List.of(claim)).get(0);
    }

    @Transactional
    public ReimbursementResponse submit(UserAccount user, Long reimbursementId) {
        Reimbursement claim = requireAuthorEditable(user, reimbursementId);
        if (receipts.countByReimbursementId(claim.getId()) < 1) {
            throw ApiException.badRequest("Upload at least one receipt before submitting");
        }
        claim.setStatus(ReimbursementStatus.SUBMITTED);
        claim.setSubmittedAt(Instant.now());
        claim.setReviewedAt(null);
        claim.setReviewerNote(null);
        reimbursements.save(claim);
        return toResponses(List.of(claim)).get(0);
    }

    @Transactional
    public ReimbursementResponse review(UserAccount user, Long reimbursementId, ReviewReimbursementRequest request) {
        Reimbursement claim = requireVisible(user, reimbursementId);
        projects.requireOwner(user, claim.getProjectId());
        if (claim.getStatus() != ReimbursementStatus.SUBMITTED) {
            throw ApiException.badRequest("Only a submitted reimbursement can be reviewed");
        }
        if (request.status() != ReimbursementStatus.APPROVED && request.status() != ReimbursementStatus.REJECTED) {
            throw ApiException.badRequest("Review outcome must be APPROVED or REJECTED");
        }
        claim.setStatus(request.status());
        claim.setReviewedAt(Instant.now());
        claim.setReviewerNote(Text.optional(request.reviewerNote(), 20000, "Reviewer note"));
        reimbursements.save(claim);
        return toResponses(List.of(claim)).get(0);
    }

    public Resource loadReceiptFile(UserAccount user, Long reimbursementId, Long receiptId) {
        requireVisible(user, reimbursementId);
        ReimbursementReceipt receipt = receipts.findByIdAndReimbursementId(receiptId, reimbursementId)
                .orElseThrow(() -> ApiException.notFound("Receipt not found"));
        return files.load(receipt.getStorageUrl());
    }

    public ReimbursementReceipt requireReceipt(UserAccount user, Long reimbursementId, Long receiptId) {
        requireVisible(user, reimbursementId);
        return receipts.findByIdAndReimbursementId(receiptId, reimbursementId)
                .orElseThrow(() -> ApiException.notFound("Receipt not found"));
    }

    public List<Reimbursement> listVisibleClaims(UserAccount user, Collection<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        if (user.getRole() == UserRole.SUPERVISOR) {
            return reimbursements.findByProjectIdIn(projectIds);
        }
        return reimbursements.findByProjectIdInAndAssistantId(projectIds, user.getId());
    }

    private Reimbursement requireVisible(UserAccount user, Long reimbursementId) {
        Reimbursement claim = reimbursements.findById(reimbursementId)
                .orElseThrow(() -> ApiException.notFound("Reimbursement not found"));
        projects.requireVisible(user, claim.getProjectId());
        if (user.getRole() == UserRole.RESEARCH_ASSISTANT && !claim.getAssistantId().equals(user.getId())) {
            throw ApiException.notFound("Reimbursement not found");
        }
        return claim;
    }

    private Reimbursement requireAuthorEditable(UserAccount user, Long reimbursementId) {
        Reimbursement claim = requireVisible(user, reimbursementId);
        if (!claim.getAssistantId().equals(user.getId()) || user.getRole() != UserRole.RESEARCH_ASSISTANT) {
            throw ApiException.forbidden("Only the research assistant who filed this claim can change it");
        }
        if (claim.getStatus() != ReimbursementStatus.DRAFT && claim.getStatus() != ReimbursementStatus.REJECTED) {
            throw ApiException.forbidden("This reimbursement can no longer be edited");
        }
        projects.requireActiveAssistant(user, claim.getProjectId());
        return claim;
    }

    private List<ReimbursementResponse> toResponses(List<Reimbursement> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = new HashMap<>();
        users.findAllById(found.stream().map(Reimbursement::getAssistantId).distinct().toList())
                .forEach(person -> names.put(person.getId(), person.getName()));
        Map<Long, List<ReimbursementReceipt>> byClaim = new HashMap<>();
        List<Long> ids = found.stream().map(Reimbursement::getId).toList();
        for (ReimbursementReceipt receipt : receipts.findByReimbursementIdInOrderByUploadedAtAscIdAsc(ids)) {
            byClaim.computeIfAbsent(receipt.getReimbursementId(), ignored -> new ArrayList<>()).add(receipt);
        }
        return found.stream()
                .map(claim -> toResponse(
                        claim,
                        byClaim.getOrDefault(claim.getId(), List.of()),
                        names.get(claim.getAssistantId())
                ))
                .toList();
    }

    private static ReimbursementResponse toResponse(
            Reimbursement claim,
            List<ReimbursementReceipt> claimReceipts,
            String assistantName
    ) {
        return new ReimbursementResponse(
                claim.getId(),
                claim.getProjectId(),
                claim.getAssistantId(),
                assistantName,
                claim.getTitle(),
                claim.getDescription(),
                claim.getAmount(),
                claim.getCurrency(),
                claim.getExpenseDate(),
                claim.getStatus(),
                claim.getSubmittedAt(),
                claim.getReviewedAt(),
                claim.getReviewerNote(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                claimReceipts.stream().map(ReimbursementService::toReceipt).toList()
        );
    }

    private static ReceiptResponse toReceipt(ReimbursementReceipt receipt) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getOriginalFilename(),
                receipt.getContentType(),
                receipt.getFileSize(),
                "/api/reimbursements/" + receipt.getReimbursementId() + "/receipts/" + receipt.getId() + "/file",
                receipt.getUploadedAt()
        );
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Amount must be greater than zero");
        }
        return amount;
    }

    private static String normalizeCurrency(String currency) {
        String value = currency == null || currency.isBlank() ? "LKR" : currency.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("^[A-Z]{3}$")) {
            throw ApiException.badRequest("Currency must be a 3-letter code");
        }
        return value;
    }
}
