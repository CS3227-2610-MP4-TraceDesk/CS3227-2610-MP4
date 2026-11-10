import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { App } from './App'

function reply(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  window.history.replaceState(null, '', '/')
})

const project = '20000000-0000-4000-8000-000000000001'
const user = '10000000-0000-4000-8000-000000000001'
const secondUser = '10000000-0000-4000-8000-000000000002'
const emptyWorkspaces = { items: [], hasMore: false }

async function signInAsSecondAccount() {
  fireEvent.change(await screen.findByLabelText('Email'), { target: { value: 'second@example.test' } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'synthetic-password' } })
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('login and server session flow', () => {
  it('shows server-discovered projects only after the server confirms a session', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { userId: user }))
      .mockResolvedValueOnce(reply(200, emptyWorkspaces))
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Choose a project' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'No projects available' })).toBeInTheDocument()
    expect(screen.queryByText(/reporter|developer|qa reviewer/i)).not.toBeInTheDocument()
  })

  it('gets CSRF, posts credentials and verifies the resulting session', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(401, { error: { code: 'UNAUTHENTICATED' } }))
      .mockResolvedValueOnce(reply(200, { token: 'synthetic-csrf' }))
      .mockResolvedValueOnce(reply(200, { userId: user }))
      .mockResolvedValueOnce(reply(200, { userId: user }))
      .mockResolvedValueOnce(reply(200, emptyWorkspaces))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.change(await screen.findByLabelText('Email'), { target: { value: 'reporter@example.test' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'synthetic-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Choose a project' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(5)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/csrf')
    expect(fetchMock.mock.calls[2][0]).toBe('/api/session/login')
    expect(fetchMock.mock.calls[2][1]).toMatchObject({
      method: 'POST',
      credentials: 'same-origin',
      body: JSON.stringify({ login: 'reporter@example.test', password: 'synthetic-password' }),
    })
    expect(new Headers(fetchMock.mock.calls[2][1]?.headers).get('X-CSRF-TOKEN')).toBe('synthetic-csrf')
    expect(fetchMock.mock.calls[3][0]).toBe('/api/session')
    expect(fetchMock.mock.calls[4][0]).toBe('/api/workspaces')
  })

  it('keeps login available and lets the user retry when session lookup is unavailable', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(503, { error: { code: 'SERVICE_UNAVAILABLE' } }))
      .mockResolvedValueOnce(reply(401, { error: { code: 'UNAUTHENTICATED' } }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('temporarily unavailable'))
    fireEvent.click(screen.getByRole('button', { name: 'Retry connection' }))
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument())
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('shows a generic credential error and stays signed out', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(401, { error: { code: 'UNAUTHENTICATED' } }))
      .mockResolvedValueOnce(reply(200, { token: 'synthetic-csrf' }))
      .mockResolvedValueOnce(reply(401, { error: { code: 'INVALID_CREDENTIALS' } }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.change(await screen.findByLabelText('Email'), { target: { value: 'reporter@example.test' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'incorrect-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.')
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Choose a project' })).not.toBeInTheDocument()
  })

  it('retains signed-in state when the server cannot confirm logout', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { userId: user }))
      .mockResolvedValueOnce(reply(200, emptyWorkspaces))
      .mockResolvedValueOnce(reply(200, { token: 'synthetic-csrf' }))
      .mockResolvedValueOnce(reply(503, { error: { code: 'SERVICE_UNAVAILABLE' } }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('could not confirm sign out')
    expect(screen.getByRole('heading', { name: 'Choose a project' })).toBeInTheDocument()
  })

  it('clears local state when the server confirms the session already ended', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { userId: user }))
      .mockResolvedValueOnce(reply(200, emptyWorkspaces))
      .mockResolvedValueOnce(reply(200, { token: 'synthetic-csrf' }))
      .mockResolvedValueOnce(reply(401, { error: { code: 'UNAUTHENTICATED' } }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('routes by the stored project role and ignores a conflicting workspace hint', async () => {
    window.history.replaceState(null, '', `/?workspace=reporter&project=${project}`)
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path === '/api/session') return reply(200, { userId: user })
      if (path === '/api/workspaces') return reply(200, { items: [{ project: { id: project, name: 'Manual Test A' }, role: 'DEVELOPER' }], hasMore: false })
      if (path === `/api/projects/${project}/developer-context`) return reply(200, { project: { id: project, name: 'Manual Test A' }, role: 'DEVELOPER' })
      if (path.startsWith(`/api/projects/${project}/reports?`)) return reply(200, { items: [], nextCursor: null })
      return reply(404, { error: { code: 'NOT_FOUND' } })
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    expect((await screen.findAllByText('Developer')).length).toBeGreaterThan(0)
    expect(fetchMock.mock.calls.some(call => String(call[0]).endsWith('/developer-context'))).toBe(true)
    expect(fetchMock.mock.calls.some(call => String(call[0]).endsWith('/reporter-context'))).toBe(false)
  })

  it('lists all discovered role memberships', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { userId: user }))
      .mockResolvedValueOnce(reply(200, { items: [
        { project: { id: project, name: 'Manual Test A' }, role: 'DEVELOPER' },
        { project: { id: '20000000-0000-4000-8000-000000000002', name: 'Manual Test B' }, role: 'REPORTER' },
        { project: { id: '20000000-0000-4000-8000-000000000003', name: 'Manual Test QA' }, role: 'QA_REVIEWER' },
      ], hasMore: false }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    expect(await screen.findByRole('button', { name: 'Manual Test A' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Manual Test B' })).toBeEnabled()
    expect(screen.getByRole('button', { name: /Manual Test QA/ })).toBeEnabled()
  })

  it('shows no workspace data when discovery is unavailable and recovers on retry', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { userId: user }))
      .mockResolvedValueOnce(reply(503, { error: { code: 'SERVICE_UNAVAILABLE' } }))
      .mockResolvedValueOnce(reply(200, { items: [{ project: { id: project, name: 'Manual Test A' }, role: 'REPORTER' }], hasMore: false }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('could not load your projects')
    expect(screen.queryByRole('button', { name: 'Manual Test A' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('button', { name: 'Manual Test A' })).toBeEnabled()
  })

  it('returns to sign in when the session expires during discovery', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(reply(200, { userId: user }))
      .mockResolvedValueOnce(reply(401, { error: { code: 'UNAUTHENTICATED' } })))
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Choose a project' })).not.toBeInTheDocument()
  })

  it('states when the membership list is incomplete', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(reply(200, { userId: user }))
      .mockResolvedValueOnce(reply(200, { items: [{ project: { id: project, name: 'Manual Test A' }, role: 'REPORTER' }], hasMore: true })))
    render(<App />)

    expect(await screen.findByText('Only the first 100 active projects are shown.')).toBeInTheDocument()
  })

  it('ignores a QA role hint for a project outside the membership list', async () => {
    window.history.replaceState(null, '', `/?workspace=qa&project=${project}`)
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { userId: user }))
      .mockResolvedValueOnce(reply(200, { items: [{ project: { id: '20000000-0000-4000-8000-000000000003', name: 'Manual Test QA' }, role: 'QA_REVIEWER' }], hasMore: false }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    expect(await screen.findByRole('button', { name: /Manual Test QA/ })).toBeEnabled()
    expect(screen.getByText(/That project is not available/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('loads the authorized QA queue, retries a summary failure, and clears it when switching projects', async () => {
    const otherProject = '20000000-0000-4000-8000-000000000002'
    const reportId = '30000000-0000-4000-8000-000000000001'
    const report = {
      id: reportId, projectId: project, reporterUserId: user, title: 'Refund display duplicates',
      status: 'READY_FOR_QA', assignedDeveloperId: secondUser, version: 3,
      createdAt: '2026-11-09T10:00:00Z', updatedAt: '2026-11-10T10:00:00Z',
      description: 'Two rows appear for one refund.', environment: 'Staging', textLogs: '',
      reproductionSteps: 'Refresh the payment.', expectedBehavior: 'One row', actualBehavior: 'Two rows',
    }
    let resolutionCalls = 0
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path === '/api/session') return reply(200, { userId: user })
      if (path === '/api/workspaces') return reply(200, { items: [
        { project: { id: project, name: 'QA Project A' }, role: 'QA_REVIEWER' },
        { project: { id: otherProject, name: 'QA Project B' }, role: 'QA_REVIEWER' },
      ], hasMore: false })
      if (path.startsWith(`/api/projects/${project}/reports?`)) return reply(200, { items: [report], nextCursor: null })
      if (path === `/api/projects/${project}/reports/${reportId}`) return reply(200, report)
      if (path.startsWith(`/api/projects/${project}/reports/${reportId}/resolutions?`)) {
        if (++resolutionCalls === 1) return reply(503)
        return reply(200, { items: [{
          id: '40000000-0000-4000-8000-000000000001', reportId, authorUserId: secondUser, revision: 1,
          rootCause: 'Duplicate IDs', changeSummary: 'Deduplicate by ID', verificationSteps: 'Refresh twice',
          fixReference: '<b>synthetic-fix</b>', createdAt: report.updatedAt,
        }], nextCursor: null })
      }
      return reply(404)
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'QA Project A' }))
    fireEvent.click(await screen.findByRole('button', { name: /Refund display duplicates.*Ready for QA/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the bug summary')
    expect(screen.queryByText('Two rows appear for one refund.')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    fireEvent.click(await screen.findByText('Proposed fix · Revision 1'))
    expect(screen.getByText('<b>synthetic-fix</b>')).toBeVisible()
    expect(screen.queryByRole('link', { name: /synthetic-fix/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Review with AI' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Settings' }))
    fireEvent.click(screen.getByRole('button', { name: 'Choose another project' }))
    fireEvent.click(await screen.findByRole('button', { name: 'QA Project B' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('This project is no longer available')
    expect(screen.queryByText('Refund display duplicates')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path]) => String(path).includes('status=READY_FOR_QA'))).toBe(true)
  })

  it('selects a membership, reauthorizes it and returns to discovery on project change', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path === '/api/session') return reply(200, { userId: user })
      if (path === '/api/workspaces') return reply(200, { items: [{ project: { id: project, name: 'Manual Test A' }, role: 'DEVELOPER' }], hasMore: false })
      if (path === `/api/projects/${project}/developer-context`) return reply(200, { project: { id: project, name: 'Manual Test A' }, role: 'DEVELOPER' })
      if (path.startsWith(`/api/projects/${project}/reports?`)) return reply(200, { items: [], nextCursor: null })
      return reply(404, { error: { code: 'NOT_FOUND' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Manual Test A' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Change project' }))

    expect(await screen.findByRole('heading', { name: 'Choose a project' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(call => String(call[0]).endsWith('/developer-context'))).toBe(true)
    expect(new URLSearchParams(window.location.search).has('project')).toBe(false)
    expect(screen.queryByRole('heading', { name: 'My work' })).not.toBeInTheDocument()
    await waitFor(() => expect(fetchMock.mock.calls.filter(call => String(call[0]) === '/api/workspaces')).toHaveLength(2))
  })

  it('routes a Reporter membership through its context and returns to discovery from the list', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path === '/api/session') return reply(200, { userId: user })
      if (path === '/api/workspaces') return reply(200, { items: [{ project: { id: project, name: 'Manual Test A' }, role: 'REPORTER' }], hasMore: false })
      if (path === `/api/projects/${project}/reporter-context`) return reply(200, { project: { id: project, name: 'Manual Test A' }, role: 'REPORTER' })
      if (path.startsWith(`/api/projects/${project}/reports?`)) return reply(200, { items: [], nextCursor: null })
      if (path.startsWith(`/api/projects/${project}/drafts`)) return reply(200, { items: [], nextCursor: null })
      return reply(404, { error: { code: 'NOT_FOUND' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Manual Test A' }))
    await screen.findByText('Reports for Manual Test A')
    fireEvent.click(screen.getByRole('button', { name: 'Change project' }))

    expect(await screen.findByRole('heading', { name: 'Choose a project' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Project ID')).not.toBeInTheDocument()
    expect(new URLSearchParams(window.location.search).has('project')).toBe(false)
  })

  it('offers discovery instead of a project ID form when a selected workspace is denied', async () => {
    window.history.replaceState(null, '', `/?project=${project}`)
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path === '/api/session') return reply(200, { userId: user })
      if (path === '/api/workspaces') return reply(200, { items: [{ project: { id: project, name: 'Manual Test A' }, role: 'DEVELOPER' }], hasMore: false })
      return reply(404, { error: { code: 'NOT_FOUND' } })
    }))
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Choose another project' }))

    expect(await screen.findByRole('heading', { name: 'Choose a project' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Project ID')).not.toBeInTheDocument()
  })

  it('clears the selected project when a selected workspace reports session expiry', async () => {
    window.history.replaceState(null, '', `/?project=${project}`)
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path === '/api/session') return reply(200, { userId: user })
      if (path === '/api/workspaces') return reply(200, { items: [{ project: { id: project, name: 'Manual Test A' }, role: 'DEVELOPER' }], hasMore: false })
      return reply(401, { error: { code: 'UNAUTHENTICATED' } })
    }))
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(new URLSearchParams(window.location.search).has('project')).toBe(false)
  })

  it('does not call a project unavailable when the membership list is incomplete', async () => {
    window.history.replaceState(null, '', `/?project=${project}`)
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(reply(200, { userId: user }))
      .mockResolvedValueOnce(reply(200, { items: [{ project: { id: '20000000-0000-4000-8000-000000000002', name: 'Manual Test B' }, role: 'REPORTER' }], hasMore: true })))
    render(<App />)

    expect(await screen.findByText(/not in the list shown/)).toBeInTheDocument()
    expect(screen.queryByText(/not available to your account/)).not.toBeInTheDocument()
  })

  it('does not carry a selected project into the next account', async () => {
    window.history.replaceState(null, '', `/?project=${project}`)
    let sessionCalls = 0
    let workspaceCalls = 0
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path === '/api/csrf') return reply(200, { token: 'synthetic-csrf' })
      if (path === '/api/session/logout') return reply(204)
      if (path === '/api/session/login') return reply(200, { userId: secondUser })
      if (path === '/api/session') return reply(200, { userId: ++sessionCalls === 1 ? user : secondUser })
      if (path === '/api/workspaces') {
        workspaceCalls += 1
        return reply(200, { items: [{ project: { id: project, name: 'Shared Project' }, role: workspaceCalls === 1 ? 'QA_REVIEWER' : 'DEVELOPER' }], hasMore: false })
      }
      return reply(404, { error: { code: 'NOT_FOUND' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    await screen.findByRole('heading', { name: 'Verification' })
    fireEvent.click(screen.getByRole('button', { name: 'Settings' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    await signInAsSecondAccount()

    expect(await screen.findByRole('button', { name: 'Shared Project' })).toBeEnabled()
    expect(new URLSearchParams(window.location.search).has('project')).toBe(false)
    expect(fetchMock.mock.calls.some(call => String(call[0]).endsWith('/developer-context'))).toBe(false)
  })

  it('discards a discovery retry that finishes after sign out', async () => {
    let resolveRetry!: (value: Response) => void
    let sessionCalls = 0
    let workspaceCalls = 0
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
      const path = String(input)
      if (path === '/api/csrf') return Promise.resolve(reply(200, { token: 'synthetic-csrf' }))
      if (path === '/api/session/logout') return Promise.resolve(reply(204))
      if (path === '/api/session/login') return Promise.resolve(reply(200, { userId: secondUser }))
      if (path === '/api/session') return Promise.resolve(reply(200, { userId: ++sessionCalls === 1 ? user : secondUser }))
      if (path === '/api/workspaces') {
        workspaceCalls += 1
        if (workspaceCalls === 1) return Promise.resolve(reply(503, { error: { code: 'SERVICE_UNAVAILABLE' } }))
        if (workspaceCalls === 2) return new Promise<Response>(resolve => { resolveRetry = resolve })
        return new Promise<Response>(() => undefined)
      }
      return Promise.resolve(reply(404, { error: { code: 'NOT_FOUND' } }))
    }))
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Retry' }))
    await screen.findByText('Loading your projects…')
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    await screen.findByRole('heading', { name: 'Sign in' })
    resolveRetry(reply(200, { items: [{ project: { id: project, name: 'Previous Account Project' }, role: 'REPORTER' }], hasMore: false }))
    await signInAsSecondAccount()

    expect(await screen.findByText('Loading your projects…')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Previous Account Project' })).not.toBeInTheDocument()
  })

  it('does not show the previous account memberships after a logout 401 and new sign in', async () => {
    let sessionCalls = 0
    let workspaceCalls = 0
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path === '/api/csrf') return reply(200, { token: 'synthetic-csrf' })
      if (path === '/api/session/logout') return reply(401, { error: { code: 'UNAUTHENTICATED' } })
      if (path === '/api/session/login') return reply(200, { userId: '10000000-0000-4000-8000-000000000002' })
      if (path === '/api/session') {
        sessionCalls += 1
        return reply(200, { userId: sessionCalls === 1 ? user : '10000000-0000-4000-8000-000000000002' })
      }
      if (path === '/api/workspaces') {
        workspaceCalls += 1
        if (workspaceCalls === 1) return reply(200, { items: [{ project: { id: project, name: 'Previous Account Project' }, role: 'REPORTER' }], hasMore: false })
        return new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
        })
      }
      return reply(404, { error: { code: 'NOT_FOUND' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    await screen.findByRole('button', { name: 'Previous Account Project' })
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    fireEvent.change(await screen.findByLabelText('Email'), { target: { value: 'second@example.test' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'synthetic-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Loading your projects…')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Previous Account Project' })).not.toBeInTheDocument()
  })
})
