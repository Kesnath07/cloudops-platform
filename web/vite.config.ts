import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// The browser always calls the API on the same origin under /api. In AWS, CloudFront routes that
// path to the load balancer; locally the dev server proxies it to the Spring Boot process.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/test/**', 'src/**/*.test.{ts,tsx}'],
      reporter: ['text-summary', 'lcov'],
    },
  },
});
