import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const project = '11111111-1111-4111-8111-111111111111'
const reportId = '22222222-2222-4222-8222-222222222222'
const actor = '44444444-4444-4444-8444-444444444444'
const noteId = '33333333-3333-4333-8333-333333333333'

test('Developer requests, previews and explicitly saves investigation suggestions at 320px', async ({ page }) => {
  const report = {
    id: reportId, projectId: project, reporterUserId: '55555555-5555-4555-8555-555555555555',
    title: 'Startup failure', description: 'The service fails during startup.', environment: 'Synthetic browser',
    reproductionSteps: 'Start the service', expectedBehavior: 'Service starts', actualBehavior: 'Startup fails',
    textLogs: '<script>window.sourceExecuted=true</script>', status: 'IN_PROGRESS', assignedDeveloperId: actor,
    createdAt: '2026-11-08T00:00:00Z', updatedAt: '2026-11-08T00:01:00Z', version: 2,
  }
  const notes = [{
    id: noteId, reportId, authorUserId: actor, lastEditedByUserId: null, body: 'Failure follows configuration loading.',
    createdAt: '2026-11-08T00:02:00Z', updatedAt: '2026-11-08T00:02:00Z', version: 1,
  }]
  const savedBodies: unknown[] = []

  await page.route('**/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const reply = (status: number, body?: unknown) => route.fulfill({ status, contentType: 'application/json', body: body === undefined ? '' : JSON.stringify(body) })
    if (url.pathname === '/api/session') return reply(200, { userId: actor, role: 'DEVELOPER' })
    if (url.pathname === '/api/workspaces') return reply(200, { items: [{ project: { id: project, name: 'Synthetic Developer Project' }, role: 'DEVELOPER' }], hasMore: false })
    if (url.pathname === '/api/csrf') return reply(200, { token: 'synthetic-csrf' })
    if (url.pathname.endsWith('/developer-context')) return reply(200, { project: { id: project, name: 'Synthetic Developer Project' }, role: 'DEVELOPER' })
    if (url.pathname.endsWith('/reports') && request.method() === 'GET') return reply(200, { items: [report], nextCursor: null })
    if (url.pathname.endsWith(`/reports/${reportId}`) && request.method() === 'GET') return reply(200, report)
    if (url.pathname.endsWith('/notes') && request.method() === 'GET') return reply(200, { items: notes, nextCursor: null })
    if (url.pathname.endsWith('/notes') && request.method() === 'POST') {
      const body = JSON.parse(request.postData() ?? '{}') as { body: string }
      savedBodies.push(body)
      const created = { ...notes[0], id: '66666666-6666-4666-8666-666666666666', body: body.body }
      notes.push(created)
      return reply(201, created)
    }
    if (url.pathname.endsWith('/resolutions') && request.method() === 'GET') return reply(200, { items: [], nextCursor: null })
    if (url.pathname.endsWith('/investigation/suggestions') && request.method() === 'POST') {
      const body = JSON.parse(request.postData() ?? '{}') as { expectedVersion: number; reportFields: string[]; notes: { id: string; expectedVersion: number }[] }
      return reply(200, {
        capability: 'INVESTIGATION', promptVersion: 'investigation-suggestion-v1', reportId, reportVersion: body.expectedVersion,
        reportFields: body.reportFields,
        sourceNotes: body.notes.map((selected, index) => ({ ref: `note${index + 1}`, id: selected.id, version: selected.expectedVersion })),
        hypotheses: [{
          statement: '<img src=x onerror=window.suggestionExecuted=true>',
          rationale: '<script>window.suggestionExecuted=true</script>Configuration is a plausible boundary.',
          sources: body.notes.length ? ['description', 'note1'] : ['description'],
        }],
        nextSteps: ['Capture debug logs around configuration loading.'],
      })
    }
    return reply(404, { error: { code: 'NOT_FOUND' } })
  })

  await page.setViewportSize({ width: 320, height: 844 })
  await page.goto(`/?workspace=developer&project=${project}`)
  await page.getByRole('button', { name: 'Startup failure' }).click()
  await expect(page.getByRole('heading', { name: 'Investigation suggestions' })).toBeVisible()
  await page.getByText('Choose AI context').click()
  await page.getByRole('checkbox', { name: 'Private note 1' }).click()
  await page.getByRole('button', { name: 'Get suggestions' }).click()

  await expect(page.getByText('<img src=x onerror=window.suggestionExecuted=true>', { exact: true })).toBeVisible()
  await expect(page.getByText(/note1 — private note 1/)).toBeVisible()
  await expect(page.locator('.developer-ai script, .developer-ai img[onerror]')).toHaveCount(0)
  expect(await page.evaluate(() => (window as Window & { suggestionExecuted?: boolean }).suggestionExecuted)).toBeUndefined()
  await page.getByRole('checkbox', { name: '<img src=x onerror=window.suggestionExecuted=true>' }).click()
  await page.getByRole('checkbox', { name: 'Capture debug logs around configuration loading.' }).click()
  await page.getByRole('button', { name: 'Add selected to draft' }).click()
  const draft = page.getByLabel('Editable private-note draft')
  await draft.fill('Edited investigation note')
  expect(savedBodies).toEqual([])
  await page.getByRole('button', { name: 'Save private note' }).click()
  await expect.poll(() => savedBodies).toEqual([{ body: 'Edited investigation note' }])

  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const accessibility = await new AxeBuilder({ page }).analyze()
  expect(accessibility.violations.map(({ id, impact }) => `${id}:${impact}`)).toEqual([])
})
