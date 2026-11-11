import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, isAbsolute, resolve, sep } from 'node:path'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'

type LocalCredentials = {
  login: string
  password: string
  projectUuid: string
}

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iu
const frontendDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '..')
type Phase = 'LOGIN' | 'DISCOVERY' | 'DRAFT' | 'REPORT' | 'DENIAL' | 'COMPLETE'
let phase: Phase = 'LOGIN'

test.afterEach(async ({ page: _page }, testInfo) => {
  void _page
  const resultPath = process.env.TRACEDESK_LOCAL_SETUP_RESULT
  if (!resultPath) return
  const allowedDirectory = resolve(frontendDirectory, 'e2e-results')
  let resolvedResultPath = resolve(resultPath)
  if (!isAbsolute(resultPath) && !resolvedResultPath.startsWith(`${allowedDirectory}${sep}`)) {
    const repoRelativePath = resultPath.replace(/^frontend[\\/]/u, '')
    resolvedResultPath = resolve(frontendDirectory, repoRelativePath)
  }
  if (!resolvedResultPath.startsWith(`${allowedDirectory}${sep}`)) return
  try {
    await mkdir(dirname(resolvedResultPath), { recursive: true })
    await writeFile(resolvedResultPath, JSON.stringify({
      phase,
      passed: testInfo.status === 'passed',
    }), { encoding: 'utf8', mode: 0o600 })
  } catch {
    // The optional handoff status is best-effort and contains no failure details.
  }
})

async function readLocalCredentials(): Promise<LocalCredentials> {
  const credentialPath = process.env.TRACEDESK_LOCAL_SETUP_CREDENTIAL_FILE
  if (!credentialPath) throw new Error('Local Reporter credential path is not configured')

  let value: unknown
  try {
    value = JSON.parse(await readFile(credentialPath, 'utf8')) as unknown
  } catch {
    throw new Error('Unable to read the configured Local Reporter credential file')
  }

  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error('Local Reporter credential file has an invalid format')
  }
  const candidate = value as Record<string, unknown>
  if (typeof candidate.login !== 'string' || candidate.login.length === 0 ||
      typeof candidate.password !== 'string' || candidate.password.length === 0 ||
      typeof candidate.projectUuid !== 'string' || !uuidPattern.test(candidate.projectUuid)) {
    throw new Error('Local Reporter credential file is missing required fields')
  }

  // Return only fields needed by this browser flow; never retain or report the full JSON.
  return { login: candidate.login, password: candidate.password, projectUuid: candidate.projectUuid }
}

test('local Reporter signs in, discovers its project, and uses real report and draft access', async ({ page }) => {
  test.setTimeout(60_000)
  const credentials = await readLocalCredentials()
  const suffix = randomUUID().slice(0, 8)
  const reportTitle = `Local setup browser report ${suffix}`
  const draftTitle = `Local setup browser draft ${suffix}`
  const field = (label: string) => page.getByRole('textbox', { name: new RegExp(`^${label}\\b`, 'i') })
  const mutationChecks: Array<{ csrf: boolean; sameOrigin: boolean }> = []

  page.on('request', request => {
    if (request.method() === 'POST' && /\/api\/projects\/[^/]+\/(reports|drafts)$/.test(new URL(request.url()).pathname)) {
      mutationChecks.push({
        csrf: Boolean(request.headers()['x-csrf-token']),
        sameOrigin: new URL(request.url()).origin === new URL(page.url()).origin,
      })
    }
  })

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()

  // The first anonymous session is obtained through the real CSRF endpoint.
  const csrfResponse = await page.evaluate(async () => fetch('/api/csrf', {
    credentials: 'same-origin',
    cache: 'no-store',
  }).then(response => response.status))
  expect(csrfResponse).toBe(200)
  const anonymousCookie = (await page.context().cookies()).find(cookie => cookie.name === 'TDSESSION')
  if (!anonymousCookie) throw new Error('Anonymous HTTPS session cookie was not issued')

  await page.getByLabel('Email', { exact: true }).fill(credentials.login)
  await page.getByLabel('Password', { exact: true }).fill(credentials.password)
  await page.getByLabel('Password', { exact: true }).press('Enter')
  await expect(page.getByRole('heading', { name: 'Choose a project' })).toBeVisible()
  const authenticatedCookie = (await page.context().cookies()).find(cookie => cookie.name === 'TDSESSION')
  if (!authenticatedCookie || authenticatedCookie.value === anonymousCookie.value) {
    throw new Error('Sign in did not rotate the anonymous session')
  }
  expect(authenticatedCookie.secure).toBe(true)
  expect(authenticatedCookie.httpOnly).toBe(true)
  expect(authenticatedCookie.sameSite).toBe('Lax')
  phase = 'DISCOVERY'

  // Membership discovery comes from the authenticated server session.
  const workspaceResponse = await page.request.get('/api/workspaces')
  expect(workspaceResponse.status()).toBe(200)
  const workspaces = await workspaceResponse.json() as {
    items?: Array<{ project?: { id?: string; name?: string }; role?: string }>
  }
  const ownedProject = workspaces.items?.find(item => item.project?.id === credentials.projectUuid)
  if (!ownedProject || ownedProject.project?.name !== 'Local Reporter Workspace' || ownedProject.role !== 'REPORTER') {
    throw new Error('Provisioned project was not discovered with Reporter membership')
  }

  await page.getByRole('button', { name: 'Open Local Reporter Workspace', exact: true }).click()
  await expect(page.getByText('Reports for Local Reporter Workspace', { exact: true })).toBeVisible()
  await expect(page.getByText('Reporter', { exact: true })).toBeVisible()
  phase = 'DRAFT'

  const draftBody = {
    title: draftTitle,
    description: 'Synthetic local setup draft description.',
    environment: '',
    reproductionSteps: '',
    expectedBehavior: '',
    actualBehavior: '',
    textLogs: '',
  }
  await page.getByRole('main').getByRole('button', { name: 'New report' }).first().click()
  await field('Title').fill(draftBody.title)
  await field('Description').fill(draftBody.description)
  await page.getByRole('button', { name: 'Save draft', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Resume draft', exact: true })).toBeVisible()
  await expect(page.getByText(draftTitle, { exact: true })).toBeVisible()
  const draftsResponse = await page.request.get(`/api/projects/${credentials.projectUuid}/drafts`)
  expect(draftsResponse.status()).toBe(200)
  const drafts = await draftsResponse.json() as { items?: Array<{ id?: string; title?: string }> }
  const savedDraft = drafts.items?.find(item => item.title === draftTitle)
  if (!savedDraft?.id || !uuidPattern.test(savedDraft.id)) throw new Error('Saved draft was not returned by the Reporter API')
  const draftRead = await page.request.get(`/api/projects/${credentials.projectUuid}/drafts/${savedDraft.id}`)
  expect(draftRead.status()).toBe(200)
  const savedDraftDetail = await draftRead.json() as { title?: string; description?: string }
  expect(savedDraftDetail.title).toBe(draftTitle)
  expect(savedDraftDetail.description).toBe(draftBody.description)
  await page.getByRole('button', { name: 'Cancel', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Reports', level: 1 })).toBeVisible()
  await page.getByRole('listitem').filter({ hasText: draftTitle })
    .getByRole('button', { name: 'Resume', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Resume draft', exact: true })).toBeVisible()
  await expect(field('Title')).toHaveValue(draftTitle)
  await expect(field('Description')).toHaveValue(draftBody.description)
  phase = 'REPORT'

  const reportValues = [
    ['Title', reportTitle],
    ['Description', 'Synthetic report created during local setup browser verification.'],
    ['Environment', 'Local isolated HTTPS browser test.'],
    ['Steps to reproduce', '1. Sign in to the provisioned Reporter account.\n2. Open its project.'],
    ['Expected behavior', 'The saved report appears in the Reporter list.'],
    ['Actual behavior', 'The real Reporter session created this synthetic report.'],
    ['Logs or error text', 'Synthetic browser verification data only.'],
  ] as const
  await page.getByRole('button', { name: 'Cancel', exact: true }).click()
  await page.getByRole('main').getByRole('button', { name: 'New report' }).first().click()
  for (const [label, value] of reportValues) await field(label).fill(value)
  await page.getByRole('button', { name: 'Create report', exact: true }).click()
  await expect(page.getByRole('heading', { name: reportTitle, exact: true })).toBeVisible()
  const reportsResponse = await page.request.get(`/api/projects/${credentials.projectUuid}/reports`)
  expect(reportsResponse.status()).toBe(200)
  const reports = await reportsResponse.json() as { items?: Array<{ id?: string; title?: string }> }
  const savedReport = reports.items?.find(item => item.title === reportTitle)
  if (!savedReport?.id || !uuidPattern.test(savedReport.id)) throw new Error('Created report was not returned by the Reporter API')
  const reportRead = await page.request.get(`/api/projects/${credentials.projectUuid}/reports/${savedReport.id}`)
  expect(reportRead.status()).toBe(200)
  const reportDetail = await reportRead.json() as { title?: string; description?: string }
  expect(reportDetail.title).toBe(reportTitle)
  expect(reportDetail.description).toBe(reportValues[1][1])
  await page.getByRole('button', { name: 'Back to reports' }).click()
  await expect(page.getByRole('main').getByText(reportTitle, { exact: true }).first()).toBeVisible()

  // The disposable helper creates this existing project with no Reporter membership.
  phase = 'DENIAL'
  const unrelatedProjectId = process.env.TRACEDESK_LOCAL_SETUP_FOREIGN_PROJECT
  if (!unrelatedProjectId || !uuidPattern.test(unrelatedProjectId) || unrelatedProjectId === credentials.projectUuid) {
    throw new Error('Existing unowned project probe is required')
  }
  const deniedContext = await page.request.get(`/api/projects/${unrelatedProjectId}/reporter-context`)
  expect(deniedContext.status()).toBe(404)
  const deniedReports = await page.request.get(`/api/projects/${unrelatedProjectId}/reports`)
  expect(deniedReports.status()).toBe(404)
  expect(mutationChecks.length).toBeGreaterThanOrEqual(2)
  expect(mutationChecks.every(check => check.csrf && check.sameOrigin)).toBe(true)
  phase = 'COMPLETE'
})
