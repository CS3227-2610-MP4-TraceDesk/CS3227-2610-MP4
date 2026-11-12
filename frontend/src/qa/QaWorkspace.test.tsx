import { afterEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

const mocks = vi.hoisted(() => ({
  fetchReports: vi.fn(),
  fetchReport: vi.fn(),
  fetchResolutions: vi.fn(),
  requestVerification: vi.fn(),
}))

vi.mock('../reports/reportApi', async original => ({
  ...(await original()), fetchReports: mocks.fetchReports, fetchReport: mocks.fetchReport,
}))
vi.mock('../developer/developerApi', async original => ({
  ...(await original()), fetchResolutions: mocks.fetchResolutions,
}))
vi.mock('./verificationApi', () => ({ requestVerification: mocks.requestVerification }))
vi.mock('./QaAssignment', () => ({ QaAssignment: () => null }))

import { QaWorkspace } from './QaWorkspace'

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
  fireEvent.click(within(screen.getByRole('group', { name: 'Bug review sections' })).getByRole('button', { name: 'Verification', exact: true }))
  fireEvent.change(await screen.findByLabelText('Verification notes'), { target: { value: 'Draft for first bug' } })
  fireEvent.change(screen.getByRole('textbox', { name: 'New check' }), { target: { value: 'Unsubmitted check' } })
  fireEvent.click(screen.getByRole('button', { name: 'Back to verification queue' }))

  await open(second.title)
  expect(screen.getByRole('button', { name: 'Summary' })).toHaveAttribute('aria-pressed', 'true')
  fireEvent.click(within(screen.getByRole('group', { name: 'Bug review sections' })).getByRole('button', { name: 'Verification', exact: true }))
  expect(await screen.findByLabelText('Verification notes')).toHaveValue('')
  fireEvent.change(screen.getByLabelText('Verification notes'), { target: { value: 'Second draft' } })
  fireEvent.click(screen.getByRole('button', { name: 'Back to verification queue' }))

  firstVersion = 2
  await open(first.title)
  expect(within(screen.getByRole('group', { name: 'Bug review sections' })).getByRole('button', { name: 'Verification', exact: true })).toHaveAttribute('aria-pressed', 'true')
  expect(await screen.findByLabelText('Verification notes')).toHaveValue('Draft for first bug')
  expect(await screen.findByText('The report changed. Reload verification before saving again.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Save verification' })).toBeDisabled()
  expect(mocks.requestVerification.mock.calls.every(call => call[3] === undefined)).toBe(true)
  fireEvent.click(screen.getByRole('button', { name: 'Discard edits and reload' }))
  await waitFor(() => expect(screen.getByLabelText('Verification notes')).toHaveValue(''))
})
