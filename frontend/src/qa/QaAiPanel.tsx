import { useEffect, useRef, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { CloseBold } from '@openai/apps-sdk-ui/components/Icon'
import { ReportApiError } from '../reports/reportApi'
import { generateQaReview, type QaSuggestion } from './qaAiApi'

export function QaAiPanel({ projectId, reportId, version, onClose, onRefresh, onSessionExpired }: {
  projectId: string
  reportId: string
  version: number
  onClose: () => void
  onRefresh: () => void
  onSessionExpired: () => void
}) {
  const request = useRef<AbortController | null>(null)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<ReportApiError | null>(null)
  const [suggestion, setSuggestion] = useState<QaSuggestion | null>(null)

  useEffect(() => () => request.current?.abort(), [])

  async function generate() {
    if (request.current) return
    const controller = new AbortController()
    request.current = controller
    setBusy(true)
    setFailure(null)
    try {
      const result = await generateQaReview(projectId, reportId, version, controller.signal)
      if (!controller.signal.aborted) setSuggestion(result)
    } catch (error) {
      if (controller.signal.aborted) return
      if (error instanceof ReportApiError && error.status === 401) onSessionExpired()
      else setFailure(error instanceof ReportApiError ? error : new ReportApiError(0))
    } finally {
      if (!controller.signal.aborted) {
        request.current = null
        setBusy(false)
      }
    }
  }

  const stale = failure?.status === 409
  const denied = failure?.status === 404 || (failure?.status === 403 && failure.code !== 'CSRF_INVALID')
  const message = stale ? 'The bug or its resolution changed. Refresh the summary before reviewing again.'
    : denied ? 'You no longer have access to review this bug.'
    : failure?.code === 'AI_CONTEXT_TOO_LARGE' ? 'The saved report and resolution exceed the AI review size limit.'
    : failure?.status === 502 ? 'AI returned an invalid review. No suggestions were shown.'
    : 'AI review is unavailable right now.'

  return <section className="qa-ai-panel" aria-labelledby="qa-ai-title">
    <header>
      <h2 id="qa-ai-title">Review with AI</h2>
      <Button color="secondary" variant="ghost" uniform size="sm" aria-label="Close AI review" onClick={onClose}>
        <CloseBold />
      </Button>
    </header>
    {!suggestion && <p>Generate a verification plan from the saved report and latest Developer resolution.
      This sends their text to the configured AI provider.</p>}
    {failure && <p role="alert">{message}</p>}
    {stale && <Button color="secondary" variant="outline" onClick={onRefresh}>Refresh summary</Button>}
    {busy && <p role="status">Generating QA review…</p>}
    {!suggestion && !busy && !stale && !denied && (
      <Button color="secondary" variant="soft" onClick={() => void generate()}>
        {failure ? 'Try again' : 'Generate review'}
      </Button>
    )}
    {suggestion && <>
      <p>Review these suggestions before using them to guide testing.</p>
      <h3>Suggested regression checks</h3>
      {suggestion.checks.length ? <ol>{suggestion.checks.map((text, index) => <li key={index}>{text}</li>)}</ol>
        : <p>No checks suggested.</p>}
      <h3>Missing verification evidence</h3>
      {suggestion.evidenceGaps.length ? <ul>{suggestion.evidenceGaps.map((text, index) => <li key={index}>{text}</li>)}</ul>
        : <p>No gaps identified.</p>}
    </>}
  </section>
}
