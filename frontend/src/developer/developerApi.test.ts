import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createNote,
  deleteNote,
  fetchDeveloperContext,
  fetchDeveloperReports,
  fetchNotes,
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

describe('developer API', () => {
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
