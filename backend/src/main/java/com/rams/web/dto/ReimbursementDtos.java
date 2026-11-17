package com.rams.web.dto;

import com.rams.domain.ReimbursementStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReimbursementDtos {

    private ReimbursementDtos() {
    }

    public record CreateReimbursementRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @NotNull @DecimalMin(value = "0.01", inclusive = true) @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
            @NotNull LocalDate expenseDate
    ) {
    }

    public record UpdateReimbursementRequest(
            @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @DecimalMin(value = "0.01", inclusive = true) @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
            LocalDate expenseDate
    ) {
    }

    public record ReviewReimbursementRequest(
            @NotNull ReimbursementStatus status,
            @Size(max = 20000) String reviewerNote
    ) {
    }

    public record ReceiptResponse(
            Long id,
            String originalFilename,
            String contentType,
            long fileSize,
            String downloadUrl,
            Instant uploadedAt
    ) {
    }

    public record ReimbursementResponse(
            Long id,
            Long projectId,
            Long assistantId,
            String assistantName,
            String title,
            String description,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            ReimbursementStatus status,
            Instant submittedAt,
            Instant reviewedAt,
            String reviewerNote,
            Instant createdAt,
            Instant updatedAt,
            List<ReceiptResponse> receipts
    ) {
    }
}
