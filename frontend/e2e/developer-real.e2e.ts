import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import {
  captureDeveloperEvidence,
  ReportRealBackend,
  type ReporterFixtureMetadata,
} from './helpers/reportRealBackend'

const fixture = new ReportRealBackend()
let accounts: ReporterFixtureMetadata

test.beforeAll(async () => {
  test.setTimeout(120_000)
  accounts = await fixture.start()
})

test.afterAll(async () => {
  await fixture.stop()
})

test('real Developer workspace enforces assignment, versions, sessions and responsive access', async ({ page, browser }) => {
  test.setTimeout(90_000)
  const assignedTitle = 'Assigned browser report <img src=x onerror=window.__developerInjected=true>'
  const unassignedTitle = 'Unassigned browser report'

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/')
  await page.getByLabel('Email', { exact: true }).fill(accounts.developerLogin)
  await page.getByLabel('Password', { exact: true }).fill(fixture.credentials.password)
  await page.getByLabel('Password', { exact: true }).press('Enter')
  await expect(page.getByRole('heading', { name: 'Choose a project' })).toBeVisible()
  await page.getByRole('button', { name: 'Synthetic Reporter Project' }).click()
  await expect(page.getByRole('heading', { name: 'My work' })).toBeVisible()
  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Light theme' }).click()
  await expect.poll(() => page.evaluate(() => document.documentElement.dataset.theme)).toBe('light')
  await page.getByRole('button', { name: 'My work' }).click()
  await expect(page.getByRole('heading', { name: 'My work' })).toBeVisible()
  await expect(page.getByRole('button', { name: assignedTitle })).toBeVisible()
  await expect(page.getByText(unassignedTitle, { exact: true })).toHaveCount(0)

  await page.getByRole('button', { name: 'All bugs' }).click()
  await expect(page.getByRole('heading', { name: 'All bugs' })).toBeVisible()
  await expect(page.getByRole('button', { name: assignedTitle })).toBeVisible()
  await expect(page.getByRole('button', { name: unassignedTitle })).toBeVisible()
  await page.getByRole('button', { name: unassignedTitle }).click()
  await expect(page.getByText(/· Unassigned$/)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Private notes' })).toBeVisible()
  await expect(page.getByText(/only to the current assignee/i)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Start investigation' })).toHaveCount(0)

  await page.getByRole('button', { name: 'Back to bugs' }).click()
  await page.getByRole('button', { name: assignedTitle }).click()
  await expect(page.getByRole('heading', { name: assignedTitle })).toBeVisible()
  await expect(page.getByText(/· Assigned to you$/)).toBeVisible()
  await expect(page.locator('.developer-detail script, .developer-detail img[onerror], .developer-detail b')).toHaveCount(0)
  expect(await page.evaluate(() => (window as Window & { __developerInjected?: boolean }).__developerInjected)).toBeUndefined()

  const lightAxe = await new AxeBuilder({ page }).analyze()
  expect(lightAxe.violations.map(({ id, impact }) => `${id}:${impact}`)).toEqual([])
  const desktop = await captureDeveloperEvidence(page, browser.version(), 'developer-detail-desktop-light')
  expect(desktop.horizontalOverflow).toBe(false)
  expect(desktop.shellHeader?.height).toBe(52)
  expect(desktop.sidebar?.width).toBe(275)

  await fixture.advanceDeveloperReportVersion()
  await page.getByRole('button', { name: 'Start investigation' }).click()
  await expect(page.getByRole('status').filter({ hasText: /refresh this report/i })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Start investigation' })).toBeVisible()
  await page.getByRole('button', { name: 'Back to bugs' }).click()
  await page.getByRole('button', { name: assignedTitle }).click()
  await expect(page.getByText('Version 2', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Start investigation' }).click()
  await expect(page.getByText(/server confirmed the change/i)).toBeVisible()
  await expect(page.getByText('In progress', { exact: true })).toBeVisible()

  const noteText = 'Real backend private note <script>window.__noteInjected=true</script>'
  await page.getByLabel('Add a private note').fill(noteText)
  await page.getByRole('button', { name: 'Save note' }).click()
  await expect(page.getByText(noteText, { exact: true })).toBeVisible()
  await expect(page.locator('.developer-note script')).toHaveCount(0)
  expect(await page.evaluate(() => (window as Window & { __noteInjected?: boolean }).__noteInjected)).toBeUndefined()

  const rootCause = 'Real backend root cause <script>window.__resolutionInjected=true</script>'
  await page.getByLabel('Root cause').fill(rootCause)
  await page.getByLabel('Change summary').fill('Bounded synthetic change summary')
  await page.getByLabel('Verification steps').fill('1. Replay the synthetic steps')
  await page.getByLabel('Fix reference (optional)').fill('synthetic-branch')
  await fixture.advanceDeveloperReportVersion()
  await page.getByRole('button', { name: 'Send to QA' }).click()
  await expect(page.getByRole('status').filter({ hasText: /not confirmed/i })).toBeVisible()
  await expect(page.getByLabel('Root cause')).toHaveValue(rootCause)
  await page.getByRole('button', { name: 'Refresh bug' }).click()
  await expect(page.getByLabel('Root cause')).toHaveValue(rootCause)
  await page.getByRole('button', { name: 'Send to QA' }).click()
  await expect(page.getByText(/sent to QA after server confirmation/i)).toBeVisible()
  await expect(page.locator('.developer-detail-top').getByText('Ready for QA', { exact: true })).toBeVisible()
  await expect(page.getByText(rootCause, { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Send to QA' })).toHaveCount(0)
  expect(await page.evaluate(() => (window as Window & { __resolutionInjected?: boolean }).__resolutionInjected)).toBeUndefined()

  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.setViewportSize({ width: 390, height: 844 })
  const mobile390 = await captureDeveloperEvidence(page, browser.version(), 'developer-detail-390-reduced-motion')
  expect(mobile390.horizontalOverflow).toBe(false)
  expect(mobile390.reducedMotion).toBe(true)
  expect(mobile390.shellTransition).toBe('none')
  const sidebarToggle = page.getByRole('button', { name: 'Toggle sidebar' })
  await sidebarToggle.click()
  await expect(page.getByRole('button', { name: 'Close navigation' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(sidebarToggle).toBeFocused()
  await page.setViewportSize({ width: 320, height: 844 })
  const mobile320 = await captureDeveloperEvidence(page, browser.version(), 'developer-detail-320-reduced-motion')
  expect(mobile320.horizontalOverflow).toBe(false)
  expect(mobile320.rail?.width).toBe(40)

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Dark theme' }).click()
  await expect.poll(() => page.evaluate(() => document.documentElement.dataset.theme)).toBe('dark')
  await page.evaluate(async () => {
    const animations = document.querySelector('.developer-settings')?.getAnimations({ subtree: true }) ?? []
    await Promise.all(animations.map(animation => animation.finished.catch(() => undefined)))
  })
  const darkAxe = await new AxeBuilder({ page }).analyze()
  expect(darkAxe.violations.map(({ id, impact, nodes }) => ({
    id,
    impact,
    nodes: nodes.map(node => ({ target: node.target, html: node.html, failureSummary: node.failureSummary })),
  }))).toEqual([])
  await page.getByRole('button', { name: 'My work' }).click()
  await page.getByRole('button', { name: assignedTitle }).click()
  await expect(page.getByRole('heading', { name: assignedTitle })).toBeVisible()

  await page.getByRole('button', { name: 'Edit' }).click()
  const edit = page.getByLabel('Edit private note')
  await edit.fill('Unsaved edit retained only until assignment denial is confirmed')
  await fixture.reassignDeveloperReport()
  await page.getByRole('listitem').filter({ has: edit }).getByRole('button', { name: 'Save note' }).click()
  await expect(page.getByRole('heading', { name: 'Private notes' })).toBeVisible()
  await expect(page.getByText(/only to the current assignee/i)).toBeVisible()
  await expect(page.getByText(/assignment changed/i).first()).toBeVisible()
  await expect(page.getByLabel('Add a private note')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Edit' })).toHaveCount(0)
  await expect(page.getByText(noteText, { exact: true })).toHaveCount(0)
  await expect(page.getByText('Unsaved edit retained only until assignment denial is confirmed', { exact: true })).toHaveCount(0)

  await fixture.invalidateDeveloperSessions()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await expect(page.getByText(noteText, { exact: true })).toHaveCount(0)
})
