package com.secretshield.dto;

import java.util.List;
import java.util.Map;

public record DashboardOverviewDto(
        long repositories,
        long activeRepositories,
        long scans,
        long findings,
        long incidents,
        Map<String, Long> scanOutcomes,
        Map<String, Long> findingsBySeverity,
        Map<String, Long> incidentsByStatus,
        long failedNotifications,
        List<RecentActivity> recentActivity,
        List<RepositorySecuritySummary> repositorySecurity) {
    public record RecentActivity(String id, String repositoryId, String operation,
            String actorType, String safeChangeSummary, String occurredAt) {}

    public record RepositorySecuritySummary(String repositoryId, String githubOwner,
            String githubName, boolean active, String latestScanId, String latestCommitSha,
            String latestScanStatus, String gateOutcome, String gateReason) {}
}
