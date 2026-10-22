package com.rams.web;

import com.rams.service.CurrentUser;
import com.rams.service.DashboardService;
import com.rams.web.dto.DashboardDtos.DashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final CurrentUser currentUser;
    private final DashboardService dashboard;

    public DashboardController(CurrentUser currentUser, DashboardService dashboard) {
        this.currentUser = currentUser;
        this.dashboard = dashboard;
    }

    @GetMapping
    public DashboardResponse get() {
        return dashboard.summarize(currentUser.require());
    }
}
