import { createHash } from 'node:crypto'
import { readFile, rename, unlink, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'

// This file attaches to the separately started backend. It never owns or starts it.
// Deliberately reject a backend credential in the browser-driver process before
// Playwright can open a page or contact the application.
if (Object.hasOwn(process.env, 'SOCLAAS_API_KEY')) {
  throw new Error('LIVE_BROWSER_DRIVER_MUST_NOT_RECEIVE_PROVIDER_KEY')
}
if (process.env.TRACEDESK_REAL_BACKEND_TEST !== 'true' || process.env.TRACEDESK_LIVE_BROWSER_TEST !== 'true') {
  throw new Error('LIVE_BROWSER_TEST_REQUIRES_BOTH_EXPLICIT_FLAGS')
}

const providerMode = process.env.TRACEDESK_LIVE_BROWSER_PROVIDER
if (providerMode !== 'synthetic' && providerMode !== 'live') {
  throw new Error('LIVE_BROWSER_PROVIDER_MODE_MUST_BE_EXPLICIT')
}

const repositoryDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const casePath = path.join(repositoryDirectory, 'workflow/reporter/experiments/reporter-ai/browser-live-case.json')
const caseSha256 = '6502ba11a271b84a3d760203d9998a285b4cd27fd1847e8dc0799fc3b2cd5faf'
if (process.env.TRACEDESK_LIVE_BROWSER_CASE_SHA256 !== caseSha256) {
  throw new Error('LIVE_BROWSER_CASE_HASH_DOES_NOT_MATCH_PINNED_CASE')
}
const metadataPath = process.env.TRACEDESK_BROWSER_FIXTURE_METADATA
const commandPath = process.env.TRACEDESK_BROWSER_FIXTURE_COMMAND_FILE
const ackPath = process.env.TRACEDESK_BROWSER_FIXTURE_ACK_FILE
const resultPath = process.env.TRACEDESK_LIVE_BROWSER_RESULT
const expectedResultDirectory = path.join(repositoryDirectory, 'workflow/reporter/runs')
if (!metadataPath || !commandPath || !ackPath || !resultPath ||
    !path.resolve(resultPath).startsWith(`${expectedResultDirectory}${path.sep}`)) {
  throw new Error('LIVE_BROWSER_FIXTURE_PATHS_ARE_REQUIRED')
}

type FixtureMetadata = {
  projectId: string
  deniedProjectId: string
  reporterId: string
  login: string
  nonmemberLogin: string
  developerLogin: string
}
type BrowserCase = {
  caseId: 'RAI-BROWSER-01'
  promptVersion: 'report-improvement-v2'
  fields: {
    title: string
    description: string
    environment: string
    reproductionSteps: string
    expectedBehavior: string
    actualBehavior: string
    textLogs: string
  }
}
type CheckName = 'offlineUnavailable' | 'singleCompletion' | 'escapedPreview' | 'noAutomaticPatch' |
  'titleSelected' | 'versionedPatch' | 'otherFieldsPreserved' | 'sameOrigin' | 'logsExcludedFromSuggestionRequest'
type RunStatus = 'FAILED' | 'NO_APPLICABLE_TITLE' | 'APPLIED_TITLE' | 'AI_UNAVAILABLE' | 'INVALID_OUTPUT' | 'SAFE_ERROR'
const checks: Record<CheckName, boolean> = {
  offlineUnavailable: false,
  singleCompletion: false,
  escapedPreview: false,
  noAutomaticPatch: false,
  titleSelected: false,
  versionedPatch: false,
  otherFieldsPreserved: false,
  sameOrigin: false,
  logsExcludedFromSuggestionRequest: false,
}

async function writeResult(status: RunStatus) {
  const target = path.resolve(resultPath!)
  const temporary = `${target}.tmp`
  const summary = {
    caseId: 'RAI-BROWSER-01',
    promptVersion: 'report-improvement-v2',
    caseSha256,
    providerMode,
    status,
    checks,
  }
  await writeFile(temporary, `${JSON.stringify(summary, null, 2)}\n`, { mode: 0o600 })
  await rename(temporary, target)
}

async function fixtureCommand(command: 'ai-off' | 'ai-on') {
  await unlink(ackPath!).catch(() => undefined)
  await writeFile(commandPath!, command, { mode: 0o600 })
  const deadline = Date.now() + 10_000
  while (Date.now() < deadline) {
    const ack = await readFile(ackPath!, 'utf8').catch(() => '')
    if (ack === 'done') return
    if (ack === 'invalid') throw new Error('LIVE_BROWSER_FIXTURE_COMMAND_REJECTED')
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  throw new Error('LIVE_BROWSER_FIXTURE_COMMAND_TIMEOUT')
}

test('attached Reporter browser journey uses one bounded suggestion and explicit title apply', async ({ page }) => {
  test.setTimeout(90_000)
  let status: RunStatus = 'FAILED'
  try {
    const [fixture, browserCase, rawCase] = await Promise.all([
      readFile(metadataPath!, 'utf8').then(value => JSON.parse(value) as FixtureMetadata),
      readFile(casePath, 'utf8').then(value => JSON.parse(value) as BrowserCase),
      readFile(casePath),
    ])
    const actualHash = createHash('sha256').update(rawCase).digest('hex')
    expect(actualHash === caseSha256).toBe(true)
    expect(browserCase.caseId === 'RAI-BROWSER-01' && browserCase.promptVersion === 'report-improvement-v2').toBe(true)
    const fieldNames = ['title', 'description', 'environment', 'reproductionSteps', 'expectedBehavior', 'actualBehavior', 'textLogs']
    expect(Object.keys(browserCase.fields).join(',') === fieldNames.join(',')).toBe(true)
    expect(Object.values(fixture).every(value => typeof value === 'string' && value.length > 0)).toBe(true)

    // Turn off AI before any browser activity. In the synthetic attach mode this
    // is the fixture provider; for a live attach it is the operator's control file.
    await fixtureCommand('ai-off')
    const login = fixture.login
    const projectId = fixture.projectId
    const password = 'Synthetic-browser-password-2026!'
    const origin = 'https://localhost:4177'
    const field = (label: string) => page.getByRole('textbox', { name: new RegExp(`^${label}\\b`, 'i') })
    const suggestionStatuses: number[] = []
    const suggestionBodies: Array<Record<string, unknown>> = []
    const patchBodies: Array<Record<string, unknown>> = []
    const sameOrigin: boolean[] = []
    page.on('request', request => {
      if (request.method() === 'POST' && /\/suggestions$/.test(new URL(request.url()).pathname)) {
        suggestionBodies.push(request.postDataJSON() as Record<string, unknown>)
        sameOrigin.push(new URL(request.url()).origin === origin)
      }
      if (request.method() === 'PATCH' && /\/drafts\//.test(new URL(request.url()).pathname)) {
        patchBodies.push(request.postDataJSON() as Record<string, unknown>)
        sameOrigin.push(new URL(request.url()).origin === origin)
      }
    })
    page.on('response', response => {
      if (response.request().method() === 'POST' && /\/suggestions$/.test(new URL(response.url()).pathname)) {
        suggestionStatuses.push(response.status())
      }
    })

    await page.goto('/')
    await page.getByLabel('Email', { exact: true }).fill(login)
    await page.getByLabel('Password', { exact: true }).fill(password)
    await page.getByLabel('Password', { exact: true }).press('Enter')
    await expect(page.getByLabel('Project ID', { exact: true })).toBeVisible()
    await page.goto(`/?project=${projectId}`)
    await page.getByRole('main').getByRole('button', { name: 'New report' }).first().click()
    const formEntries = [
      ['Title', browserCase.fields.title],
      ['Description', browserCase.fields.description],
      ['Environment', browserCase.fields.environment],
      ['Steps to reproduce', browserCase.fields.reproductionSteps],
      ['Expected behavior', browserCase.fields.expectedBehavior],
      ['Actual behavior', browserCase.fields.actualBehavior],
      ['Logs or error text', browserCase.fields.textLogs],
    ] as const
    for (const [label, value] of formEntries) await field(label).fill(value)
    const saveResponse = page.waitForResponse(response => response.request().method() === 'POST' && /\/drafts$/.test(new URL(response.url()).pathname))
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    const savedResponse = await saveResponse
    expect(savedResponse.ok()).toBe(true)
    const saved = await savedResponse.json() as { id: string; version: number }
    expect(typeof saved.id === 'string' && Number.isSafeInteger(saved.version) && saved.version > 0).toBe(true)
    await expect(page.getByRole('heading', { name: 'Resume draft' })).toBeVisible()
    await page.locator('summary').filter({ hasText: 'Review with AI' }).click()
    await expect(page.getByText(/Logs are excluded/)).toBeVisible()

    const unavailableResponse = page.waitForResponse(response => response.request().method() === 'POST' && /\/suggestions$/.test(new URL(response.url()).pathname))
    await page.getByRole('button', { name: 'Generate suggestions', exact: true }).click()
    const unavailable = await unavailableResponse
    await expect(page.getByRole('region', { name: 'AI report review' }).getByRole('status')).toContainText('AI review is unavailable')
    checks.offlineUnavailable = unavailable.status() === 503
    checks.noAutomaticPatch = patchBodies.length === 0
    checks.logsExcludedFromSuggestionRequest = suggestionBodies.length === 1 &&
      Object.keys(suggestionBodies[0] ?? {}).join(',') === 'expectedVersion'
    if (!checks.offlineUnavailable || !checks.noAutomaticPatch || !checks.logsExcludedFromSuggestionRequest) {
      status = 'SAFE_ERROR'
      await writeResult(status)
      throw new Error('LIVE_BROWSER_OFFLINE_PREFLIGHT_FAILED')
    }

    await fixtureCommand('ai-on')
    const completionResponse = page.waitForResponse(response => response.request().method() === 'POST' && /\/suggestions$/.test(new URL(response.url()).pathname))
    await page.getByRole('button', { name: 'Generate suggestions', exact: true }).click()
    const completion = await completionResponse
    checks.singleCompletion = suggestionBodies.length === 2 && suggestionStatuses.length === 2 &&
      suggestionBodies.every(body => Object.keys(body).join(',') === 'expectedVersion' && body.expectedVersion === saved.version)
    if (completion.status() === 502) {
      status = 'INVALID_OUTPUT'
      await writeResult(status)
      throw new Error('LIVE_BROWSER_INVALID_OUTPUT_STOPPED')
    }
    if (completion.status() !== 200) {
      status = completion.status() === 503 ? 'AI_UNAVAILABLE' : 'SAFE_ERROR'
      await writeResult(status)
      throw new Error('LIVE_BROWSER_COMPLETION_FAILED_STOPPED')
    }

    let responseValue: unknown
    try {
      responseValue = await completion.json()
    } catch {
      status = 'INVALID_OUTPUT'
      await writeResult(status)
      throw new Error('LIVE_BROWSER_INVALID_OUTPUT_STOPPED')
    }
    const suggestion = (typeof responseValue === 'object' && responseValue !== null && !Array.isArray(responseValue)
      ? responseValue
      : {}) as {
        capability?: unknown; promptVersion?: unknown; sourceKind?: unknown; sourceId?: unknown; sourceVersion?: unknown
        changes?: unknown; missingInformation?: unknown
      }
    const validEnvelope = Object.keys(suggestion).sort().join(',') ===
      ['capability', 'promptVersion', 'sourceKind', 'sourceId', 'sourceVersion', 'changes', 'missingInformation'].sort().join(',') &&
      suggestion.capability === 'REPORT_IMPROVEMENT' &&
      suggestion.promptVersion === 'report-improvement-v2' && suggestion.sourceKind === 'DRAFT' &&
      suggestion.sourceId === saved.id && suggestion.sourceVersion === saved.version &&
      Array.isArray(suggestion.changes) && suggestion.changes.every(change => change !== null && typeof change === 'object' &&
        Object.keys(change).sort().join(',') === 'field,replacement' &&
        typeof (change as { field?: unknown }).field === 'string' && typeof (change as { replacement?: unknown }).replacement === 'string') &&
      Array.isArray(suggestion.missingInformation) && suggestion.missingInformation.every(question => typeof question === 'string')
    if (!validEnvelope) {
      status = 'INVALID_OUTPUT'
      await writeResult(status)
      throw new Error('LIVE_BROWSER_INVALID_OUTPUT_STOPPED')
    }
    const changes = Array.isArray(suggestion.changes) ? suggestion.changes as Array<{ field?: unknown; replacement?: unknown }> : []
    const titleChange = changes.find(change => change.field === 'title' && typeof change.replacement === 'string')
    if (!titleChange || titleChange.replacement === browserCase.fields.title) {
      status = 'NO_APPLICABLE_TITLE'
      checks.noAutomaticPatch = patchBodies.length === 0
      await writeResult(status)
      test.skip(true, 'NO_APPLICABLE_TITLE')
      return
    }

    const replacement = titleChange.replacement as string
    const preview = page.locator('.report-ai-preview')
    const previewText = await preview.innerText()
    checks.escapedPreview = previewText.includes(replacement) &&
      await preview.locator('img,script').count() === 0 &&
      await page.evaluate(() => (window as Window & { __aiInjected?: boolean }).__aiInjected !== true)
    checks.noAutomaticPatch = patchBodies.length === 0
    checks.sameOrigin = sameOrigin.every(Boolean)
    expect(checks.escapedPreview && checks.noAutomaticPatch && checks.sameOrigin && checks.singleCompletion).toBe(true)
    await expect(preview.getByRole('heading', { name: 'Suggested changes' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Apply selected changes' })).toBeDisabled()

    await page.getByRole('checkbox', { name: 'Apply Title', exact: true }).check()
    checks.titleSelected = true
    const applyResponse = page.waitForResponse(response => response.request().method() === 'PATCH' &&
      new URL(response.url()).pathname.endsWith(`/drafts/${saved.id}`))
    await page.getByRole('button', { name: 'Apply selected changes' }).click()
    const applied = await applyResponse
    const appliedOk = applied.status() === 200
    const patch = patchBodies[0]
    const expectedPatch: Record<string, unknown> = {
      title: replacement,
      description: browserCase.fields.description,
      environment: browserCase.fields.environment,
      reproductionSteps: browserCase.fields.reproductionSteps,
      expectedBehavior: browserCase.fields.expectedBehavior,
      actualBehavior: browserCase.fields.actualBehavior,
      textLogs: browserCase.fields.textLogs,
      expectedVersion: saved.version,
    }
    checks.versionedPatch = appliedOk && patchBodies.length === 1 &&
      JSON.stringify(Object.keys(patch ?? {}).sort()) === JSON.stringify(Object.keys(expectedPatch).sort()) &&
      Object.entries(expectedPatch).every(([key, value]) => patch?.[key] === value)
    const persistedResponse = await page.request.get(`/api/projects/${projectId}/drafts/${saved.id}`)
    const persisted = await persistedResponse.json() as Record<string, unknown>
    checks.otherFieldsPreserved = persistedResponse.ok() && persisted.title === replacement &&
      persisted.description === browserCase.fields.description && persisted.environment === browserCase.fields.environment &&
      persisted.reproductionSteps === browserCase.fields.reproductionSteps &&
      persisted.expectedBehavior === browserCase.fields.expectedBehavior && persisted.actualBehavior === browserCase.fields.actualBehavior &&
      persisted.textLogs === browserCase.fields.textLogs && persisted.version === saved.version + 1
    expect(checks.versionedPatch && checks.otherFieldsPreserved).toBe(true)
    status = 'APPLIED_TITLE'
    await writeResult(status)
  } catch (error) {
    if (status === 'NO_APPLICABLE_TITLE') throw error
    if (status === 'FAILED') status = 'SAFE_ERROR'
    await writeResult(status).catch(() => undefined)
    // A Playwright cause can contain credentials or model text; expose only the fixed safe status.
    // eslint-disable-next-line preserve-caught-error
    throw new Error('LIVE_BROWSER_RUN_FAILED_WITH_SAFE_SUMMARY')
  }
})
