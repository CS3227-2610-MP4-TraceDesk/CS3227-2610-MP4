import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { SegmentedControl } from '@openai/apps-sdk-ui/components/SegmentedControl'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { VerificationBoard, VerificationCheckInspector } from '../preview/qa/QaVerificationBoard'
import { ReportApiError } from '../reports/reportApi'
import { requestVerification, type Decision, type Verification } from './verificationApi'
import { hasQaVerificationEdits, type QaReviewDraft } from './qaReviewDraft'
import '../preview/qa/qa-review-workspace.css'

type Props = {
  projectId: string
  reportId: string
  reportVersion: number
  draft: QaReviewDraft
  onDraftChange: (patch: Partial<QaReviewDraft>) => void
  onStaleChange: (stale: boolean) => void
  onAccessDenied: () => void
  busy: boolean
  setBusy: (busy: boolean) => void
  onSessionExpired: () => void
  onSummary: () => void
  onSaved: (review: Verification) => void
}

export function QaVerification({ projectId, reportId, reportVersion, draft, onDraftChange, busy, setBusy,
  onSessionExpired, onSummary, onSaved, onAccessDenied, onStaleChange }: Props) {
  const { review, saved, title, reason } = draft
  const [observedVersion, setObservedVersion] = useState(0)
  const [reloadRequired, setReloadRequired] = useState(false)
  const [layout, setLayout] = useState<'Board' | 'List'>('Board')
  const [selected, setSelected] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const request = useRef<AbortController | null>(null)

  function loadOrSave(decision?: Decision) {
    if (request.current) return
    setBusy(true)
    setError('')
    setNotice('')
    void runRequest(decision)
  }

  async function runRequest(decision?: Decision, restoreDraft = false) {
    const controller = new AbortController()
    request.current = controller
    try {
      const next = await requestVerification(projectId, reportId, controller.signal, decision && review ? {
        expectedVersion: review.version, checks: review.checks, notes: review.notes,
        decision, reason: decision === 'SEND_BACK' ? reason.trim() : '',
      } : undefined)
      if (controller.signal.aborted) return
      setObservedVersion(next.version)
      setReloadRequired(false)
      if (!restoreDraft || !hasQaVerificationEdits(draft)) {
        onDraftChange({ review: next, saved: next, ...(!decision && { title: '', reason: '' }) })
        if (!decision) setSelected(null)
      }
      if (decision) {
        setNotice(decision === 'SAVE' ? 'Verification saved.' : decision === 'CLOSE' ? 'Bug verified and closed.' : 'Bug sent back to the Developer.')
        onSaved(next)
      }
    } catch (failure) {
      if (controller.signal.aborted) return
      const problem = failure instanceof ReportApiError ? failure : null
      if (problem?.status === 401) onSessionExpired()
      else if (problem?.status === 404 || (problem?.status === 403 && problem.code !== 'CSRF_INVALID')) {
        onAccessDenied()
        setError('Verification is no longer available to your account.')
      } else {
        if (restoreDraft || problem?.code === 'STALE_VERSION' || problem?.code === 'INVALID_STATE') setReloadRequired(true)
        setError(problem?.code === 'STALE_VERSION' || problem?.code === 'INVALID_STATE'
          ? 'The report changed. Reload verification before saving again.'
          : 'Verification could not be loaded or saved. Check your connection and input.')
      }
    } finally {
      if (!controller.signal.aborted) {
        request.current = null
        setBusy(false)
      }
    }
  }

  const loadInitialReview = useEffectEvent(() => { setBusy(true); void runRequest(undefined, true) })
  useEffect(() => {
    loadInitialReview()
    return () => {
      request.current?.abort()
      request.current = null
    }
  }, [])

  const stale = reloadRequired || Boolean(review && review.version < Math.max(reportVersion, observedVersion))
  useEffect(() => { onStaleChange(stale) }, [stale, onStaleChange])
  const editable = review?.status === 'READY_FOR_QA' && !busy && !stale
  const names = review?.checks.map(check => check.title.trim().toLowerCase()) ?? []
  const valid = names.every(Boolean) && new Set(names).size === names.length &&
    (review?.checks.every(check => Array.from(check.title).length <= 300 &&
      Array.from(check.notes ?? '').length <= 250) ?? false) &&
    Array.from(review?.notes ?? '').length <= 2000
  const canAdd = Boolean(editable && title.trim() && names.length < 30 && !names.includes(title.trim().toLowerCase()))
  const canClose = Boolean(editable && valid && names.length && review?.checks.every(check => check.result === 'Pass'))

  return <section id="qa-verification-section" tabIndex={-1} className="qa-verification-content" aria-label="Verification checklist">
    <div className="qa-review-heading">
      <h2>Verification</h2>
      {review && <SegmentedControl aria-label="Verification view" size="sm" value={layout} onChange={setLayout}>
        <SegmentedControl.Option value="Board">Board</SegmentedControl.Option>
        <SegmentedControl.Option value="List">List</SegmentedControl.Option>
      </SegmentedControl>}
    </div>
    {error && <p role="alert">{error}</p>}
    {stale && !error && <p role="status">The report changed. Reload verification before saving again.</p>}
    {notice && <p role="status">{notice}</p>}
    {busy && <p role="status">Working…</p>}
    {!review && !busy && <Button color="secondary" variant="outline" onClick={() => void loadOrSave()}>
      {error ? 'Retry' : 'Load verification'}
    </Button>}
    {review && <>
      <p className="qa-instruction muted">Resolution revision {review.revision} · {review.checks.filter(check => check.result !== 'Pending').length}/{names.length} checked</p>
      {review.reason && <p className="qa-instruction">Send-back reason: {review.reason}</p>}
      {editable && <form className="qa-add-check" onSubmit={event => {
        event.preventDefault()
        if (!canAdd) return
        onDraftChange({ review: { ...review, checks: [...review.checks, { title: title.trim(), result: 'Pending' }] }, title: '' })
      }}>
        <Input aria-label="New check" placeholder="Describe a verification check" value={title} maxLength={300}
          onChange={event => onDraftChange({ title: event.target.value })} />
        <Button type="submit" color="secondary" variant="soft" disabled={!canAdd}>Add check</Button>
      </form>}
      {layout === 'Board' ? <VerificationBoard checks={review.checks} selectedIndex={selected} onSelect={setSelected} />
        : <ul className="qa-checklist">{review.checks.map((check, index) => <li key={index}>
          <Button block color="secondary" variant="ghost" aria-pressed={selected === index}
            onClick={() => setSelected(index)}>{check.title || 'Untitled check'} · {check.result}</Button>
        </li>)}</ul>}
      <VerificationCheckInspector checks={review.checks} selectedIndex={selected} editable={editable}
        onChecksChange={checks => onDraftChange({ review: { ...review, checks } })} onViewThread={onSummary}
        onDeselect={() => setSelected(null)} />
      <div className="qa-notes">
        <label htmlFor="qa-saved-notes">Verification notes</label>
        <Textarea id="qa-saved-notes" value={review.notes} rows={3} maxLength={2000} readOnly={!editable}
          onChange={event => onDraftChange({ review: { ...review, notes: event.target.value } })} />
      </div>
      {review.status === 'READY_FOR_QA' && <>
        {!valid && <p role="status">Use unique check titles and keep each check note within 250 characters.</p>}
        <div className="qa-decision">
          <Button color="secondary" variant="ghost" disabled={busy} onClick={() => void loadOrSave()}>
            {hasQaVerificationEdits(draft) ? 'Discard edits and reload' : 'Reload verification'}
          </Button>
          <Button color="secondary" variant="soft" disabled={!editable || !valid || review === saved}
            onClick={() => void loadOrSave('SAVE')}>Save verification</Button>
          <Button color="primary" disabled={!canClose} onClick={() => void loadOrSave('CLOSE')}>Verify and close</Button>
        </div>
        <div className="qa-reopen">
          <label htmlFor="qa-send-back-reason">Reason for sending back</label>
          <Textarea id="qa-send-back-reason" value={reason} rows={2} maxLength={2000} disabled={!editable}
            onChange={event => onDraftChange({ reason: event.target.value })} />
          <Button color="danger" variant="soft" disabled={!editable || !valid || !reason.trim()}
            onClick={() => void loadOrSave('SEND_BACK')}>Send back to Developer</Button>
        </div>
      </>}
    </>}
  </section>
}
