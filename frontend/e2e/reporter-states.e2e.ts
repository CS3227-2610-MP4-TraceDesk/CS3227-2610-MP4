import { expect, test, type Page } from '@playwright/test'
import { mockAuthApi } from './helpers/authApi'

// Controlled failures supplement the separately required, unmocked backend journey.
const projectId = '44b10495-a536-4c6e-a410-93bd2d9330fd'
const reportId = '76989078-9c82-4ac5-9218-fc2b262f47e9'
const actorId = 'f2e29c3e-2b62-4420-a631-b1300f148328'
const reportsPath = `/api/projects/${projectId}/reports`
type PostResult = number | 'network'
type RecordedPost = { body: string; key: string | undefined }

async function controlledApi(page: Page, results: PostResult[], forbiddenCode = 'FORBIDDEN') {
  const authRequests = await mockAuthApi(page, { workspaces: [{ project: { id: projectId, name: 'Controlled browser project' }, role: 'REPORTER' }] })
  await page.context().addCookies([{
    name: 'TDSESSION', value: 'synthetic-session', url: 'https://localhost:4177',
    secure: true, httpOnly: true, sameSite: 'Lax',
  }])
  const posts: RecordedPost[] = []
  let saved: Record<string, unknown> | null = null
  await page.route('**/api/projects/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    if (url.pathname === `/api/projects/${projectId}/reporter-context`) {
      await route.fulfill({ json: { project: { id: projectId, name: 'Controlled browser project' }, role: 'REPORTER' } })
      return
    }
    if (url.pathname === `/api/projects/${projectId}/drafts` && request.method() === 'GET') {
      await route.fulfill({ json: { items: [], nextCursor: null } })
      return
    }
    if (url.pathname === reportsPath && request.method() === 'GET') {
      await route.fulfill({ json: { items: saved ? [saved] : [], nextCursor: null } })
      return
    }
    if (url.pathname === `${reportsPath}/${reportId}` && request.method() === 'GET' && saved) {
      await route.fulfill({ json: saved })
      return
    }
    if (url.pathname === reportsPath && request.method() === 'POST') {
      const headers = await request.allHeaders()
      const body = request.postData() ?? ''
      posts.push({ body, key: headers['idempotency-key'] })
      const result = results[Math.min(posts.length - 1, results.length - 1)]
      if (result === 'network') {
        await route.abort('failed')
        return
      }
      if (result === 201) {
        saved = {
          ...JSON.parse(body) as Record<string, unknown>,
          id: reportId, projectId, reporterUserId: actorId, status: 'OPEN',
          assignedDeveloperId: null, version: 1,
          createdAt: '2026-11-08T00:00:00Z', updatedAt: '2026-11-08T00:00:00Z',
        }
        await route.fulfill({ status: 201, json: saved })
        return
      }
      const code = result === 400 ? 'VALIDATION_ERROR' : result === 403 ? forbiddenCode
        : result === 409 ? 'IDEMPOTENCY_CONFLICT' : 'SERVICE_UNAVAILABLE'
      await route.fulfill({ status: result, json: { error: { code, message: 'Request could not be completed' } } })
      return
    }
    await route.fulfill({ status: 404, json: { error: { code: 'NOT_FOUND', message: 'Not found' } } })
  })
  return { posts, authRequests }
}

async function composer(page: Page, title: string) {
  await page.goto(`/?project=${projectId}`)
  await expect(page.getByRole('heading', { name: 'Reports', level: 1, exact: true })).toBeVisible()
  await page.getByRole('main').getByRole('button', { name: 'New report', exact: true }).first().click()
  await page.getByLabel(/^Title\b/).fill(title)
  await page.getByLabel(/^Description\b/).fill('Synthetic description retained through failure')
  await page.getByLabel(/^Environment\b/).fill('Synthetic browser')
  await page.getByLabel(/^Steps to reproduce\b/).fill('Open the test page')
  await page.getByLabel(/^Expected behavior\b/).fill('The action succeeds')
  await page.getByLabel(/^Actual behavior\b/).fill('The action fails')
  await page.getByLabel(/^Logs or error text\b/).fill('Synthetic log\nSecond line')
}

test('a CSRF-only rejection refreshes once and preserves the immutable submission', async ({ page }) => {
  const state = await controlledApi(page, [403, 201], 'CSRF_INVALID')
  await composer(page, 'CSRF retry report')
  await page.getByRole('button', { name: 'Create report', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'CSRF retry report', exact: true })).toBeVisible()
  expect(state.posts).toHaveLength(2)
  expect(state.posts[0].body === state.posts[1].body).toBe(true)
  expect(state.posts[0].key === state.posts[1].key).toBe(true)
  expect(state.authRequests.filter(request => request.path === '/api/csrf')).toHaveLength(2)
})

for (const failure of [503, 'network'] as const) {
  test(`an unconfirmed ${failure} outcome freezes input and retries the same report`, async ({ page }) => {
    const state = await controlledApi(page, [failure, 201])
    await composer(page, 'Unconfirmed retry report')
    await page.getByRole('button', { name: 'Create report', exact: true }).click()
    await expect(page.getByRole('status')).toContainText('unconfirmed')
    await expect(page.getByRole('heading', { name: 'New report', exact: true })).toBeVisible()
    await expect(page.getByLabel(/^Title\b/)).toHaveValue('Unconfirmed retry report')
    await expect(page.getByLabel(/^Title\b/)).toBeDisabled()
    await page.getByRole('button', { name: 'Retry submission', exact: true }).click()
    await expect(page.getByRole('heading', { name: 'Unconfirmed retry report', exact: true })).toBeVisible()
    expect(state.posts).toHaveLength(2)
    expect(state.posts[0].body === state.posts[1].body).toBe(true)
    expect(state.posts[0].key === state.posts[1].key).toBe(true)
  })
}

test('known validation rejection preserves editable input and a revised attempt uses a new key', async ({ page }) => {
  const state = await controlledApi(page, [400, 201])
  await composer(page, 'Rejected report')
  await page.getByRole('button', { name: 'Create report', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('server rejected')
  await expect(page.getByLabel(/^Title\b/)).toHaveValue('Rejected report')
  await expect(page.getByLabel(/^Title\b/)).toBeEnabled()
  await page.getByLabel(/^Title\b/).fill('Revised report')
  await page.getByRole('button', { name: 'Create report', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Revised report', exact: true })).toBeVisible()
  expect(state.posts).toHaveLength(2)
  expect(state.posts[0].key === state.posts[1].key).toBe(false)
  expect(state.posts[0].body === state.posts[1].body).toBe(false)
})

test('business denial does not retry or display a successful saved report', async ({ page }) => {
  const state = await controlledApi(page, [403])
  await composer(page, 'Denied private report')
  await page.getByRole('button', { name: 'Create report', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Project unavailable', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Denied private report', exact: true })).toHaveCount(0)
  await expect(page.getByLabel(/^Title\b/)).toHaveCount(0)
  expect(state.posts).toHaveLength(1)
  expect(state.authRequests.filter(request => request.path === '/api/csrf')).toHaveLength(1)
})

test('an idempotency conflict freezes the form without claiming success or silently resubmitting', async ({ page }) => {
  const state = await controlledApi(page, [409])
  await composer(page, 'Conflicted report')
  await page.getByRole('button', { name: 'Create report', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('conflicts')
  await expect(page.getByRole('heading', { name: 'New report', exact: true })).toBeVisible()
  await expect(page.getByLabel(/^Title\b/)).toHaveValue('Conflicted report')
  await expect(page.getByLabel(/^Title\b/)).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Create report', exact: true })).toBeDisabled()
  await expect(page.getByRole('heading', { name: 'Conflicted report', exact: true })).toHaveCount(0)
  expect(state.posts).toHaveLength(1)
})
