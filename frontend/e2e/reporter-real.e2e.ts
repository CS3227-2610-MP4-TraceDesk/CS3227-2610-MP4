import AxeBuilder from '@axe-core/playwright'
import { createHash } from 'node:crypto'
import { expect, test } from '@playwright/test'
import {
  captureReporterEvidence,
  ReportRealBackend,
  type ReporterFixtureMetadata,
} from './helpers/reportRealBackend'

const fixture = new ReportRealBackend()
let accounts: ReporterFixtureMetadata

test.beforeAll(async () => {
  // Cold CI Gradle compilation is part of setup; the fixture itself has a 90s deadline.
  test.setTimeout(120_000)
  accounts = await fixture.start()
})

test.afterAll(async () => {
  await fixture.stop()
})

test('real HTTPS session creates, lists, reads, denies and restores a Reporter report', async ({ page, browser }) => {
  const password = fixture.credentials.password
  const formField = (name: string) => page.getByRole('textbox', { name: new RegExp(`^${name}\\b`, 'i') })
  const reportTitle = 'Browser report <img src=x onerror=window.__reportInjected=true>'
  const description = 'Untrusted text: <script>window.__reportInjected=true</script>'
  const logText = '<img src=x onerror=window.__logsInjected=true>\nsynthetic trace'
  const postResponses: number[] = []
  let outageAttempt = false
  const outageRequests: Array<{ hash: string; key: string | undefined }> = []
  const mutationContracts: Array<{ keys: string; hasCsrf: boolean; sameOrigin: boolean; hasIdempotency: boolean }> = []
  page.on('response', (response) => {
    if (response.request().method() === 'POST' && /\/api\/projects\/[^/]+\/reports$/.test(response.url())) {
      postResponses.push(response.status())
    }
  })
  page.on('request', (request) => {
    if (request.method() === 'POST' && /\/api\/projects\/[^/]+\/reports$/.test(request.url())) {
      const body = JSON.parse(request.postData() ?? 'null') as Record<string, unknown>
      mutationContracts.push({
        keys: Object.keys(body).sort().join(','),
        hasCsrf: Boolean(request.headers()['x-csrf-token']),
        sameOrigin: new URL(request.url()).origin === new URL(page.url()).origin,
        hasIdempotency: Boolean(request.headers()['idempotency-key']),
      })
    }
    if (outageAttempt && request.method() === 'POST' && /\/api\/projects\/[^/]+\/reports$/.test(request.url())) {
      outageRequests.push({
        hash: createHash('sha256').update(request.postData() ?? '').digest('hex'),
        key: request.headers()['idempotency-key'],
      })
    }
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()

  // Establish an anonymous session first; login must rotate it server-side.
  await page.evaluate(async () => {
    await fetch('/api/csrf', { credentials: 'same-origin', cache: 'no-store' })
  })
  const anonymousCookie = (await page.context().cookies()).find((cookie) => cookie.name === 'TDSESSION')
  if (!anonymousCookie) throw new Error('The HTTPS API did not issue its anonymous session cookie')
  await page.getByLabel('Email', { exact: true }).fill(accounts.login)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByLabel('Password', { exact: true }).press('Enter')
  await expect(page.getByLabel('Project ID', { exact: true })).toBeVisible()
  const authenticatedCookie = (await page.context().cookies()).find((cookie) => cookie.name === 'TDSESSION')
  if (!authenticatedCookie || authenticatedCookie.value === anonymousCookie.value) {
    throw new Error('Login did not rotate the anonymous session identifier')
  }
  expect(authenticatedCookie.secure).toBe(true)
  expect(authenticatedCookie.httpOnly).toBe(true)
  expect(authenticatedCookie.sameSite).toBe('Lax')

  await page.getByLabel('Project ID', { exact: true }).fill(accounts.projectId)
  await page.getByLabel('Project ID', { exact: true }).press('Enter')
  await expect(page.getByText('Reports for Synthetic Reporter Project', { exact: true })).toBeVisible()
  await expect(page.getByText('Reporter', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Light theme' }).click()
  await page.getByRole('button', { name: 'Reports' }).click()
  await expect(page.getByRole('heading', { name: 'Reports', level: 1 })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'No reports yet' })).toBeVisible()
  await captureReporterEvidence(page, browser.version(), 'reporter-empty-list')
  await page.getByLabel('Filter by status', { exact: true }).selectOption('CLOSED')
  await expect(page.getByRole('heading', { name: 'No reports match this status' })).toBeVisible()
  await page.getByRole('button', { name: 'Clear filter' }).click()
  await page.getByRole('main').getByRole('button', { name: 'New report' }).first().click()
  await expect(formField('Title')).toBeVisible()

  for (const label of [
    'Title', 'Description', 'Environment', 'Steps to reproduce',
    'Expected behavior', 'Actual behavior', 'Logs or error text',
  ]) {
    await expect(formField(label)).toBeVisible()
  }
  const createButton = page.getByRole('button', { name: 'Create report' })
  await createButton.focus()
  await createButton.press('Enter')
  await expect(formField('Title')).toHaveAttribute('aria-invalid', 'true')
  await expect(formField('Title')).toBeFocused()
  await expect.poll(() => page.evaluate(() => document.activeElement?.matches(':focus-visible') ?? false))
    .toBe(true)
  expect(postResponses).toHaveLength(0)
  await formField('Title').fill(reportTitle)
  await formField('Description').fill(description)
  await formField('Environment').fill('Synthetic browser fixture, Chromium over HTTPS')
  await formField('Steps to reproduce').fill('1. Open the report workspace.\n2. Observe the saved report.')
  await formField('Expected behavior').fill('The saved report is displayed as plain text.')
  await formField('Actual behavior').fill('The synthetic browser fixture created this report.')
  await formField('Logs or error text').fill(logText)

  const axe = await new AxeBuilder({ page }).analyze()
  expect(axe.violations.map(({ id, impact }) => `${id}:${impact}`)).toEqual([])
  const desktop = await captureReporterEvidence(page, browser.version(), 'reporter-composer-desktop-light')
  expect(desktop.horizontalOverflow).toBe(false)
  expect(desktop.scrollTop.document).toBe(0)
  expect(desktop.form?.y ?? -1).toBeGreaterThanOrEqual(0)
  expect(desktop.form?.width ?? 0).toBeLessThanOrEqual(816)
  expect(desktop.shellHeader?.height).toBe(52)
  expect(desktop.sidebar?.width).toBe(275)
  await page.setViewportSize({ width: 390, height: 844 })
  const composer390 = await captureReporterEvidence(page, browser.version(), 'reporter-composer-390')
  expect(composer390.horizontalOverflow).toBe(false)
  expect(composer390.submit).not.toBeNull()
  const sidebarToggle = page.getByRole('button', { name: 'Toggle sidebar' })
  await sidebarToggle.click()
  await expect(page.getByRole('button', { name: 'Close navigation' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(sidebarToggle).toBeFocused()
  await page.setViewportSize({ width: 320, height: 844 })
  const composer320 = await captureReporterEvidence(page, browser.version(), 'reporter-composer-320')
  expect(composer320.horizontalOverflow).toBe(false)
  expect(composer320.submit).not.toBeNull()
  expect(composer320.rail?.width).toBe(40)
  if ((composer320.submit?.x ?? -1) + (composer320.submit?.width ?? 0) > 320) {
    throw new Error('The create action clips outside the 320px viewport')
  }
  await page.setViewportSize({ width: 1440, height: 900 })
  await expect(formField('Description')).toHaveValue(description)

  await expect(createButton).toBeEnabled()
  await createButton.dblclick()
  await expect(page.getByRole('heading', { name: reportTitle })).toBeVisible()
  await expect(page.getByText(/^open$/i)).toBeVisible()
  expect(postResponses.filter((status) => status === 201)).toHaveLength(1)
  expect(postResponses).toHaveLength(1)
  for (const contract of mutationContracts) {
    if (contract.keys !== 'actualBehavior,description,environment,expectedBehavior,reproductionSteps,textLogs,title' ||
        !contract.hasCsrf || !contract.sameOrigin || !contract.hasIdempotency) {
      throw new Error('Report mutation violated its same-origin, CSRF, idempotency or seven-field wire contract')
    }
  }
  await expect(page.locator('.report-detail img[onerror], .report-detail script')).toHaveCount(0)
  await expect(page.getByText(description, { exact: true })).toBeVisible()
  await expect(page.getByText(logText, { exact: true })).toBeVisible()
  const desktopDetail = await captureReporterEvidence(page, browser.version(), 'reporter-detail-desktop')
  expect(desktopDetail.horizontalOverflow).toBe(false)
  expect(await page.evaluate(() => (window as Window & { __reportInjected?: boolean; __logsInjected?: boolean }).__reportInjected)).toBeUndefined()
  expect(await page.evaluate(() => (window as Window & { __reportInjected?: boolean; __logsInjected?: boolean }).__logsInjected)).toBeUndefined()

  await page.getByRole('button', { name: 'Back to reports' }).click()
  const savedReport = page.getByRole('main').getByRole('button', { name: reportTitle })
  await expect(savedReport).toBeVisible()
  await captureReporterEvidence(page, browser.version(), 'reporter-list-after-create')
  await savedReport.click()
  await expect(page.getByRole('heading', { name: reportTitle })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Reports', level: 1 })).toBeVisible()
  const persistedReport = page.getByRole('main').getByRole('button', { name: reportTitle })
  await expect(persistedReport).toBeVisible()
  await persistedReport.click()
  await expect(page.getByRole('heading', { name: reportTitle })).toBeVisible()
  await expect(page.getByText(logText, { exact: true })).toBeVisible()

  // Break the real mutation's audit insert locally, observe the 503, restore
  // the table, and retry the frozen body and key.
  await page.getByRole('button', { name: 'Back to reports' }).click()
  await page.getByRole('main').getByRole('button', { name: 'New report' }).first().click()
  const outageValues = [
    ['Title', 'Temporary audit outage keeps this submission'],
    ['Description', 'Retain this exact body through an unknown outcome.'],
    ['Environment', 'Synthetic isolated PostgreSQL outage'],
    ['Steps to reproduce', '1. Submit while the fixture audit table is unavailable.'],
    ['Expected behavior', 'Retry the same immutable report.'],
    ['Actual behavior', 'The API reports a temporary unavailable response.'],
    ['Logs or error text', 'No credential material is included.'],
  ] as const
  const outageTitle = outageValues[0][1]
  for (const [label, value] of outageValues) await formField(label).fill(value)
  await fixture.setAuditAvailable(false)
  outageAttempt = true
  await page.getByRole('button', { name: 'Create report' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'outcome is unconfirmed' })).toBeVisible()
  for (const [label, value] of outageValues) {
    await expect(formField(label)).toBeDisabled()
    await expect(formField(label)).toHaveValue(value)
  }
  expect(postResponses.at(-1)).toBe(503)
  await fixture.setAuditAvailable(true)
  await page.getByRole('button', { name: 'Retry submission' }).click()
  await expect(page.getByRole('heading', { name: outageTitle })).toBeVisible()
  expect(postResponses.at(-1)).toBe(201)
  if (outageRequests.length !== 2 || outageRequests[0]?.hash !== outageRequests[1]?.hash ||
      !outageRequests[0]?.key || outageRequests[0].key !== outageRequests[1]?.key) {
    throw new Error('Unknown-outcome retry changed its immutable body or idempotency key')
  }
  if (mutationContracts.length !== 3) throw new Error('Expected one initial submission and one same-key retry after the real 503')
  outageAttempt = false

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('heading', { name: outageTitle })).toBeVisible()
  const mobile390 = await captureReporterEvidence(page, browser.version(), 'reporter-detail-390')
  expect(mobile390.horizontalOverflow).toBe(false)
  await page.setViewportSize({ width: 320, height: 844 })
  const mobile320 = await captureReporterEvidence(page, browser.version(), 'reporter-detail-320')
  expect(mobile320.horizontalOverflow).toBe(false)
  expect(mobile320.submit).toBeNull()
  await page.setViewportSize({ width: 1440, height: 900 })

  // A project UUID is only an untrusted target; membership is checked by the API.
  await page.goto(`/?project=${accounts.deniedProjectId}`)
  await expect(page.getByRole('heading', { name: 'Project unavailable' })).toBeVisible()
  await expect(page.getByText('Synthetic Reporter Project')).toHaveCount(0)
  await expect(page.getByText(reportTitle)).toHaveCount(0)
  await expect(page.getByText(outageTitle)).toHaveCount(0)
  await expect(page.getByText('Synthetic Unavailable Project')).toHaveCount(0)

  await page.goto('/')
  await page.getByRole('button', { name: 'Settings' }).click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()

  await page.getByLabel('Email', { exact: true }).fill(accounts.nonmemberLogin)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByLabel('Password', { exact: true }).press('Enter')
  await expect(page.getByLabel('Project ID', { exact: true })).toBeVisible()
  await page.goto(`/?project=${accounts.projectId}`)
  await expect(page.getByText('Synthetic Reporter Project')).toHaveCount(0)
  await expect(page.getByText(reportTitle)).toHaveCount(0)
  await expect(page.getByText(outageTitle)).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Project unavailable' })).toBeVisible()

  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Email', { exact: true }).fill(accounts.developerLogin)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByLabel('Password', { exact: true }).press('Enter')
  await expect(page.getByLabel('Project ID', { exact: true })).toBeVisible()
  await page.goto(`/?project=${accounts.projectId}`)
  await expect(page.getByRole('heading', { name: 'Project unavailable' })).toBeVisible()
  await expect(page.getByText('Synthetic Reporter Project')).toHaveCount(0)
  await expect(page.getByText(reportTitle)).toHaveCount(0)
  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Email', { exact: true }).fill(accounts.login)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByLabel('Password', { exact: true }).press('Enter')
  await expect(page.getByRole('heading', { name: 'Reports', level: 1 })).toBeVisible()
  await page.goto(`/?project=${accounts.projectId}`)
  await expect(page.getByText('Reports for Synthetic Reporter Project', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Dark theme' }).click()
  await page.getByRole('button', { name: 'Reports' }).click()
  await page.getByRole('main').getByRole('button', { name: 'New report' }).first().click()
  await formField('Title').fill('Theme and draft preservation check')
  const darkAxe = await new AxeBuilder({ page }).analyze()
  expect(darkAxe.violations.map(({ id, impact }) => `${id}:${impact}`)).toEqual([])
  await captureReporterEvidence(page, browser.version(), 'reporter-composer-dark')
  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Light theme' }).click()
  await page.getByRole('button', { name: 'Reports' }).click()
  await expect(formField('Title')).toHaveValue('Theme and draft preservation check')
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.setViewportSize({ width: 320, height: 844 })
  const responsiveComposer = await captureReporterEvidence(page, browser.version(), 'reporter-composer-320-reduced-motion')
  expect(responsiveComposer.reducedMotion).toBe(true)
  expect(responsiveComposer.horizontalOverflow).toBe(false)
  expect(responsiveComposer.shellTransition).toBe('none')

  await fixture.invalidateReporterSessions()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Email', { exact: true }).fill(accounts.login)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByLabel('Password', { exact: true }).press('Enter')
  await expect(page.getByRole('heading', { name: 'Reports', level: 1 })).toBeVisible()
  await expect(page.getByRole('main').getByRole('button', { name: reportTitle })).toBeVisible()
  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
})

test('real saved draft AI preview applies only explicit selected changes through versioned PATCH', async ({ page, browser }) => {
  test.setTimeout(60_000)
  await page.goto('/')
  await page.getByLabel('Email', { exact: true }).fill(accounts.login)
  await page.getByLabel('Password', { exact: true }).fill(fixture.credentials.password)
  await page.getByLabel('Password', { exact: true }).press('Enter')
  await expect(page.getByLabel('Project ID', { exact: true })).toBeVisible()
  await page.goto(`/?project=${accounts.projectId}`)
  await page.getByRole('main').getByRole('button', { name: 'New report' }).first().click()
  const field = (name: string) => page.getByRole('textbox', { name: new RegExp(`^${name}\\b`, 'i') })
  const originalDescription = 'A synthetic Java exception needs clearer reproduction details.'
  const logs = 'AI_DO_NOT_SEND_LOG_CANARY <script>window.__aiLogInjected=true</script>'
  for (const [label, value] of [
    ['Title', 'Original Java report'], ['Description', originalDescription],
    ['Environment', 'Java 25 on a synthetic local test'], ['Steps to reproduce', '1. Run the synthetic sample.'],
    ['Expected behavior', 'The sample completes.'], ['Actual behavior', 'The sample throws an exception.'],
    ['Logs or error text', logs],
  ]) await field(label).fill(value)
  await expect(page.locator('summary').filter({ hasText: 'Review with AI' })).toHaveCount(0)
  const savedResponse = page.waitForResponse(response => response.request().method() === 'POST' && /\/drafts$/.test(new URL(response.url()).pathname))
  await page.getByRole('button', { name: 'Save draft', exact: true }).click()
  const saved = await (await savedResponse).json() as { id: string; version: number }
  await expect(page.getByRole('heading', { name: 'Resume draft' })).toBeVisible()
  const patchBodies: Array<Record<string, unknown>> = []
  const suggestionBodies: Array<Record<string, unknown>> = []
  page.on('request', request => {
    if (request.method() === 'PATCH' && request.url().includes(`/drafts/${saved.id}`)) patchBodies.push(request.postDataJSON() as Record<string, unknown>)
    if (request.method() === 'POST' && request.url().endsWith(`/drafts/${saved.id}/suggestions`)) suggestionBodies.push(request.postDataJSON() as Record<string, unknown>)
  })
  await page.locator('summary').filter({ hasText: 'Review with AI' }).click()
  await expect(page.getByText(/Logs are excluded/)).toBeVisible()
  await fixture.setAiAvailable(false)
  await page.getByRole('button', { name: 'Generate suggestions' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'AI review is unavailable' })).toBeVisible()
  await expect(field('Title')).toHaveValue('Original Java report')
  await expect(field('Logs or error text')).toHaveValue(logs)
  expect(patchBodies).toHaveLength(0)
  await fixture.setAiAvailable(true)
  await page.getByRole('button', { name: 'Generate suggestions' }).click()
  const replacement = 'Clearer Java report <img src=x onerror=window.__aiInjected=true>'
  await expect(page.getByText(replacement, { exact: true })).toBeVisible()
  await expect(page.getByText('Which Java version reproduces this issue?', { exact: true })).toBeVisible()
  await expect(page.locator('.report-ai-preview img, .report-ai-preview script')).toHaveCount(0)
  expect(await page.evaluate(() => (window as Window & { __aiInjected?: boolean }).__aiInjected)).toBeUndefined()
  expect(patchBodies).toHaveLength(0)
  expect(suggestionBodies).toEqual([{ expectedVersion: saved.version }, { expectedVersion: saved.version }])
  const axe = await new AxeBuilder({ page }).analyze()
  expect(axe.violations.map(({ id, impact }) => `${id}:${impact}`)).toEqual([])
  await captureReporterEvidence(page, browser.version(), 'reporter-ai-preview-desktop')
  await page.setViewportSize({ width: 320, height: 844 })
  const mobile = await captureReporterEvidence(page, browser.version(), 'reporter-ai-preview-320')
  expect(mobile.horizontalOverflow).toBe(false)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.getByRole('checkbox', { name: 'Apply Title', exact: true }).check()
  const appliedResponse = page.waitForResponse(response => response.request().method() === 'PATCH' && response.url().includes(`/drafts/${saved.id}`))
  await page.getByRole('button', { name: 'Apply selected changes' }).click()
  const applied = await appliedResponse
  expect(applied.status()).toBe(200)
  await expect(field('Title')).toHaveValue(replacement)
  await expect(field('Description')).toHaveValue(originalDescription)
  await expect(field('Logs or error text')).toHaveValue(logs)
  expect(patchBodies).toHaveLength(1)
  expect(patchBodies[0]?.expectedVersion).toBe(saved.version)
  expect(patchBodies[0]?.textLogs).toBe(logs)
  const persisted = await page.request.get(`/api/projects/${accounts.projectId}/drafts/${saved.id}`)
  expect(persisted.ok()).toBe(true)
  const current = await persisted.json() as { title: string; description: string; textLogs: string; version: number }
  expect(current).toMatchObject({ title: replacement, description: originalDescription, textLogs: logs, version: saved.version + 1 })
})
