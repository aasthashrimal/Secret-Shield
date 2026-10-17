import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { createApi } from './api';
import type { User } from './types';

type Request = ReturnType<typeof createApi>;
type Session = { user: User | null; request: Request; login: (email: string, password: string) => Promise<void>; register: (username: string, email: string, password: string) => Promise<void>; logout: () => void };
const Context = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const logout = useCallback(() => { setToken(null); setUser(null); }, []);
  const request = useMemo(() => createApi(() => token, logout), [token, logout]);
  const login = useCallback(async (email: string, password: string) => {
    const result = await request<{ token: string; username: string; email: string }>(
      '/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
    const me = await createApi(() => result.token, logout)<User>('/api/auth/me');
    setToken(result.token);
    setUser(me);
  }, [request, logout]);
  const register = useCallback(async (username: string, email: string, password: string) => {
    await request('/api/auth/register', { method: 'POST', body: JSON.stringify({ username, email, password }) });
    await login(email, password);
  }, [request, login]);
  return <Context.Provider value={{ user, request, login, register, logout }}>{children}</Context.Provider>;
}

export function useSession() {
  const session = useContext(Context);
  if (!session) throw new Error('SessionProvider missing');
  return session;
}
