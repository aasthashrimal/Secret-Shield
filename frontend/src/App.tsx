import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import { ApiError, query } from './api';
import { useActions } from './actions';
import { useResource } from './hooks';
import { useSession } from './session';
import type { Overview, Page, Repository } from './types';
import { Badge, DateTime, Pager, RepositoryLink, Section, SeverityBars, StateMessage } from './ui';
import { RepositoryPage, ScanPage, IncidentPage } from './RepositoryPage';

function AuthPage({ register = false }: { register?: boolean }) {
  const { user, login, register: createAccount } = useSession();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  if (user) return <Navigate to="/" replace />;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true); setError(null);
    try {
      const email = String(form.get('email'));
      const password = String(form.get('password'));
      if (register) await createAccount(String(form.get('username')), email, password);
      else await login(email, password);
      navigate('/', { replace: true });
    } catch (err) {
      setError(!register && err instanceof ApiError && err.status === 401
        ? 'Incorrect email or password. New here? Create an account below.'
        : err instanceof Error ? err.message : 'Sign-in failed.');
    }
    finally { setBusy(false); }
  }
  return <main className="auth-page"><div className="auth-brand"><span className="brand-mark">✦</span><h1>Secret Shield</h1><p>See your repository security posture clearly.</p></div>
    <form className="auth-card" onSubmit={submit}><h2>{register ? 'Create an account' : 'Welcome back'}</h2>
      <p>{register ? 'Start tracking scans and incidents.' : 'Sign in to review your repositories.'}</p>
      {error && <div role="alert" className="form-error">{error}</div>}
      {register && <label>Username<input name="username" autoComplete="username" minLength={3} maxLength={50} required /></label>}
      <label>Email<input name="email" type="email" autoComplete="email" required /></label>
      <label>Password<input name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'} minLength={8} required /></label>
      <button className="primary full" disabled={busy}>{busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}</button>
      <p className="auth-switch">{register ? 'Already have an account?' : 'New to Secret Shield?'} <Link to={register ? '/login' : '/register'}>{register ? 'Sign in' : 'Create account'}</Link></p>
    </form></main>;
}

function Shell() {
  const { user, logout } = useSession();
  return <div className="app-shell"><aside className="sidebar"><Link to="/" className="brand"><span className="brand-mark">✦</span><span>Secret Shield</span></Link>
    <nav aria-label="Main navigation"><NavLink end to="/">Overview</NavLink><NavLink to="/repositories">Repositories</NavLink></nav>
    <div className="sidebar-foot"><span className="avatar">{user?.username.slice(0, 1).toUpperCase()}</span><span><strong>{user?.username}</strong><small>{user?.role}</small></span><button className="text-button" onClick={logout}>Log out</button></div>
  </aside><div className="main-column"><header className="topbar"><span>Security workspace</span><span className="topbar-user">{user?.email}</span></header><main className="content"><Routes>
      <Route index element={<OverviewPage />} />
      <Route path="repositories" element={<RepositoriesPage />} />
      <Route path="repositories/:id" element={<RepositoryPage />} />
      <Route path="repositories/:id/scans/:scanId" element={<ScanPage />} />
      <Route path="repositories/:id/incidents/:incidentId" element={<IncidentPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes></main></div></div>;
}

function OverviewPage() {
  const { data, loading, error } = useResource<Overview>('/api/dashboard/overview');
  return <><div className="page-heading"><div><p className="eyebrow">Your security posture</p><h1>Overview</h1><p>Recent scans, findings, and incidents across the repositories you can access.</p></div><Link className="button primary" to="/repositories">View repositories</Link></div>
    <StateMessage loading={loading} error={error} empty={!data}>
      {data && <><div className="metric-grid"><Metric label="Repositories" value={data.repositories} note={`${data.activeRepositories} active`} /><Metric label="Scans" value={data.scans} note={`${data.scanOutcomes.CLEAN} clean`} /><Metric label="Findings" value={data.findings} note="Occurrences" /><Metric label="Open incidents" value={data.incidentsByStatus.OPEN} note={`${data.incidents} total`} /><Metric label="Failed alerts" value={data.failedNotifications} note="Delivery attempts" /></div>
        <div className="two-column"><Section title="Findings by severity"><SeverityBars counts={data.findingsBySeverity} /></Section><Section title="Scan outcomes"><div className="status-list">{Object.entries(data.scanOutcomes).map(([name, count]) => <div key={name}><Badge value={name} /><strong>{count}</strong></div>)}</div></Section></div>
        <Section title="Repository security" action={<Link to="/repositories">See all</Link>}><div className="table-wrap"><table><thead><tr><th>Repository</th><th>Latest scan</th><th>Gate</th><th>Commit</th></tr></thead><tbody>{data.repositorySecurity.map(item => <tr key={item.repositoryId}><td><RepositoryLink id={item.repositoryId}>{item.githubOwner}/{item.githubName}</RepositoryLink></td><td>{item.latestScanStatus ? <Badge value={item.latestScanStatus} /> : 'No scan'}</td><td><Badge value={item.gateReason} /></td><td><code>{item.latestCommitSha?.slice(0, 12) ?? '—'}</code></td></tr>)}</tbody></table>{data.repositorySecurity.length === 0 && <p className="empty-inline">No repositories yet. Create one to begin.</p>}</div></Section>
        <Section title="Recent activity"><ol className="timeline">{data.recentActivity.map(item => <li key={item.id}><div><strong>{item.operation.replaceAll('_', ' ')}</strong><span>{item.safeChangeSummary}</span><small><RepositoryLink id={item.repositoryId}>Repository #{item.repositoryId}</RepositoryLink> · {item.actorType} · <DateTime value={item.occurredAt} /></small></div></li>)}</ol>{data.recentActivity.length === 0 && <p className="empty-inline">No recent activity.</p>}</Section>
      </>}
    </StateMessage></>;
}

function Metric({ label, value, note }: { label: string; value: number; note: string }) {
  return <article className="metric"><span>{label}</span><strong>{value}</strong><small>{note}</small></article>;
}

function RepositoriesPage() {
  const { createRepository } = useActions();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [active, setActive] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [creating, setCreating] = useState(false);
  const path = `/api/repositories?${query({ page, size: 10, q: search, active })}`;
  const { data, loading, error } = useResource<Page<Repository>>(path, refresh);
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true); setFormError(null);
    try {
      await createRepository(String(form.get('owner')), String(form.get('name')), String(form.get('branch')));
      setCreating(false); setRefresh(value => value + 1);
    } catch (err) { setFormError(err instanceof Error ? err.message : 'Could not create repository.'); }
    finally { setBusy(false); }
  }
  return <><div className="page-heading"><div><p className="eyebrow">Inventory</p><h1>Repositories</h1><p>Manage your tracked GitHub repositories and review their scans.</p></div><button className="primary" onClick={() => setCreating(value => !value)}>{creating ? 'Close form' : 'Add repository'}</button></div>
    {creating && <Section title="Add repository"><form className="form-grid" onSubmit={create}>{formError && <div role="alert" className="form-error wide">{formError}</div>}<label>GitHub owner<input name="owner" required placeholder="team-name" /></label><label>Repository name<input name="name" required placeholder="service" /></label><label>Default branch<input name="branch" defaultValue="main" required /></label><button className="primary" disabled={busy}>{busy ? 'Saving…' : 'Add repository'}</button></form></Section>}
    <Section title="All repositories"><div className="filters"><label>Search<input value={search} onChange={event => { setSearch(event.target.value); setPage(0); }} placeholder="Owner or repository" /></label><label>Status<select value={active} onChange={event => { setActive(event.target.value); setPage(0); }}><option value="">All</option><option value="true">Active</option><option value="false">Inactive</option></select></label></div>
      <StateMessage loading={loading} error={error} empty={!data || data.content.length === 0}><div className="table-wrap"><table><thead><tr><th>Repository</th><th>Default branch</th><th>Status</th><th>Added</th></tr></thead><tbody>{data?.content.map(repository => <tr key={repository.id}><td><RepositoryLink id={repository.id}>{repository.githubOwner}/{repository.githubName}</RepositoryLink><small className="table-sub">{repository.canonicalUrl}</small></td><td><code>{repository.defaultBranch}</code></td><td><Badge value={repository.active ? 'ACTIVE' : 'INACTIVE'} /></td><td><DateTime value={repository.createdAt} /></td></tr>)}</tbody></table></div></StateMessage>
      {data && <Pager data={data} onPage={setPage} />}
    </Section></>;
}

export default function App() {
  const { user } = useSession();
  return <Routes>
    <Route path="/login" element={<AuthPage />} />
    <Route path="/register" element={<AuthPage register />} />
    <Route path="/*" element={user ? <Shell /> : <Navigate to="/login" replace />} />
  </Routes>;
}
