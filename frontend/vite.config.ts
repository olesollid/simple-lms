import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}', 'tests/**/*.test.mjs'],
  },
  server: {
    // These paths aren't under /api -- they're served directly by Spring Security's own
    // filters (OAuth2 login/callback and logout), so each needs its own proxy entry.
    // '/login/oauth2' (not a bare '/login') is the OAuth2 callback path
    // ('/login/oauth2/code/{registrationId}'); keep it scoped so it can never shadow a future
    // frontend route that happens to start with '/login'.
    proxy: {
      '/api': 'http://localhost:8080',
      '/logout': 'http://localhost:8080',
      '/oauth2': 'http://localhost:8080',
      '/login/oauth2': 'http://localhost:8080',
    },
  },
})
