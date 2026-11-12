import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Badge } from '@openai/apps-sdk-ui/components/Badge'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Bug, FileDocument, Plus } from '@openai/apps-sdk-ui/components/Icon'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { AppShell } from '../shell/AppShell'
import { SidebarGroup, SidebarItem } from '../shell/Sidebar'
import type { TrayItem } from '../shell/IconTray'
import { createDraft, createReport, deleteDraft, fetchDraft, fetchDrafts, fetchProjectContext, fetchReport, fetchReports, isCanonicalUuid, ReportApiError, submitDraft, updateDraft, updateReport, type DraftDetail, type DraftSummary, type ProjectContext, type ReportDetail, type ReportStatus, type ReportSummary } from './reportApi'
import { emptyReport, reportFieldRules, reportFields, validateReport, type FieldErrors, type ReportField, type ReportValues } from './reportValidation'
import { ReporterAiPanel } from './ReporterAiPanel'
import { ReportActivity } from './ReportActivity'
import './reporter-workspace.css'

type WorkspaceArea = 'reports' | 'settings'
type ReportView = 'list' | 'new' | 'detail'
type SubmissionState = 'editing' | 'pending' | 'unconfirmed' | 'conflict'
type Attempt = { body: string; key: string; values: ReportValues }
type DraftAttempt = { body: string; key: string }
type EditorKind = 'new' | 'draft' | 'report'

const statuses: { value: ReportStatus | ''; label: string }[] = [
  { value: '', label: 'All statuses' },
  { value: 'OPEN', label: 'Open' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'READY_FOR_QA', label: 'Ready for QA' },
  { value: 'CLOSED', label: 'Closed' },
]

const trayItems: readonly TrayItem[] = [
  { id: 'reports', label: 'Reports', icon: <Bug /> },
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
  onChooseWorkspace,
}: {
  theme: 'light' | 'dark'
  onThemeChange: (theme: 'light' | 'dark') => void
  onSessionExpired: () => void
  onSignOut: () => void
  signingOut: boolean
  signOutError: string | null
  onChooseWorkspace?: () => void
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
  const [drafts, setDrafts] = useState<DraftSummary[]>([])
  const [draftCursor, setDraftCursor] = useState<string | null>(null)
  const [draftLoading, setDraftLoading] = useState(false)
  const [draftError, setDraftError] = useState<string | null>(null)
  const [draftHasLoaded, setDraftHasLoaded] = useState(false)
  const [activeDraft, setActiveDraft] = useState<DraftDetail | null>(null)
  const [editorKind, setEditorKind] = useState<EditorKind>('new')
  const [editorReportVersion, setEditorReportVersion] = useState<number | null>(null)
  const [editorReportId, setEditorReportId] = useState<string | null>(null)
  const [draftAttempt, setDraftAttempt] = useState<DraftAttempt | null>(null)
  const [draftBusy, setDraftBusy] = useState(false)
  const [editRecovery, setEditRecovery] = useState<ReportDetail | null>(null)
  const [editRecoveryMessage, setEditRecoveryMessage] = useState<string | null>(null)
  const [editReviewRequired, setEditReviewRequired] = useState(false)
  const [draftRecovery, setDraftRecovery] = useState<DraftDetail | null>(null)
  const [draftReadRequired, setDraftReadRequired] = useState(false)
  const [editorClock, setEditorClock] = useState(() => Date.now())
  useEffect(() => { const timer = window.setInterval(() => setEditorClock(Date.now()), 30_000); return () => window.clearInterval(timer) }, [])
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
  const [detailRefresh, setDetailRefresh] = useState(0)
  const [values, setValues] = useState<ReportValues>(emptyReport)
  const [editorBaseValues, setEditorBaseValues] = useState<ReportValues>(emptyReport)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [validationSummary, setValidationSummary] = useState<string | null>(null)
  const [submissionState, setSubmissionState] = useState<SubmissionState>('editing')
  const [submissionMessage, setSubmissionMessage] = useState<string | null>(null)
  const [attempt, setAttempt] = useState<Attempt | null>(null)
  const [submitBusy, setSubmitBusy] = useState(false)
  const [listNeedsRefresh, setListNeedsRefresh] = useState(false)
  const projectEpoch = useRef(0)
  const listEpoch = useRef(0)
  const draftEpoch = useRef(0)
  const detailEpoch = useRef(0)
  const activeProjectController = useRef<AbortController | null>(null)
  const activeListController = useRef<AbortController | null>(null)
  const activeDraftController = useRef<AbortController | null>(null)
  const activeMutationController = useRef<AbortController | null>(null)
  const activeDetailController = useRef<AbortController | null>(null)
  const activeCreateController = useRef<AbortController | null>(null)
  const submitLock = useRef(false)
  const submittedDetailId = useRef<string | null>(null)
  const [contextRetry, setContextRetry] = useState(0)
  const projectInputRef = useRef<HTMLInputElement>(null)

  const composerDirty = useMemo(() => reportFields.some(field => values[field] !== editorBaseValues[field]), [editorBaseValues, values])
  const composerFrozen = submissionState === 'unconfirmed' || submissionState === 'conflict' || Boolean(draftAttempt)

  const clearProtectedProject = useCallback(() => {
    projectEpoch.current += 1
    listEpoch.current += 1
    detailEpoch.current += 1
    activeProjectController.current?.abort()
    activeListController.current?.abort()
    activeDraftController.current?.abort()
    activeMutationController.current?.abort()
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
    setDrafts([])
    setDraftCursor(null)
    setDraftLoading(false)
    setDraftError(null)
    setDraftHasLoaded(false)
    setActiveDraft(null)
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
    setEditorBaseValues(emptyReport)
    setFieldErrors({})
    setValidationSummary(null)
    setSubmissionMessage(null)
    setSubmissionState('editing')
    setAttempt(null)
    setDraftAttempt(null)
    setDraftBusy(false)
    setEditRecovery(null)
    setEditRecoveryMessage(null)
    setEditReviewRequired(false)
    setDraftRecovery(null)
    setDraftReadRequired(false)
    setEditorKind('new')
    setEditorReportVersion(null)
    setEditorReportId(null)
    setSubmitBusy(false)
    submitLock.current = false
  }, [])

  const leaveComposer = useCallback((action: string): boolean => {
    if (view !== 'new' || (!composerDirty && submissionState !== 'unconfirmed' && submissionState !== 'conflict' && submissionState !== 'pending' && !draftAttempt && !draftBusy)) return true
    if (submissionState === 'pending' || draftBusy) {
      setSubmissionMessage('The report is still being submitted. Wait for the save outcome before changing projects or reports.')
      return false
    }
    const message = submissionState === 'unconfirmed'
      ? `This report's save outcome is unconfirmed. ${action} will discard the local retry. It will not cancel a report that may already have been saved. Retry this same submission first to avoid creating a second report. Continue leaving?`
      : draftAttempt
        ? `The draft save outcome is unconfirmed. ${action} will discard the local retry. It will not cancel a draft that may already have been saved. Retry this same save first to avoid creating a duplicate. Continue leaving?`
        : submissionState === 'conflict'
        ? `The server reported a submission conflict. ${action} will discard this frozen report form. Continue?`
        : `${action} will discard unsaved ${editorKind === 'draft' ? 'draft changes' : editorKind === 'report' ? 'report edits' : 'report details'}. Continue?`
    return window.confirm(message)
  }, [composerDirty, draftAttempt, draftBusy, editorKind, submissionState, view])

  const resetComposer = useCallback(() => {
    activeMutationController.current?.abort()
    activeMutationController.current = null
    activeCreateController.current?.abort()
    activeCreateController.current = null
    setValues(emptyReport)
    setEditorBaseValues(emptyReport)
    setFieldErrors({})
    setValidationSummary(null)
    setSubmissionMessage(null)
    setSubmissionState('editing')
    setAttempt(null)
    setDraftAttempt(null)
    setDraftBusy(false)
    setActiveDraft(null)
    setEditorKind('new')
    setEditorReportVersion(null)
    setEditorReportId(null)
    setEditRecovery(null)
    setEditRecoveryMessage(null)
    setEditReviewRequired(false)
    setDraftRecovery(null)
    setDraftReadRequired(false)
    setSubmitBusy(false)
  }, [])

  useEffect(() => () => {
    projectEpoch.current += 1
    listEpoch.current += 1
    detailEpoch.current += 1
    activeProjectController.current?.abort()
    activeListController.current?.abort()
    activeDraftController.current?.abort()
    activeMutationController.current?.abort()
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
      setDrafts([])
      setDraftCursor(null)
      setDraftHasLoaded(false)
      setDraftError(null)
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

  const loadDrafts = useCallback(async (cursor: string | null = null, append = false) => {
    if (!context || !projectId) return
    const epoch = ++draftEpoch.current
    const controller = new AbortController()
    activeDraftController.current?.abort()
    activeDraftController.current = controller
    setDraftLoading(true)
    setDraftError(null)
    try {
      const page = await fetchDrafts(projectId, controller.signal, cursor ?? undefined)
      if (controller.signal.aborted || epoch !== draftEpoch.current) return
      setDrafts(previous => append ? [...previous, ...page.items] : page.items)
      setDraftCursor(page.nextCursor)
      setDraftHasLoaded(true)
    } catch (error) {
      if (controller.signal.aborted || epoch !== draftEpoch.current) return
      const outcome = handleProtectedFailure(error)
      if (outcome === 'unavailable') setDraftError('Saved drafts could not be loaded. Try again.')
    } finally {
      if (!controller.signal.aborted && epoch === draftEpoch.current) setDraftLoading(false)
    }
  }, [context, handleProtectedFailure, projectId])

  useEffect(() => {
    if (!context) return
    let active = true
    queueMicrotask(() => { if (active) void loadDrafts() })
    return () => {
      active = false
      activeDraftController.current?.abort()
    }
  }, [context, loadDrafts])

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
  }, [context, detailRefresh, handleProtectedFailure, projectId, selectedReportId, view])

  const beginNewReport = useCallback(() => {
    if (view === 'new') return true
    if (!leaveComposer('Starting a new report')) return false
    resetComposer()
    setSelectedReportId(null)
    setDetail(null)
    setEditorKind('new')
    setEditorBaseValues(emptyReport)
    setEditorReportId(null)
    setEditorReportVersion(null)
    setActiveDraft(null)
    setView('new')
    return true
  }, [leaveComposer, resetComposer, view])

  const resumeDraft = useCallback(async (id: string) => {
    if (!context || !projectId || (view === 'new' && !leaveComposer('Opening another draft'))) return
    activeDraftController.current?.abort()
    const controller = new AbortController()
    activeDraftController.current = controller
    const epoch = ++draftEpoch.current
    setDraftLoading(true)
    setDraftError(null)
    try {
      const saved = await fetchDraft(projectId, id, controller.signal)
      if (controller.signal.aborted || epoch !== draftEpoch.current || projectId !== saved.projectId) return
      resetComposer()
      setActiveDraft(saved)
      setEditorKind('draft')
      setEditorBaseValues(Object.fromEntries(reportFields.map(field => [field, saved[field]])) as ReportValues)
      setValues(Object.fromEntries(reportFields.map(field => [field, saved[field]])) as ReportValues)
      setEditorReportId(null)
      setEditorReportVersion(null)
      setSelectedReportId(null)
      setView('new')
    } catch (error) {
      if (controller.signal.aborted || epoch !== draftEpoch.current) return
      const outcome = handleProtectedFailure(error)
      if (outcome === 'unavailable') setDraftError(error instanceof ReportApiError && error.status === 410 ? 'This draft has expired and is no longer available.' : 'This draft could not be opened. Refresh the draft list and try again.')
    } finally {
      if (!controller.signal.aborted && epoch === draftEpoch.current) setDraftLoading(false)
    }
  }, [context, handleProtectedFailure, leaveComposer, projectId, resetComposer, view])

  const beginReportEdit = useCallback(() => {
    if (!detail || detail.status !== 'OPEN') return
    setEditorKind('report')
    setEditorReportId(detail.id)
    setEditorReportVersion(detail.version)
    const current = Object.fromEntries(reportFields.map(field => [field, detail[field]])) as ReportValues
    setEditorBaseValues(current)
    setValues(current)
    setFieldErrors({})
    setValidationSummary(null)
    setSubmissionMessage(null)
    setEditRecovery(null)
    setEditRecoveryMessage(null)
    setEditReviewRequired(false)
    setView('new')
  }, [detail])

  const requestProjectChange = useCallback(() => {
    if (!leaveComposer('Changing projects')) return
    resetComposer()
    setView('list')
    if (onChooseWorkspace) {
      onChooseWorkspace()
      return
    }
    setProjectInput(projectId)
    setShowProjectForm(true)
  }, [leaveComposer, onChooseWorkspace, projectId, resetComposer])

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
    if (next === 'reports' || next === 'settings') setArea(next)
  }, [])

  const handleSignOut = useCallback(() => {
    clearProtectedProject()
    onSignOut()
  }, [clearProtectedProject, onSignOut])

  const submitReport = useCallback(async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (editorKind !== 'new') return
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
  }, [attempt, context, editorKind, handleProtectedFailure, projectId, submissionState, submitBusy, values])

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
    setDetailRefresh(value => value + 1)
  }, [selectedReportId])

  const persistDraft = useCallback(async (aiValues?: ReportValues) => {
    if (!context || !projectId || draftBusy || submitBusy || Boolean(draftRecovery) || draftReadRequired) return
    const requestedProjectId = projectId
    const requestedEpoch = projectEpoch.current
    const original = activeDraft
    const canonicalValues = Object.fromEntries(reportFields.map(field => [field, (aiValues ?? values)[field]])) as ReportValues
    const keyAttempt = original ? null : draftAttempt ?? { body: JSON.stringify(canonicalValues), key: crypto.randomUUID() }
    const body = original
      ? JSON.stringify({ ...canonicalValues, expectedVersion: original.version })
      : keyAttempt!.body
    if (!original) setDraftAttempt(keyAttempt)
    setDraftBusy(true)
    setSubmissionMessage(original ? 'Saving draft changes…' : 'Saving draft…')
    const controller = new AbortController()
    activeMutationController.current?.abort()
    activeMutationController.current = controller
    const requestSignal = AbortSignal.any([controller.signal, AbortSignal.timeout(20_000)])
    try {
      const saved = original
        ? await updateDraft(requestedProjectId, original.id, body, requestSignal)
        : await createDraft(requestedProjectId, body, keyAttempt!.key, requestSignal)
      if (controller.signal.aborted || requestedEpoch !== projectEpoch.current) return
      if (original && (saved.id !== original.id || saved.version !== original.version + 1 || !reportFields.every(field => saved[field] === canonicalValues[field]))) throw new ReportApiError(502, 'INVALID_RESPONSE')
      setActiveDraft(saved)
      setEditorKind('draft')
      const savedValues = Object.fromEntries(reportFields.map(field => [field, saved[field]])) as ReportValues
      setValues(savedValues)
      setEditorBaseValues(savedValues)
      setDraftAttempt(null)
      setDraftRecovery(null)
      setDrafts(previous => [saved, ...previous.filter(item => item.id !== saved.id)])
      setSubmissionMessage(`Draft saved. It expires ${new Date(saved.expiresAt).toLocaleDateString()}.`)
      void loadDrafts()
    } catch (error) {
      if (controller.signal.aborted || requestedEpoch !== projectEpoch.current) return
      if (error instanceof ReportApiError && (error.status === 401 || error.status === 403 || error.status === 404)) {
        handleProtectedFailure(error)
        return
      }
      if (!original && error instanceof ReportApiError && error.status >= 400 && error.status < 500 && error.status !== 409) {
        setDraftAttempt(null)
        setSubmissionMessage(error.status === 400 ? 'The server rejected this draft. Review the fields and try again.' : 'The draft was not saved. Your information is still here.')
        return
      }
      if (!original && error instanceof ReportApiError && error.status === 409 && (error.code === 'DRAFT_LIMIT' || error.code === 'IDEMPOTENCY_CONFLICT')) {
        setDraftAttempt(null)
        setSubmissionMessage(error.code === 'DRAFT_LIMIT' ? 'You already have the maximum number of saved drafts in this project. Delete one before saving another.' : 'The draft request conflicted with an earlier save. Your text is still here; try again to start a fresh save request.')
        return
      }
      const recover = async () => {
        if (!original) {
          setDraftAttempt(keyAttempt)
          setSubmissionMessage('The save outcome is unconfirmed. Retry this same draft save to resolve it.')
          return
        }
        setDraftReadRequired(true)
        try {
          const current = await fetchDraft(requestedProjectId, original.id, AbortSignal.timeout(20_000))
          if (requestedEpoch !== projectEpoch.current) return
          setDraftReadRequired(false)
          const currentValues = Object.fromEntries(reportFields.map(field => [field, current[field]])) as ReportValues
          if (current.version > original.version && reportFields.every(field => currentValues[field] === canonicalValues[field])) {
            setActiveDraft(current)
            setValues(currentValues)
            setEditorBaseValues(currentValues)
            setSubmissionMessage('Draft save confirmed from the current server version.')
            void loadDrafts()
          } else {
            setDraftRecovery(current)
            setActiveDraft(current)
            setSubmissionMessage('A newer draft version is available. Review its saved values before choosing how to continue.')
            void loadDrafts()
          }
        } catch (refreshError) {
          if (refreshError instanceof ReportApiError && (refreshError.status === 401 || refreshError.status === 403 || refreshError.status === 404)) handleProtectedFailure(refreshError)
          else setSubmissionMessage('The draft save outcome is unconfirmed. Your edits remain here; retry loading the saved version before saving again.')
        }
      }
      if (error instanceof ReportApiError && error.status === 409) setSubmissionMessage('This draft changed elsewhere. Loading the current saved version…')
      else setSubmissionMessage('The draft save outcome is unconfirmed. Checking the current saved version…')
      await recover()
    } finally {
      if (requestedEpoch === projectEpoch.current) setDraftBusy(false)
      if (activeMutationController.current === controller) activeMutationController.current = null
    }
  }, [activeDraft, context, draftAttempt, draftBusy, draftRecovery, draftReadRequired, handleProtectedFailure, loadDrafts, projectId, submitBusy, values])

  const submitSavedDraft = useCallback(async () => {
    if (!activeDraft || !context || !projectId || draftBusy || submitBusy || draftReadRequired) return
    if (composerDirty) {
      setSubmissionMessage('Save your draft changes first, then submit the saved version.')
      return
    }
    const errors = validateReport(values)
    setFieldErrors(errors)
    const invalid = reportFields.find(field => errors[field]) ?? null
    if (invalid) {
      setValidationSummary('Complete the required fields before submitting this draft.')
      requestAnimationFrame(() => document.getElementById(`report-${invalid}`)?.focus())
      return
    }
    setValidationSummary(null)
    setDraftBusy(true)
    setSubmissionMessage('Submitting draft…')
    const requestedProjectId = projectId
    const requestedEpoch = projectEpoch.current
    const controller = new AbortController()
    activeMutationController.current?.abort()
    activeMutationController.current = controller
    const requestSignal = AbortSignal.any([controller.signal, AbortSignal.timeout(20_000)])
    try {
      const result = await submitDraft(requestedProjectId, activeDraft.id, activeDraft.version, crypto.randomUUID(), requestSignal)
      if (controller.signal.aborted || requestedEpoch !== projectEpoch.current) return
      setActiveDraft(null)
      setDraftAttempt(null)
      setDrafts(previous => previous.map(item => item.id === activeDraft.id ? { ...item, submittedReportId: result.id, version: item.version + 1 } : item))
      setDetail(result)
      setSelectedReportId(result.id)
      setDetailLoading(false)
      submittedDetailId.current = result.id
      setListNeedsRefresh(true)
      setView('detail')
      setSubmissionMessage(null)
      void loadDrafts()
    } catch (error) {
      if (controller.signal.aborted || requestedEpoch !== projectEpoch.current) return
      if (error instanceof ReportApiError && (error.status === 401 || error.status === 403 || error.status === 404)) handleProtectedFailure(error)
      else if (error instanceof ReportApiError && error.status === 410) setSubmissionMessage('This draft has expired and can no longer be submitted.')
      else if (error instanceof ReportApiError && error.status === 409 && error.code === 'STALE_VERSION') {
        setSubmissionMessage('This draft changed. Reload it before submitting so you can review the latest saved fields.')
        try {
          const current = await fetchDraft(requestedProjectId, activeDraft.id, requestSignal)
          if (requestedEpoch === projectEpoch.current) {
            setDraftRecovery(current)
            setActiveDraft(current)
          }
        } catch (refreshError) {
          if (refreshError instanceof ReportApiError && (refreshError.status === 401 || refreshError.status === 403 || refreshError.status === 404)) handleProtectedFailure(refreshError)
        }
      } else if (error instanceof ReportApiError && error.status >= 400 && error.status < 500 && !(error.status === 409 && error.code === 'DRAFT_ALREADY_SUBMITTED')) setSubmissionMessage('The draft was not submitted. Your fields are still here.')
      else {
        setSubmissionMessage('The submission outcome is unconfirmed. Checking the saved draft before offering another action…')
        try {
          const recoverySignal = AbortSignal.timeout(20_000)
          const current = await fetchDraft(requestedProjectId, activeDraft.id, recoverySignal)
          if (requestedEpoch !== projectEpoch.current) return
          if (current.submittedReportId) {
            const savedReport = await fetchReport(requestedProjectId, current.submittedReportId, recoverySignal)
            if (requestedEpoch !== projectEpoch.current) return
            setDetail(savedReport)
            setSelectedReportId(savedReport.id)
            setDetailLoading(false)
            submittedDetailId.current = savedReport.id
            setListNeedsRefresh(true)
            setView('detail')
            setActiveDraft(null)
            void loadDrafts()
            return
          }
          setDraftRecovery(current)
          setActiveDraft(current)
          setSubmissionMessage('The current server draft is not submitted. Review its saved values before trying again.')
        } catch (refreshError) {
          if (refreshError instanceof ReportApiError && (refreshError.status === 401 || refreshError.status === 403 || refreshError.status === 404)) handleProtectedFailure(refreshError)
          else setSubmissionMessage('The submission outcome is unconfirmed. The current draft could not be checked; retry loading it before continuing.')
        }
      }
    } finally {
      if (requestedEpoch === projectEpoch.current) setDraftBusy(false)
      if (activeMutationController.current === controller) activeMutationController.current = null
    }
  }, [activeDraft, composerDirty, context, draftBusy, draftReadRequired, handleProtectedFailure, loadDrafts, projectId, submitBusy, values])

  const saveReportEdits = useCallback(async (aiValues?: ReportValues) => {
    if (!editorReportId || editorReportVersion === null || !context || !projectId || submitBusy || draftBusy || editReviewRequired) return
    const errors = validateReport(aiValues ?? values)
    setFieldErrors(errors)
    const invalid = reportFields.find(field => errors[field]) ?? null
    if (invalid) {
      setValidationSummary('Review the highlighted fields before saving changes.')
      requestAnimationFrame(() => document.getElementById(`report-${invalid}`)?.focus())
      return
    }
    setValidationSummary(null)
    const canonicalValues = Object.fromEntries(reportFields.map(field => [field, (aiValues ?? values)[field]])) as ReportValues
    const body = JSON.stringify({ ...canonicalValues, expectedVersion: editorReportVersion })
    const requestedProjectId = projectId
    const requestedEpoch = projectEpoch.current
    const expectedVersion = editorReportVersion
    const controller = new AbortController()
    activeMutationController.current?.abort()
    activeMutationController.current = controller
    const requestSignal = AbortSignal.any([controller.signal, AbortSignal.timeout(20_000)])
    setDraftBusy(true)
    setSubmissionMessage('Saving report changes…')
    try {
      const saved = await updateReport(requestedProjectId, editorReportId, body, requestSignal)
      if (controller.signal.aborted || requestedEpoch !== projectEpoch.current) return
      if (saved.version !== expectedVersion + 1 || !reportFields.every(field => saved[field] === canonicalValues[field])) throw new ReportApiError(502, 'INVALID_RESPONSE')
      setValues(Object.fromEntries(reportFields.map(field => [field, saved[field]])) as ReportValues)
      setEditorReportVersion(saved.version)
      setDetail(saved)
      setSelectedReportId(saved.id)
      setListNeedsRefresh(true)
      setView('detail')
      setEditorBaseValues(Object.fromEntries(reportFields.map(field => [field, saved[field]])) as ReportValues)
      setSubmissionMessage(null)
      setEditRecovery(null)
      setEditRecoveryMessage(null)
      setEditReviewRequired(false)
    } catch (error) {
      if (controller.signal.aborted || requestedEpoch !== projectEpoch.current) return
      if (error instanceof ReportApiError && (error.status === 401 || error.status === 403 || error.status === 404)) {
        handleProtectedFailure(error)
        return
      }
      if (error instanceof ReportApiError && error.status >= 400 && error.status < 500 && error.status !== 409) {
        setSubmissionMessage(error.status === 409 && error.code === 'INVALID_STATE' ? 'This report is no longer open for editing. Your text is still here.' : error.status === 400 ? 'The server rejected these changes. Review the fields and try again.' : 'The report was not updated. Your text is still here.')
        return
      }
      try {
        const latest = await fetchReport(requestedProjectId, editorReportId, AbortSignal.timeout(20_000))
        if (requestedEpoch !== projectEpoch.current) return
        const latestValues = Object.fromEntries(reportFields.map(field => [field, latest[field]])) as ReportValues
        if (latest.version > expectedVersion && reportFields.every(field => latestValues[field] === canonicalValues[field])) {
          setDetail(latest)
          setSelectedReportId(latest.id)
          setListNeedsRefresh(true)
          setView('detail')
          setSubmissionMessage(null)
          setEditRecovery(null)
          setEditReviewRequired(false)
          return
        }
        setEditRecovery(latest)
        setDetail(latest)
        setEditRecoveryMessage(error instanceof ReportApiError && error.status === 409
          ? 'Another save created a newer version. Compare it with your edits below before choosing how to continue.'
          : 'The save outcome was uncertain. The current server version is shown below; compare it before retrying.')
        setEditReviewRequired(true)
        setSubmissionMessage(reportFields.every(field => latestValues[field] === canonicalValues[field]) && latest.version > expectedVersion
          ? 'The current server representation confirms these changes were saved.'
          : 'Your unsaved edits are still here. Review the current version before saving again.')
      } catch (refreshError) {
        if (refreshError instanceof ReportApiError && (refreshError.status === 401 || refreshError.status === 403 || refreshError.status === 404)) handleProtectedFailure(refreshError)
        else {
          setEditRecoveryMessage('Could not load the current server version. Your local edits are preserved; retry the version check before saving again.')
          setEditReviewRequired(true)
          setSubmissionMessage('Your save is unconfirmed. No retry is available until the current version is checked.')
        }
      }
    } finally {
      if (requestedEpoch === projectEpoch.current) setDraftBusy(false)
      if (activeMutationController.current === controller) activeMutationController.current = null
    }
  }, [context, draftBusy, editReviewRequired, editorReportId, editorReportVersion, handleProtectedFailure, projectId, submitBusy, values])

  const deleteSavedDraft = useCallback(async (draft: DraftSummary) => {
    if (!projectId || (activeDraft?.id === draft.id && view === 'new' && !leaveComposer('Deleting this draft')) || !window.confirm(`Delete the saved draft “${draft.title || 'Untitled draft'}”? This cannot be undone.`)) return
    const requestedEpoch = projectEpoch.current
    try {
      await deleteDraft(projectId, draft.id, draft.version)
      if (requestedEpoch !== projectEpoch.current) return
      setDrafts(previous => previous.filter(item => item.id !== draft.id))
      if (activeDraft?.id === draft.id) { resetComposer(); setView('list') }
      setDraftError(null)
    } catch (error) {
      if (requestedEpoch !== projectEpoch.current) return
      if (error instanceof ReportApiError && (error.status === 401 || error.status === 403 || error.status === 404)) handleProtectedFailure(error)
      else if (error instanceof ReportApiError && error.status === 409) setDraftError('This draft changed since the list loaded. The list has been refreshed; review it before deleting again.')
      else if (error instanceof ReportApiError && error.status === 410) setDraftError('This draft has expired. Refreshing the saved draft list.')
      else setDraftError('The draft was not deleted. Try again.')
      if (error instanceof ReportApiError && (error.status === 409 || error.status === 410)) void loadDrafts()
    }
  }, [activeDraft, handleProtectedFailure, leaveComposer, loadDrafts, projectId, resetComposer, view])

  const refreshEditVersion = useCallback(async () => {
    if (!projectId || !editorReportId) return
    const controller = new AbortController()
    activeDetailController.current?.abort()
    activeDetailController.current = controller
    setEditRecoveryMessage('Loading the current report version…')
    try {
      const latest = await fetchReport(projectId, editorReportId, controller.signal)
      if (controller.signal.aborted) return
      setEditRecovery(latest)
      setDetail(latest)
      setEditRecoveryMessage('Compare the current saved values with your edits before choosing how to continue.')
      setSubmissionMessage('Your local edits are preserved. Review the current version below.')
    } catch (error) {
      if (controller.signal.aborted) return
      if (error instanceof ReportApiError && (error.status === 401 || error.status === 403 || error.status === 404)) handleProtectedFailure(error)
      else setEditRecoveryMessage('Could not load the current server version. Your local edits remain here; retry the version check.')
    }
  }, [editorReportId, handleProtectedFailure, projectId])

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
      <SidebarGroup title="Drafts">
        {drafts.map(draft => (
          <SidebarItem
            key={draft.id}
            label={draft.title || 'Untitled draft'}
            icon={<FileDocument />}
            selected={view === 'new' && editorKind === 'draft' && activeDraft?.id === draft.id}
            onSelect={() => draft.submittedReportId ? openReport(draft.submittedReportId, dismissOverlay) : void resumeDraft(draft.id)}
          />
        ))}
        {draftLoading && drafts.length === 0 && <p className="report-sidebar-note">Loading drafts…</p>}
        {draftHasLoaded && drafts.length === 0 && <p className="report-sidebar-note">No saved drafts</p>}
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

  function renderProjectExit() {
    if (!onChooseWorkspace) return renderProjectForm()
    return <Button color="secondary" variant="outline" onClick={requestProjectChange}>Choose another project</Button>
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
            {renderProjectExit()}
          </div>
        ) : contextUnavailable ? (
          <div className="report-notice" role="alert">
            <h1 id="report-gate-title">Project could not be opened</h1>
            <p>TraceDesk could not verify project access. Try again.</p>
          {projectId && <Button color="secondary" variant="outline" onClick={() => { setContextUnavailable(false); setContextLoading(true); setContextRetry(value => value + 1) }}>Retry</Button>}
            {renderProjectExit()}
          </div>
        ) : (
          <div className="report-notice">
            <h1 id="report-gate-title">Open a project</h1>
            <p>Open a project to view and submit your reports.</p>
            {renderProjectExit()}
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
          <Button color="secondary" variant="ghost" className="report-change-project" onClick={onChooseWorkspace ? requestProjectChange : () => { setProjectInput(projectId); setShowProjectForm(value => !value) }}>
            {showProjectForm ? 'Close project form' : 'Change project'}
          </Button>
        </div>
        {showProjectForm && <div className="report-project-switch">{renderProjectForm()}</div>}
        <section className="report-drafts" aria-labelledby="drafts-heading">
          <div className="report-section-heading">
            <div><h2 id="drafts-heading">Saved drafts</h2><p className="muted">Private to your account in this project. Drafts expire 30 days after creation.</p></div>
            <Button color="secondary" variant="outline" onClick={() => void loadDrafts()} disabled={draftLoading}>Refresh drafts</Button>
          </div>
          {draftError && <div className="report-notice" role="alert"><p>{draftError}</p></div>}
          {draftLoading && drafts.length === 0 && <p className="report-status-message" role="status">Loading drafts…</p>}
          {!draftLoading && draftHasLoaded && drafts.length === 0 && <p className="report-empty-inline">No saved drafts in this project.</p>}
          {drafts.length > 0 && <ul className="report-draft-list" aria-label="Saved drafts">
            {drafts.map(draft => (
              <li className="report-draft-row" key={draft.id}>
                <div className="report-draft-copy">
                  <strong>{draft.title || 'Untitled draft'}</strong>
                  <span>{draft.submittedReportId ? 'Submitted report' : 'Draft'} · Saved {new Date(draft.updatedAt).toLocaleString()} · Expires {new Date(draft.expiresAt).toLocaleDateString()}</span>
                </div>
                {draft.submittedReportId
                  ? <Button color="secondary" variant="outline" onClick={() => openReport(draft.submittedReportId!)}>View report</Button>
                  : <Button color="primary" variant="outline" onClick={() => void resumeDraft(draft.id)} disabled={draftLoading}>Resume</Button>}
                {!draft.submittedReportId && <Button color="secondary" variant="ghost" onClick={() => void deleteSavedDraft(draft)}>Delete</Button>}
              </li>
            ))}
          </ul>}
          {draftCursor && !draftLoading && <Button color="secondary" variant="outline" onClick={() => void loadDrafts(draftCursor, true)}>Load more drafts</Button>}
        </section>
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
          {detail.status === 'OPEN' && <Button color="primary" variant="outline" onClick={beginReportEdit}>Edit report</Button>}
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
        <ReportActivity key={`${projectId}:${detail.id}:${detail.version}`} projectId={projectId} reportId={detail.id} onProtectedFailure={handleProtectedFailure} onRefresh={retryDetail} />
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
            <h1 id="new-report-heading">{editorKind === 'report' ? 'Edit report' : editorKind === 'draft' ? 'Resume draft' : 'New report'}</h1>
            <p className="muted">{editorKind === 'report' ? `Editing report version ${editorReportVersion}. Only reports that are still open can be changed.` : editorKind === 'draft' ? `Saved draft · version ${activeDraft?.version ?? '—'} · expires ${activeDraft ? new Date(activeDraft.expiresAt).toLocaleDateString() : '—'}. Drafts are saved only when you choose Save draft.` : 'Share the details your team needs to reproduce and investigate the issue.'}</p>
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
                disabled: submitBusy || draftBusy || composerFrozen,
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
          {editorKind === 'new' || composerDirty ? <p className="muted">Save your report as a draft or save your latest edits before using AI review.</p> : !submitBusy && !draftBusy && !composerFrozen && !editReviewRequired && !draftRecovery && !draftReadRequired && (
            (editorKind === 'draft' && activeDraft && !activeDraft.submittedReportId && Date.parse(activeDraft.expiresAt) > editorClock) ||
            (editorKind === 'report' && editorReportId && editorReportVersion && detail?.status === 'OPEN')
          ) ? <ReporterAiPanel
            key={`${projectId}:${editorKind}:${editorKind === 'draft' ? activeDraft!.id : editorReportId}:${editorKind === 'draft' ? activeDraft!.version : editorReportVersion}`}
            projectId={projectId}
            source={editorKind === 'draft' ? { kind: 'DRAFT', id: activeDraft!.id, version: activeDraft!.version } : { kind: 'REPORT', id: editorReportId!, version: editorReportVersion! }}
            values={values}
            onProtectedFailure={handleProtectedFailure}
            onApply={async (nextValues, expectedVersion) => {
              if (composerDirty || draftBusy || submitBusy || editReviewRequired || draftRecovery || draftReadRequired) return
              if (editorKind === 'draft' && activeDraft?.version === expectedVersion) await persistDraft(nextValues)
              else if (editorKind === 'report' && editorReportVersion === expectedVersion) await saveReportEdits(nextValues)
            }}
          /> : null}
          {validationSummary && <p className="report-validation-summary" role="alert">{validationSummary}</p>}
          {draftReadRequired && activeDraft && <div className="report-notice" role="status">
            <p>The save outcome is unconfirmed. Check the current saved draft before saving or applying suggestions again.</p>
            <Button type="button" color="secondary" variant="outline" onClick={() => {
              const epoch = projectEpoch.current
              const controller = new AbortController()
              activeMutationController.current?.abort()
              activeMutationController.current = controller
              void fetchDraft(projectId, activeDraft.id, AbortSignal.any([controller.signal, AbortSignal.timeout(20_000)])).then(current => {
                if (controller.signal.aborted || epoch !== projectEpoch.current) return
                setDraftRecovery(current); setActiveDraft(current); setDraftReadRequired(false)
              }).catch(error => { if (!controller.signal.aborted && epoch === projectEpoch.current) handleProtectedFailure(error) })
            }}>Check current draft version</Button>
          </div>}
          {draftRecovery && editorKind === 'draft' && <section className="report-version-review" aria-labelledby="draft-version-review-title">
            <h2 id="draft-version-review-title">Current saved draft · version {draftRecovery.version}</h2>
            <p>The values below are the current server copy. Your local edits remain in the form above.</p>
            <dl>{reportFields.map(field => <div key={field}><dt>{reportFieldRules[field].label}</dt><dd>{draftRecovery[field] || <span className="muted">Empty</span>}</dd></div>)}</dl>
            <div className="report-form-actions">
              <Button color="secondary" variant="outline" type="button" onClick={() => { const latest = Object.fromEntries(reportFields.map(field => [field, draftRecovery[field]])) as ReportValues; setValues(latest); setEditorBaseValues(latest); setActiveDraft(draftRecovery); setDraftRecovery(null); setSubmissionMessage('Loaded the current saved draft.') }}>Discard edits and load current draft</Button>
              <Button color="primary" variant="outline" type="button" onClick={() => { const reviewed = draftRecovery; setActiveDraft(reviewed); setEditorBaseValues(Object.fromEntries(reportFields.map(field => [field, reviewed[field]])) as ReportValues); setDraftRecovery(null); setSubmissionMessage('Current draft reviewed. Save your preserved edits to create a new version.') }}>Keep my edits after review</Button>
            </div>
          </section>}
          {editReviewRequired && <section className="report-version-review" aria-labelledby="report-version-review-title">
            <h2 id="report-version-review-title">Review the current report</h2>
            {editRecoveryMessage && <p role="status">{editRecoveryMessage}</p>}
            {editRecovery ? <>
              <p>Server version {editRecovery.version} · status {statusLabel(editRecovery.status)}. Your local edits remain in the form above.</p>
              <dl>{reportFields.map(field => <div key={field}><dt>{reportFieldRules[field].label}</dt><dd>{editRecovery[field] || <span className="muted">Empty</span>}</dd></div>)}</dl>
              <div className="report-form-actions">
                <Button color="secondary" variant="outline" type="button" onClick={() => { const current = Object.fromEntries(reportFields.map(field => [field, editRecovery[field]])) as ReportValues; setValues(current); setEditorBaseValues(current); setEditorReportVersion(editRecovery.version); setEditReviewRequired(false); setSubmissionMessage('Loaded the current saved report version.') }}>Discard local edits and load current version</Button>
                <Button color="primary" variant="outline" type="button" disabled={editRecovery.status !== 'OPEN'} onClick={() => { setEditorReportVersion(editRecovery.version); setEditReviewRequired(false); setSubmissionMessage(`You reviewed server version ${editRecovery.version}. Saving will replace all seven fields with your current form values.`) }}>Keep my edits and use version {editRecovery.version}</Button>
              </div>
            </> : <Button color="secondary" variant="outline" type="button" onClick={() => void refreshEditVersion()}>Retry version check</Button>}
          </section>}
          {submissionMessage && <p className={`report-submission-message report-submission-${submissionState}`} role="status">{submissionMessage}</p>}
          {editorKind === 'draft' && composerDirty && <p className="muted">Save your latest draft changes before submitting it.</p>}
          <div className="report-form-actions">
            <Button color="secondary" variant="ghost" type="button" disabled={submitBusy || draftBusy} onClick={() => { if (leaveComposer(editorKind === 'report' ? 'Leaving report editing' : 'Returning to reports')) { const wasEdit = editorKind === 'report'; resetComposer(); setView(wasEdit ? 'detail' : 'list') } }}>Cancel</Button>
            {editorKind === 'new' && <Button color="secondary" variant="outline" type="button" disabled={submitBusy || draftBusy || (composerFrozen && !draftAttempt)} loading={draftBusy} onClick={() => void persistDraft()}>{draftAttempt ? 'Retry draft save' : 'Save draft'}</Button>}
            {editorKind === 'draft' && <>
              <Button color="secondary" variant="outline" type="button" disabled={submitBusy || draftBusy || composerFrozen || Boolean(draftRecovery) || draftReadRequired} loading={draftBusy} onClick={() => void persistDraft()}>Save draft</Button>
              <Button color="primary" type="button" disabled={submitBusy || draftBusy || Boolean(draftRecovery) || draftReadRequired || composerDirty} loading={draftBusy} onClick={() => void submitSavedDraft()}>Submit as report</Button>
            </>}
            {editorKind === 'report' && <Button color="primary" type="button" disabled={submitBusy || draftBusy || editReviewRequired || !composerDirty} loading={draftBusy} onClick={() => void saveReportEdits()}>Save changes</Button>}
            {editorKind === 'new' && <Button color="primary" type="submit" disabled={submitBusy || draftBusy || submissionState === 'conflict'} loading={submitBusy}>{submissionState === 'unconfirmed' ? 'Retry submission' : submitBusy ? 'Creating report…' : 'Create report'}</Button>}
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
      {area === 'reports' ? renderReports() : (
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
            <Button color="secondary" variant="outline" disabled={signingOut} onClick={handleSignOut}>{signingOut ? 'Signing out…' : 'Sign out'}</Button>
          </section>
        </section>
      )}
    </AppShell>
  )
}
