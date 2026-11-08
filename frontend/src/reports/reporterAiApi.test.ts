import { afterEach, describe, expect, it, vi } from 'vitest'
import { generateReportSuggestion, isReportSuggestion, type AiSource } from './reporterAiApi'

const projectId = '44b10495-a536-4c6e-a410-93bd2d9330fd'
const source: AiSource = { kind: 'REPORT', id: '76989078-9c82-4ac5-9218-fc2b262f47e9', version: 7 }
const suggestion = () => ({ capability: 'REPORT_IMPROVEMENT', promptVersion: 'report-improvement-v1', sourceKind: source.kind, sourceId: source.id, sourceVersion: source.version, changes: [{ field: 'title', replacement: '<img src=x onerror=alert(1)>' }], missingInformation: ['Which Java version?'] })
function reply(status: number, body: unknown) { return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }) }
afterEach(() => vi.unstubAllGlobals())

describe('Reporter AI strict transport', () => {
  it('sends only the saved expected version once, same-origin with CSRF', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(reply(200, { token: 'synthetic' })).mockResolvedValueOnce(reply(200, suggestion()))
    vi.stubGlobal('fetch', fetchMock)
    await expect(generateReportSuggestion(projectId, source)).resolves.toMatchObject({ sourceVersion: 7 })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1]).toEqual([`/api/projects/${projectId}/reports/${source.id}/suggestions`, expect.objectContaining({ method: 'POST', credentials: 'same-origin', cache: 'no-store', body: '{"expectedVersion":7}', headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'synthetic' } })])
  })
  for (const status of [403, 503]) it(`does not retry ${status}, including CSRF`, async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(reply(200, { token: 'synthetic' })).mockResolvedValueOnce(reply(status, { error: { code: status === 403 ? 'CSRF_INVALID' : 'AI_UNAVAILABLE' } }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(generateReportSuggestion(projectId, source)).rejects.toMatchObject({ status })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
  it('does not dispatch after cancellation during CSRF lookup', async () => {
    let finish!: (response: Response) => void
    const fetchMock = vi.fn().mockReturnValueOnce(new Promise<Response>(resolve => { finish = resolve }))
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()
    const pending = generateReportSuggestion(projectId, source, controller.signal)
    controller.abort(); finish(reply(200, { token: 'synthetic' }))
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
  it('maps expired session lookup without dispatch', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(reply(401, { error: { code: 'UNAUTHENTICATED' } }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(generateReportSuggestion(projectId, source)).rejects.toMatchObject({ status: 401 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
  it('rejects oversized and malformed response envelopes', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(reply(200, { token: 'synthetic' })).mockResolvedValueOnce(new Response(' '.repeat(21 * 1024)))
    vi.stubGlobal('fetch', fetchMock)
    await expect(generateReportSuggestion(projectId, source)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
})

describe('Reporter AI result validation', () => {
  it('keeps inert markup as exact plain text, including questions-only output', () => {
    expect(isReportSuggestion(suggestion(), source)).toBe(true)
    expect(isReportSuggestion({ ...suggestion(), changes: [] }, source)).toBe(true)
    expect(isReportSuggestion({ ...suggestion(), changes: [], missingInformation: [] }, source)).toBe(true)
  })
  const invalid = [
    { ...suggestion(), extra: 'private' }, { ...suggestion(), sourceKind: 'DRAFT' }, { ...suggestion(), sourceVersion: 8 },
    { ...suggestion(), changes: [{ field: 'textLogs', replacement: 'private' }] },
    { ...suggestion(), changes: [{ field: 'title', replacement: 'x', extra: true }] },
    { ...suggestion(), changes: [{ field: 'title', replacement: 'x' }, { field: 'title', replacement: 'y' }] },
    { ...suggestion(), changes: [{ field: 'title', replacement: 'x'.repeat(161) }] },
    { ...suggestion(), changes: [{ field: 'title', replacement: '\ud800' }] },
    { ...suggestion(), changes: [{ field: 'title', replacement: '\u0080' }] },
    { ...suggestion(), changes: [{ field: 'title', replacement: '\u2003' }] },
    { ...suggestion(), missingInformation: ['x'.repeat(501)] }, { ...suggestion(), missingInformation: Array(6).fill('Why?') },
  ]
  for (const [index, value] of invalid.entries()) it(`rejects invalid result ${index + 1} as a whole`, () => expect(isReportSuggestion(value, source)).toBe(false))
})
