import type { Overview, Page, Repository, Scan, Finding, Incident } from './types';

export type FixtureState<T> = { state: 'loading' } | { state: 'empty' } | { state: 'error'; message: string } | { state: 'success'; data: T };
export const overviewFixture: Overview = {
  repositories: 2, activeRepositories: 2, scans: 3, findings: 1, incidents: 1,
  scanOutcomes: { QUEUED: 0, RUNNING: 0, CLEAN: 2, FINDINGS_DETECTED: 1, FAILED: 0 },
  findingsBySeverity: { CRITICAL: 0, HIGH: 1, MEDIUM: 0, LOW: 0, INFO: 0 },
  incidentsByStatus: { OPEN: 1, RESOLVED: 0, IGNORED: 0 }, failedNotifications: 0,
  recentActivity: [{ id: '41', repositoryId: '7', operation: 'INCIDENT_CREATED', actorType: 'SYSTEM', safeChangeSummary: 'Incident opened from a scan finding', occurredAt: '2026-10-17T08:00:00Z' }],
  repositorySecurity: [{ repositoryId: '7', githubOwner: 'demo', githubName: 'service', active: true, latestScanId: '21', latestCommitSha: '0123456789abcdef0123456789abcdef01234567', latestScanStatus: 'FINDINGS_DETECTED', gateOutcome: 'BLOCK', gateReason: 'FINDINGS_DETECTED' }],
};
export const states = <T,>(data: T): FixtureState<T>[] => [
  { state: 'loading' }, { state: 'empty' }, { state: 'success', data },
  { state: 'error', message: 'The request could not be completed.' },
];
export const sampleRepository: Repository = { id: '7', ownerId: '2', githubOwner: 'demo', githubName: 'service', canonicalUrl: 'https://github.com/demo/service', defaultBranch: 'main', active: true, createdAt: '2026-10-17T07:00:00Z', updatedAt: '2026-10-17T07:00:00Z' };
export const sampleScan: Scan = { id: '21', repositoryId: '7', commitSha: '0123456789abcdef0123456789abcdef01234567', branch: 'main', trigger: 'MANUAL', runId: null, runAttempt: null, scannerVersion: '8.30.0', status: 'FINDINGS_DETECTED', failureCode: null, startedAt: '2026-10-17T08:00:00Z', completedAt: '2026-10-17T08:00:01Z', createdAt: '2026-10-17T08:00:00Z', updatedAt: '2026-10-17T08:00:01Z' };
export const sampleFinding: Finding = { id: '31', scanId: '21', incidentId: '35', fingerprint: 'a'.repeat(64), ruleId: 'generic-api-key', severity: 'HIGH', filePath: 'config/example.txt', line: 4, commitSha: sampleScan.commitSha, sanitizedDescription: 'Potential secret detected by rule generic-api-key', createdAt: '2026-10-17T08:00:01Z' };
export const sampleIncident: Incident = { id: '35', repositoryId: '7', fingerprint: sampleFinding.fingerprint, severity: 'HIGH', status: 'OPEN', reviewReason: null, lastActorType: 'SYSTEM', lastActorReference: null, version: 0, createdAt: '2026-10-17T08:00:01Z', updatedAt: '2026-10-17T08:00:01Z' };
export const page = <T,>(content: T[]): Page<T> => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 });
