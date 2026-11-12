import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { Select } from '@openai/apps-sdk-ui/components/Select'
import { SegmentedControl } from '@openai/apps-sdk-ui/components/SegmentedControl'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { VerificationBoard, VerificationCheckInspector } from '../preview/qa/QaVerificationBoard'
import type { TestTemplate } from '../preview/qa/qaTemplateFixtures'
import { applyQaTemplate } from './applyQaTemplate'
import { ReportApiError } from '../reports/reportApi'
import { requestVerification, type Decision, type Verification } from './verificationApi'
import '../preview/qa/qa-review-workspace.css'

type Props = {
  projectId: string
  reportId: string
  reportVersion: number
  templates: TestTemplate[]
  review: Verification | null
  setReview: (review: Verification | null) => void
  busy: boolean
  setBusy: (busy: boolean) => void
  onSessionExpired: () => void
  onSummary: () => void
  onSaved: (review: Verification) => void
}

export function QaVerification({ projectId, reportId, reportVersion, review, setReview, templates, busy, setBusy,
  onSessionExpired, onSummary, onSaved }: Props) {
  const [saved, setSaved] = useState<Verification | null>(null)
  const [layout, setLayout] = useState<'Board' | 'List'>('Board')
  const [selected, setSelected] = useState<number | null>(null)
  const [title, setTitle] = useState('')
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [templateId, setTemplateId] = useState('')
  const request = useRef<AbortController | null>(null)

  function loadOrSave(decision?: Decision) {
    if (request.current) return
    setBusy(true)
    setError('')
    setNotice('')
    void runRequest(decision)
  }

  async function runRequest(decision?: Decision) {
    const controller = new AbortController()
    request.current = controller
    try {
      const next = await requestVerification(projectId, reportId, controller.signal, decision && review ? {
        expectedVersion: review.version, checks: review.checks, notes: review.notes,
        decision, reason: decision === 'SEND_BACK' ? reason.trim() : '',
      } : undefined)
      if (controller.signal.aborted) return
      setReview(next)
      setSaved(next)
      if (!decision) {
        setSelected(null)
        setTitle('')
        setReason('')
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
        setReview(null)
        setSaved(null)
        setError('Verification is no longer available to your account.')
      } else {
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

  const loadInitialReview = useEffectEvent(() => void runRequest())
  useEffect(() => {
    loadInitialReview()
    return () => {
      request.current?.abort()
      request.current = null
    }
  }, [])

  const stale = Boolean(review && review.version < reportVersion)
  const editable = review?.status === 'READY_FOR_QA' && !busy && !stale
  const names = review?.checks.map(check => check.title.trim().toLowerCase()) ?? []
  const valid = names.every(Boolean) && new Set(names).size === names.length &&
    (review?.checks.every(check => Array.from(check.title).length <= 300 &&
      Array.from(check.notes ?? '').length <= 250) ?? false) &&
    Array.from(review?.notes ?? '').length <= 2000
  const canAdd = Boolean(editable && title.trim() && names.length < 30 && !names.includes(title.trim().toLowerCase()))
  const canClose = Boolean(editable && valid && names.length && review?.checks.every(check => check.result === 'Pass'))

  const template = templates.find(item => item.id === templateId)
  function applyTemplate() {
    if (!review || !editable || !template) return
    const result = applyQaTemplate(review, template)
    if ('error' in result) {
      setNotice(result.error)
      return
    }
    setReview(result.draft)
    setTemplateId('')
    setNotice('Added ' + result.added + ' pending ' + (result.added === 1 ? 'check' : 'checks') + ' to the draft. Save Verification to keep them.')
  }

  return <section className="qa-verification-content" aria-label="Verification checklist">
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
        setReview({ ...review, checks: [...review.checks, { title: title.trim(), result: 'Pending' }] })
        setTitle('')
      }}>
        <Input aria-label="New check" placeholder="Describe a verification check" value={title} maxLength={300}
          onChange={event => setTitle(event.target.value)} />
        <Button type="submit" color="secondary" variant="soft" disabled={!canAdd}>Add check</Button>
      </form>}
      {editable && templates.length > 0 && <div className="qa-add-check">
        <Select aria-label="Test template" placeholder="Choose template" value={templateId}
          options={templates.map(item => ({ value: item.id, label: item.name }))}
          onChange={item => setTemplateId(item.value)} />
        <Button color="secondary" variant="soft" disabled={!template} onClick={applyTemplate}>Apply template</Button>
      </div>}
      {layout === 'Board' ? <VerificationBoard checks={review.checks} selectedIndex={selected} onSelect={setSelected} />
        : <ul className="qa-checklist">{review.checks.map((check, index) => <li key={index}>
          <Button block color="secondary" variant="ghost" aria-pressed={selected === index}
            onClick={() => setSelected(index)}>{check.title || 'Untitled check'} · {check.result}</Button>
        </li>)}</ul>}
      <VerificationCheckInspector checks={review.checks} selectedIndex={selected} editable={editable}
        onChecksChange={checks => setReview({ ...review, checks })} onViewThread={onSummary}
        onDeselect={() => setSelected(null)} />
      <div className="qa-notes">
        <label htmlFor="qa-saved-notes">Verification notes</label>
        <Textarea id="qa-saved-notes" value={review.notes} rows={3} maxLength={2000} readOnly={!editable}
          onChange={event => setReview({ ...review, notes: event.target.value })} />
      </div>
      {review.status === 'READY_FOR_QA' && <>
        {!valid && <p role="status">Use unique check titles and keep each check note within 250 characters.</p>}
        <div className="qa-decision">
          <Button color="secondary" variant="ghost" disabled={busy} onClick={() => void loadOrSave()}>
            {review === saved ? 'Reload verification' : 'Discard edits and reload'}
          </Button>
          <Button color="secondary" variant="soft" disabled={!editable || !valid || review === saved}
            onClick={() => void loadOrSave('SAVE')}>Save verification</Button>
          <Button color="primary" disabled={!canClose} onClick={() => void loadOrSave('CLOSE')}>Verify and close</Button>
        </div>
        <div className="qa-reopen">
          <label htmlFor="qa-send-back-reason">Reason for sending back</label>
          <Textarea id="qa-send-back-reason" value={reason} rows={2} maxLength={2000} disabled={!editable}
            onChange={event => setReason(event.target.value)} />
          <Button color="danger" variant="soft" disabled={!editable || !valid || !reason.trim()}
            onClick={() => void loadOrSave('SEND_BACK')}>Send back to Developer</Button>
        </div>
      </>}
    </>}
  </section>
}
