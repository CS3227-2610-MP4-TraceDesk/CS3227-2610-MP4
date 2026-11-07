import tailwindcss from '@tailwindcss/vite'
import basicSsl from '@vitejs/plugin-basic-ssl'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

const apiProxyTarget = process.env.TRACEDESK_API_PROXY_TARGET ?? 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [basicSsl(), react(), tailwindcss()],
  server: {
    proxy: {
      '/api': { target: apiProxyTarget, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    // Playwright owns e2e/; vitest must not collect its specs.
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
  },
})
