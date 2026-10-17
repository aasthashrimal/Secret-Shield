import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';
import type { Page, Severity } from './types';

export function Badge({ value }: { value: string }) {
  return <span className={`badge badge-${value.toLowerCase().replaceAll('_', '-')}`}>{value.replaceAll('_', ' ')}</span>;
}

export function DateTime({ value }: { value: string | null }) {
  if (!value) return <span>—</span>;
  const date = new Date(value);
  return <time dateTime={value} title={value}>{Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date)}</time>;
}

export function StateMessage({ loading, error, empty, children }: { loading: boolean; error: string | null; empty: boolean; children: ReactNode }) {
  if (loading) return <div className="state" role="status">Loading…</div>;
  if (error) return <div className="state state-error" role="alert">{error}</div>;
  if (empty) return <div className="state">No records yet.</div>;
  return <>{children}</>;
}

export function Pager<T>({ data, onPage }: { data: Page<T>; onPage: (page: number) => void }) {
  return <nav className="pager" aria-label="Results pages">
    <button disabled={data.page === 0} onClick={() => onPage(data.page - 1)}>Previous</button>
    <span>Page {data.page + 1} of {Math.max(data.totalPages, 1)} · {data.totalElements} total</span>
    <button disabled={data.page + 1 >= data.totalPages} onClick={() => onPage(data.page + 1)}>Next</button>
  </nav>;
}

export function Section({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return <section className="panel"><div className="panel-head"><h2>{title}</h2>{action}</div>{children}</section>;
}

export function SeverityBars({ counts }: { counts: Record<Severity, number> }) {
  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  return <div className="severity-bars" aria-label="Findings by severity">
    {(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'] as Severity[]).map(level =>
      <div className="severity-row" key={level}><Badge value={level} /><div className="bar-track"><span className={`bar bar-${level.toLowerCase()}`} style={{ width: `${total ? counts[level] / total * 100 : 0}%` }} /></div><strong>{counts[level]}</strong></div>)}
  </div>;
}

export function RepositoryLink({ id, children }: { id: string; children: ReactNode }) {
  return <Link to={`/repositories/${id}`}>{children}</Link>;
}
