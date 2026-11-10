import { defineConfig, devices } from '@playwright/test'

const port = 4177
const localSetupOnly = process.env.TRACEDESK_LOCAL_SETUP_TEST === 'true'
const liveBrowserOnly = process.env.TRACEDESK_REAL_BACKEND_TEST === 'true' &&
  process.env.TRACEDESK_LIVE_BROWSER_TEST === 'true'

if (localSetupOnly && process.env.TRACEDESK_REAL_BACKEND_TEST !== 'true') {
  throw new Error('LOCAL_SETUP_TEST_REQUIRES_REAL_BACKEND_PROXY')
}
if (localSetupOnly && liveBrowserOnly) {
  throw new Error('LOCAL_SETUP_TEST_CANNOT_RUN_WITH_LIVE_AI_PROJECT')
}
if (localSetupOnly && !process.env.TRACEDESK_LOCAL_SETUP_CREDENTIAL_FILE) {
  throw new Error('LOCAL_SETUP_TEST_REQUIRES_CREDENTIAL_FILE_PATH')
}
if ((localSetupOnly || liveBrowserOnly) && Object.hasOwn(process.env, 'SOCLAAS_API_KEY')) {
  throw new Error('BROWSER_ONLY_TEST_MUST_NOT_RECEIVE_PROVIDER_KEY')
}
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: localSetupOnly || liveBrowserOnly
    ? [['./e2e/helpers/liveBrowserReporter.ts']]
    : process.env.CI
    ? [['list'], ['junit', { outputFile: 'e2e-results/junit.xml' }],
      ['html', { outputFolder: 'e2e-results/html', open: 'never' }]]
    : [['list'], ['html', { outputFolder: 'e2e-results/html', open: 'never' }]],
  outputDir: localSetupOnly ? 'e2e-results/local-setup-artifacts' : 'e2e-results/artifacts',
  use: {
    baseURL: localSetupOnly ? 'https://localhost:4178' : `https://localhost:${port}`,
    ignoreHTTPSErrors: true,
    trace: localSetupOnly ? 'off' : 'retain-on-failure',
    screenshot: localSetupOnly ? 'off' : 'only-on-failure',
    video: 'off',
    ...devices['Desktop Chrome'],
  },
  projects: [
    ...(localSetupOnly ? [{
      name: 'local-reporter-setup',
      testMatch: '**/local-reporter-setup.e2e.ts',
      retries: 0,
      use: {
        baseURL: 'https://localhost:4178',
        ignoreHTTPSErrors: true,
        trace: 'off' as const,
        screenshot: 'off' as const,
        video: 'off' as const,
      },
    }] : []),
    ...(!localSetupOnly && !liveBrowserOnly ? [
      { name: 'production', testMatch: ['**/foundation.e2e.ts', '**/reporter-states.e2e.ts', '**/reporter-ai.e2e.ts', '**/developer-states.e2e.ts'] },
      {
        name: 'development',
        testMatch: ['**/login-preview.e2e.ts', '**/workspace-preview.e2e.ts'],
        use: { baseURL: 'https://localhost:4178', ignoreHTTPSErrors: true },
      },
    ] : []),
    ...(!localSetupOnly && process.env.TRACEDESK_REAL_BACKEND_TEST === 'true' && process.env.TRACEDESK_LIVE_BROWSER_TEST !== 'true' ? [{
      name: 'real-backend',
      testMatch: '**/reporter-real.e2e.ts',
      use: {
        baseURL: `https://localhost:${port}`,
        ignoreHTTPSErrors: true,
        trace: 'off' as const,
        screenshot: 'off' as const,
        video: 'off' as const,
      },
    }] : []),
    ...(!localSetupOnly && liveBrowserOnly ? [{
      name: 'reporter-ai-live-browser',
      testMatch: ['**/reporter-ai-live.e2e.ts'],
      retries: 0,
      use: {
        baseURL: `https://localhost:${port}`,
        ignoreHTTPSErrors: true,
        trace: 'off' as const,
        screenshot: 'off' as const,
        video: 'off' as const,
      },
    }] : []),
  ],
  webServer: localSetupOnly ? [
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 4178 --strictPort',
      url: 'https://localhost:4178',
      ignoreHTTPSErrors: true,
      reuseExistingServer: false,
      stdout: 'ignore',
      stderr: 'ignore',
      timeout: 30_000,
    },
  ] : [
    {
      command: 'npm run preview -- --host 127.0.0.1 --port 4177 --strictPort',
      url: 'https://localhost:4177',
      ignoreHTTPSErrors: true,
      reuseExistingServer: false,
      ...(liveBrowserOnly ? { stdout: 'ignore' as const, stderr: 'ignore' as const } : {}),
      timeout: 30_000,
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 4178 --strictPort',
      url: 'https://localhost:4178',
      ignoreHTTPSErrors: true,
      reuseExistingServer: !process.env.CI,
      ...(liveBrowserOnly ? { stdout: 'ignore' as const, stderr: 'ignore' as const } : {}),
      timeout: 30_000,
    },
  ],
})
