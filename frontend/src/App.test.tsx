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
})

describe('login and server session flow', () => {
  it('shows the project gate only after the server confirms a session', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply(200, { userId: 'synthetic-user' })))

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Open a project' })).toBeInTheDocument()
    expect(screen.queryByText(/reporter|developer|qa reviewer/i)).not.toBeInTheDocument()
  })

  it('gets CSRF, posts credentials and verifies the resulting session', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(401, { error: { code: 'UNAUTHENTICATED' } }))
      .mockResolvedValueOnce(reply(200, { token: 'synthetic-csrf' }))
      .mockResolvedValueOnce(reply(200, { userId: 'synthetic-user' }))
      .mockResolvedValueOnce(reply(200, { userId: 'synthetic-user' }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.change(await screen.findByLabelText('Email'), { target: { value: 'reporter@example.test' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'synthetic-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Open a project' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/csrf')
    expect(fetchMock.mock.calls[2][0]).toBe('/api/session/login')
    expect(fetchMock.mock.calls[2][1]).toMatchObject({
      method: 'POST',
      credentials: 'same-origin',
      body: JSON.stringify({ login: 'reporter@example.test', password: 'synthetic-password' }),
    })
    expect(new Headers(fetchMock.mock.calls[2][1]?.headers).get('X-CSRF-TOKEN')).toBe('synthetic-csrf')
    expect(fetchMock.mock.calls[3][0]).toBe('/api/session')
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
    expect(screen.queryByRole('heading', { name: 'Open a project' })).not.toBeInTheDocument()
  })

  it('retains signed-in state when the server cannot confirm logout', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { userId: 'synthetic-user' }))
      .mockResolvedValueOnce(reply(200, { token: 'synthetic-csrf' }))
      .mockResolvedValueOnce(reply(503, { error: { code: 'SERVICE_UNAVAILABLE' } }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Settings' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('could not confirm sign out')
    expect(screen.getByRole('heading', { name: 'Settings' })).toBeInTheDocument()
  })

  it('clears local state when the server confirms the session already ended', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { userId: 'synthetic-user' }))
      .mockResolvedValueOnce(reply(200, { token: 'synthetic-csrf' }))
      .mockResolvedValueOnce(reply(401, { error: { code: 'UNAUTHENTICATED' } }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Settings' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})
