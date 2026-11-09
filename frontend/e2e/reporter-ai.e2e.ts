import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'
import { mockAuthApi } from './helpers/authApi'

const projectId = '44b10495-a536-4c6e-a410-93bd2d9330fd'
const id = '76989078-9c82-4ac5-9218-fc2b262f47e9'
const path = `/api/projects/${projectId}/reports/${id}`
const saved = () => ({ id, projectId, reporterUserId: 'f2e29c3e-2b62-4420-a631-b1300f148328', title: 'Synthetic Java failure', description: 'Java application fails', environment: 'Java synthetic', reproductionSteps: 'Open Java application', expectedBehavior: 'Application starts', actualBehavior: 'An exception appears', textLogs: 'PRIVATE_LOG_CANARY', status: 'OPEN', assignedDeveloperId: null, version: 1, createdAt: '2026-11-08T00:00:00Z', updatedAt: '2026-11-08T00:00:00Z' })
const suggestion = () => ({ capability: 'REPORT_IMPROVEMENT', promptVersion: 'report-improvement-v2', sourceKind: 'REPORT', sourceId: id, sourceVersion: 1, changes: [{ field: 'title', replacement: '<img src=x onerror="window.aiExecuted=true">' }, { field: 'description', replacement: 'A Java startup exception appears.' }], missingInformation: ['<script>window.aiExecuted=true</script>Which Java version?'] })

async function setup(page: Page, options: { generate?: (route: Route) => Promise<void>; stale?: boolean } = {}) {
  await mockAuthApi(page, { workspaces: [{ project: { id: projectId, name: 'Synthetic AI project' }, role: 'REPORTER' }] })
  await page.context().addCookies([{ name: 'TDSESSION', value: 'synthetic-session', url: 'https://localhost:4177', secure: true, httpOnly: true, sameSite: 'Lax' }])
  const state = { current: saved(), generations: 0, patches: [] as Record<string, unknown>[] }
  await page.route('**/api/projects/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/reporter-context')) return route.fulfill({ json: { project: { id: projectId, name: 'Synthetic AI project' }, role: 'REPORTER' } })
    if (url.pathname === `/api/projects/${projectId}/drafts`) return route.fulfill({ json: { items: [], nextCursor: null } })
    if (url.pathname === `/api/projects/${projectId}/reports`) return route.fulfill({ json: { items: [state.current], nextCursor: null } })
    if (url.pathname === `${path}/suggestions`) {
      state.generations += 1
      expect(route.request().postDataJSON()).toEqual({ expectedVersion: 1 })
      return options.generate ? options.generate(route) : route.fulfill({ json: suggestion() })
    }
    if (url.pathname === path && route.request().method() === 'PATCH') {
      const body = route.request().postDataJSON() as Record<string, unknown>
      state.patches.push(body)
      if (options.stale) { state.current = { ...state.current, title: 'Newer server title', version: 2 }; return route.fulfill({ status: 409, json: { error: { code: 'STALE_VERSION' } } }) }
      state.current = { ...state.current, ...body, version: state.current.version + 1 }
      return route.fulfill({ json: state.current })
    }
    if (url.pathname === path) return route.fulfill({ json: state.current })
    return route.fulfill({ status: 404, json: { error: { code: 'NOT_FOUND' } } })
  })
  await page.goto(`/?project=${projectId}`)
  await page.getByRole('button', { name: /^Synthetic Java failure/ }).first().click()
  await page.getByRole('button', { name: 'Edit report', exact: true }).click()
  return state
}
async function generate(page: Page) {
  await page.locator('summary').filter({ hasText: 'Review with AI' }).click()
  await expect(page.getByText(/Logs are excluded/)).toBeVisible()
  await page.getByRole('button', { name: 'Generate suggestions', exact: true }).click()
}

test('AI preview is inert and explicit selected apply preserves logs and unselected fields', async ({ page }) => {
  const state = await setup(page)
  expect(state.generations).toBe(0)
  await generate(page)
  await expect(page.getByRole('heading', { name: 'Suggested changes' })).toBeVisible()
  expect(state.patches).toHaveLength(0)
  await page.screenshot({ path: 'e2e-results/reporter-ai-desktop.png', fullPage: true })
  await expect(page.getByRole('region', { name: 'AI report review' }).locator('img,script')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Apply selected changes' })).toBeDisabled()
  await page.getByRole('checkbox', { name: 'Apply Description', exact: true }).check()
  await page.getByRole('button', { name: 'Apply selected changes' }).click()
  await expect(page.getByRole('heading', { name: 'Synthetic Java failure', exact: true })).toBeVisible()
  expect(state.patches).toEqual([{ ...Object.fromEntries(Object.entries(saved()).filter(([key]) => ['title', 'description', 'environment', 'reproductionSteps', 'expectedBehavior', 'actualBehavior', 'textLogs'].includes(key))), description: 'A Java startup exception appears.', expectedVersion: 1 }])
})

test('a dirty editor cancels delivery, preserves human text and requires saving first', async ({ page }) => {
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const state = await setup(page, { generate: async route => { await gate; await route.fulfill({ json: suggestion() }).catch(() => {}) } })
  await generate(page)
  await expect(page.getByRole('status')).toContainText('Reviewing')
  await page.getByLabel(/^Title\b/).fill('Human text remains')
  release()
  await expect(page.getByRole('region', { name: 'AI report review' })).toHaveCount(0)
  await expect(page.getByText(/save your latest edits before using AI/)).toBeVisible()
  await expect(page.getByLabel(/^Title\b/)).toHaveValue('Human text remains')
  expect(state.patches).toHaveLength(0)
})

for (const action of ['cancel', 'leave'] as const) test(`${action} discards late suggestions without applying or retrying`, async ({ page }) => {
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const state = await setup(page, { generate: async route => { await gate; await route.fulfill({ json: suggestion() }).catch(() => {}) } })
  await generate(page)
  await page.getByRole('button', { name: action === 'cancel' ? 'Cancel AI review' : 'Cancel', exact: true }).click()
  release()
  if (action === 'cancel') await expect(page.getByRole('status')).toContainText('cancelled')
  else await expect(page.getByRole('button', { name: 'Edit report', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Suggested changes' })).toHaveCount(0)
  expect(state.generations).toBe(1)
  expect(state.patches).toHaveLength(0)
})

for (const code of ['AI_UNAVAILABLE', 'CSRF_INVALID', 'AI_INVALID_OUTPUT']) test(`generation ${code} is safe and never automatically replayed`, async ({ page }) => {
  const state = await setup(page, { generate: route => route.fulfill({ status: code === 'CSRF_INVALID' ? 403 : code === 'AI_INVALID_OUTPUT' ? 502 : 503, json: { error: { code } } }) })
  await generate(page)
  await expect(page.getByRole('region', { name: 'AI report review' }).getByRole('status')).toContainText(code === 'CSRF_INVALID' ? 'security token expired' : code === 'AI_INVALID_OUTPUT' ? 'invalid suggestion' : 'AI review is unavailable')
  expect(state.generations).toBe(1)
  expect(state.patches).toHaveLength(0)
  await expect(page.getByLabel(/^Title\b/)).toBeEnabled()
})

test('stale apply preserves human source and requires explicit current-version review', async ({ page }) => {
  const state = await setup(page, { stale: true })
  await generate(page)
  await page.getByRole('checkbox', { name: 'Apply Description', exact: true }).check()
  await page.getByRole('button', { name: 'Apply selected changes' }).click()
  await expect(page.getByRole('heading', { name: 'Review the current report' })).toBeVisible()
  await expect(page.getByLabel(/^Description\b/)).toHaveValue(saved().description)
  await expect(page.getByRole('heading', { name: 'Suggested changes' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Save changes', exact: true })).toBeDisabled()
  expect(state.patches).toHaveLength(1)
})

for (const width of [320, 390]) test(`AI disclosure and selectable plain text work with keyboard at ${width}px and pass accessibility`, async ({ page }) => {
  await page.setViewportSize({ width, height: 800 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await setup(page)
  const toggle = page.locator('summary').filter({ hasText: 'Review with AI' })
  await toggle.focus(); await page.keyboard.press('Enter')
  await page.getByRole('button', { name: 'Generate suggestions' }).click()
  const checkbox = page.getByRole('checkbox', { name: 'Apply Title', exact: true })
  await checkbox.focus(); await page.keyboard.press('Space')
  await expect(checkbox).toBeChecked()
  await expect(page.getByRole('button', { name: 'Apply selected changes' })).toBeEnabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: `e2e-results/reporter-ai-${width}.png`, fullPage: true })
  const a11y = await new AxeBuilder({ page }).analyze()
  expect(a11y.violations).toEqual([])
})

for (const action of ['project', 'signout'] as const) test(`pending AI delivery is discarded after ${action} context changes`, async ({ page }) => {
  let release!: () => void
  let delivered = false
  const gate = new Promise<void>(resolve => { release = resolve })
  const state = await setup(page, { generate: async route => { await gate; await route.fulfill({ json: suggestion() }).catch(() => {}); delivered = true } })
  await generate(page)
  await expect.poll(() => state.generations).toBe(1)
  if (action === 'project') {
    await page.getByRole('button', { name: 'Change project', exact: true }).click()
    await expect(page.getByRole('heading', { name: 'Choose a project' })).toBeVisible()
  } else {
    await page.getByRole('button', { name: 'Settings', exact: true }).click()
    await page.getByRole('button', { name: 'Sign out', exact: true }).click()
    await expect(page.getByRole('heading', { name: /Sign in/ })).toBeVisible()
  }
  release()
  await expect.poll(() => delivered).toBe(true)
  await expect(page.getByRole('heading', { name: 'Suggested changes' })).toHaveCount(0)
  expect(state.generations).toBe(1)
  expect(state.patches).toHaveLength(0)
})
