package com.secretshield.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.secretshield.dto.DashboardOverviewDto;
import com.secretshield.entity.User;
import com.secretshield.service.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/overview")
    public DashboardOverviewDto overview(@AuthenticationPrincipal User actor,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return dashboard.overview(actor, from, to);
    }
}
