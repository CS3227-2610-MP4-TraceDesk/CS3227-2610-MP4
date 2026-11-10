import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchWorkspaces } from './workspaceApi'

function reply(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

afterEach(() => vi.unstubAllGlobals())

describe('workspace discovery API', () => {
  it('accepts bounded server memberships and sends no actor or role claim', async () => {
    const project = '20000000-0000-4000-8000-000000000001'
    const fetchMock = vi.fn().mockResolvedValue(reply(200, {
      items: [{ project: { id: project, name: 'Manual Test A' }, role: 'DEVELOPER' }],
      hasMore: false,
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchWorkspaces()).resolves.toEqual({
      items: [{ project: { id: project, name: 'Manual Test A' }, role: 'DEVELOPER' }],
      hasMore: false,
    })
    expect(fetchMock).toHaveBeenCalledWith('/api/workspaces', {
      credentials: 'same-origin', cache: 'no-store', signal: undefined,
    })
  })

  it.each([
    { items: [{ project: { id: 'not-a-uuid', name: 'Bad' }, role: 'REPORTER' }], hasMore: false },
    { items: [{ project: { id: '20000000-0000-4000-8000-000000000001', name: 'Bad' }, role: 'ADMIN' }], hasMore: false },
    { items: [], hasMore: 'false' },
  ])('fails closed on an invalid response', async body => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply(200, body)))
    await expect(fetchWorkspaces()).rejects.toMatchObject({ status: 502, code: 'INVALID_RESPONSE' })
  })

  it('rejects duplicate project memberships', async () => {
    const item = { project: { id: '20000000-0000-4000-8000-000000000001', name: 'Duplicate' }, role: 'REPORTER' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply(200, { items: [item, item], hasMore: false })))
    await expect(fetchWorkspaces()).rejects.toMatchObject({ status: 502, code: 'INVALID_RESPONSE' })
  })
})
