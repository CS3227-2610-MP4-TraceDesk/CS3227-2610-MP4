import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ReportActivity } from './ReportActivity'
import { ReportApiError } from './reportApi'

const mocks = vi.hoisted(() => ({ fetch: vi.fn() }))
vi.mock('./reportApi', async importOriginal => ({ ...await importOriginal<typeof import('./reportApi')>(), fetchReportActivity: mocks.fetch }))
const props = { projectId: '44b10495-a536-4c6e-a410-93bd2d9330fd', reportId: '76989078-9c82-4ac5-9218-fc2b262f47e9', onProtectedFailure: vi.fn() }
const event = { id: '9223372036854775800', eventCode: 'REPORT_INVESTIGATION_STARTED', occurredAt: '2026-11-12T01:00:00Z', priorStatus: 'OPEN', newStatus: 'IN_PROGRESS', priorVersion: 1, newVersion: 2 }
beforeEach(() => vi.resetAllMocks())
afterEach(cleanup)

describe('Reporter activity', () => {
  it('loads confirmed chronology with explicit pagination and refresh', async () => {
    let resolve!: (value: unknown) => void
    mocks.fetch.mockReturnValueOnce(new Promise(done => { resolve = done }))
      .mockResolvedValueOnce({ items: [{ ...event, id: '9223372036854775801', eventCode: 'REPORT_RESOLUTION_PROPOSED', priorStatus: 'IN_PROGRESS', newStatus: 'READY_FOR_QA' }], nextCursor: null })
      .mockResolvedValueOnce({ items: [], nextCursor: null })
    render(<ReportActivity {...props} />)
    expect(screen.getByRole('status')).toHaveTextContent('Loading activity')
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
    await waitFor(() => expect(mocks.fetch).toHaveBeenCalledTimes(1))
    resolve({ items: [event], nextCursor: 'signed-page' })
    await screen.findByText('Investigation started')
    fireEvent.click(screen.getByRole('button', { name: 'Load more activity' }))
    await screen.findByText('Resolution proposed for QA review')
    expect(screen.getAllByRole('listitem').map(item => item.textContent)).toEqual([expect.stringContaining('Investigation started'), expect.stringContaining('Resolution proposed for QA review')])
    expect(mocks.fetch).toHaveBeenLastCalledWith(props.projectId, props.reportId, 'signed-page', expect.any(AbortSignal))
    expect(screen.queryByRole('button', { name: 'Load more activity' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Refresh activity' }))
    await screen.findByText('No activity yet.')
    expect(mocks.fetch).toHaveBeenCalledTimes(3)
  })

  it('preserves confirmed events on service failure and retries only on user action', async () => {
    mocks.fetch.mockResolvedValueOnce({ items: [event], nextCursor: 'next' })
      .mockRejectedValueOnce(new ReportApiError(503, 'SERVICE_UNAVAILABLE'))
      .mockResolvedValueOnce({ items: [], nextCursor: null })
    render(<ReportActivity {...props} />)
    await screen.findByText('Investigation started')
    fireEvent.click(screen.getByRole('button', { name: 'Load more activity' }))
    await screen.findByRole('alert')
    expect(screen.getByText('Investigation started')).toBeVisible()
    expect(mocks.fetch).toHaveBeenCalledTimes(2)
    fireEvent.click(screen.getByRole('button', { name: 'Retry activity' }))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  it.each([401, 403, 404])('clears protected activity and reports access failure %s', async status => {
    mocks.fetch.mockResolvedValueOnce({ items: [event], nextCursor: 'next' }).mockRejectedValueOnce(new ReportApiError(status))
    render(<ReportActivity {...props} />)
    await screen.findByText('Investigation started')
    fireEvent.click(screen.getByRole('button', { name: 'Refresh activity' }))
    await waitFor(() => expect(props.onProtectedFailure).toHaveBeenCalled())
    expect(screen.queryByText('Investigation started')).not.toBeInTheDocument()
  })

  it('requires refresh after a cursor expires', async () => {
    mocks.fetch.mockRejectedValueOnce(new ReportApiError(400, 'INVALID_CURSOR'))
    render(<ReportActivity {...props} />)
    await screen.findByText('This activity page has expired. Refresh activity to continue.')
    expect(screen.queryByRole('button', { name: 'Retry activity' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh activity' })).toBeEnabled()
  })

  it('cancels the old report request and ignores its late response', async () => {
    let resolve!: (value: unknown) => void
    mocks.fetch.mockReturnValueOnce(new Promise(done => { resolve = done })).mockResolvedValueOnce({ items: [], nextCursor: null })
    const { rerender } = render(<ReportActivity key="first" {...props} />)
    await waitFor(() => expect(mocks.fetch).toHaveBeenCalledTimes(1))
    const signal = mocks.fetch.mock.calls[0][3] as AbortSignal
    rerender(<ReportActivity key="second" {...props} reportId="7d73ebf6-b143-470e-b6e4-79d5af0cf0aa" />)
    await screen.findByText('No activity yet.')
    expect(signal.aborted).toBe(true)
    resolve({ items: [event], nextCursor: null })
    await waitFor(() => expect(screen.queryByText('Investigation started')).not.toBeInTheDocument())
  })
})
