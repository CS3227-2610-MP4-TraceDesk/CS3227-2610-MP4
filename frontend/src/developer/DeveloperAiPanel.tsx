import { useEffect, useMemo, useRef, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Checkbox } from '@openai/apps-sdk-ui/components/Checkbox'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { reportFieldRules } from '../reports/reportValidation'
import { Disclosure } from '../shared/Disclosure'
import {
  DeveloperApiError,
  investigationReportFields,
  requestInvestigationSuggestions,
  type InvestigationNote,
  type InvestigationReportField,
  type InvestigationSelection,
  type InvestigationSuggestion,
} from './developerApi'

const defaultFields = investigationReportFields.filter(field => field !== 'textLogs')

function failureMessage(error: unknown) {
  if (error instanceof DeveloperApiError) {
    if (error.code === 'AI_CONTEXT_TOO_LARGE') return 'Selected context is too large. Deselect report fields or notes, or save a short excerpt as a private note and select it.'
    if (error.code === 'AI_INVALID_OUTPUT' || error.code === 'INVALID_RESPONSE') return 'AI returned an invalid suggestion. Private notes remain available.'
    if (error.code === 'AI_UNAVAILABLE') return 'AI suggestions are unavailable right now. Private notes remain available.'
    if (error.code === 'SERVICE_UNAVAILABLE') return 'The suggestion service is temporarily unavailable. Private notes remain available.'
    if (error.code === 'STALE_VERSION' || error.code === 'INVALID_STATE') return 'The investigation changed since it was last refreshed. Refresh before requesting new suggestions.'
  }
  return 'Suggestions could not be requested. Private notes remain available.'
}

function requestToken(projectId: string, reportId: string, selection: InvestigationSelection) {
  return JSON.stringify([projectId, reportId, selection.expectedVersion, selection.reportFields, selection.notes])
}

function itemDraft(result: InvestigationSuggestion, selected: Set<string>) {
  const sections: string[] = []
  result.hypotheses.forEach((hypothesis, index) => {
    if (selected.has(`hypothesis:${index}`)) sections.push(`Hypothesis\n${hypothesis.statement}\n\nRationale\n${hypothesis.rationale}`)
  })
  const steps = result.nextSteps.filter((_, index) => selected.has(`step:${index}`))
  if (steps.length) sections.push(`Next steps\n${steps.map(step => `- ${step}`).join('\n')}`)
  return sections.join('\n\n')
}

export function DeveloperAiPanel({
  projectId,
  reportId,
  reportVersion,
  notes,
  disabled,
  onSaveDraft,
  onProtectedFailure,
  onSourceStale,
}: {
  projectId: string
  reportId: string
  reportVersion: number
  notes: InvestigationNote[]
  disabled: boolean
  onSaveDraft: (body: string) => Promise<boolean>
  onProtectedFailure: (error: DeveloperApiError) => void
  onSourceStale: () => void
}) {
  const [fields, setFields] = useState<Set<InvestigationReportField>>(new Set(defaultFields))
  const [noteIds, setNoteIds] = useState<Set<string>>(new Set())
  const [pending, setPending] = useState(false)
  const [saving, setSaving] = useState(false)
  const [result, setResult] = useState<InvestigationSuggestion | null>(null)
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set())
  const [draft, setDraft] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [failure, setFailure] = useState(false)
  const controller = useRef<AbortController | null>(null)
  const activeToken = useRef<string | null>(null)
  const requestLock = useRef(false)

  const selection = useMemo<InvestigationSelection>(() => ({
    expectedVersion: reportVersion,
    reportFields: investigationReportFields.filter(field => fields.has(field)),
    notes: notes.filter(note => noteIds.has(note.id)).map(note => ({ id: note.id, expectedVersion: note.version })),
  }), [fields, noteIds, notes, reportVersion])
  const token = requestToken(projectId, reportId, selection)
  const tokenRef = useRef(token)

  useEffect(() => { tokenRef.current = token }, [token])

  useEffect(() => () => {
    activeToken.current = null
    controller.current?.abort()
  }, [])

  useEffect(() => {
    if (activeToken.current && activeToken.current !== token) {
      activeToken.current = null
      controller.current?.abort()
      controller.current = null
      requestLock.current = false
      setPending(false)
    }
  }, [token])

  function clearPreviewForSelection() {
    activeToken.current = null
    controller.current?.abort()
    controller.current = null
    requestLock.current = false
    setPending(false)
    setResult(null)
    setSelectedItems(new Set())
    setDraft('')
    setMessage(null)
    setFailure(false)
  }

  function toggleField(field: InvestigationReportField, checked: boolean) {
    clearPreviewForSelection()
    setFields(current => {
      const next = new Set(current)
      if (checked) next.add(field); else next.delete(field)
      return next
    })
  }

  function toggleNote(id: string, checked: boolean) {
    clearPreviewForSelection()
    setNoteIds(current => {
      const next = new Set(current)
      if (checked) next.add(id); else next.delete(id)
      return next
    })
  }

  function cancel() {
    activeToken.current = null
    controller.current?.abort()
    controller.current = null
    requestLock.current = false
    setPending(false)
    setResult(null)
    setSelectedItems(new Set())
    setFailure(false)
    setMessage('Suggestion request cancelled. A request already sent may still count toward usage.')
  }

  async function generate() {
    if (requestLock.current || selection.reportFields.length === 0) return
    requestLock.current = true
    const generationToken = token
    activeToken.current = generationToken
    const requestController = new AbortController()
    controller.current = requestController
    setPending(true)
    setResult(null)
    setSelectedItems(new Set())
    setMessage(null)
    setFailure(false)
    try {
      const suggestion = await requestInvestigationSuggestions(projectId, reportId, selection, requestController.signal)
      if (requestController.signal.aborted || activeToken.current !== generationToken || tokenRef.current !== generationToken) return
      setResult(suggestion)
    } catch (error) {
      if (requestController.signal.aborted || activeToken.current !== generationToken || tokenRef.current !== generationToken) return
      if (error instanceof DeveloperApiError && error.status === 401) onProtectedFailure(error)
      else if (error instanceof DeveloperApiError && (error.status === 403 || error.status === 404)) onProtectedFailure(error)
      else {
        if (error instanceof DeveloperApiError && (error.code === 'STALE_VERSION' || error.code === 'INVALID_STATE')) onSourceStale()
        setFailure(true)
        setMessage(failureMessage(error))
      }
    } finally {
      if (activeToken.current === generationToken) {
        activeToken.current = null
        controller.current = null
        requestLock.current = false
        setPending(false)
      }
    }
  }

  function toggleItem(key: string, checked: boolean) {
    setSelectedItems(current => {
      const next = new Set(current)
      if (checked) next.add(key); else next.delete(key)
      return next
    })
  }

  async function save() {
    if (!draft.trim() || saving || disabled) return
    setSaving(true)
    try {
      if (await onSaveDraft(draft)) {
        setDraft('')
        setMessage('Private note saved after server confirmation.')
        setFailure(false)
      }
    } finally { setSaving(false) }
  }

  function sourceName(source: string) {
    if ((investigationReportFields as readonly string[]).includes(source)) return reportFieldRules[source as InvestigationReportField].label
    const provenance = result?.sourceNotes.find(note => note.ref === source)
    const noteIndex = provenance ? notes.findIndex(note => note.id === provenance.id) : -1
    return noteIndex >= 0 ? `${source} — private note ${noteIndex + 1}` : source
  }

  const emptyResult = Boolean(result && result.hypotheses.length === 0 && result.nextSteps.length === 0)
  return <section className="developer-ai" aria-labelledby="developer-ai-title">
    <div className="developer-section-heading"><div><h2 id="developer-ai-title">Investigation suggestions</h2><p>Use saved report details and selected private notes to request advisory investigation ideas.</p></div></div>
    <p>The selected text goes to the configured AI provider. Suggestions are advisory and may be inaccurate.</p>
    <Disclosure label="Choose AI context">
      <div className="developer-ai-context">
        <fieldset><legend>Report fields</legend>
          {investigationReportFields.map(field => <Checkbox key={field} label={reportFieldRules[field].label} checked={fields.has(field)}
            onCheckedChange={checked => toggleField(field, checked === true)} />)}
        </fieldset>
        <fieldset><legend>Private notes (up to 5)</legend>
          {notes.length === 0 && <p className="muted">No private notes are available to select.</p>}
          {notes.map((note, index) => <Checkbox key={note.id} label={`Private note ${index + 1}`} checked={noteIds.has(note.id)}
            disabled={!noteIds.has(note.id) && noteIds.size >= 5} onCheckedChange={checked => toggleNote(note.id, checked === true)} />)}
        </fieldset>
      </div>
    </Disclosure>
    <div className="developer-note-actions">
      <Button type="button" color="secondary" variant="outline" disabled={disabled || pending || selection.reportFields.length === 0} onClick={() => void generate()}>Get suggestions</Button>
      {pending && <Button type="button" color="secondary" variant="ghost" onClick={cancel}>Cancel suggestions</Button>}
    </div>
    {pending && <p role="status">Getting investigation suggestions…</p>}
    {message && <p role={failure ? 'alert' : 'status'}>{message}</p>}
    {result && <div className="developer-ai-preview">
      <h3>Suggestion preview</h3>
      {emptyResult && <p>No suggestions were supported by the selected context.</p>}
      {result.hypotheses.length > 0 && <><h4>Hypotheses</h4><ol className="developer-ai-list">{result.hypotheses.map((hypothesis, index) => <li key={index}>
        <Checkbox label={hypothesis.statement} checked={selectedItems.has(`hypothesis:${index}`)} onCheckedChange={checked => toggleItem(`hypothesis:${index}`, checked === true)} />
        <p><strong>Rationale:</strong> {hypothesis.rationale}</p>
        <p className="muted"><strong>Sources:</strong> {hypothesis.sources.map(sourceName).join(', ')}</p>
      </li>)}</ol></>}
      {result.nextSteps.length > 0 && <><h4>Next steps</h4><ul className="developer-ai-list">{result.nextSteps.map((step, index) => <li key={index}>
        <Checkbox label={step} checked={selectedItems.has(`step:${index}`)} onCheckedChange={checked => toggleItem(`step:${index}`, checked === true)} />
      </li>)}</ul></>}
      {!emptyResult && <Button type="button" color="secondary" variant="soft" disabled={selectedItems.size === 0} onClick={() => setDraft(itemDraft(result, selectedItems))}>Add selected to draft</Button>}
      <div className="developer-ai-draft">
        <label htmlFor="developer-ai-note-draft">Editable private-note draft</label>
        <p id="developer-ai-draft-help" className="muted">Nothing is saved until you choose Save private note. The ordinary private-note rules apply.</p>
        <Textarea id="developer-ai-note-draft" aria-describedby="developer-ai-draft-help" rows={7} value={draft} disabled={disabled || saving}
          onChange={event => setDraft(event.target.value)} />
        <div className="developer-composer-foot"><span>{Array.from(draft).length.toLocaleString()} / 8,000</span>
          <Button type="button" color="primary" disabled={disabled || saving || !draft.trim() || Array.from(draft).length > 8_000} loading={saving} onClick={() => void save()}>Save private note</Button>
        </div>
      </div>
    </div>}
  </section>
}
