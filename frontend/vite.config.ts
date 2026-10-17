import { defineConfig } from 'vitest/config';
import { loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': loadEnv(mode, '.', 'VITE_').VITE_PROXY_TARGET ?? 'http://localhost:8080' } },
  test: { environment: 'jsdom' },
}));
