import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Badge } from '@openai/apps-sdk-ui/components/Badge'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Bug, FileDocument, Folder, Plus } from '@openai/apps-sdk-ui/components/Icon'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { AppShell } from '../shell/AppShell'
import { SidebarGroup, SidebarItem } from '../shell/Sidebar'
import type { TrayItem } from '../shell/IconTray'
import { createReport, fetchProjectContext, fetchReport, fetchReports, isCanonicalUuid, ReportApiError, type ProjectContext, type ReportDetail, type ReportStatus, type ReportSummary } from './reportApi'
import { emptyReport, reportFieldRules, reportFields, validateReport, type FieldErrors, type ReportField, type ReportValues } from './reportValidation'
import './reporter-workspace.css'

type WorkspaceArea = 'reports' | 'environments' | 'settings'
type ReportView = 'list' | 'new' | 'detail'
type SubmissionState = 'editing' | 'pending' | 'unconfirmed' | 'conflict'
type Attempt = { body: string; key: string; values: ReportValues }

const statuses: { value: ReportStatus | ''; label: string }[] = [
  { value: '', label: 'All statuses' },
  { value: 'OPEN', label: 'Open' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'READY_FOR_QA', label: 'Ready for QA' },
  { value: 'CLOSED', label: 'Closed' },
]

const trayItems: readonly TrayItem[] = [
  { id: 'reports', label: 'Reports', icon: <Bug /> },
  { id: 'environments', label: 'Environments', icon: <Folder /> },
]

function initialProjectId(): string {
  return new URLSearchParams(window.location.search).get('project') ?? ''
}

function statusLabel(status: ReportStatus): string {
  return statuses.find(option => option.value === status)?.label ?? status
}

function statusColor(status: ReportStatus): 'info' | 'warning' | 'success' | 'secondary' {
  if (status === 'OPEN') return 'info'
  if (status === 'IN_PROGRESS') return 'warning'
  if (status === 'READY_FOR_QA') return 'success'
  return 'secondary'
}

function errorMessage(error: unknown): string {
  if (error instanceof ReportApiError && error.status === 401) return 'Your session has expired. Sign in again to continue.'
  if (error instanceof ReportApiError && (error.status === 403 || error.status === 404)) return 'This project or report is unavailable to your account.'
  return 'TraceDesk is temporarily unavailable. Your current information is still here. Try again.'
}

export function ReporterWorkspace({
  theme,
  onThemeChange,
  onSessionExpired,
  onSignOut,
  signingOut,
  signOutError,
}: {
  theme: 'light' | 'dark'
  onThemeChange: (theme: 'light' | 'dark') => void
  onSessionExpired: () => void
  onSignOut: () => void
  signingOut: boolean
  signOutError: string | null
}) {
  const routeProject = initialProjectId()
  const [area, setArea] = useState<WorkspaceArea>('reports')
  const [projectId, setProjectId] = useState(() => isCanonicalUuid(routeProject) ? routeProject : '')
  const [projectInput, setProjectInput] = useState(routeProject)
  const [projectError, setProjectError] = useState<string | null>(() => routeProject && !isCanonicalUuid(routeProject) ? 'Enter a project ID in canonical UUID format.' : null)
  const [context, setContext] = useState<ProjectContext | null>(null)
  const [contextLoading, setContextLoading] = useState(() => isCanonicalUuid(routeProject))
  const [contextUnavailable, setContextUnavailable] = useState(false)
  const [contextDenied, setContextDenied] = useState(false)
  const [showProjectForm, setShowProjectForm] = useState(!isCanonicalUuid(routeProject))
  const [statusFilter, setStatusFilter] = useState<ReportStatus | ''>('')
  const [reports, setReports] = useState<ReportSummary[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [listLoading, setListLoading] = useState(false)
  const [listUnavailable, setListUnavailable] = useState(false)
  const [listDenied, setListDenied] = useState(false)
  const [listError, setListError] = useState<string | null>(null)
  const [listHasLoaded, setListHasLoaded] = useState(false)
  const [view, setView] = useState<ReportView>('list')
  const [selectedReportId, setSelectedReportId] = useState<string | null>(null)
  const [detail, setDetail] = useState<ReportDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [values, setValues] = useState<ReportValues>(emptyReport)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [validationSummary, setValidationSummary] = useState<string | null>(null)
  const [submissionState, setSubmissionState] = useState<SubmissionState>('editing')
  const [submissionMessage, setSubmissionMessage] = useState<string | null>(null)
  const [attempt, setAttempt] = useState<Attempt | null>(null)
  const [submitBusy, setSubmitBusy] = useState(false)
  const [listNeedsRefresh, setListNeedsRefresh] = useState(false)
  const projectEpoch = useRef(0)
  const listEpoch = useRef(0)
  const detailEpoch = useRef(0)
  const activeProjectController = useRef<AbortController | null>(null)
  const activeListController = useRef<AbortController | null>(null)
  const activeDetailController = useRef<AbortController | null>(null)
  const activeCreateController = useRef<AbortController | null>(null)
  const submitLock = useRef(false)
  const submittedDetailId = useRef<string | null>(null)
  const [contextRetry, setContextRetry] = useState(0)
  const projectInputRef = useRef<HTMLInputElement>(null)

  const composerDirty = useMemo(() => reportFields.some(field => values[field].length > 0), [values])
  const composerFrozen = submissionState === 'unconfirmed' || submissionState === 'conflict'

  const clearProtectedProject = useCallback(() => {
    projectEpoch.current += 1
    listEpoch.current += 1
    detailEpoch.current += 1
    activeProjectController.current?.abort()
    activeListController.current?.abort()
    activeDetailController.current?.abort()
    activeCreateController.current?.abort()
    activeCreateController.current = null
    submitLock.current = false
    activeProjectController.current = null
    activeListController.current = null
    activeDetailController.current = null
    setContext(null)
    setContextLoading(false)
    setContextUnavailable(false)
    setContextDenied(false)
    setReports([])
    setStatusFilter('')
    setNextCursor(null)
    setListHasLoaded(false)
    setListLoading(false)
    setListError(null)
    setListUnavailable(false)
    setListDenied(false)
    setDetail(null)
    setDetailLoading(false)
    setDetailError(null)
    setSelectedReportId(null)
    setView('list')
    setListNeedsRefresh(false)
    setValues(emptyReport)
    setFieldErrors({})
    setValidationSummary(null)
    setSubmissionMessage(null)
    setSubmissionState('editing')
    setAttempt(null)
    setSubmitBusy(false)
    submitLock.current = false
  }, [])

  const leaveComposer = useCallback((action: string): boolean => {
    if (view !== 'new' || (!composerDirty && submissionState !== 'unconfirmed' && submissionState !== 'conflict' && submissionState !== 'pending')) return true
    if (submissionState === 'pending') {
      setSubmissionMessage('The report is still being submitted. Wait for the save outcome before changing projects or reports.')
      return false
    }
    const message = submissionState === 'unconfirmed'
      ? `This report's save outcome is unconfirmed. ${action} will discard the local retry. It will not cancel a report that may already have been saved. Retry this same submission first to avoid creating a second report. Continue leaving?`
      : submissionState === 'conflict'
        ? `The server reported a submission conflict. ${action} will discard this frozen report form. Continue?`
        : `${action} will discard this unsaved report. Continue?`
    return window.confirm(message)
  }, [composerDirty, submissionState, view])

  const resetComposer = useCallback(() => {
    activeCreateController.current?.abort()
    activeCreateController.current = null
    setValues(emptyReport)
    setFieldErrors({})
    setValidationSummary(null)
    setSubmissionMessage(null)
    setSubmissionState('editing')
    setAttempt(null)
    setSubmitBusy(false)
  }, [])

  useEffect(() => () => {
    projectEpoch.current += 1
    listEpoch.current += 1
    detailEpoch.current += 1
    activeProjectController.current?.abort()
    activeListController.current?.abort()
    activeDetailController.current?.abort()
    activeCreateController.current?.abort()
  }, [])

  const handleProjectOpen = useCallback((event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const nextId = projectInput.trim()
    if (!isCanonicalUuid(nextId)) {
      setProjectError('Enter a project ID in canonical UUID format.')
      projectInputRef.current?.focus()
      return
    }
    if (nextId === projectId && context) {
      setProjectError(null)
      setShowProjectForm(false)
      return
    }
    if (!leaveComposer('Opening another project')) return
    clearProtectedProject()
    resetComposer()
    setProjectError(null)
    setContextDenied(false)
    setContextUnavailable(false)
    setProjectId(nextId)
    setProjectInput(nextId)
    setShowProjectForm(false)
    setContextLoading(true)
    setContext(null)
    setContextRetry(value => value + 1)
    const url = new URL(window.location.href)
    url.searchParams.set('project', nextId)
    window.history.replaceState(null, '', url)
  }, [clearProtectedProject, context, leaveComposer, projectId, projectInput, resetComposer])

  const handleProtectedFailure = useCallback((error: unknown) => {
    if (error instanceof ReportApiError && error.status === 401) {
      clearProtectedProject()
      onSessionExpired()
      return 'session' as const
    }
    if (error instanceof ReportApiError && (error.status === 403 || error.status === 404)) {
      clearProtectedProject()
      setContextDenied(true)
      return 'denied' as const
    }
    return 'unavailable' as const
  }, [clearProtectedProject, onSessionExpired])

  useEffect(() => {
    if (!projectId) return
    const epoch = ++projectEpoch.current
    const controller = new AbortController()
    activeProjectController.current?.abort()
    activeProjectController.current = controller
    void fetchProjectContext(projectId, controller.signal).then(result => {
      if (controller.signal.aborted || epoch !== projectEpoch.current) return
      setContext(result)
      setContextUnavailable(false)
      setContextDenied(false)
      setReports([])
      setNextCursor(null)
      setListHasLoaded(false)
      setListLoading(true)
      setListError(null)
      setSelectedReportId(null)
      setDetail(null)
      setView('list')
    }).catch(error => {
      if (controller.signal.aborted || epoch !== projectEpoch.current) return
      const outcome = handleProtectedFailure(error)
      if (outcome === 'unavailable') setContextUnavailable(true)
    }).finally(() => {
      if (!controller.signal.aborted && epoch === projectEpoch.current) setContextLoading(false)
    })
    return () => controller.abort()
  }, [contextRetry, handleProtectedFailure, projectId])

  const loadReports = useCallback(async (cursor: string | null, append: boolean) => {
    if (!context || !projectId) return
    const epoch = ++listEpoch.current
    const controller = new AbortController()
    activeListController.current?.abort()
    activeListController.current = controller
    setListLoading(true)
    setListError(null)
    if (!append) {
      setListUnavailable(false)
      setListDenied(false)
    }
    try {
      const page = await fetchReports(projectId, statusFilter, cursor, controller.signal)
      if (controller.signal.aborted || epoch !== listEpoch.current) return
      setReports(previous => append ? [...previous, ...page.items] : page.items)
      setNextCursor(page.nextCursor)
      setListHasLoaded(true)
      setListUnavailable(false)
      setListDenied(false)
      setListNeedsRefresh(false)
    } catch (error) {
      if (controller.signal.aborted || epoch !== listEpoch.current) return
      const outcome = handleProtectedFailure(error)
      if (outcome === 'unavailable') {
        setListUnavailable(true)
        setListError(errorMessage(error))
      } else if (outcome === 'denied') {
        setListDenied(true)
        setListError('Reports for this project are unavailable to your account.')
      }
    } finally {
      if (!controller.signal.aborted && epoch === listEpoch.current) setListLoading(false)
    }
  }, [context, handleProtectedFailure, projectId, statusFilter])

  useEffect(() => {
    if (!context) return
    const epoch = ++listEpoch.current
    const controller = new AbortController()
    activeListController.current?.abort()
    activeListController.current = controller
    void fetchReports(projectId, statusFilter, null, controller.signal).then(page => {
      if (controller.signal.aborted || epoch !== listEpoch.current) return
      setReports(page.items)
      setNextCursor(page.nextCursor)
      setListHasLoaded(true)
      setListUnavailable(false)
      setListDenied(false)
      setListError(null)
      setListNeedsRefresh(false)
    }).catch(error => {
      if (controller.signal.aborted || epoch !== listEpoch.current) return
      const outcome = handleProtectedFailure(error)
      if (outcome === 'unavailable') {
        setListUnavailable(true)
        setListError(errorMessage(error))
      } else if (outcome === 'denied') {
        setListDenied(true)
        setListError('Reports for this project are unavailable to your account.')
      }
    }).finally(() => {
      if (!controller.signal.aborted && epoch === listEpoch.current) setListLoading(false)
    })
    return () => controller.abort()
  }, [context, handleProtectedFailure, projectId, statusFilter])

  const openReport = useCallback((id: string, dismissOverlay?: () => void) => {
    if (view === 'new' && !leaveComposer('Opening another report')) return
    if (view === 'new') resetComposer()
    detailEpoch.current += 1
    activeDetailController.current?.abort()
    setDetail(null)
    setDetailError(null)
    setSelectedReportId(id)
    setDetailLoading(true)
    setView('detail')
    dismissOverlay?.()
  }, [leaveComposer, resetComposer, view])

  useEffect(() => {
    if (view !== 'detail' || !selectedReportId || !projectId || !context) return
    if (submittedDetailId.current === selectedReportId) {
      submittedDetailId.current = null
      return
    }
    const epoch = ++detailEpoch.current
    const controller = new AbortController()
    activeDetailController.current?.abort()
    activeDetailController.current = controller
    void fetchReport(projectId, selectedReportId, controller.signal).then(result => {
      if (controller.signal.aborted || epoch !== detailEpoch.current) return
      setDetail(result)
    }).catch(error => {
      if (controller.signal.aborted || epoch !== detailEpoch.current) return
      const outcome = handleProtectedFailure(error)
      if (outcome === 'unavailable') setDetailError('This report could not be loaded. Try again.')
      else if (outcome === 'denied') setDetailError('This report is unavailable to your account.')
    }).finally(() => {
      if (!controller.signal.aborted && epoch === detailEpoch.current) setDetailLoading(false)
    })
    return () => controller.abort()
  }, [context, handleProtectedFailure, projectId, selectedReportId, view])

  const beginNewReport = useCallback(() => {
    if (view === 'new') return true
    if (!leaveComposer('Starting a new report')) return false
    resetComposer()
    setSelectedReportId(null)
    setDetail(null)
    setView('new')
    return true
  }, [leaveComposer, resetComposer, view])

  const requestProjectChange = useCallback(() => {
    if (!leaveComposer('Changing projects')) return
    resetComposer()
    setView('list')
    setProjectInput(projectId)
    setShowProjectForm(true)
  }, [leaveComposer, projectId, resetComposer])

  const returnToList = useCallback(() => {
    setDetail(null)
    setSelectedReportId(null)
    setDetailError(null)
    setView('list')
    if (listNeedsRefresh) void loadReports(null, false)
  }, [listNeedsRefresh, loadReports])

  const changeStatusFilter = useCallback((status: ReportStatus | '') => {
    setReports([])
    setNextCursor(null)
    setListHasLoaded(false)
    setListError(null)
    setListUnavailable(false)
    setListDenied(false)
    setListLoading(true)
    setStatusFilter(status)
  }, [])

  const handleAreaSelect = useCallback((next: string) => {
    const nextArea = next as WorkspaceArea
    if (nextArea === 'environments' && !leaveComposer('Opening Environments')) return
    if (nextArea === 'environments' && view === 'new') resetComposer()
    setArea(nextArea)
  }, [leaveComposer, resetComposer, view])

  const submitReport = useCallback(async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!context || !projectId || submitBusy || submitLock.current) return
    submitLock.current = true
    let resolvedAttempt: Attempt
    if (submissionState === 'unconfirmed' && attempt) {
      resolvedAttempt = attempt
      setSubmissionState('pending')
      setSubmissionMessage('Retrying the same report submission…')
      setSubmitBusy(true)
    } else {
      if (submissionState === 'conflict') {
        submitLock.current = false
        return
      }
      const errors = validateReport(values)
      setFieldErrors(errors)
      const invalid = reportFields.find(field => errors[field]) ?? null
      if (invalid) {
        setValidationSummary('Review the highlighted fields before creating the report.')
        requestAnimationFrame(() => document.getElementById(`report-${invalid}`)?.focus())
        submitLock.current = false
        return
      }
      setValidationSummary(null)
      const canonicalValues = Object.fromEntries(reportFields.map(field => [field, values[field]])) as ReportValues
      const nextAttempt: Attempt = {
        body: JSON.stringify(canonicalValues),
        key: crypto.randomUUID(),
        values: canonicalValues,
      }
      resolvedAttempt = nextAttempt
      setAttempt(nextAttempt)
      setSubmissionState('pending')
      setSubmissionMessage('Creating report…')
      setSubmitBusy(true)
    }

    const requestedProjectId = projectId
    const requestedEpoch = projectEpoch.current
    const controller = new AbortController()
    const requestSignal = AbortSignal.any([controller.signal, AbortSignal.timeout(20_000)])
    activeCreateController.current?.abort()
    activeCreateController.current = controller
    try {
      const saved = await createReport(requestedProjectId, resolvedAttempt.body, resolvedAttempt.key, requestSignal)
      if (controller.signal.aborted || requestedEpoch !== projectEpoch.current) return
      setAttempt(null)
      setSubmissionState('editing')
      setSubmissionMessage(null)
      setValues(emptyReport)
      setFieldErrors({})
      setValidationSummary(null)
      setDetail(saved)
      setSelectedReportId(saved.id)
      setDetailLoading(false)
      submittedDetailId.current = saved.id
      setListNeedsRefresh(true)
      setView('detail')
    } catch (error) {
      if (controller.signal.aborted || requestedEpoch !== projectEpoch.current) return
      if (error instanceof ReportApiError && error.status === 401) {
        handleProtectedFailure(error)
      } else if (error instanceof ReportApiError && error.status === 403) {
        if (error.code === 'CSRF_INVALID') {
          setSubmissionState('editing')
          setAttempt(null)
          setSubmissionMessage('The security token could not be refreshed. Your report is still here; try again.')
        } else handleProtectedFailure(error)
      } else if (error instanceof ReportApiError && error.status === 404) {
        handleProtectedFailure(error)
      } else if (error instanceof ReportApiError && error.status === 409) {
        setSubmissionState('conflict')
        setSubmissionMessage('The submission key conflicts with another request. The report was not confirmed. Return to the reports list and check before starting another submission.')
        setAttempt(resolvedAttempt)
      } else if (error instanceof ReportApiError && error.status >= 400 && error.status < 500) {
        setAttempt(null)
        setSubmissionState('editing')
        setSubmissionMessage(error.status === 400
          ? 'The server rejected this report. Review the fields and try again.'
          : 'The report was not submitted. Your information is still here.')
      } else {
        setSubmissionState('unconfirmed')
        setAttempt(resolvedAttempt)
        setSubmissionMessage('The save outcome is unconfirmed. Retry uses the same report and submission key.')
      }
    } finally {
      if (requestedEpoch === projectEpoch.current) setSubmitBusy(false)
      if (activeCreateController.current === controller) activeCreateController.current = null
      submitLock.current = false
    }
  }, [attempt, context, handleProtectedFailure, projectId, submissionState, submitBusy, values])

  const editField = useCallback((field: ReportField, value: string) => {
    if (composerFrozen || submissionState === 'pending') return
    setValues(previous => ({ ...previous, [field]: value }))
    if (fieldErrors[field]) setFieldErrors(previous => ({ ...previous, [field]: undefined }))
    if (submissionMessage) setSubmissionMessage(null)
  }, [composerFrozen, fieldErrors, submissionMessage, submissionState])

  const retryDetail = useCallback(() => {
    if (!selectedReportId) return
    setDetail(null)
    setDetailError(null)
    setDetailLoading(true)
    setView('detail')
    detailEpoch.current += 1
    activeDetailController.current?.abort()
    // Changing the selection to the same value would not retrigger the effect.
    setSelectedReportId(null)
    requestAnimationFrame(() => setSelectedReportId(selectedReportId))
  }, [selectedReportId])

  const renderSidebar = (dismissOverlay: () => void) => (
    <>
      <SidebarGroup title="Reports">
        <Button color="primary" className="report-new-button" onClick={() => { if (beginNewReport()) dismissOverlay() }}>
          <Plus />
          New report
        </Button>
      </SidebarGroup>
      <SidebarGroup title="Submitted">
        {reports.map(report => (
          <SidebarItem
            key={report.id}
            label={report.title}
            icon={<FileDocument />}
            selected={view === 'detail' && selectedReportId === report.id}
            onSelect={() => openReport(report.id, dismissOverlay)}
          />
        ))}
        {listLoading && reports.length === 0 && <p className="report-sidebar-note">Loading reports…</p>}
        {listHasLoaded && reports.length === 0 && <p className="report-sidebar-note">No submitted reports</p>}
      </SidebarGroup>
    </>
  )

  const sidebar = area === 'reports' && context ? {
    title: 'Reports',
    renderItems: renderSidebar,
  } : undefined

  function renderProjectForm() {
    return (
      <form className="report-project-form" onSubmit={handleProjectOpen}>
        <label htmlFor="report-project-id">Project ID</label>
        <p className="muted">Enter a project ID supplied by your team. TraceDesk will check your access.</p>
        <div className="report-project-row">
          <Input
            ref={projectInputRef}
            id="report-project-id"
            name="projectId"
            autoComplete="off"
            value={projectInput}
            onChange={event => { setProjectInput(event.target.value); setProjectError(null) }}
            aria-invalid={Boolean(projectError)}
            aria-describedby={projectError ? 'report-project-error' : undefined}
            className="report-project-input"
          />
          <Button color="primary" type="submit" disabled={contextLoading}>
            {contextLoading ? 'Opening…' : 'Open project'}
          </Button>
        </div>
        {projectError && <p id="report-project-error" className="report-field-error" role="alert">{projectError}</p>}
      </form>
    )
  }

  function renderProjectGate() {
    return (
      <section className="report-gate" aria-labelledby="report-gate-title">
        {contextLoading ? (
          <p className="report-status-message" role="status">Checking project access…</p>
        ) : contextDenied ? (
          <div className="report-notice" role="alert">
            <h1 id="report-gate-title">Project unavailable</h1>
            <p>This project is unavailable to your account.</p>
            {renderProjectForm()}
          </div>
        ) : contextUnavailable ? (
          <div className="report-notice" role="alert">
            <h1 id="report-gate-title">Project could not be opened</h1>
            <p>TraceDesk could not verify project access. Try again.</p>
          {projectId && <Button color="secondary" variant="outline" onClick={() => { setContextUnavailable(false); setContextLoading(true); setContextRetry(value => value + 1) }}>Retry</Button>}
            {renderProjectForm()}
          </div>
        ) : (
          <div className="report-notice">
            <h1 id="report-gate-title">Open a project</h1>
            <p>Open a project to view and submit your reports.</p>
            {renderProjectForm()}
          </div>
        )}
      </section>
    )
  }

  function renderList() {
    return (
      <section className="report-library" aria-labelledby="reports-heading">
        <div className="report-page-heading">
          <div>
            <h1 id="reports-heading">Reports</h1>
            <p className="muted">Reports for {context?.project.name}</p>
          </div>
          <Button color="primary" onClick={beginNewReport}><Plus />New report</Button>
        </div>
        <div className="report-list-controls">
          <label htmlFor="report-status-filter">Filter by status</label>
          <select id="report-status-filter" value={statusFilter} onChange={event => changeStatusFilter(event.target.value as ReportStatus | '')}>
            {statuses.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
          <Button color="secondary" variant="ghost" className="report-change-project" onClick={() => { setProjectInput(projectId); setShowProjectForm(value => !value) }}>
            {showProjectForm ? 'Close project form' : 'Change project'}
          </Button>
        </div>
        {showProjectForm && <div className="report-project-switch">{renderProjectForm()}</div>}
        {listError && <div className="report-notice" role="alert"><p>{listError}</p></div>}
        {listLoading && reports.length === 0 && <p className="report-status-message" role="status">Loading reports…</p>}
        {!listLoading && !listError && listHasLoaded && reports.length === 0 && (
          <div className="report-empty-state">
            <h2>{statusFilter ? 'No reports match this status' : 'No reports yet'}</h2>
            <p>{statusFilter ? 'Clear the status filter to see all reports in this project.' : 'Create a report to share a bug with your team.'}</p>
            {statusFilter && <Button color="secondary" variant="outline" onClick={() => changeStatusFilter('')}>Clear filter</Button>}
          </div>
        )}
        {reports.length > 0 && (
          <div className="report-list" aria-label="Submitted reports">
            {reports.map(report => (
              <button className="report-row" type="button" key={report.id} onClick={() => openReport(report.id)}>
                <span className="report-row-title">{report.title}</span>
                <Badge color={statusColor(report.status)}>{statusLabel(report.status)}</Badge>
                <time dateTime={report.updatedAt}>{new Date(report.updatedAt).toLocaleDateString()}</time>
              </button>
            ))}
          </div>
        )}
        {listLoading && reports.length > 0 && <p className="report-status-message" role="status">Loading more reports…</p>}
        {nextCursor && !listLoading && <Button color="secondary" variant="outline" onClick={() => void loadReports(nextCursor, true)}>Load more</Button>}
        {listError && listUnavailable && <Button color="secondary" variant="outline" onClick={() => void loadReports(null, false)}>Retry</Button>}
      </section>
    )
  }

  function renderDetail() {
    if (detailLoading) return <p className="report-status-message" role="status">Loading report…</p>
    if (!detail && detailError) return <section className="report-notice" role="alert"><p>{detailError}</p><Button color="secondary" variant="outline" onClick={retryDetail}>Retry</Button><Button color="secondary" variant="ghost" onClick={returnToList}>Back to reports</Button></section>
    if (!detail) return <p className="report-status-message" role="status">Loading report…</p>
    return (
      <article className="report-detail">
        <div className="report-detail-top">
          <Button color="secondary" variant="ghost" onClick={returnToList}>Back to reports</Button>
          <Badge color={statusColor(detail.status)}>{statusLabel(detail.status)}</Badge>
          <span className="report-version">Version {detail.version}</span>
        </div>
        <h1>{detail.title}</h1>
        <p className="report-detail-meta">Report {detail.id} · Updated {new Date(detail.updatedAt).toLocaleString()}</p>
        <dl className="report-detail-fields">
          {reportFields.map(field => (
            <div key={field} className={field === 'textLogs' ? 'report-detail-field report-detail-logs' : 'report-detail-field'}>
              <dt>{reportFieldRules[field].label}</dt>
              <dd>{detail[field] || <span className="muted">No logs provided</span>}</dd>
            </div>
          ))}
        </dl>
      </article>
    )
  }

  function renderComposer() {
    return (
      <section className="report-form-wrap" aria-labelledby="new-report-heading">
        <form className="report-form" onSubmit={event => void submitReport(event)} noValidate>
          <div className="report-form-heading">
            <div className="report-form-heading-row">
              <p className="report-form-eyebrow">{context?.project.name}</p>
              <Button color="secondary" variant="ghost" type="button" onClick={requestProjectChange}>Change project</Button>
            </div>
            <h1 id="new-report-heading">New report</h1>
            <p className="muted">Share the details your team needs to reproduce and investigate the issue.</p>
          </div>
          <div className="report-form-group">
            {reportFields.map(field => {
              const rule = reportFieldRules[field]
              const id = `report-${field}`
              const errorId = `${id}-error`
              const isLong = field !== 'title'
              const label = <label htmlFor={id}>{rule.label}{rule.required && <span className="report-required"> · required</span>}</label>
              const controlProps = {
                id,
                name: field,
                value: values[field],
                disabled: submitBusy || composerFrozen,
                invalid: Boolean(fieldErrors[field]),
                'aria-invalid': Boolean(fieldErrors[field]),
                'aria-required': rule.required,
                'aria-describedby': fieldErrors[field] ? errorId : undefined,
                onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => editField(field, event.target.value),
              }
              return (
                <div className={`report-field report-field-${field}`} key={field}>
                  {label}
                  {isLong
                    ? <Textarea {...controlProps} rows={field === 'textLogs' ? 5 : field === 'description' ? 4 : 3} />
                    : <Input {...controlProps} />}
                  <div className="report-field-foot">
                    {fieldErrors[field]
                      ? <p className="report-field-error" id={errorId}>{fieldErrors[field]}</p>
                      : <span className="report-field-hint">Up to {rule.maxCodePoints.toLocaleString()} characters</span>}
                    <span className="report-character-count">{Array.from(values[field]).length.toLocaleString()} / {rule.maxCodePoints.toLocaleString()}</span>
                  </div>
                </div>
              )
            })}
          </div>
          {validationSummary && <p className="report-validation-summary" role="alert">{validationSummary}</p>}
          {submissionMessage && <p className={`report-submission-message report-submission-${submissionState}`} role="status">{submissionMessage}</p>}
          <div className="report-form-actions">
            <Button color="secondary" variant="ghost" type="button" disabled={submitBusy} onClick={() => { if (leaveComposer('Returning to reports')) { resetComposer(); setView('list') } }}>Cancel</Button>
            <Button color="primary" type="submit" disabled={submitBusy || submissionState === 'conflict'} loading={submitBusy}>
              {submissionState === 'unconfirmed' ? 'Retry submission' : submitBusy ? 'Creating report…' : 'Create report'}
            </Button>
          </div>
          {submissionState === 'conflict' && <Button color="secondary" variant="outline" onClick={() => { if (leaveComposer('Returning to reports')) { resetComposer(); setView('list') } }}>Back to reports</Button>}
        </form>
      </section>
    )
  }

  function renderReports() {
    if (!projectId || !context) return renderProjectGate()
    if (contextLoading) return <p className="report-status-message" role="status">Checking project access…</p>
    if (listDenied) return <section className="report-notice" role="alert"><h1>Reports unavailable</h1><p>Reports for this project are unavailable to your account.</p></section>
    if (view === 'new') return renderComposer()
    if (view === 'detail') return renderDetail()
    return renderList()
  }

  return (
    <AppShell
      items={trayItems}
      activeId={area}
      onSelect={handleAreaSelect}
      sidebar={sidebar}
      headerContent={(
        <div className="report-shell-heading">
          <span>{context?.project.name ?? 'TraceDesk'}</span>
          {context && <span className="report-shell-role">Reporter</span>}
        </div>
      )}
    >
      {area === 'reports' ? renderReports() : area === 'environments' ? (
        <section className="report-notice report-area-unavailable"><Folder /><h1>Environments</h1><p>Saved environments are not available yet.</p><Button color="secondary" variant="outline" onClick={() => setArea('reports')}>Back to reports</Button></section>
      ) : (
        <section className="report-settings" aria-labelledby="settings-heading">
          <h1 id="settings-heading">Settings</h1>
          <section className="report-settings-group">
            <h2>Appearance</h2>
            <p className="muted">Choose how TraceDesk looks on this device.</p>
            <div className="report-theme-buttons">
              <Button color="secondary" variant={theme === 'light' ? 'soft' : 'outline'} aria-pressed={theme === 'light'} onClick={() => onThemeChange('light')}>Light theme</Button>
              <Button color="secondary" variant={theme === 'dark' ? 'soft' : 'outline'} aria-pressed={theme === 'dark'} onClick={() => onThemeChange('dark')}>Dark theme</Button>
            </div>
          </section>
          <section className="report-settings-group">
            <h2>Account</h2>
            <p className="muted">Signed in to TraceDesk.</p>
            <p className="muted">Your account is verified by your TraceDesk session.</p>
            {signOutError && <p className="sign-in-error" role="alert">{signOutError}</p>}
            <Button color="secondary" variant="outline" disabled={signingOut} onClick={onSignOut}>{signingOut ? 'Signing out…' : 'Sign out'}</Button>
          </section>
        </section>
      )}
    </AppShell>
  )
}
