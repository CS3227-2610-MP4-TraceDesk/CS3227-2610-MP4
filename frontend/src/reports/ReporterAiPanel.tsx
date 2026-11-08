import { useEffect, useRef, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Checkbox } from '@openai/apps-sdk-ui/components/Checkbox'
import { Disclosure } from '../shared/Disclosure'
import { ReportApiError } from './reportApi'
import { generateReportSuggestion, type AiField, type AiSource, type ReportSuggestion } from './reporterAiApi'
import { reportFieldRules, type ReportValues } from './reportValidation'
import './reporter-ai.css'

function failureMessage(error: unknown): string {
  if (error instanceof ReportApiError) {
    if (error.code === 'AI_CONTEXT_TOO_LARGE') return 'The saved report is too long for AI review. Shorten its report fields and save before trying again.'
    if (error.status === 409) return 'This saved source changed or is no longer editable. Review the current version before requesting another suggestion.'
    if (error.status === 410) return 'This draft has expired. AI review is unavailable.'
    if (error.status === 502) return 'AI returned an invalid suggestion. No changes were applied.'
    if (error.code === 'CSRF_INVALID') return 'The security token expired. No suggestion was confirmed. Choose Generate suggestions to try a new request.'
  }
  return 'AI review is unavailable. You can continue editing and saving normally. No changes were applied.'
}

export function ReporterAiPanel({ projectId, source, values, onApply, onProtectedFailure }: {
  projectId: string; source: AiSource; values: ReportValues
  onApply: (values: ReportValues, version: number) => Promise<void>
  onProtectedFailure: (error: unknown) => unknown
}) {
  const [pending, setPending] = useState(false)
  const [result, setResult] = useState<ReportSuggestion | null>(null)
  const [selected, setSelected] = useState<Set<AiField>>(new Set())
  const [message, setMessage] = useState<string | null>(null)
  const generation = useRef(0)
  const controller = useRef<AbortController | null>(null)
  const requestLock = useRef(false)
  useEffect(() => () => { generation.current += 1; controller.current?.abort() }, [])

  function cancel() {
    generation.current += 1
    controller.current?.abort()
    controller.current = null
    requestLock.current = false
    setPending(false)
    setResult(null)
    setSelected(new Set())
    setMessage('AI review cancelled. Any usage already dispatched may still be accounted for.')
  }

  async function generate() {
    if (requestLock.current) return
    requestLock.current = true
    const current = ++generation.current
    const requestController = new AbortController()
    controller.current = requestController
    setPending(true); setResult(null); setSelected(new Set()); setMessage(null)
    try {
      const suggestion = await generateReportSuggestion(projectId, source, AbortSignal.any([requestController.signal, AbortSignal.timeout(65_000)]))
      if (current !== generation.current || requestController.signal.aborted) return
      setResult(suggestion)
    } catch (error) {
      if (current !== generation.current || requestController.signal.aborted) return
      if (error instanceof ReportApiError && (error.status === 401 || (error.status === 403 && error.code !== 'CSRF_INVALID') || error.status === 404)) onProtectedFailure(error)
      else setMessage(failureMessage(error))
    } finally {
      if (current === generation.current) { setPending(false); requestLock.current = false }
    }
  }

  async function apply() {
    if (!result || selected.size === 0 || requestLock.current) return
    requestLock.current = true
    const next = { ...values }
    for (const change of result.changes) if (selected.has(change.field)) next[change.field] = change.replacement
    setResult(null); setSelected(new Set())
    try { await onApply(next, result.sourceVersion) } finally { requestLock.current = false }
  }

  return <section className="report-ai" aria-label="AI report review">
    <Disclosure label="Review with AI" onToggle={event => { if (!event.currentTarget.open) cancel() }}>
    <div className="report-ai-content">
      <h2>Review your saved report</h2>
      <p>AI can suggest clearer wording and questions about missing details. Title, description, environment, steps to reproduce, expected behavior and actual behavior will be sent to the configured AI provider. Logs are excluded.</p>
      <p className="muted">Suggestions may be inaccurate. Review each change before applying it. AI does not submit reports or change their status.</p>
      <div className="report-form-actions">
        <Button type="button" color="secondary" variant="outline" disabled={pending} onClick={() => void generate()}>Generate suggestions</Button>
        {pending && <Button type="button" color="secondary" variant="ghost" onClick={cancel}>Cancel AI review</Button>}
      </div>
      {pending && <p role="status">Reviewing the saved version…</p>}
      {message && <p role="status">{message}</p>}
      {result && <div className="report-ai-preview">
        <h3>Suggested changes</h3>
        <p className="muted">Select the fields to save to version {result.sourceVersion}. Unselected fields and logs stay exactly as saved.</p>
        {result.changes.length === 0 && <p>No wording changes were suggested.</p>}
        {result.changes.map(change => <div className="report-ai-change" key={change.field}>
          <Checkbox label={`Apply ${reportFieldRules[change.field].label}`} checked={selected.has(change.field)} onCheckedChange={checked => setSelected(previous => { const next = new Set(previous); if (checked === true) next.add(change.field); else next.delete(change.field); return next })} />
          <p className="report-ai-text">{change.replacement}</p>
        </div>)}
        {result.missingInformation.length > 0 && <><h3>Details to consider</h3><ul>{result.missingInformation.map((question, index) => <li className="report-ai-text" key={index}>{question}</li>)}</ul></>}
        <Button type="button" color="primary" disabled={selected.size === 0} onClick={() => void apply()}>Apply selected changes</Button>
      </div>}
    </div>
    </Disclosure>
  </section>
}
