package com.secretshield.service;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.dto.DashboardOverviewDto;
import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;

import jakarta.validation.ConstraintViolationException;

@Service
public class DashboardService {
    private final NamedParameterJdbcTemplate jdbc;
    private final GateDecisionService gate;

    public DashboardService(NamedParameterJdbcTemplate jdbc, GateDecisionService gate) {
        this.jdbc = jdbc;
        this.gate = gate;
    }

    @Transactional(readOnly = true)
    public DashboardOverviewDto overview(User actor, String from, String to) {
        if (actor == null) throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        LocalDateTime start = parseTime(from);
        LocalDateTime end = parseTime(to);
        if (start != null && end != null && !start.isBefore(end)) throw invalid();
        var p = new MapSqlParameterSource()
                .addValue("ownerId", actor.getRole() == UserRole.ADMIN ? null : actor.getId(), Types.BIGINT)
                .addValue("fromTime", start, Types.TIMESTAMP)
                .addValue("toTime", end, Types.TIMESTAMP);
        long repositories = count("SELECT count(*) FROM project_repositories r WHERE " + owner(), p);
        long active = count("SELECT count(*) FROM project_repositories r WHERE " + owner()
                + " AND r.active", p);
        long scans = count("SELECT count(*) FROM scans x JOIN project_repositories r ON r.id=x.repository_id"
                + " WHERE " + owner() + window("x.created_at"), p);
        long findings = count("SELECT count(*) FROM findings x JOIN scans s ON s.id=x.scan_id"
                + " JOIN project_repositories r ON r.id=s.repository_id WHERE " + owner()
                + window("x.created_at"), p);
        long incidents = count("SELECT count(*) FROM incidents x JOIN project_repositories r"
                + " ON r.id=x.repository_id WHERE " + owner() + window("x.created_at"), p);
        long failed = count("SELECT count(*) FROM notification_deliveries x"
                + " JOIN incidents i ON i.id=x.incident_id"
                + " JOIN project_repositories r ON r.id=i.repository_id WHERE " + owner()
                + " AND x.status='FAILED'" + window("x.created_at"), p);
        var scanOutcomes = distribution("QUEUED,RUNNING,CLEAN,FINDINGS_DETECTED,FAILED",
                "SELECT x.status, count(*) AS amount FROM scans x JOIN project_repositories r"
                        + " ON r.id=x.repository_id WHERE " + owner() + window("x.created_at")
                        + " GROUP BY x.status", p);
        var severities = distribution("CRITICAL,HIGH,MEDIUM,LOW,INFO",
                "SELECT x.severity AS status, count(*) AS amount FROM findings x"
                        + " JOIN scans s ON s.id=x.scan_id JOIN project_repositories r"
                        + " ON r.id=s.repository_id WHERE " + owner() + window("x.created_at")
                        + " GROUP BY x.severity", p);
        var states = distribution("OPEN,RESOLVED,IGNORED",
                "SELECT x.status, count(*) AS amount FROM incidents x JOIN project_repositories r"
                        + " ON r.id=x.repository_id WHERE " + owner() + window("x.created_at")
                        + " GROUP BY x.status", p);
        var activity = jdbc.query("""
                SELECT a.id, a.repository_id, a.operation, a.actor_type,
                       a.safe_change_summary, a.occurred_at
                FROM audit_events a JOIN project_repositories r ON r.id=a.repository_id
                WHERE (CAST(:ownerId AS bigint) IS NULL OR r.owner_id=:ownerId)
                  AND (CAST(:fromTime AS timestamp) IS NULL OR a.occurred_at >= :fromTime)
                  AND (CAST(:toTime AS timestamp) IS NULL OR a.occurred_at < :toTime)
                ORDER BY a.occurred_at DESC, a.id DESC LIMIT 10
                """, p, (rs, row) -> new DashboardOverviewDto.RecentActivity(
                Long.toString(rs.getLong("id")), Long.toString(rs.getLong("repository_id")),
                rs.getString("operation"), rs.getString("actor_type"),
                rs.getString("safe_change_summary"), utc(rs.getTimestamp("occurred_at"))));
        var summaries = jdbc.query("""
                SELECT r.id, r.github_owner, r.github_name, r.active,
                       s.id AS scan_id, s.commit_sha, s.status
                FROM project_repositories r
                LEFT JOIN LATERAL (
                    SELECT id, commit_sha, status FROM scans
                    WHERE repository_id=r.id ORDER BY created_at DESC, id DESC LIMIT 1
                ) s ON true
                WHERE (CAST(:ownerId AS bigint) IS NULL OR r.owner_id=:ownerId)
                ORDER BY r.created_at DESC, r.id DESC LIMIT 10
                """, p, (rs, row) -> {
            Long scanId = rs.getObject("scan_id", Long.class);
            String commit = rs.getString("commit_sha");
            var decision = scanId == null ? null : gate.decide(actor, rs.getLong("id"), commit);
            return new DashboardOverviewDto.RepositorySecuritySummary(
                    Long.toString(rs.getLong("id")), rs.getString("github_owner"),
                    rs.getString("github_name"), rs.getBoolean("active"),
                    scanId == null ? null : scanId.toString(), commit,
                    rs.getString("status"), decision == null ? "BLOCK" : decision.outcome(),
                    decision == null ? "NO_SCAN" : decision.reason());
        });
        return new DashboardOverviewDto(repositories, active, scans, findings, incidents,
                scanOutcomes, severities, states, failed, activity, summaries);
    }

    private long count(String sql, MapSqlParameterSource parameters) {
        return jdbc.queryForObject(sql, parameters, Long.class);
    }

    private Map<String, Long> distribution(String values, String sql, MapSqlParameterSource p) {
        var result = new LinkedHashMap<String, Long>();
        for (String value : values.split(",")) result.put(value, 0L);
        jdbc.query(sql, p, (org.springframework.jdbc.core.RowCallbackHandler)
                rs -> result.put(rs.getString("status"), rs.getLong("amount")));
        return result;
    }

    private static String owner() { return "(CAST(:ownerId AS bigint) IS NULL OR r.owner_id=:ownerId)"; }
    private static String window(String column) {
        return " AND (CAST(:fromTime AS timestamp) IS NULL OR " + column + " >= :fromTime)"
                + " AND (CAST(:toTime AS timestamp) IS NULL OR " + column + " < :toTime)";
    }
    private static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalDateTime(); }
        catch (Exception error) { throw invalid(); }
    }
    private static String utc(Timestamp value) { return value.toLocalDateTime().toInstant(ZoneOffset.UTC).toString(); }
    private static ConstraintViolationException invalid() {
        return new ConstraintViolationException("Invalid dashboard range", Set.of());
    }
}
