import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'

const requestInvestigationSuggestions = vi.hoisted(() => vi.fn())
vi.mock('./developerApi', async original => ({ ...(await original()), requestInvestigationSuggestions }))

import { DeveloperAiPanel } from './DeveloperAiPanel'
import { DeveloperApiError, type InvestigationSuggestion } from './developerApi'

const projectId = '11111111-1111-4111-8111-111111111111'
const reportId = '22222222-2222-4222-8222-222222222222'
const note = {
  id: '33333333-3333-4333-8333-333333333333', reportId,
  authorUserId: '44444444-4444-4444-8444-444444444444', lastEditedByUserId: null,
  body: 'Saved note text', createdAt: '2026-11-08T00:02:00Z', updatedAt: '2026-11-08T00:02:00Z', version: 1,
}
const suggestion: InvestigationSuggestion = {
  capability: 'INVESTIGATION', promptVersion: 'investigation-suggestion-v1', reportId, reportVersion: 2,
  reportFields: ['title', 'description', 'environment', 'reproductionSteps', 'expectedBehavior', 'actualBehavior'],
  sourceNotes: [],
  hypotheses: [{
    statement: '<img src=x onerror=window.injected=true>',
    rationale: '<script>window.injected=true</script>',
    sources: ['title'],
  }],
  nextSteps: ['<svg onload=window.injected=true>'],
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(next => { resolve = next })
  return { promise, resolve }
}

function setup(overrides: Partial<React.ComponentProps<typeof DeveloperAiPanel>> = {}) {
  const props: React.ComponentProps<typeof DeveloperAiPanel> = {
    projectId, reportId, reportVersion: 2, notes: [note], disabled: false,
    onSaveDraft: vi.fn().mockResolvedValue(true), onProtectedFailure: vi.fn(), onSourceStale: vi.fn(),
    ...overrides,
  }
  return { props, ...render(<DeveloperAiPanel {...props} />) }
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('Developer AI panel', () => {
  it('uses the specified defaults, renders hostile suggestions inert, and saves only after an edited explicit draft save', async () => {
    const onSaveDraft = vi.fn().mockResolvedValue(true)
    requestInvestigationSuggestions.mockResolvedValue(suggestion)
    const { container } = setup({ onSaveDraft })

    fireEvent.click(screen.getByText('Choose AI context'))
    expect(screen.getByRole('checkbox', { name: 'Title' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Logs or error text' })).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Private note 1' })).not.toBeChecked()
    fireEvent.click(screen.getByRole('button', { name: 'Get suggestions' }))

    expect(await screen.findByText(suggestion.hypotheses[0].statement, { exact: true })).toBeInTheDocument()
    expect(screen.getByText(suggestion.hypotheses[0].rationale, { exact: true })).toBeInTheDocument()
    expect(screen.getByText('Sources:').closest('p')).toHaveTextContent('Sources: Title')
    expect(container.querySelector('script, img, svg[onload]')).toBeNull()
    expect(onSaveDraft).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('checkbox', { name: suggestion.hypotheses[0].statement }))
    fireEvent.click(screen.getByRole('checkbox', { name: suggestion.nextSteps[0] }))
    fireEvent.click(screen.getByRole('button', { name: 'Add selected to draft' }))
    const draft = screen.getByLabelText('Editable private-note draft')
    expect((draft as HTMLTextAreaElement).value).toContain('Hypothesis')
    expect(onSaveDraft).not.toHaveBeenCalled()
    fireEvent.change(draft, { target: { value: 'Edited ordinary private note' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save private note' }))
    await waitFor(() => expect(onSaveDraft).toHaveBeenCalledWith('Edited ordinary private note'))
  })

  it('shows an explicit empty-result state', async () => {
    requestInvestigationSuggestions.mockResolvedValue({ ...suggestion, hypotheses: [], nextSteps: [] })
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'Get suggestions' }))
    expect(await screen.findByText('No suggestions were supported by the selected context.')).toBeInTheDocument()
  })

  it('discards a late response after the context selection changes', async () => {
    const pending = deferred<InvestigationSuggestion>()
    requestInvestigationSuggestions.mockReturnValue(pending.promise)
    setup()
    fireEvent.click(screen.getByText('Choose AI context'))
    fireEvent.click(screen.getByRole('button', { name: 'Get suggestions' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Logs or error text' }))
    await act(async () => { pending.resolve(suggestion); await pending.promise })
    expect(screen.queryByText(suggestion.hypotheses[0].statement, { exact: true })).not.toBeInTheDocument()
  })

  it.each([
    ['field', 'Logs or error text'],
    ['note', 'Private note 1'],
  ])('clears the preview and editable draft when the %s selection changes', async (_, selectionLabel) => {
    requestInvestigationSuggestions.mockResolvedValue(suggestion)
    setup()
    fireEvent.click(screen.getByText('Choose AI context'))
    fireEvent.click(screen.getByRole('button', { name: 'Get suggestions' }))
    await screen.findByText(suggestion.hypotheses[0].statement, { exact: true })
    fireEvent.click(screen.getByRole('checkbox', { name: suggestion.hypotheses[0].statement }))
    fireEvent.click(screen.getByRole('button', { name: 'Add selected to draft' }))
    expect((screen.getByLabelText('Editable private-note draft') as HTMLTextAreaElement).value).toContain('Hypothesis')

    fireEvent.click(screen.getByRole('checkbox', { name: selectionLabel }))
    expect(screen.queryByText('Suggestion preview')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Get suggestions' }))
    await screen.findByText(suggestion.hypotheses[0].statement, { exact: true })
    expect(screen.getByLabelText('Editable private-note draft')).toHaveValue('')
  })

  it('discards a late response after the report identity changes', async () => {
    const pending = deferred<InvestigationSuggestion>()
    requestInvestigationSuggestions.mockReturnValue(pending.promise)
    const first = setup()
    fireEvent.click(screen.getByRole('button', { name: 'Get suggestions' }))
    const otherReport = '77777777-7777-4777-8777-777777777777'
    first.rerender(<DeveloperAiPanel key={`${projectId}:${otherReport}`} {...first.props} reportId={otherReport} />)
    await act(async () => { pending.resolve(suggestion); await pending.promise })
    expect(screen.queryByText(suggestion.hypotheses[0].statement, { exact: true })).not.toBeInTheDocument()
  })

  it('cancels without describing usage as refunded and ignores the late result', async () => {
    const pending = deferred<InvestigationSuggestion>()
    requestInvestigationSuggestions.mockReturnValue(pending.promise)
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'Get suggestions' }))
    fireEvent.click(screen.getByRole('button', { name: 'Cancel suggestions' }))
    expect(screen.getByText(/already sent may still count toward usage/i)).toBeInTheDocument()
    expect(screen.queryByText(/refund/i)).not.toBeInTheDocument()
    await act(async () => { pending.resolve(suggestion); await pending.promise })
    expect(screen.queryByText(suggestion.hypotheses[0].statement, { exact: true })).not.toBeInTheDocument()
  })

  it.each([
    ['AI_UNAVAILABLE', 503, /AI suggestions are unavailable/i],
    ['AI_INVALID_OUTPUT', 502, /invalid suggestion/i],
    ['SERVICE_UNAVAILABLE', 503, /suggestion service is temporarily unavailable/i],
    ['AI_CONTEXT_TOO_LARGE', 400, /Deselect report fields or notes/i],
  ])('keeps note drafting usable for %s', async (code, status, expected) => {
    requestInvestigationSuggestions.mockRejectedValue(new DeveloperApiError(status, code))
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'Get suggestions' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(expected)
    expect(screen.getByRole('button', { name: 'Get suggestions' })).toBeEnabled()
  })

  it.each([[401, 'UNAUTHENTICATED'], [403, 'FORBIDDEN']])('delegates protected failure %s to the workspace', async (status, code) => {
    const onProtectedFailure = vi.fn()
    requestInvestigationSuggestions.mockRejectedValue(new DeveloperApiError(status, code))
    setup({ onProtectedFailure })
    fireEvent.click(screen.getByRole('button', { name: 'Get suggestions' }))
    await waitFor(() => expect(onProtectedFailure).toHaveBeenCalledWith(expect.objectContaining({ status, code })))
  })
})
