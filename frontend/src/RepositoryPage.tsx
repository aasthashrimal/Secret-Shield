import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { query } from './api';
import { useActions } from './actions';
import { useResource } from './hooks';
import { useSession } from './session';
import type { AuditEvent, Finding, Incident, Notification, Page, Repository, Scan } from './types';
import { Badge, DateTime, Pager, Section, StateMessage } from './ui';

const stamp = (value: string | null) => <DateTime value={value} />;
const asInstant = (value: string) => value ? new Date(value).toISOString() : undefined;

function ErrorLine({ value }: { value: string | null }) { return value && <p className="form-error" role="alert">{value}</p>; }

export function RepositoryPage() {
  const { id } = useParams();
  const { user } = useSession();
  const actions = useActions();
  const navigate = useNavigate();
  const [refresh, setRefresh] = useState(0);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [scanPage, setScanPage] = useState(0);
  const [incidentPage, setIncidentPage] = useState(0);
  const [auditPage, setAuditPage] = useState(0);
  const [scanStatus, setScanStatus] = useState('');
  const [branch, setBranch] = useState('');
  const [commit, setCommit] = useState('');
  const [scanFrom, setScanFrom] = useState('');
  const [scanTo, setScanTo] = useState('');
  const [incidentFrom, setIncidentFrom] = useState('');
  const [incidentTo, setIncidentTo] = useState('');
  const [incidentStatus, setIncidentStatus] = useState('');
  const [severity, setSeverity] = useState('');
  const base = `/api/repositories/${id}`;
  const repository = useResource<Repository>(id ? base : null, refresh);
  const scans = useResource<Page<Scan>>(`${base}/scans?${query({ page: scanPage, size: 10, status: scanStatus, branch, commitSha: commit, from: asInstant(scanFrom), to: asInstant(scanTo) })}`, refresh);
  const incidents = useResource<Page<Incident>>(`${base}/incidents?${query({ page: incidentPage, size: 10, status: incidentStatus, severity, from: asInstant(incidentFrom), to: asInstant(incidentTo) })}`, refresh);
  const audit = useResource<Page<AuditEvent>>(`${base}/audit-events?${query({ page: auditPage, size: 10 })}`, refresh);
  async function mutate(action: () => Promise<unknown>) {
    setBusy(true); setMutationError(null);
    try { await action(); setRefresh(value => value + 1); return true; }
    catch (error) { setMutationError(error instanceof Error ? error.message : 'Request failed.'); return false; }
    finally { setBusy(false); }
  }
  async function createScan(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    await mutate(() => actions.createScan(id!, String(form.get('branch')), String(form.get('commit'))));
  }
  const canEdit = repository.data && (user?.role === 'ADMIN' || repository.data.ownerId === user?.id);
  return <><div className="breadcrumbs"><Link to="/repositories">Repositories</Link><span>/</span><span>{repository.data ? `${repository.data.githubOwner}/${repository.data.githubName}` : 'Detail'}</span></div>
    <StateMessage loading={repository.loading} error={repository.error} empty={!repository.data}>
      {repository.data && <><div className="page-heading"><div><p className="eyebrow">Repository security</p><h1>{repository.data.githubOwner}/{repository.data.githubName}</h1><p><a href={repository.data.canonicalUrl} target="_blank" rel="noreferrer">Open on GitHub ↗</a> · Default branch <code>{repository.data.defaultBranch}</code></p></div><Badge value={repository.data.active ? 'ACTIVE' : 'INACTIVE'} /></div>
        <ErrorLine value={mutationError} />
        {canEdit && repository.data.active && <Section title="Repository settings"><div className="settings-row"><form onSubmit={event => { event.preventDefault(); const form = new FormData(event.currentTarget); void mutate(() => actions.updateBranch(id!, String(form.get('branch')))); }}><label>Default branch<input name="branch" defaultValue={repository.data.defaultBranch} required /></label><button disabled={busy}>Save branch</button></form><button className="danger" disabled={busy} onClick={() => { if (window.confirm('Deactivate this repository? Existing evidence stays visible.')) void mutate(() => actions.deactivateRepository(id!)); }}>Deactivate repository</button></div></Section>}
      </>}
    </StateMessage>
    {repository.data && <><Section title="Scans" action={<span className="hint">Use a checked-out repository at the configured scanner workspace.</span>}>
      {repository.data.active && <form className="form-grid scan-form" onSubmit={createScan}><label>Branch<input name="branch" defaultValue={repository.data.defaultBranch} required /></label><label>Commit SHA<input name="commit" pattern="[0-9a-fA-F]{40}" title="40 hexadecimal characters" required placeholder="40-character commit SHA" /></label><button className="primary" disabled={busy}>Queue scan</button></form>}
      <div className="filters"><label>Status<select value={scanStatus} onChange={event => { setScanStatus(event.target.value); setScanPage(0); }}><option value="">All</option>{['QUEUED', 'RUNNING', 'CLEAN', 'FINDINGS_DETECTED', 'FAILED'].map(value => <option key={value}>{value}</option>)}</select></label><label>Branch<input value={branch} onChange={event => { setBranch(event.target.value); setScanPage(0); }} placeholder="main" /></label><label>Commit SHA<input value={commit} onChange={event => { setCommit(event.target.value); setScanPage(0); }} placeholder="Full SHA" /></label><label>From<input type="datetime-local" value={scanFrom} onChange={event => { setScanFrom(event.target.value); setScanPage(0); }} /></label><label>Before<input type="datetime-local" value={scanTo} onChange={event => { setScanTo(event.target.value); setScanPage(0); }} /></label></div>
      <StateMessage loading={scans.loading} error={scans.error} empty={!scans.data || scans.data.content.length === 0}><div className="table-wrap"><table><thead><tr><th>Commit</th><th>Branch</th><th>Outcome</th><th>Created</th></tr></thead><tbody>{scans.data?.content.map(scan => <tr key={scan.id}><td><Link to={`/repositories/${id}/scans/${scan.id}`}><code>{scan.commitSha.slice(0, 12)}</code></Link></td><td>{scan.branch}</td><td><Badge value={scan.status} /></td><td>{stamp(scan.createdAt)}</td></tr>)}</tbody></table></div></StateMessage>
      {scans.data && <Pager data={scans.data} onPage={setScanPage} />}
    </Section>
    <Section title="Incidents"><div className="filters"><label>Status<select value={incidentStatus} onChange={event => { setIncidentStatus(event.target.value); setIncidentPage(0); }}><option value="">All</option>{['OPEN', 'RESOLVED', 'IGNORED'].map(value => <option key={value}>{value}</option>)}</select></label><label>Severity<select value={severity} onChange={event => { setSeverity(event.target.value); setIncidentPage(0); }}><option value="">All</option>{['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'].map(value => <option key={value}>{value}</option>)}</select></label><label>From<input type="datetime-local" value={incidentFrom} onChange={event => { setIncidentFrom(event.target.value); setIncidentPage(0); }} /></label><label>Before<input type="datetime-local" value={incidentTo} onChange={event => { setIncidentTo(event.target.value); setIncidentPage(0); }} /></label></div>
      <StateMessage loading={incidents.loading} error={incidents.error} empty={!incidents.data || incidents.data.content.length === 0}><div className="table-wrap"><table><thead><tr><th>Incident</th><th>Severity</th><th>Status</th><th>Updated</th></tr></thead><tbody>{incidents.data?.content.map(item => <tr key={item.id}><td><Link to={`/repositories/${id}/incidents/${item.id}`}>#{item.id} · <code>{item.fingerprint.slice(0, 12)}</code></Link></td><td><Badge value={item.severity} /></td><td><Badge value={item.status} /></td><td>{stamp(item.updatedAt)}</td></tr>)}</tbody></table></div></StateMessage>
      {incidents.data && <Pager data={incidents.data} onPage={setIncidentPage} />}
    </Section>
    <Section title="Audit history"><AuditTimeline events={audit.data?.content ?? []} loading={audit.loading} error={audit.error} />{audit.data && <Pager data={audit.data} onPage={setAuditPage} />}</Section>
    </>}
  </>;
}

function AuditTimeline({ events, loading, error }: { events: AuditEvent[]; loading: boolean; error: string | null }) {
  return <StateMessage loading={loading} error={error} empty={events.length === 0}><ol className="timeline">{events.map(item => <li key={item.id}><strong>{item.operation.replaceAll('_', ' ')}</strong><span>{item.safeChangeSummary}</span><small>{item.actorType} · <DateTime value={item.occurredAt} /></small></li>)}</ol></StateMessage>;
}

export function ScanPage() {
  const { id, scanId } = useParams();
  const { startScan } = useActions();
  const [refresh, setRefresh] = useState(0);
  const [page, setPage] = useState(0);
  const [severity, setSeverity] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const scan = useResource<Scan>(`/api/repositories/${id}/scans/${scanId}`, refresh);
  const findings = useResource<Page<Finding>>(`/api/repositories/${id}/scans/${scanId}/findings?${query({ page, size: 10, severity })}`, refresh);
  async function start() {
    setBusy(true); setError(null);
    try { await startScan(id!, scanId!); setRefresh(value => value + 1); }
    catch (err) { setError(err instanceof Error ? err.message : 'Could not start scan.'); }
    finally { setBusy(false); }
  }
  return <><div className="breadcrumbs"><Link to="/repositories">Repositories</Link><span>/</span><Link to={`/repositories/${id}`}>Repository #{id}</Link><span>/</span><span>Scan #{scanId}</span></div><div className="page-heading"><div><p className="eyebrow">Scan detail</p><h1>Scan #{scanId}</h1><p>Inspect this exact branch and commit.</p></div>{scan.data?.status === 'QUEUED' && <button className="primary" disabled={busy} onClick={start}>{busy ? 'Scanning…' : 'Start scan'}</button>}</div><ErrorLine value={error} />
    <Section title="Scan result"><StateMessage loading={scan.loading} error={scan.error} empty={!scan.data}>{scan.data && <dl className="detail-grid"><div><dt>Status</dt><dd><Badge value={scan.data.status} /></dd></div><div><dt>Branch</dt><dd>{scan.data.branch}</dd></div><div><dt>Commit</dt><dd><code className="breakable">{scan.data.commitSha}</code></dd></div><div><dt>Trigger</dt><dd>{scan.data.trigger}</dd></div><div><dt>Scanner version</dt><dd>{scan.data.scannerVersion ?? '—'}</dd></div><div><dt>Failure code</dt><dd>{scan.data.failureCode ?? '—'}</dd></div><div><dt>Started</dt><dd>{stamp(scan.data.startedAt)}</dd></div><div><dt>Completed</dt><dd>{stamp(scan.data.completedAt)}</dd></div></dl>}</StateMessage></Section>
    <Section title="Findings"><div className="filters"><label>Severity<select value={severity} onChange={event => { setSeverity(event.target.value); setPage(0); }}><option value="">All</option>{['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'].map(value => <option key={value}>{value}</option>)}</select></label></div><StateMessage loading={findings.loading} error={findings.error} empty={!findings.data || findings.data.content.length === 0}><div className="table-wrap"><table><thead><tr><th>Rule</th><th>Severity</th><th>Safe location</th><th>Incident</th></tr></thead><tbody>{findings.data?.content.map(item => <tr key={item.id}><td><strong>{item.ruleId}</strong><small className="table-sub">{item.sanitizedDescription}</small></td><td><Badge value={item.severity} /></td><td><code className="breakable">{item.filePath}:{item.line}</code></td><td><Link to={`/repositories/${id}/incidents/${item.incidentId}`}>#{item.incidentId}</Link></td></tr>)}</tbody></table></div></StateMessage>{findings.data && <Pager data={findings.data} onPage={setPage} />}</Section>
  </>;
}

export function IncidentPage() {
  const { id, incidentId } = useParams();
  const { user } = useSession();
  const { transitionIncident } = useActions();
  const [refresh, setRefresh] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reason, setReason] = useState('');
  const [notificationPage, setNotificationPage] = useState(0);
  const incident = useResource<Incident>(`/api/repositories/${id}/incidents/${incidentId}`, refresh);
  const repository = useResource<Repository>(`/api/repositories/${id}`);
  const audit = useResource<Page<AuditEvent>>(`/api/repositories/${id}/audit-events?${query({ size: 100 })}`, refresh);
  const notifications = useResource<Page<Notification>>(`/api/repositories/${id}/incidents/${incidentId}/notifications?${query({ page: notificationPage, size: 10 })}`, refresh);
  const related = audit.data?.content.filter(item => item.targetType === 'INCIDENT' && item.targetId === incidentId) ?? [];
  const canReview = repository.data && (user?.role === 'ADMIN' || repository.data.ownerId === user?.id);
  async function transition(status: 'OPEN' | 'RESOLVED' | 'IGNORED') {
    if (!incident.data) return;
    setBusy(true); setError(null);
    try {
      await transitionIncident(id!, incidentId!, status, reason, incident.data.version);
      setReason(''); setRefresh(value => value + 1);
    } catch (err) { setError(err instanceof Error ? err.message : 'Could not update incident.'); setRefresh(value => value + 1); }
    finally { setBusy(false); }
  }
  return <><div className="breadcrumbs"><Link to="/repositories">Repositories</Link><span>/</span><Link to={`/repositories/${id}`}>Repository #{id}</Link><span>/</span><span>Incident #{incidentId}</span></div><div className="page-heading"><div><p className="eyebrow">Incident review</p><h1>Incident #{incidentId}</h1><p>Review the finding history and record the action taken.</p></div>{incident.data && <Badge value={incident.data.status} />}</div>
    <ErrorLine value={error} />
    <Section title="Incident"><StateMessage loading={incident.loading} error={incident.error} empty={!incident.data}>{incident.data && <><dl className="detail-grid"><div><dt>Severity</dt><dd><Badge value={incident.data.severity} /></dd></div><div><dt>Status</dt><dd><Badge value={incident.data.status} /></dd></div><div><dt>Fingerprint</dt><dd><code className="breakable">{incident.data.fingerprint}</code></dd></div><div><dt>Last review reason</dt><dd>{incident.data.reviewReason ?? '—'}</dd></div><div><dt>Updated</dt><dd>{stamp(incident.data.updatedAt)}</dd></div></dl><p className="callout">If a credential leaked, rotate or revoke it at its provider, remove it from source, and run a new scan. Reviewing an incident does not unblock deployment.</p>
      {canReview && <div className="review-controls"><label>Reason for this change<textarea value={reason} onChange={event => setReason(event.target.value)} rows={3} maxLength={1000} placeholder="Describe the investigation and action taken" /></label><div className="actions"><button disabled={busy || reason.trim().length < 10 || incident.data.status === 'OPEN'} onClick={() => void transition('OPEN')}>Reopen</button><button disabled={busy || reason.trim().length < 10 || incident.data.status !== 'OPEN'} onClick={() => void transition('RESOLVED')}>Resolve</button>{user?.role === 'ADMIN' && <button disabled={busy || reason.trim().length < 10 || incident.data.status !== 'OPEN'} onClick={() => void transition('IGNORED')}>Ignore</button>}</div></div>}
    </>}</StateMessage></Section>
    <Section title="Incident history"><AuditTimeline events={related} loading={audit.loading} error={audit.error} /><p className="hint">Showing up to the 100 newest repository audit events.</p></Section>
    <Section title="Notifications"><StateMessage loading={notifications.loading} error={notifications.error} empty={!notifications.data || notifications.data.content.length === 0}><div className="table-wrap"><table><thead><tr><th>Recipient alias</th><th>Status</th><th>Attempts</th><th>Safe error code</th><th>Updated</th></tr></thead><tbody>{notifications.data?.content.map(item => <tr key={item.id}><td>{item.recipientConfigReference}</td><td><Badge value={item.status} /></td><td>{item.attemptCount}</td><td>{item.safeErrorCode ?? '—'}</td><td>{stamp(item.updatedAt)}</td></tr>)}</tbody></table></div></StateMessage>{notifications.data && <Pager data={notifications.data} onPage={setNotificationPage} />}</Section>
  </>;
}
