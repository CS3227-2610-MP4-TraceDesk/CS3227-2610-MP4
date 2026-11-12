import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { QaHistoricalReview, QaReviewHistory, type QaHistoryEntry } from './QaReviewHistory'

afterEach(cleanup)

const entry: QaHistoryEntry = {
  reportId: 'report-1',
  title: 'Refund totals are duplicated',
  resolutionId: 'resolution-1',
  revision: 2,
  decision: 'SEND_BACK',
  reviewedAt: '2026-11-10T08:30:00Z',
  reviewerUserId: 'qa-reviewer-1',
}

describe('QA review history presentation', () => {
  it('shows a visible empty state', () => {
    render(<QaReviewHistory entries={[]} onOpen={vi.fn()} />)

    expect(screen.getByText('No completed QA reviews yet.')).toBeVisible()
  })

  it('opens the selected review entry', () => {
    const onOpen = vi.fn()
    render(<QaReviewHistory entries={[entry]} onOpen={onOpen} />)

    fireEvent.click(screen.getByRole('button', {
      name: 'Refund totals are duplicated, revision 2, Sent back',
    }))

    expect(onOpen).toHaveBeenCalledWith(entry)
  })

  it('renders historical review fields read-only as text', () => {
    const onBack = vi.fn()
    render(<QaHistoricalReview review={{
      ...entry,
      checks: [{ title: 'Refresh twice', result: 'Pass', notes: 'No duplicates appeared.' }],
      notes: 'Verified on build 1.4.2.',
      reason: 'Please verify with multiple refunds.',
    }} onBack={onBack} />)

    expect(screen.getByText('Refund totals are duplicated')).toBeVisible()
    expect(screen.getByText('Sent back')).toBeVisible()
    expect(screen.getByText('Reason for sending back')).toBeVisible()
    expect(screen.getByText('Please verify with multiple refunds.')).toBeVisible()
    fireEvent.click(screen.getByText('Refresh twice', { selector: 'summary span' }))
    expect(screen.getByText('No duplicates appeared.')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Back to review history' }))
    expect(onBack).toHaveBeenCalledOnce()
  })
})
