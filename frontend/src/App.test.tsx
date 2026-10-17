import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import App from './App';
import { SessionProvider } from './session';
import { overviewFixture, page } from './fixtures';

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it('logs in, loads the dashboard, and redirects after the next request expires', async () => {
  const fetchMock = vi.fn().mockImplementation((url: string) => {
    if (url === '/api/auth/login') return Promise.resolve(Response.json({ token: 'synthetic-token', username: 'demo', email: 'demo@example.invalid' }));
    if (url === '/api/auth/me') return Promise.resolve(Response.json({ id: '1', username: 'demo', email: 'demo@example.invalid', role: 'DEVELOPER' }));
    if (url === '/api/dashboard/overview') return Promise.resolve(Response.json(overviewFixture));
    if (url.startsWith('/api/repositories?')) return Promise.resolve(Response.json({ code: 'UNAUTHORIZED' }, { status: 401 }));
    throw new Error('Unexpected URL');
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<MemoryRouter initialEntries={['/login']}><SessionProvider><App /></SessionProvider></MemoryRouter>);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), 'demo@example.invalid');
  await user.type(screen.getByLabelText('Password'), 'synthetic-pass');
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
  await screen.findByRole('heading', { name: 'Overview' });
  expect(await screen.findByText('Findings by severity')).toBeTruthy();
  await user.click(screen.getByRole('link', { name: /^Repositories$/ }));
  await waitFor(() => expect(screen.getByRole('heading', { name: 'Welcome back' })).toBeTruthy());
  expect(fetchMock.mock.calls.some(call => call[0] === '/api/dashboard/overview')).toBe(true);
});

it('shows the empty repository state after login and logs out', async () => {
  vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => {
    if (url === '/api/auth/login') return Promise.resolve(Response.json({ token: 'synthetic-token' }));
    if (url === '/api/auth/me') return Promise.resolve(Response.json({ id: '1', username: 'demo', email: 'demo@example.invalid', role: 'DEVELOPER' }));
    if (url === '/api/dashboard/overview') return Promise.resolve(Response.json(overviewFixture));
    if (url.startsWith('/api/repositories?')) return Promise.resolve(Response.json(page([])));
    throw new Error('Unexpected URL');
  }));
  render(<MemoryRouter initialEntries={['/login']}><SessionProvider><App /></SessionProvider></MemoryRouter>);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), 'demo@example.invalid');
  await user.type(screen.getByLabelText('Password'), 'synthetic-pass');
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
  await user.click(await screen.findByRole('link', { name: /^Repositories$/ }));
  await screen.findByText('No records yet.');
  await user.click(screen.getByRole('button', { name: 'Log out' }));
  await screen.findByRole('heading', { name: 'Welcome back' });
});
