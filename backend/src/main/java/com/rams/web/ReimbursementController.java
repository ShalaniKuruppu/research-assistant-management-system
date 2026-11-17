package com.rams.web;

import com.rams.domain.ReimbursementReceipt;
import com.rams.domain.UserAccount;
import com.rams.service.CurrentUser;
import com.rams.service.ReimbursementService;
import com.rams.web.dto.ReimbursementDtos.CreateReimbursementRequest;
import com.rams.web.dto.ReimbursementDtos.ReimbursementResponse;
import com.rams.web.dto.ReimbursementDtos.ReviewReimbursementRequest;
import com.rams.web.dto.ReimbursementDtos.UpdateReimbursementRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReimbursementController {

    private final CurrentUser currentUser;
    private final ReimbursementService reimbursements;

    public ReimbursementController(CurrentUser currentUser, ReimbursementService reimbursements) {
        this.currentUser = currentUser;
        this.reimbursements = reimbursements;
    }

    @PostMapping("/projects/{projectId}/reimbursements")
    public ResponseEntity<ReimbursementResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateReimbursementRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reimbursements.create(currentUser.require(), projectId, request));
    }

    @GetMapping("/projects/{projectId}/reimbursements")
    public List<ReimbursementResponse> list(@PathVariable Long projectId) {
        return reimbursements.list(currentUser.require(), projectId);
    }

    @GetMapping("/reimbursements/{reimbursementId}")
    public ReimbursementResponse get(@PathVariable Long reimbursementId) {
        return reimbursements.get(currentUser.require(), reimbursementId);
    }

    @PatchMapping("/reimbursements/{reimbursementId}")
    public ReimbursementResponse update(
            @PathVariable Long reimbursementId,
            @Valid @RequestBody UpdateReimbursementRequest request
    ) {
        return reimbursements.update(currentUser.require(), reimbursementId, request);
    }

    @PostMapping(path = "/reimbursements/{reimbursementId}/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReimbursementResponse uploadReceipt(
            @PathVariable Long reimbursementId,
            @RequestPart("file") MultipartFile file
    ) {
        return reimbursements.uploadReceipt(currentUser.require(), reimbursementId, file);
    }

    @DeleteMapping("/reimbursements/{reimbursementId}/receipts/{receiptId}")
    public ReimbursementResponse deleteReceipt(
            @PathVariable Long reimbursementId,
            @PathVariable Long receiptId
    ) {
        return reimbursements.deleteReceipt(currentUser.require(), reimbursementId, receiptId);
    }

    @PostMapping("/reimbursements/{reimbursementId}/submit")
    public ReimbursementResponse submit(@PathVariable Long reimbursementId) {
        return reimbursements.submit(currentUser.require(), reimbursementId);
    }

    @PostMapping("/reimbursements/{reimbursementId}/review")
    public ReimbursementResponse review(
            @PathVariable Long reimbursementId,
            @Valid @RequestBody ReviewReimbursementRequest request
    ) {
        return reimbursements.review(currentUser.require(), reimbursementId, request);
    }

    @GetMapping("/reimbursements/{reimbursementId}/receipts/{receiptId}/file")
    public ResponseEntity<Resource> download(
            @PathVariable Long reimbursementId,
            @PathVariable Long receiptId
    ) {
        UserAccount user = currentUser.require();
        ReimbursementReceipt receipt = reimbursements.requireReceipt(user, reimbursementId, receiptId);
        Resource resource = reimbursements.loadReceiptFile(user, reimbursementId, receiptId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(receipt.getContentType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String filename = receipt.getOriginalFilename().replace("\"", "");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filename + "\"; filename*=UTF-8''"
                                + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8)
                )
                .body(resource);
    }
}
