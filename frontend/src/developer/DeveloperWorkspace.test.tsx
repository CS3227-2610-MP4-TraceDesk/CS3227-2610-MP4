import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

const mocks = vi.hoisted(() => ({
  fetchDeveloperContext: vi.fn(),
  fetchDeveloperReports: vi.fn(),
  fetchDeveloperReport: vi.fn(),
  fetchNotes: vi.fn(),
  fetchResolutions: vi.fn(),
  proposeResolution: vi.fn(),
  startInvestigation: vi.fn(),
  createNote: vi.fn(),
  updateNote: vi.fn(),
  deleteNote: vi.fn(),
  requestInvestigationSuggestions: vi.fn(),
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

beforeEach(() => {
  mocks.fetchResolutions.mockResolvedValue({ items: [], nextCursor: null })
})

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

  describe('proposed resolution', () => {
    const workingReport = { ...report, status: 'IN_PROGRESS' as const, version: 2 }
    const fields = { rootCause: '<script>cause</script>', changeSummary: 'Changed', verificationSteps: 'Verify', fixReference: '' }
    const revision = { ...fields, id: '88888888-8888-4888-8888-888888888888', reportId, authorUserId: actor, revision: 1, createdAt: '2026-11-08T00:03:00Z' }

    function setUp(detail: typeof report | typeof workingReport = workingReport) {
      window.history.replaceState(null, '', `/?workspace=developer&project=${project}`)
      mocks.fetchDeveloperContext.mockResolvedValue({ project: { id: project, name: 'Developer Project' }, role: 'DEVELOPER' })
      mocks.fetchDeveloperReports.mockResolvedValue({ items: [detail], nextCursor: null })
      mocks.fetchDeveloperReport.mockResolvedValue(detail)
      mocks.fetchNotes.mockResolvedValue({ items: [], nextCursor: null })
      render(<DeveloperWorkspace {...props()} />)
    }

    async function openAndFill() {
      fireEvent.click((await screen.findByText(report.title, { exact: true })).closest('button')!)
      fireEvent.change(await screen.findByLabelText('Root cause'), { target: { value: fields.rootCause } })
      fireEvent.change(screen.getByLabelText('Change summary'), { target: { value: fields.changeSummary } })
      fireEvent.change(screen.getByLabelText('Verification steps'), { target: { value: fields.verificationSteps } })
    }

    it('shows the handoff only after the server confirms it and renders it as text', async () => {
      setUp()
      mocks.proposeResolution.mockResolvedValue({ report: { ...workingReport, status: 'READY_FOR_QA', version: 3 }, resolution: revision })
      await openAndFill()
      fireEvent.click(screen.getByRole('button', { name: 'Send to QA' }))
      await waitFor(() => expect(mocks.proposeResolution).toHaveBeenCalledWith(project, reportId, fields, 2, expect.any(AbortSignal)))
      expect(await screen.findByText('Proposed resolution sent to QA after server confirmation.')).toBeInTheDocument()
      expect(screen.getByText(fields.rootCause, { exact: true })).toBeInTheDocument()
      expect(document.querySelector('.developer-detail script')).toBeNull()
      expect(screen.queryByRole('button', { name: 'Send to QA' })).not.toBeInTheDocument()
      expect(screen.getByText('Version 3')).toBeInTheDocument()
    })

    it('preserves the text and offers a refresh when the report version is stale', async () => {
      setUp()
      mocks.proposeResolution.mockRejectedValue(new DeveloperApiError(409, 'STALE_VERSION'))
      await openAndFill()
      fireEvent.click(screen.getByRole('button', { name: 'Send to QA' }))
      expect(await screen.findByText(/was not confirmed. Your text is preserved/)).toBeInTheDocument()
      expect(screen.getByLabelText('Root cause')).toHaveValue(fields.rootCause)
      fireEvent.click(screen.getByRole('button', { name: 'Refresh bug' }))
      await waitFor(() => expect(mocks.fetchDeveloperReport).toHaveBeenCalledTimes(2))
      expect(await screen.findByLabelText('Root cause')).toHaveValue(fields.rootCause)
    })

    it.each([
      [new DeveloperApiError(409, 'INVALID_STATE'), /was not confirmed. Your text is preserved/],
      [new DeveloperApiError(503, 'SERVICE_UNAVAILABLE'), /was not confirmed. Your text is preserved/],
      [new DeveloperApiError(0, 'NETWORK_ERROR'), /was not confirmed. Your text is preserved/],
      [new DeveloperApiError(400, 'VALIDATION_ERROR'), /rejected these fields/],
      [new DeveloperApiError(409, 'RESOLUTION_LIMIT'), /limit of 50/],
    ])('preserves the text on recoverable failure %#', async (failure, message) => {
      setUp()
      mocks.proposeResolution.mockRejectedValue(failure)
      await openAndFill()
      fireEvent.click(screen.getByRole('button', { name: 'Send to QA' }))
      expect(await screen.findByText(message)).toBeInTheDocument()
      expect(screen.getByLabelText('Root cause')).toHaveValue(fields.rootCause)
    })

    it('keeps a confirmed handoff when an older history response lands after it', async () => {
      const history = deferred<{ items: typeof revision[]; nextCursor: null }>()
      mocks.fetchResolutions.mockReturnValue(history.promise)
      setUp()
      mocks.proposeResolution.mockResolvedValue({ report: { ...workingReport, status: 'READY_FOR_QA', version: 3 }, resolution: revision })
      await openAndFill()
      fireEvent.click(screen.getByRole('button', { name: 'Send to QA' }))
      await screen.findByText('Proposed resolution sent to QA after server confirmation.')
      expect(screen.getByText(fields.rootCause, { exact: true })).toBeInTheDocument()
      await act(async () => {
        history.resolve({ items: [], nextCursor: null })
        await history.promise
      })
      expect(screen.getByText(fields.rootCause, { exact: true })).toBeInTheDocument()
      expect(screen.queryByText('No resolution has been proposed yet.')).not.toBeInTheDocument()
    })

    it('does not duplicate a confirmed handoff already present in the history response', async () => {
      const proposal = deferred<unknown>()
      mocks.fetchResolutions.mockRejectedValueOnce(new DeveloperApiError(503, 'SERVICE_UNAVAILABLE'))
      setUp()
      mocks.proposeResolution.mockReturnValue(proposal.promise)
      await openAndFill()
      const retry = await screen.findByRole('button', { name: 'Retry' })
      fireEvent.click(screen.getByRole('button', { name: 'Send to QA' }))
      mocks.fetchResolutions.mockResolvedValue({ items: [revision], nextCursor: null })
      fireEvent.click(retry)
      await screen.findByText(/^Revision 1/)
      await act(async () => {
        proposal.resolve({ report: { ...workingReport, status: 'READY_FOR_QA', version: 3 }, resolution: revision })
        await proposal.promise
      })
      await screen.findByText('Proposed resolution sent to QA after server confirmation.')
      expect(screen.getAllByText(fields.rootCause, { exact: true })).toHaveLength(1)
      expect(screen.queryByText(/Earlier revisions/)).not.toBeInTheDocument()
    })

    it('does not send the handoff when Enter is pressed in the fix reference', async () => {
      setUp()
      await openAndFill()
      const notPrevented = fireEvent.keyDown(screen.getByLabelText('Fix reference (optional)'), { key: 'Enter' })
      expect(notPrevented).toBe(false)
      expect(mocks.proposeResolution).not.toHaveBeenCalled()
    })

    it('removes the form after a reassignment denial', async () => {
      setUp()
      mocks.proposeResolution.mockRejectedValue(new DeveloperApiError(403, 'FORBIDDEN'))
      await openAndFill()
      fireEvent.click(screen.getByRole('button', { name: 'Send to QA' }))
      expect(await screen.findByText('Your assignment changed. The proposed resolution was not sent to QA.')).toBeInTheDocument()
      expect(screen.queryByLabelText('Root cause')).not.toBeInTheDocument()
    })

    it('keeps the form away from an open report and shows history to another Developer', async () => {
      const other = { ...workingReport, assignedDeveloperId: '77777777-7777-4777-8777-777777777777' }
      mocks.fetchResolutions.mockResolvedValue({ items: [revision, { ...revision, id: '99999999-9999-4999-8999-999999999999', revision: 2, rootCause: 'Latest cause' }], nextCursor: null })
      setUp(other)
      fireEvent.click(await screen.findByRole('button', { name: 'All bugs' }))
      fireEvent.click((await screen.findByText(report.title, { exact: true })).closest('button')!)
      expect(await screen.findByText('Latest cause')).toBeInTheDocument()
      expect(screen.getByText('Earlier revisions (1)')).toBeInTheDocument()
      expect(screen.queryByLabelText('Root cause')).not.toBeInTheDocument()
    })
  })

  describe('investigation suggestions', () => {
    const workingReport = { ...report, status: 'IN_PROGRESS' as const, version: 2 }
    const suggestion = {
      capability: 'INVESTIGATION' as const, promptVersion: 'investigation-suggestion-v1' as const,
      reportId, reportVersion: 2,
      reportFields: ['title', 'description', 'environment', 'reproductionSteps', 'expectedBehavior', 'actualBehavior'],
      sourceNotes: [],
      hypotheses: [{ statement: 'Inspect the startup path', rationale: 'The failure begins during startup.', sources: ['description'] }],
      nextSteps: ['Reproduce with debug logging'],
    }

    function setUp(properties = props()) {
      window.history.replaceState(null, '', `/?workspace=developer&project=${project}`)
      mocks.fetchDeveloperContext.mockResolvedValue({ project: { id: project, name: 'Developer Project' }, role: 'DEVELOPER' })
      mocks.fetchDeveloperReports.mockResolvedValue({ items: [workingReport], nextCursor: null })
      mocks.fetchDeveloperReport.mockResolvedValue(workingReport)
      mocks.fetchNotes.mockResolvedValue({ items: [note], nextCursor: null })
      render(<DeveloperWorkspace {...properties} />)
      return properties
    }

    async function openPanel() {
      fireEvent.click((await screen.findByText(report.title, { exact: true })).closest('button')!)
      await screen.findByRole('heading', { name: 'Investigation suggestions' })
    }

    it('removes the panel and note actions after a 403 generation denial', async () => {
      mocks.requestInvestigationSuggestions.mockRejectedValue(new DeveloperApiError(403, 'FORBIDDEN'))
      setUp()
      await openPanel()
      fireEvent.click(screen.getByRole('button', { name: 'Get suggestions' }))
      expect(await screen.findByText(/access changed/i)).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Investigation suggestions' })).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Add a private note')).not.toBeInTheDocument()
    })

    it('uses the existing session-expired flow for a 401 generation response', async () => {
      mocks.requestInvestigationSuggestions.mockRejectedValue(new DeveloperApiError(401, 'UNAUTHENTICATED'))
      const properties = setUp()
      await openPanel()
      fireEvent.click(screen.getByRole('button', { name: 'Get suggestions' }))
      await waitFor(() => expect(properties.onSessionExpired).toHaveBeenCalledTimes(1))
    })

    it('shows the shared freshness warning after focus revalidation and preserves the AI-assisted draft', async () => {
      mocks.requestInvestigationSuggestions.mockResolvedValue(suggestion)
      setUp()
      await openPanel()
      fireEvent.click(screen.getByRole('button', { name: 'Get suggestions' }))
      fireEvent.click(await screen.findByRole('checkbox', { name: suggestion.hypotheses[0].statement }))
      fireEvent.click(screen.getByRole('button', { name: 'Add selected to draft' }))
      const draft = screen.getByLabelText('Editable private-note draft')
      fireEvent.change(draft, { target: { value: 'Keep this edited draft' } })

      mocks.fetchDeveloperReport.mockResolvedValue({ ...workingReport, version: 3 })
      fireEvent.focus(window)
      expect(await screen.findByText(/investigation changed since it was last refreshed/i)).toBeInTheDocument()
      expect(screen.getByLabelText('Editable private-note draft')).toHaveValue('Keep this edited draft')
      expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument()
    })
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
