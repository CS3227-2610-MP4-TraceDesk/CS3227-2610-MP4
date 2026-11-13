import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createNote,
  claimReport,
  deleteNote,
  fetchDeveloperContext,
  fetchDeveloperReports,
  fetchNotes,
  fetchResolutions,
  proposeResolution,
  releaseReport,
  requestInvestigationSuggestions,
  startInvestigation,
  updateNote,
} from './developerApi'

const project = '11111111-1111-4111-8111-111111111111'
const report = '22222222-2222-4222-8222-222222222222'
const note = '33333333-3333-4333-8333-333333333333'
const actor = '44444444-4444-4444-8444-444444444444'

function response(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const detail = {
  id: report, projectId: project, reporterUserId: '55555555-5555-4555-8555-555555555555',
  title: 'Plain report', description: 'Description', environment: 'Browser', reproductionSteps: 'Steps',
  expectedBehavior: 'Expected', actualBehavior: 'Actual', textLogs: '', status: 'IN_PROGRESS',
  assignedDeveloperId: actor, createdAt: '2026-11-08T00:00:00Z', updatedAt: '2026-11-08T00:01:00Z', version: 2,
}
const noteValue = {
  id: note, reportId: report, authorUserId: actor, lastEditedByUserId: null, body: '<script>plain text</script>',
  createdAt: '2026-11-08T00:02:00Z', updatedAt: '2026-11-08T00:02:00Z', version: 1,
}

afterEach(() => vi.unstubAllGlobals())

describe('proposed resolution API', () => {
  const fields = { rootCause: 'Cause', changeSummary: 'Change', verificationSteps: 'Verify', fixReference: '' }
  const resolution = { ...fields, id: note, reportId: report, authorUserId: actor, revision: 1, createdAt: '2026-11-08T00:03:00Z' }

  it('posts the expected report version under CSRF and accepts only a confirmed QA handoff', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(201, { report: { ...detail, status: 'READY_FOR_QA', version: 3 }, resolution }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(proposeResolution(project, report, fields, 2)).resolves.toMatchObject({ report: { status: 'READY_FOR_QA' }, resolution: { revision: 1 } })
    const [path, init] = fetchMock.mock.calls[1]
    expect(path).toBe(`/api/projects/${project}/reports/${report}/resolutions`)
    expect(JSON.parse(init.body)).toEqual({ ...fields, expectedVersion: 2 })
    expect(init.headers['X-CSRF-TOKEN']).toBe('csrf')

    fetchMock.mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(201, { report: detail, resolution }))
    await expect(proposeResolution(project, report, fields, 2)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })

  it('reports conflicts and rejects a revision that belongs to another report', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(409, { error: { code: 'STALE_VERSION' } }))
      .mockResolvedValueOnce(response(200, { items: [{ ...resolution, reportId: note }], nextCursor: null }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(proposeResolution(project, report, fields, 2)).rejects.toMatchObject({ status: 409, code: 'STALE_VERSION' })
    await expect(fetchResolutions(project, report)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
})

describe('developer API', () => {
  it('requests typed investigation suggestions once with CSRF, versions and exact selection', async () => {
    const selection = {
      expectedVersion: 2,
      reportFields: ['title', 'description'] as const,
      notes: [{ id: note, expectedVersion: 1 }],
    }
    const suggestion = {
      capability: 'INVESTIGATION', promptVersion: 'investigation-suggestion-v1', reportId: report, reportVersion: 2,
      reportFields: ['title', 'description'], sourceNotes: [{ ref: 'note1', id: note, version: 1 }],
      hypotheses: [{ statement: 'Check startup', rationale: 'The report describes startup failure.', sources: ['description', 'note1'] }],
      nextSteps: ['Reproduce with debug logs'],
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(200, suggestion))
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()

    await expect(requestInvestigationSuggestions(project, report, { ...selection, reportFields: [...selection.reportFields] }, controller.signal)).resolves.toEqual(suggestion)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    const [path, init] = fetchMock.mock.calls[1]
    expect(path).toBe(`/api/projects/${project}/reports/${report}/investigation/suggestions`)
    expect(init).toMatchObject({ method: 'POST', credentials: 'same-origin', cache: 'no-store', signal: controller.signal })
    expect(new Headers(init.headers).get('X-CSRF-TOKEN')).toBe('csrf')
    expect(new Headers(init.headers).get('Idempotency-Key')).toBeNull()
    expect(JSON.parse(init.body)).toEqual(selection)
  })

  it('maps suggestion error codes without replay and rejects invalid provenance', async () => {
    const selection = { expectedVersion: 2, reportFields: ['title'] as const, notes: [] }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(400, { error: { code: 'AI_CONTEXT_TOO_LARGE', message: 'fixed' } }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(requestInvestigationSuggestions(project, report, { ...selection, reportFields: [...selection.reportFields] })).rejects.toMatchObject({ status: 400, code: 'AI_CONTEXT_TOO_LARGE' })
    expect(fetchMock).toHaveBeenCalledTimes(2)

    fetchMock.mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(200, {
        capability: 'INVESTIGATION', promptVersion: 'investigation-suggestion-v1', reportId: report, reportVersion: 99,
        reportFields: ['title'], sourceNotes: [], hypotheses: [], nextSteps: [],
      }))
    await expect(requestInvestigationSuggestions(project, report, { ...selection, reportFields: [...selection.reportFields] })).rejects.toMatchObject({ status: 502, code: 'INVALID_RESPONSE' })
  })

  it('loads only server-validated Developer context and sends an assignment filter only for My work', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(200, { project: { id: project, name: 'Project' }, role: 'DEVELOPER' }))
      .mockResolvedValueOnce(response(200, { items: [detail], nextCursor: null }))
      .mockResolvedValueOnce(response(200, { items: [detail], nextCursor: null }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchDeveloperContext(project)).resolves.toMatchObject({ role: 'DEVELOPER' })
    await expect(fetchDeveloperReports(project, 'assigned-to-me', 'IN_PROGRESS', null)).resolves.toMatchObject({ items: [{ id: report }] })
    await expect(fetchDeveloperReports(project, 'all', '', null)).resolves.toMatchObject({ items: [{ id: report }] })
    expect(fetchMock.mock.calls[1][0]).toContain(`assignedToMe=true`)
    expect(fetchMock.mock.calls[1][0]).toContain(`status=IN_PROGRESS`)
    expect(fetchMock.mock.calls[2][0]).not.toContain('assignedToMe')
  })

  it('sends CSRF, exact versions and a stable note-create key', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(200, detail))
      .mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(201, noteValue))
      .mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(200, { ...noteValue, body: 'edited', lastEditedByUserId: actor, version: 2 }))
      .mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(204))
    vi.stubGlobal('fetch', fetchMock)

    await startInvestigation(project, report, 1)
    await createNote(project, report, noteValue.body, '66666666-6666-4666-8666-666666666666')
    await updateNote(project, report, note, 'edited', 1)
    await deleteNote(project, report, note, 2)

    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'POST', body: JSON.stringify({ expectedVersion: 1 }) })
    expect(new Headers(fetchMock.mock.calls[3][1]?.headers).get('Idempotency-Key')).toBe('66666666-6666-4666-8666-666666666666')
    expect(fetchMock.mock.calls[5][1]).toMatchObject({ method: 'PATCH', body: JSON.stringify({ body: 'edited', expectedVersion: 1 }) })
    expect(fetchMock.mock.calls[7][0]).toContain('expectedVersion=2')
  })

  it('claims and releases only server-confirmed assignments with strict result validation', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(200, { assignedDeveloperId: actor, version: 2, updatedAt: '2026-11-08T00:02:00Z' }))
      .mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(200, { assignedDeveloperId: null, version: 3, updatedAt: '2026-11-08T00:03:00Z' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(claimReport(project, report, 1, actor)).resolves.toMatchObject({ assignedDeveloperId: actor, version: 2 })
    await expect(releaseReport(project, report, 2, 'Queue for another developer')).resolves.toMatchObject({ assignedDeveloperId: null, version: 3 })
    expect(fetchMock.mock.calls[1][0]).toBe(`/api/projects/${project}/reports/${report}/claim`)
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'POST', body: JSON.stringify({ expectedVersion: 1 }) })
    expect(fetchMock.mock.calls[3][0]).toBe(`/api/projects/${project}/reports/${report}/release`)
    expect(fetchMock.mock.calls[3][1]).toMatchObject({ method: 'POST', body: JSON.stringify({ expectedVersion: 2, reason: 'Queue for another developer' }) })

    fetchMock.mockResolvedValueOnce(response(200, { token: 'csrf' }))
      .mockResolvedValueOnce(response(200, { assignedDeveloperId: actor, version: 4, updatedAt: '2026-11-08T00:04:00Z' }))
    await expect(releaseReport(project, report, 3, 'Still mine')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })

  it('validates note pages and rejects cross-report notes', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, {
      items: [{ ...noteValue, reportId: '77777777-7777-4777-8777-777777777777' }], nextCursor: null,
    })))
    await expect(fetchNotes(project, report, null)).rejects.toMatchObject({ status: 502, code: 'INVALID_RESPONSE' })
  })

  it('retries CSRF once without changing the mutation body or idempotency key', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(200, { token: 'old' }))
      .mockResolvedValueOnce(response(403, { error: { code: 'CSRF_INVALID' } }))
      .mockResolvedValueOnce(response(200, { token: 'new' }))
      .mockResolvedValueOnce(response(201, noteValue))
    vi.stubGlobal('fetch', fetchMock)
    await createNote(project, report, noteValue.body, '66666666-6666-4666-8666-666666666666')
    expect(fetchMock.mock.calls[1][1]?.body).toBe(fetchMock.mock.calls[3][1]?.body)
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('Idempotency-Key')).toBe(new Headers(fetchMock.mock.calls[3][1]?.headers).get('Idempotency-Key'))
  })
})
