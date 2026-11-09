import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

const mocks = vi.hoisted(() => ({
  fetchDeveloperContext: vi.fn(),
  fetchDeveloperReports: vi.fn(),
  fetchDeveloperReport: vi.fn(),
  fetchNotes: vi.fn(),
  startInvestigation: vi.fn(),
  createNote: vi.fn(),
  updateNote: vi.fn(),
  deleteNote: vi.fn(),
}))

vi.mock('./developerApi', async (original) => ({ ...(await original()), ...mocks }))

import { DeveloperWorkspace } from './DeveloperWorkspace'
import { DeveloperApiError } from './developerApi'

const project = '11111111-1111-4111-8111-111111111111'
const reportId = '22222222-2222-4222-8222-222222222222'
const actor = '44444444-4444-4444-8444-444444444444'
const report = {
  id: reportId, projectId: project, reporterUserId: '55555555-5555-4555-8555-555555555555',
  title: '<img src=x onerror=window.injected=true>', description: '<script>plain text</script>', environment: 'Browser',
  reproductionSteps: 'Steps', expectedBehavior: 'Expected', actualBehavior: 'Actual', textLogs: '<b>logs</b>', status: 'OPEN' as const,
  assignedDeveloperId: actor, createdAt: '2026-11-08T00:00:00Z', updatedAt: '2026-11-08T00:01:00Z', version: 1,
}
const note = { id: '33333333-3333-4333-8333-333333333333', reportId, authorUserId: actor, lastEditedByUserId: null, body: '<script>note</script>', createdAt: '2026-11-08T00:02:00Z', updatedAt: '2026-11-08T00:02:00Z', version: 1 }

function props() {
  return { sessionUserId: actor, theme: 'dark' as const, onThemeChange: vi.fn(), onSessionExpired: vi.fn(), onSignOut: vi.fn(), signingOut: false, signOutError: null }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(next => { resolve = next })
  return { promise, resolve }
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  vi.restoreAllMocks()
  window.history.replaceState(null, '', '/')
})

describe('Developer workspace', () => {
  it('uses trusted context, assigned filtering, safe text, confirmed start and private notes', async () => {
    window.history.replaceState(null, '', `/?workspace=developer&project=${project}`)
    mocks.fetchDeveloperContext.mockResolvedValue({ project: { id: project, name: 'Developer Project' }, role: 'DEVELOPER' })
    mocks.fetchDeveloperReports.mockResolvedValue({ items: [report], nextCursor: null })
    mocks.fetchDeveloperReport.mockResolvedValue(report)
    mocks.fetchNotes.mockResolvedValue({ items: [note], nextCursor: null })
    mocks.startInvestigation.mockResolvedValue({ ...report, status: 'IN_PROGRESS', version: 2 })
    mocks.createNote.mockResolvedValue({ ...note, id: '66666666-6666-4666-8666-666666666666', body: 'new private note' })

    const { container } = render(<DeveloperWorkspace {...props()} />)
    expect(await screen.findByRole('heading', { name: 'My work' })).toBeInTheDocument()
    expect(mocks.fetchDeveloperReports).toHaveBeenCalledWith(project, 'assigned-to-me', '', null, expect.any(AbortSignal))
    fireEvent.click((await screen.findByText(report.title, { exact: true })).closest('button')!)
    expect(await screen.findByRole('heading', { name: report.title })).toBeInTheDocument()
    expect(screen.getByText(report.description, { exact: true })).toBeInTheDocument()
    expect(screen.getByText(note.body, { exact: true })).toBeInTheDocument()
    expect(container.querySelector('script, img[onerror], b')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Start investigation' }))
    await waitFor(() => expect(mocks.startInvestigation).toHaveBeenCalledWith(project, reportId, 1, expect.any(AbortSignal)))
    expect(await screen.findByText(/server confirmed the change/i)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Add a private note'), { target: { value: 'new private note' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save note' }))
    await waitFor(() => expect(mocks.createNote).toHaveBeenCalledWith(project, reportId, 'new private note', expect.any(String), expect.any(AbortSignal)))
    expect(await screen.findByText('new private note')).toBeInTheDocument()
    expect(mocks.createNote).toHaveBeenCalledWith(project, reportId, 'new private note', expect.any(String), expect.any(AbortSignal))
  })

  it('loads All bugs without an assignment filter and hides notes for another assignee', async () => {
    window.history.replaceState(null, '', `/?workspace=developer&project=${project}`)
    const otherReport = { ...report, assignedDeveloperId: '77777777-7777-4777-8777-777777777777' }
    mocks.fetchDeveloperContext.mockResolvedValue({ project: { id: project, name: 'Developer Project' }, role: 'DEVELOPER' })
    mocks.fetchDeveloperReports.mockResolvedValue({ items: [otherReport], nextCursor: null })
    mocks.fetchDeveloperReport.mockResolvedValue(otherReport)

    render(<DeveloperWorkspace {...props()} />)
    await screen.findByRole('heading', { name: 'My work' })
    fireEvent.click(screen.getByRole('button', { name: 'All bugs' }))
    expect(await screen.findByRole('heading', { name: 'All bugs' })).toBeInTheDocument()
    await waitFor(() => expect(mocks.fetchDeveloperReports).toHaveBeenLastCalledWith(project, 'all', '', null, expect.any(AbortSignal)))
    fireEvent.click((await screen.findByText(report.title, { exact: true })).closest('button')!)
    expect(await screen.findByText('Private investigation notes are available only to the current assignee.')).toBeInTheDocument()
    expect(mocks.fetchNotes).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Start investigation' })).not.toBeInTheDocument()
  })

  it('preserves a note draft on a recoverable failure and removes actions after reassignment', async () => {
    window.history.replaceState(null, '', `/?workspace=developer&project=${project}`)
    const workingReport = { ...report, status: 'IN_PROGRESS' as const, version: 2 }
    mocks.fetchDeveloperContext.mockResolvedValue({ project: { id: project, name: 'Developer Project' }, role: 'DEVELOPER' })
    mocks.fetchDeveloperReports.mockResolvedValue({ items: [workingReport], nextCursor: null })
    mocks.fetchDeveloperReport.mockResolvedValueOnce(workingReport).mockRejectedValue(new DeveloperApiError(503, 'SERVICE_UNAVAILABLE'))
    mocks.fetchNotes.mockResolvedValue({ items: [], nextCursor: null })
    mocks.createNote.mockRejectedValue(new DeveloperApiError(403, 'FORBIDDEN'))

    render(<DeveloperWorkspace {...props()} />)
    fireEvent.click((await screen.findByText(report.title, { exact: true })).closest('button')!)
    const composer = await screen.findByLabelText('Add a private note')
    fireEvent.change(composer, { target: { value: 'preserve me' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save note' }))
    await waitFor(() => expect(screen.getByText('Your assignment changed. Your unsaved note remains in the composer.')).toBeInTheDocument())
    expect(mocks.createNote).toHaveBeenCalledWith(project, reportId, 'preserve me', expect.any(String), expect.any(AbortSignal))
    expect(screen.queryByLabelText('Add a private note')).not.toBeInTheDocument()
  })

  it('discards a delayed private-note result after switching reports', async () => {
    window.history.replaceState(null, '', `/?workspace=developer&project=${project}`)
    const first = { ...report, status: 'IN_PROGRESS' as const, version: 2, title: 'First bug' }
    const second = { ...first, id: '77777777-7777-4777-8777-777777777777', title: 'Second bug' }
    const pendingCreate = deferred<typeof note>()
    mocks.fetchDeveloperContext.mockResolvedValue({ project: { id: project, name: 'Developer Project' }, role: 'DEVELOPER' })
    mocks.fetchDeveloperReports.mockResolvedValue({ items: [first, second], nextCursor: null })
    mocks.fetchDeveloperReport.mockImplementation((_projectId, id) => Promise.resolve(id === first.id ? first : second))
    mocks.fetchNotes.mockResolvedValue({ items: [], nextCursor: null })
    mocks.createNote.mockReturnValue(pendingCreate.promise)

    render(<DeveloperWorkspace {...props()} />)
    fireEvent.click(await screen.findByRole('button', { name: /First bug/ }))
    fireEvent.change(await screen.findByLabelText('Add a private note'), { target: { value: 'belongs only to the first bug' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save note' }))
    fireEvent.click(screen.getByRole('button', { name: 'Back to bugs' }))
    fireEvent.click(await screen.findByRole('button', { name: /Second bug/ }))
    expect(await screen.findByRole('heading', { name: 'Second bug' })).toBeInTheDocument()

    await act(async () => {
      pendingCreate.resolve({ ...note, body: 'belongs only to the first bug' })
      await pendingCreate.promise
    })
    expect(mocks.createNote).toHaveBeenCalled()
    expect(screen.queryByText('belongs only to the first bug')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Second bug' })).toBeInTheDocument()
  })

  it('applies confirmed note edits and deletes through the current report context', async () => {
    window.history.replaceState(null, '', `/?workspace=developer&project=${project}`)
    const workingReport = { ...report, status: 'IN_PROGRESS' as const, version: 2 }
    const editedNote = { ...note, body: 'confirmed edit', lastEditedByUserId: actor, version: 2 }
    mocks.fetchDeveloperContext.mockResolvedValue({ project: { id: project, name: 'Developer Project' }, role: 'DEVELOPER' })
    mocks.fetchDeveloperReports.mockResolvedValue({ items: [workingReport], nextCursor: null })
    mocks.fetchDeveloperReport.mockResolvedValue(workingReport)
    mocks.fetchNotes.mockResolvedValue({ items: [note], nextCursor: null })
    mocks.updateNote.mockResolvedValue(editedNote)
    mocks.deleteNote.mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    render(<DeveloperWorkspace {...props()} />)
    fireEvent.click((await screen.findByText(report.title, { exact: true })).closest('button')!)
    const editButton = await screen.findByRole('button', { name: 'Edit' })
    fireEvent.click(editButton)
    const editField = screen.getByLabelText('Edit private note')
    fireEvent.change(editField, { target: { value: editedNote.body } })
    fireEvent.click(within(editField.closest('li')!).getByRole('button', { name: 'Save note' }))
    await waitFor(() => expect(mocks.updateNote).toHaveBeenCalledWith(project, reportId, note.id, editedNote.body, 1, expect.any(AbortSignal)))
    expect(await screen.findByText('Private note updated after server confirmation.')).toBeInTheDocument()
    expect(await screen.findByText(editedNote.body)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
    await waitFor(() => expect(mocks.deleteNote).toHaveBeenCalledWith(project, reportId, note.id, 2, expect.any(AbortSignal)))
    expect(screen.queryByText(editedNote.body)).not.toBeInTheDocument()
  })

  it('retries unavailable context even when the project ID is unchanged', async () => {
    window.history.replaceState(null, '', `/?workspace=developer&project=${project}`)
    mocks.fetchDeveloperContext
      .mockRejectedValueOnce(new DeveloperApiError(503, 'SERVICE_UNAVAILABLE'))
      .mockResolvedValue({ project: { id: project, name: 'Developer Project' }, role: 'DEVELOPER' })
    mocks.fetchDeveloperReports.mockResolvedValue({ items: [], nextCursor: null })

    render(<DeveloperWorkspace {...props()} />)
    expect(await screen.findByRole('heading', { name: 'Developer workspace unavailable' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('heading', { name: 'My work' })).toBeInTheDocument()
    expect(mocks.fetchDeveloperContext).toHaveBeenCalledTimes(2)
  })
})
