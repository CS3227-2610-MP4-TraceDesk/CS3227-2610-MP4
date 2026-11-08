import tailwindcss from '@tailwindcss/vite'
import basicSsl from '@vitejs/plugin-basic-ssl'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

const useRealBackend = process.env.TRACEDESK_REAL_BACKEND_TEST === 'true'
const apiProxyTarget = useRealBackend
  ? 'https://127.0.0.1:18089'
  : process.env.TRACEDESK_API_PROXY_TARGET ?? 'http://127.0.0.1:8080'
const apiProxy = { target: apiProxyTarget, changeOrigin: true, secure: !useRealBackend }

export default defineConfig({
  plugins: [basicSsl(), react(), tailwindcss()],
  server: {
    proxy: {
      '/api': apiProxy,
    },
  },
  preview: {
    proxy: {
      '/api': apiProxy,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    // Playwright owns e2e/; vitest must not collect its specs.
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
  },
})
