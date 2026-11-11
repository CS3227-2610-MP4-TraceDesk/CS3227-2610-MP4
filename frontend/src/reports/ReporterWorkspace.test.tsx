import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ReporterWorkspace } from './ReporterWorkspace'

const projectOne = '44b10495-a536-4c6e-a410-93bd2d9330fd'
const projectTwo = '7d73ebf6-b143-470e-b6e4-79d5af0cf0aa'

function response(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function projectContext(id: string, name: string) {
  return { project: { id, name }, role: 'REPORTER' }
}

function reportPage(title: string) {
  return {
    items: [{
      id: '76989078-9c82-4ac5-9218-fc2b262f47e9',
      projectId: projectOne,
      reporterUserId: 'f2e29c3e-2b62-4420-a631-b1300f148328',
      title,
      status: 'OPEN',
      assignedDeveloperId: null,
      createdAt: '2026-11-08T00:00:00Z',
      updatedAt: '2026-11-08T00:00:00Z',
      version: 1,
    }],
    nextCursor: null,
  }
}

function workspaceProps(onSessionExpired = vi.fn()) {
  return {
    theme: 'dark' as const,
    onThemeChange: vi.fn(),
    onSessionExpired,
    onSignOut: vi.fn(),
    signingOut: false,
    signOutError: null,
  }
}

function fillValidReport() {
  fireEvent.change(screen.getByLabelText(/Title/), { target: { value: 'Cannot save report' } })
  fireEvent.change(screen.getByLabelText(/Description/), { target: { value: 'Saving fails on submit.' } })
  fireEvent.change(screen.getByLabelText(/^Environment(?:\s|$)/u), { target: { value: 'Desktop browser' } })
  fireEvent.change(screen.getByLabelText(/Steps to reproduce/), { target: { value: 'Open the report form and submit.' } })
  fireEvent.change(screen.getByLabelText(/Expected behavior/), { target: { value: 'The report is saved.' } })
  fireEvent.change(screen.getByLabelText(/Actual behavior/), { target: { value: 'The save is delayed.' } })
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  window.history.replaceState(null, '', '/')
})

describe('Reporter workspace request isolation', () => {
  it('does not render a late list response from the previous project after switching', async () => {
    let resolveOldList!: (value: Response) => void
    const oldListResponse = new Promise<Response>(resolve => { resolveOldList = resolve })
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const path = String(input)
      if (path === `/api/projects/${projectOne}/reporter-context`) return Promise.resolve(response(200, projectContext(projectOne, 'First project')))
      if (path === `/api/projects/${projectOne}/reports?limit=25`) return oldListResponse
      if (path === `/api/projects/${projectTwo}/reporter-context`) return Promise.resolve(response(200, projectContext(projectTwo, 'Second project')))
      if (path === `/api/projects/${projectTwo}/reports?limit=25`) return Promise.resolve(response(200, { items: [], nextCursor: null }))
      throw new Error(`Unexpected request ${path}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    window.history.replaceState(null, '', `/?project=${projectOne}`)
    render(<ReporterWorkspace {...workspaceProps()} />)

    await screen.findByText('Reports for First project')
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`/api/projects/${projectOne}/reports?limit=25`, expect.objectContaining({ signal: expect.any(AbortSignal) })))
    fireEvent.click(screen.getByRole('button', { name: 'Change project' }))
    fireEvent.change(screen.getByLabelText('Project ID'), { target: { value: projectTwo } })
    fireEvent.click(screen.getByRole('button', { name: 'Open project' }))
    await screen.findByText('Reports for Second project')

    resolveOldList(response(200, reportPage('Private result from the first project')))
    await waitFor(() => expect(screen.queryByText('Private result from the first project')).not.toBeInTheDocument())
    expect(screen.getByText('No reports yet')).toBeInTheDocument()
  })

  it('keeps the unsaved-work warning when project change returns to discovery', async () => {
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
      const path = String(input)
      if (path === `/api/projects/${projectOne}/reporter-context`) return Promise.resolve(response(200, projectContext(projectOne, 'First project')))
      return Promise.resolve(response(200, { items: [], nextCursor: null }))
    }))
    const confirm = vi.fn().mockReturnValueOnce(false).mockReturnValueOnce(true)
    vi.stubGlobal('confirm', confirm)
    window.history.replaceState(null, '', `/?project=${projectOne}`)
    const onChooseWorkspace = vi.fn()
    render(<ReporterWorkspace {...workspaceProps()} onChooseWorkspace={onChooseWorkspace} />)

    await screen.findByText('Reports for First project')
    fireEvent.click(screen.getAllByRole('button', { name: 'New report' })[0])
    await screen.findByLabelText(/Title/)
    fillValidReport()

    fireEvent.click(screen.getByRole('button', { name: 'Change project' }))
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(onChooseWorkspace).not.toHaveBeenCalled()
    expect(screen.getByLabelText(/Title/)).toHaveValue('Cannot save report')

    fireEvent.click(screen.getByRole('button', { name: 'Change project' }))
    expect(onChooseWorkspace).toHaveBeenCalledTimes(1)
  })

  it('aborts protected list work when the signed-in workspace unmounts', async () => {
    let resolveList!: (value: Response) => void
    const delayedList = new Promise<Response>(resolve => { resolveList = resolve })
    const sessionExpired = vi.fn()
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const path = String(input)
      if (path === `/api/projects/${projectOne}/reporter-context`) return Promise.resolve(response(200, projectContext(projectOne, 'First project')))
      if (path === `/api/projects/${projectOne}/reports?limit=25`) return delayedList
      throw new Error(`Unexpected request ${path}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    window.history.replaceState(null, '', `/?project=${projectOne}`)
    const view = render(<ReporterWorkspace {...workspaceProps(sessionExpired)} />)
    await screen.findByText('Reports for First project')
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`/api/projects/${projectOne}/reports?limit=25`, expect.objectContaining({ signal: expect.any(AbortSignal) })))

    view.unmount()
    resolveList(response(401, { error: { code: 'UNAUTHENTICATED' } }))
    await Promise.resolve()
    expect(sessionExpired).not.toHaveBeenCalled()
  })

  it('blocks destructive navigation while a submission is pending', async () => {
    const postIsPending = new Promise<Response>(() => undefined)
    const fetchMock = vi.fn((input: RequestInfo | URL, options?: RequestInit) => {
      const path = String(input)
      if (path === `/api/projects/${projectOne}/reporter-context`) return Promise.resolve(response(200, projectContext(projectOne, 'First project')))
      if (path === `/api/projects/${projectOne}/reports?limit=25`) return Promise.resolve(response(200, { items: [], nextCursor: null }))
      if (path === '/api/csrf') return Promise.resolve(response(200, { token: 'csrf-token' }))
      if (path === `/api/projects/${projectOne}/reports` && options?.method === 'POST') {
        return postIsPending
      }
      throw new Error(`Unexpected request ${path}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    window.history.replaceState(null, '', `/?project=${projectOne}`)
    render(<ReporterWorkspace {...workspaceProps()} />)
    await screen.findByText('Reports for First project')
    await screen.findByText('No reports yet')
    fireEvent.click(within(screen.getByRole('main')).getByRole('button', { name: 'New report' }))
    fillValidReport()
    fireEvent.click(screen.getByRole('button', { name: 'Create report' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`/api/projects/${projectOne}/reports`, expect.objectContaining({ method: 'POST' })))

    fireEvent.click(screen.getByRole('button', { name: 'Settings' }))
    expect(screen.getByRole('heading', { name: 'Settings' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Environments' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Reports' }))
    expect(screen.getByRole('heading', { name: 'New report' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Change project' }))
    expect(screen.getByText('The report is still being submitted. Wait for the save outcome before changing projects or reports.')).toBeInTheDocument()
    expect(screen.getByLabelText(/Title/)).toHaveValue('Cannot save report')
  })

  it('freezes the same form for retry when a request times out with an unknown outcome', async () => {
    const timeoutController = new AbortController()
    vi.spyOn(AbortSignal, 'timeout').mockReturnValue(timeoutController.signal)
    const fetchMock = vi.fn((input: RequestInfo | URL, options?: RequestInit) => {
      const path = String(input)
      if (path === `/api/projects/${projectOne}/reporter-context`) return Promise.resolve(response(200, projectContext(projectOne, 'First project')))
      if (path === `/api/projects/${projectOne}/reports?limit=25`) return Promise.resolve(response(200, { items: [], nextCursor: null }))
      if (path === '/api/csrf') return Promise.resolve(response(200, { token: 'csrf-token' }))
      if (path === `/api/projects/${projectOne}/reports` && options?.method === 'POST') {
        return new Promise<Response>((_resolve, reject) => {
          options.signal?.addEventListener('abort', () => reject(new DOMException('Request timed out', 'AbortError')), { once: true })
        })
      }
      throw new Error(`Unexpected request ${path}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    window.history.replaceState(null, '', `/?project=${projectOne}`)
    render(<ReporterWorkspace {...workspaceProps()} />)
    await screen.findByText('Reports for First project')
    await screen.findByText('No reports yet')
    fireEvent.click(within(screen.getByRole('main')).getByRole('button', { name: 'New report' }))
    fillValidReport()
    const bodyValue = 'Cannot save report'
    fireEvent.click(screen.getByRole('button', { name: 'Create report' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`/api/projects/${projectOne}/reports`, expect.objectContaining({ method: 'POST' })))
    timeoutController.abort()

    expect(await screen.findByText('The save outcome is unconfirmed. Retry uses the same report and submission key.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry submission' })).toBeEnabled()
    expect(screen.getByLabelText(/Title/)).toBeDisabled()
    expect(screen.getByLabelText(/Title/)).toHaveValue(bodyValue)
    const firstPost = fetchMock.mock.calls.find(([url, options]) => url === `/api/projects/${projectOne}/reports` && options?.method === 'POST')?.[1]
    expect(firstPost?.body).toContain(bodyValue)
    expect(new Headers(firstPost?.headers).get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/u)
  })
})
