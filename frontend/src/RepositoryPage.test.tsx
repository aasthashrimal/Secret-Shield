import { useEffect } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { IncidentPage } from './RepositoryPage';
import { SessionProvider, useSession } from './session';
import { page, sampleIncident, sampleRepository } from './fixtures';

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

function SignedInIncident() {
  const { user, login } = useSession();
  useEffect(() => { void login('demo@example.invalid', 'synthetic-pass'); }, []);
  return user ? <Routes><Route path="/repositories/:id/incidents/:incidentId" element={<IncidentPage />} /></Routes> : <div>Signing in…</div>;
}

it('submits the incident version and refreshes the status after resolving', async () => {
  let incident = sampleIncident;
  const patches: unknown[] = [];
  vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string, options: RequestInit) => {
    if (url === '/api/auth/login') return Promise.resolve(Response.json({ token: 'synthetic-token' }));
    if (url === '/api/auth/me') return Promise.resolve(Response.json({ id: '2', username: 'demo', email: 'demo@example.invalid', role: 'DEVELOPER' }));
    if (url === '/api/repositories/7/incidents/35' && options?.method === 'PATCH') {
      const body = JSON.parse(String(options.body));
      patches.push(body);
      incident = { ...incident, status: 'RESOLVED', version: incident.version + 1, reviewReason: body.reason };
      return Promise.resolve(Response.json(incident));
    }
    if (url === '/api/repositories/7/incidents/35') return Promise.resolve(Response.json(incident));
    if (url === '/api/repositories/7') return Promise.resolve(Response.json(sampleRepository));
    if (url.startsWith('/api/repositories/7/audit-events?')) return Promise.resolve(Response.json(page([])));
    if (url.startsWith('/api/repositories/7/incidents/35/notifications?')) return Promise.resolve(Response.json(page([])));
    throw new Error(`Unexpected URL ${url}`);
  }));
  render(<MemoryRouter initialEntries={['/repositories/7/incidents/35']}><SessionProvider><SignedInIncident /></SessionProvider></MemoryRouter>);
  const user = userEvent.setup();
  await screen.findByRole('heading', { name: 'Incident #35' });
  await user.type(await screen.findByLabelText('Reason for this change'), 'Synthetic fixture removed and credential rotated.');
  await user.click(screen.getByRole('button', { name: 'Resolve' }));
  await waitFor(() => expect(patches).toEqual([{ status: 'RESOLVED', reason: 'Synthetic fixture removed and credential rotated.', version: 0 }]));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Resolve' }).hasAttribute('disabled')).toBe(true));
  expect(screen.queryByRole('button', { name: 'Ignore' })).toBeNull();
});
