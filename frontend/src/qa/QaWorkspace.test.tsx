import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

const mocks = vi.hoisted(() => ({
  fetchReports: vi.fn(),
  fetchReport: vi.fn(),
  fetchResolutions: vi.fn(),
  requestVerification: vi.fn(),
  fetchReviewedByMe: vi.fn(),
  fetchQaTemplates: vi.fn(),
}))

vi.mock('../reports/reportApi', async original => ({
  ...(await original()), fetchReports: mocks.fetchReports, fetchReport: mocks.fetchReport,
}))
vi.mock('../developer/developerApi', async original => ({
  ...(await original()), fetchResolutions: mocks.fetchResolutions,
}))
vi.mock('./verificationApi', () => ({ requestVerification: mocks.requestVerification }))
vi.mock('./QaAssignment', () => ({ QaAssignment: () => null }))
vi.mock('./qaHistoryApi', async original => ({
  ...(await original()), fetchReviewedByMe: mocks.fetchReviewedByMe,
}))
vi.mock('./qaTemplatesApi', async original => ({
  ...(await original()), fetchQaTemplates: mocks.fetchQaTemplates,
}))

import { QaWorkspace } from './QaWorkspace'

beforeEach(() => {
  mocks.fetchReviewedByMe.mockResolvedValue({ items: [], nextCursor: null })
  mocks.fetchQaTemplates.mockResolvedValue([])
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

it('restores each bug tab and draft and protects a draft from a changed server version', async () => {
  const first = { id: 'one', title: 'First QA bug', version: 1 }
  const second = { id: 'two', title: 'Second QA bug', version: 1 }
  const reports = [first, second].map(item => ({
    ...item, status: 'READY_FOR_QA', updatedAt: '2026-11-12T00:00:00Z',
    description: 'Description', textLogs: 'Test logs', expectedBehavior: 'Expected', actualBehavior: 'Actual',
    reproductionSteps: 'Steps', environment: 'Test',
  }))
  let firstVersion = 1
  mocks.fetchReports.mockResolvedValue({ items: reports, nextCursor: null })
  mocks.fetchReport.mockImplementation(async (_project, reportId) => ({
    ...reports.find(report => report.id === reportId),
    version: reportId === first.id ? firstVersion : 1,
  }))
  mocks.fetchResolutions.mockResolvedValue({
    items: [{ id: 'resolution', revision: 1, rootCause: 'Cause', changeSummary: 'Fix', verificationSteps: 'Check' }],
    nextCursor: null,
  })
  mocks.requestVerification.mockImplementation(async (_project, reportId) => ({
    version: reportId === first.id ? firstVersion : 1, revision: 1, status: 'READY_FOR_QA',
    checks: [], notes: '', reason: '', decision: 'SAVE',
  }))
  render(<QaWorkspace project={{ id: 'project', name: 'QA project' }} theme="dark"
    onThemeChange={vi.fn()} onSessionExpired={vi.fn()} onSignOut={vi.fn()} onChooseWorkspace={vi.fn()}
    signingOut={false} signOutError={null} />)

  async function open(title: string) {
    fireEvent.click(await within(screen.getByRole('main')).findByRole('button', { name: new RegExp(title) }))
    await screen.findByRole('group', { name: 'Bug review sections' })
  }

  await open(first.title)
  fireEvent.click(within(screen.getByRole('group', { name: 'Bug review sections' })).getByRole('button', { name: 'Verification' }))
  fireEvent.change(await screen.findByLabelText('Verification notes'), { target: { value: 'Draft for first bug' } })
  fireEvent.change(screen.getByRole('textbox', { name: 'New check' }), { target: { value: 'Unsubmitted check' } })
  const group = within(screen.getByRole('complementary', { name: 'Verification' }))
    .getByText('Awaiting verification').closest('details')!
  fireEvent.click(group.querySelector('summary')!)
  expect(group).not.toHaveAttribute('open')
  expect(screen.getByLabelText('Verification notes')).toHaveValue('Draft for first bug')
  fireEvent.click(group.querySelector('summary')!)
  fireEvent.click(screen.getByRole('button', { name: 'Back to verification queue' }))

  await open(second.title)
  expect(screen.getByRole('button', { name: 'Summary' })).toHaveAttribute('aria-pressed', 'true')
  fireEvent.click(within(screen.getByRole('group', { name: 'Bug review sections' })).getByRole('button', { name: 'Verification' }))
  expect(await screen.findByLabelText('Verification notes')).toHaveValue('')
  fireEvent.change(screen.getByLabelText('Verification notes'), { target: { value: 'Second draft' } })
  fireEvent.click(screen.getByRole('button', { name: 'Back to verification queue' }))

  firstVersion = 2
  await open(first.title)
  expect(within(screen.getByRole('group', { name: 'Bug review sections' })).getByRole('button', { name: 'Verification' })).toHaveAttribute('aria-pressed', 'true')
  expect(await screen.findByLabelText('Verification notes')).toHaveValue('Draft for first bug')
  expect(await screen.findByText('The report changed. Reload verification before saving again.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Save verification' })).toBeDisabled()
  expect(mocks.requestVerification.mock.calls.every(call => call[3] === undefined)).toBe(true)
  fireEvent.click(screen.getByRole('button', { name: 'Discard edits and reload' }))
  await waitFor(() => expect(screen.getByLabelText('Verification notes')).toHaveValue(''))
})

it('groups bugs under disclosures and keeps all queue filters in the main view', async () => {
  const ready = { id: 'ready', title: 'Awaiting bug', status: 'READY_FOR_QA', updatedAt: '2026-11-12T00:00:00Z' }
  const open = { id: 'open', title: 'Unassigned bug', status: 'OPEN', updatedAt: '2026-11-12T00:00:00Z' }
  mocks.fetchReports.mockImplementation(async (_project, status) => ({
    items: status === 'READY_FOR_QA' ? [ready] : [ready, open], nextCursor: null,
  }))
  mocks.fetchReviewedByMe.mockResolvedValue({
    items: [{ reportId: 'past', title: 'Reviewed bug', resolutionId: 'resolution-past', revision: 2,
      decision: 'CLOSE', reviewedAt: '2026-11-11T00:00:00Z', reviewerUserId: 'qa' }], nextCursor: null,
  })
  render(<QaWorkspace project={{ id: 'project', name: 'QA project' }} theme="dark"
    onThemeChange={vi.fn()} onSessionExpired={vi.fn()} onSignOut={vi.fn()} onChooseWorkspace={vi.fn()}
    signingOut={false} signOutError={null} />)

  const sidebar = within(screen.getByRole('complementary', { name: 'Verification' }))
  expect(await sidebar.findByRole('button', { name: 'Awaiting bug' })).toBeVisible()
  expect(await sidebar.findByRole('button', { name: 'Reviewed bug · Revision 2' })).toBeVisible()
  expect(sidebar.queryByRole('button', { name: 'Awaiting verification' })).not.toBeInTheDocument()
  expect(sidebar.queryByRole('button', { name: 'Reviewed by me' })).not.toBeInTheDocument()
  expect(sidebar.queryByRole('button', { name: 'All bugs' })).not.toBeInTheDocument()

  fireEvent.click(within(screen.getByRole('group', { name: 'Bug queues' })).getByRole('button', { name: 'All bugs' }))
  expect(await within(screen.getByRole('main')).findByRole('button', { name: /Unassigned bug/ })).toBeVisible()
  expect(within(screen.getByRole('main')).getByRole('heading', { name: 'Verification' })).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: 'Grid view' }))
  fireEvent.click(within(screen.getByRole('group', { name: 'Bug queues' })).getByRole('button', { name: 'Reviewed by me' }))
  expect(await within(screen.getByRole('main')).findByRole('button', { name: /Reviewed bug, revision 2/ })).toBeVisible()
  expect(within(screen.getByRole('main')).getByRole('heading', { name: 'Verification' })).toBeVisible()
  expect(screen.getByRole('button', { name: 'Grid view' })).toHaveAttribute('aria-pressed', 'true')
  expect(screen.getByRole('button', { name: 'Sort bugs' })).toBeVisible()
  expect(screen.getByRole('button', { name: 'Display options' })).toBeVisible()
  fireEvent.change(screen.getByRole('searchbox', { name: 'Find bug' }), { target: { value: 'missing' } })
  expect(within(screen.getByRole('main')).queryByRole('button', { name: /Reviewed bug, revision 2/ })).not.toBeInTheDocument()
  expect(mocks.fetchReviewedByMe).toHaveBeenCalledWith('project', null, expect.any(AbortSignal))

  fireEvent.change(sidebar.getByRole('searchbox', { name: 'Search review queue' }), { target: { value: 'Awaiting' } })
  expect(sidebar.getByRole('button', { name: 'Awaiting bug' })).toBeVisible()
  expect(sidebar.queryByRole('button', { name: 'Reviewed bug · Revision 2' })).not.toBeInTheDocument()
})
