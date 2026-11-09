import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const project = '11111111-1111-4111-8111-111111111111'
const reportId = '22222222-2222-4222-8222-222222222222'
const actor = '44444444-4444-4444-8444-444444444444'
const noteId = '33333333-3333-4333-8333-333333333333'

test('Developer workspace is safe, keyboard reachable, responsive and reduced-motion aware', async ({ page }) => {
  const reportQueries: string[] = []
  let report = {
    id: reportId, projectId: project, reporterUserId: '55555555-5555-4555-8555-555555555555',
    title: '<img src=x onerror=window.__developerInjected=true>', description: '<script>plain report text</script>',
    environment: 'Synthetic browser', reproductionSteps: '1. Use the keyboard\n2. Open the report',
    expectedBehavior: 'Safe text', actualBehavior: 'Safe text remains visible', textLogs: '<b>plain logs</b>',
    status: 'OPEN', assignedDeveloperId: actor, createdAt: '2026-11-08T00:00:00Z',
    updatedAt: '2026-11-08T00:01:00Z', version: 1,
  }
  const resolutions: Record<string, unknown>[] = []
  const notes = [{ id: noteId, reportId, authorUserId: actor, lastEditedByUserId: null, body: '<script>plain private note</script>', createdAt: '2026-11-08T00:02:00Z', updatedAt: '2026-11-08T00:02:00Z', version: 1 }]

  await page.route('**/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const reply = (status: number, body?: unknown) => route.fulfill({ status, contentType: 'application/json', body: body === undefined ? '' : JSON.stringify(body) })
    if (url.pathname === '/api/session') return reply(200, { userId: actor })
    if (url.pathname === '/api/workspaces') return reply(200, { items: [{ project: { id: project, name: 'Synthetic Developer Project' }, role: 'DEVELOPER' }], hasMore: false })
    if (url.pathname === '/api/csrf') return reply(200, { token: 'synthetic-csrf' })
    if (url.pathname.endsWith('/developer-context')) return reply(200, { project: { id: project, name: 'Synthetic Developer Project' }, role: 'DEVELOPER' })
    if (url.pathname.endsWith('/reports') && request.method() === 'GET') {
      reportQueries.push(url.search)
      return reply(200, { items: [report], nextCursor: null })
    }
    if (url.pathname.endsWith(`/reports/${reportId}`) && request.method() === 'GET') return reply(200, report)
    if (url.pathname.endsWith('/investigation/start')) {
      report = { ...report, status: 'IN_PROGRESS', version: 2 }
      return reply(200, report)
    }
    if (url.pathname.endsWith('/notes') && request.method() === 'GET') return reply(200, { items: notes, nextCursor: null })
    if (url.pathname.endsWith('/notes') && request.method() === 'POST') {
      const body = JSON.parse(request.postData() ?? '{}') as { body: string }
      const created = { ...notes[0], id: '66666666-6666-4666-8666-666666666666', body: body.body }
      notes.push(created)
      return reply(201, created)
    }
    if (url.pathname.endsWith('/resolutions') && request.method() === 'GET') return reply(200, { items: resolutions, nextCursor: null })
    if (url.pathname.endsWith('/resolutions') && request.method() === 'POST') {
      const fields = JSON.parse(request.postData() ?? '{}') as { rootCause: string; changeSummary: string; verificationSteps: string; fixReference: string }
      const resolution = { rootCause: fields.rootCause, changeSummary: fields.changeSummary, verificationSteps: fields.verificationSteps, fixReference: fields.fixReference, id: '88888888-8888-4888-8888-888888888888', reportId, authorUserId: actor, revision: 1, createdAt: '2026-11-08T00:03:00Z' }
      resolutions.push(resolution)
      report = { ...report, status: 'READY_FOR_QA', version: 3 }
      return reply(201, { report, resolution })
    }
    return reply(404, { error: { code: 'NOT_FOUND' } })
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(`/?workspace=reporter&project=${project}`)
  await expect(page.getByRole('heading', { name: 'My work' })).toBeVisible()
  await expect.poll(() => new URLSearchParams(reportQueries.at(-1)).get('assignedToMe')).toBe('true')
  await page.getByRole('button', { name: 'All bugs' }).click()
  await expect(page.getByRole('heading', { name: 'All bugs' })).toBeVisible()
  await expect.poll(() => new URLSearchParams(reportQueries.at(-1)).has('assignedToMe')).toBe(false)
  await page.locator('.developer-row').focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('heading', { name: report.title })).toBeVisible()
  await expect(page.locator('.developer-detail script, .developer-detail img[onerror], .developer-detail b')).toHaveCount(0)
  expect(await page.evaluate(() => (window as Window & { __developerInjected?: boolean }).__developerInjected)).toBeUndefined()
  const desktopAxe = await new AxeBuilder({ page }).analyze()
  expect(desktopAxe.violations.map(({ id, impact }) => `${id}:${impact}`)).toEqual([])

  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Dark theme' }).click()
  await expect.poll(() => page.evaluate(() => document.documentElement.dataset.theme)).toBe('dark')
  const signOutColours = await page.getByRole('button', { name: 'Sign out' }).evaluate((button) => ({
    actual: getComputedStyle(button).color,
    token: getComputedStyle(document.body).color,
  }))
  expect(signOutColours.actual).toBe(signOutColours.token)
  const settingsAxe = await new AxeBuilder({ page }).analyze()
  expect(settingsAxe.violations.map(({ id, impact }) => `${id}:${impact}`)).toEqual([])
  await page.getByRole('button', { name: 'My work' }).click()
  await page.getByRole('button', { name: report.title }).click()
  await expect(page.getByRole('heading', { name: report.title })).toBeVisible()

  await page.getByRole('button', { name: 'Start investigation' }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByText(/server confirmed the change/i)).toBeVisible()
  const composer = page.getByLabel('Add a private note')
  await composer.fill('Keyboard-created private note')
  await page.getByRole('button', { name: 'Save note' }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByText('Keyboard-created private note')).toBeVisible()

  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('heading', { name: report.title })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const toggle = page.getByRole('button', { name: 'Toggle sidebar' })
  await toggle.click()
  await expect(page.getByRole('button', { name: 'Close navigation' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(toggle).toBeFocused()
  expect(await page.evaluate(() => getComputedStyle(document.querySelector('.app-frame')!).transition)).toBe('none')

  await page.setViewportSize({ width: 320, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const mobileAxe = await new AxeBuilder({ page }).analyze()
  expect(mobileAxe.violations.map(({ id, impact }) => `${id}:${impact}`)).toEqual([])

  await page.getByLabel('Root cause').fill('<script>plain root cause</script>')
  await page.getByLabel('Change summary').fill('Synthetic change summary')
  await page.getByLabel('Verification steps').fill('Synthetic verification steps')
  await page.getByRole('button', { name: 'Send to QA' }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByText(/sent to QA after server confirmation/i)).toBeVisible()
  await expect(page.getByText('<script>plain root cause</script>', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Send to QA' })).toHaveCount(0)
  await expect(page.locator('.developer-detail script')).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const handoffAxe = await new AxeBuilder({ page }).analyze()
  expect(handoffAxe.violations.map(({ id, impact }) => `${id}:${impact}`)).toEqual([])
})
