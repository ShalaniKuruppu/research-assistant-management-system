package com.rams.web;

import com.rams.domain.UserAccount;
import com.rams.service.CurrentUser;
import com.rams.service.ReportService;
import com.rams.web.dto.ReportDtos.CreateFeedbackRequest;
import com.rams.web.dto.ReportDtos.CreateReportRequest;
import com.rams.web.dto.ReportDtos.FeedbackResponse;
import com.rams.web.dto.ReportDtos.ReportResponse;
import com.rams.web.dto.ReportDtos.UpdateReportRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final CurrentUser currentUser;
    private final ReportService reports;

    public ReportController(CurrentUser currentUser, ReportService reports) {
        this.currentUser = currentUser;
        this.reports = reports;
    }

    @PostMapping("/projects/{projectId}/reports")
    public ResponseEntity<ReportResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateReportRequest request
    ) {
        UserAccount user = currentUser.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(reports.create(user, projectId, request));
    }

    @GetMapping("/projects/{projectId}/reports")
    public List<ReportResponse> list(@PathVariable Long projectId) {
        return reports.list(currentUser.require(), projectId);
    }

    @GetMapping("/reports/{reportId}")
    public ReportResponse get(@PathVariable Long reportId) {
        return reports.get(currentUser.require(), reportId);
    }

    @PatchMapping("/reports/{reportId}")
    public ReportResponse update(@PathVariable Long reportId, @Valid @RequestBody UpdateReportRequest request) {
        return reports.update(currentUser.require(), reportId, request);
    }

    @PostMapping("/reports/{reportId}/submit")
    public ReportResponse submit(@PathVariable Long reportId) {
        return reports.submit(currentUser.require(), reportId);
    }

    @PostMapping("/reports/{reportId}/feedback")
    public ResponseEntity<FeedbackResponse> feedback(
            @PathVariable Long reportId,
            @Valid @RequestBody CreateFeedbackRequest request
    ) {
        UserAccount user = currentUser.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(reports.addFeedback(user, reportId, request));
    }

    @GetMapping("/reports/{reportId}/feedback")
    public List<FeedbackResponse> listFeedback(@PathVariable Long reportId) {
        return reports.listFeedback(currentUser.require(), reportId);
    }
}
