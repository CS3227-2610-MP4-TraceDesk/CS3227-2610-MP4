import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Select } from '@openai/apps-sdk-ui/components/Select'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { fetchAssignment, ReportApiError, saveAssignment, type AssignmentPage, type AssignmentResult } from '../reports/reportApi'

const clearAssignment = 'unassigned'

function AssignmentTrigger({ label }: { label: string }) {
  return <><span className="sr-only">Developer: </span>{label}</>
}

export function QaAssignment({ projectId, reportId, assignedDeveloperId, reportVersion, closed, onSessionExpired, onRefresh }: {
  projectId: string
  reportId: string
  assignedDeveloperId: string | null
  reportVersion: number
  closed: boolean
  onSessionExpired: () => void
  onRefresh: (confirmed?: AssignmentResult) => void
}) {
  const [page, setPage] = useState<AssignmentPage | null>(null)
  const [developer, setDeveloper] = useState('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [denied, setDenied] = useState(false)
  const [refreshRequired, setRefreshRequired] = useState(false)
  const request = useRef<AbortController | null>(null)

  useEffect(() => () => request.current?.abort(), [])

  function startRequest() {
    const controller = new AbortController()
    request.current = controller
    setBusy(true)
    setMessage('')
    return controller
  }

  function fail(error: unknown, saving: boolean) {
    const status = error instanceof ReportApiError ? error.status : 0
    if (status === 401) {
      onSessionExpired()
    } else if (status === 404 || (status === 403 && error instanceof ReportApiError && error.code !== 'CSRF_INVALID')) {
      setDenied(true)
      setPage(null)
      setMessage('Developer assignment is no longer available to your account.')
      onRefresh()
    } else if (status === 409 || status === 412 || (error instanceof ReportApiError && error.code === 'INVALID_TARGET')) {
      setRefreshRequired(true)
      setMessage(error instanceof ReportApiError && error.code === 'INVALID_TARGET'
        ? 'That developer is no longer eligible. Refresh the assignment before trying again.'
        : 'This assignment changed. Refresh it before trying again.')
      onRefresh()
    } else if (error instanceof ReportApiError && error.code === 'VALIDATION_ERROR') {
      setMessage('Check the assignment reason and try again.')
    } else {
      setMessage(saving ? 'Could not save the assignment. Try again.' : 'Could not load assignment details. Try again.')
    }
  }

  async function load(cursor: string | null = null) {
    if (request.current) return
    const controller = startRequest()
    try {
      const next = await fetchAssignment(projectId, reportId, cursor, controller.signal)
      if (controller.signal.aborted) return
      if (cursor && page && (page.version !== next.version || page.assignmentEligible !== next.assignmentEligible ||
        page.assignedDeveloperId !== next.assignedDeveloperId)) {
        setRefreshRequired(true)
        setMessage('This assignment changed. Refresh it before trying again.')
        onRefresh()
        return
      }
      const items = cursor && page ? [...page.items, ...next.items.filter(item => !page.items.some(existing => existing.id === item.id))] : next.items
      setPage({ ...next, items, nextCursor: next.nextCursor === cursor ? null : next.nextCursor })
      setRefreshRequired(false)
      if (next.version !== reportVersion) onRefresh()
    } catch (error) {
      if (!controller.signal.aborted) fail(error, false)
    } finally {
      if (!controller.signal.aborted) {
        request.current = null
        setBusy(false)
      }
    }
  }

  const currentDeveloper = page ? page.assignedDeveloperId : assignedDeveloperId
  const choices = page?.items.filter(item => item.id !== currentDeveloper) ?? []
  const selectedAvailable = developer === clearAssignment ? currentDeveloper !== null : choices.some(item => item.id === developer)
  const needsReason = currentDeveloper !== null
  const trimmedReason = reason.trim()
  const reasonTooLong = Array.from(reason).length > 500 || new TextEncoder().encode(reason).length > 2000
  const needsRefresh = refreshRequired || Boolean(page && page.version < reportVersion)
  const canSave = !closed && !busy && !needsRefresh && selectedAvailable &&
    (!needsReason || (trimmedReason && !reasonTooLong))

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!page || !canSave || request.current) return
    const controller = startRequest()
    try {
      const confirmed = await saveAssignment(projectId, reportId, {
        expectedVersion: page.version,
        ...(developer === clearAssignment ? {} : { developerId: developer }),
        ...(needsReason ? { reason } : {}),
      }, controller.signal)
      if (controller.signal.aborted) return
      setPage(null)
      setDeveloper('')
      setReason('')
      setMessage('Developer assignment updated.')
      onRefresh(confirmed)
    } catch (error) {
      if (!controller.signal.aborted) fail(error, true)
    } finally {
      if (!controller.signal.aborted) {
        request.current = null
        setBusy(false)
      }
    }
  }

  return <section className="qa-summary-fix qa-developer-assignment" aria-labelledby="qa-assignment-heading">
    <h2 id="qa-assignment-heading">Developer assignment</h2>
    <p>Current developer: {currentDeveloper ?? 'Unassigned'}{currentDeveloper && page && !page.assignmentEligible ? ' (no longer eligible)' : ''}</p>
    {message && <p role={message === 'Developer assignment updated.' ? 'status' : 'alert'}>{message}</p>}
    {busy && <p role="status">{page ? 'Updating assignment details…' : 'Loading assignment details…'}</p>}
    {!closed && !denied && (!page || needsRefresh) && <Button type="button" color="secondary" variant="outline" size="sm"
      disabled={busy} onClick={() => void load()}>{needsRefresh ? 'Refresh assignment' : 'Manage assignment'}</Button>}
    {!closed && page && <form className="qa-assignment-form" onSubmit={submit}>
      <label htmlFor="qa-assignment-developer">Developer</label>
      <Select id="qa-assignment-developer" value={developer} disabled={busy || needsRefresh}
        TriggerView={AssignmentTrigger} placeholder="Choose a developer" size="sm" listMaxWidth={360} onChange={item => setDeveloper(item.value)}
        options={[
          ...choices.map(item => ({ value: item.id, label: item.id })),
          ...(currentDeveloper ? [{ value: clearAssignment, label: 'Clear assignment' }] : []),
        ]} />
      {!choices.length && !page.nextCursor && <p className="muted">No other eligible developers are available.</p>}
      {page.nextCursor && <Button type="button" color="secondary" variant="outline" size="sm" disabled={busy || needsRefresh}
        onClick={() => void load(page.nextCursor)}>Load more developers</Button>}
      {needsReason && <div>
        <label htmlFor="qa-assignment-reason">Reason</label>
        <Textarea id="qa-assignment-reason" value={reason} required rows={2} disabled={busy || needsRefresh} invalid={reasonTooLong}
          onChange={event => setReason(event.target.value)} />
        {reasonTooLong && <p role="alert">Use at most 500 characters for the assignment reason.</p>}
      </div>}
      <Button type="submit" color="primary" size="sm" disabled={!canSave}>
        Save assignment
      </Button>
    </form>}
  </section>
}
