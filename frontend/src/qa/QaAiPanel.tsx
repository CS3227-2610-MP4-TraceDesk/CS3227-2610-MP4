import { useEffect, useRef, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Checkbox } from '@openai/apps-sdk-ui/components/Checkbox'
import { CloseBold } from '@openai/apps-sdk-ui/components/Icon'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { ReportApiError } from '../reports/reportApi'
import { generateQaReview, type QaExchange, type QaSuggestion } from './qaAiApi'

type AddChecks = (checks: string[]) => string

function SuggestionChecks({ checks, onAdd, disabled }: { checks: string[]; onAdd: AddChecks; disabled: boolean }) {
  const [selected, setSelected] = useState<number[]>([])
  const [notice, setNotice] = useState('')

  return <>
    <ul className="qa-ai-checks">{checks.map((check, index) => <li key={index}>
      <Checkbox label={check} checked={selected.includes(index)} disabled={disabled}
        onCheckedChange={checked => {
          setNotice('')
          setSelected(current => checked === true ? [...current, index] : current.filter(item => item !== index))
        }} />
    </li>)}</ul>
    <Button color="secondary" variant="soft" disabled={disabled || !selected.length}
      onClick={() => setNotice(onAdd(checks.filter((_, index) => selected.includes(index))))}>
      Add selected to Verification
    </Button>
    {notice && <p role="status">{notice}</p>}
  </>
}

export function QaAiPanel({ projectId, reportId, version, onClose, onRefresh, onSessionExpired, onAddChecks }: {
  projectId: string
  reportId: string
  version: number
  onClose: () => void
  onRefresh: () => void
  onSessionExpired: () => void
  onAddChecks: AddChecks
}) {
  const request = useRef<AbortController | null>(null)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<ReportApiError | null>(null)
  const [suggestion, setSuggestion] = useState<QaSuggestion | null>(null)
  const [question, setQuestion] = useState('')
  const [turns, setTurns] = useState<(QaExchange & QaSuggestion)[]>([])

  useEffect(() => () => request.current?.abort(), [])

  async function generate(followUpQuestion?: string) {
    if (request.current) return
    const controller = new AbortController()
    request.current = controller
    setBusy(true)
    setFailure(null)
    try {
      const followUp = followUpQuestion && suggestion ? {
        question: followUpQuestion,
        plan: suggestion,
        history: turns.slice(-2).map(({ question, answer }) => ({ question, answer })),
      } : undefined
      const result = await generateQaReview(projectId, reportId, version, controller.signal, followUp)
      if (controller.signal.aborted) return
      if (followUp && result.answer) {
        const { answer, checks, evidenceGaps } = result
        setTurns(current => [...current, { question: followUp.question, answer, checks, evidenceGaps }])
        setQuestion('')
      } else setSuggestion({ checks: result.checks, evidenceGaps: result.evidenceGaps })
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
    : failure?.code === 'AI_CONTEXT_TOO_LARGE' ? 'This report and conversation exceed the AI review size limit.'
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
    {busy && !suggestion && <p role="status">Generating QA review…</p>}
    {!suggestion && !busy && !stale && !denied && (
      <Button color="secondary" variant="soft" onClick={() => void generate()}>
        {failure ? 'Try again' : 'Generate review'}
      </Button>
    )}
    {suggestion && <>
      <p>Review these suggestions before using them to guide testing.</p>
      <h3>Suggested regression checks</h3>
      {suggestion.checks.length ? <SuggestionChecks checks={suggestion.checks} onAdd={onAddChecks} disabled={stale || denied} />
        : <p>No checks suggested.</p>}
      <h3>Missing verification evidence</h3>
      {suggestion.evidenceGaps.length ? <ul>{suggestion.evidenceGaps.map((text, index) => <li key={index}>{text}</li>)}</ul>
        : <p>No gaps identified.</p>}
      <section className="qa-ai-chat" aria-labelledby="qa-ai-chat-title">
        <h3 id="qa-ai-chat-title">Follow-up chat</h3>
        {turns.map((turn, index) => <article className="qa-ai-turn" aria-label={`Follow-up ${index + 1}`} key={index}>
          <p><strong>You</strong></p>
          <p>{turn.question}</p>
          <p><strong>AI</strong></p>
          <p>{turn.answer}</p>
          {turn.checks.length > 0 && <SuggestionChecks checks={turn.checks} onAdd={onAddChecks} disabled={stale || denied} />}
          {turn.evidenceGaps.length > 0 && <>
            <h4>Evidence gaps</h4>
            <ul>{turn.evidenceGaps.map((gap, index) => <li key={index}>{gap}</li>)}</ul>
          </>}
        </article>)}
        {busy && <p role="status">Getting a reply…</p>}
        <form onSubmit={event => {
          event.preventDefault()
          if (question.trim() && !busy && !stale && !denied) void generate(question.trim())
        }}>
          <label htmlFor="qa-follow-up-question">Ask a follow-up</label>
          <p id="qa-follow-up-help" className="muted">Your question, the displayed plan and recent replies go to the AI provider.</p>
          <Textarea id="qa-follow-up-question" aria-describedby="qa-follow-up-help" value={question}
            rows={3} maxLength={500} readOnly={busy} disabled={stale || denied}
            onChange={event => setQuestion(event.target.value)} />
          <Button type="submit" color="secondary" variant="soft" disabled={!question.trim() || busy || stale || denied}>
            Send question
          </Button>
        </form>
      </section>
    </>}
  </section>
}
