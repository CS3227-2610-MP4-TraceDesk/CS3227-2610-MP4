import { useCallback, useEffect, useRef, useState } from 'react'
import { Badge } from '@openai/apps-sdk-ui/components/Badge'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Bug, FileDocument } from '@openai/apps-sdk-ui/components/Icon'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { AppShell } from '../shell/AppShell'
import { SidebarGroup, SidebarItem } from '../shell/Sidebar'
import type { TrayItem } from '../shell/IconTray'
import { ReportApiError, isCanonicalUuid, type ReportDetail, type ReportStatus, type ReportSummary } from '../reports/reportApi'
import { ReportDetailLayout } from '../reports/ReportDetailLayout'
import { Disclosure } from '../shared/Disclosure'
import { DeveloperAiPanel } from './DeveloperAiPanel'
import {
  createNote,
  claimReport,
  deleteNote,
  DeveloperApiError,
  fetchDeveloperContext,
  fetchDeveloperReport,
  fetchDeveloperReports,
  fetchNotes,
  fetchResolutions,
  proposeResolution,
  releaseReport,
  startInvestigation,
  updateNote,
  type DeveloperContext,
  type InvestigationNote,
  type ProposedResolution,
  type ResolutionFields,
} from './developerApi'
import './developer-workspace.css'

type Destination = 'mine' | 'all'
type Area = 'bugs' | 'settings'
type ApiError = DeveloperApiError | ReportApiError

const statuses: { value: ReportStatus | ''; label: string }[] = [
  { value: '', label: 'All statuses' },
  { value: 'OPEN', label: 'Open' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'READY_FOR_QA', label: 'Ready for QA' },
  { value: 'CLOSED', label: 'Closed' },
]

const trayItems: readonly TrayItem[] = [{ id: 'bugs', label: 'Bugs', icon: <Bug /> }]

const emptyResolution: ResolutionFields = { rootCause: '', changeSummary: '', verificationSteps: '', fixReference: '' }
const resolutionTextFields: { field: Exclude<keyof ResolutionFields, 'fixReference'>; label: string }[] = [
  { field: 'rootCause', label: 'Root cause' },
  { field: 'changeSummary', label: 'Change summary' },
  { field: 'verificationSteps', label: 'Verification steps' },
]

function mergeResolutions(current: ProposedResolution[], incoming: ProposedResolution[]) {
  const byId = new Map([...current, ...incoming].map(resolution => [resolution.id, resolution]))
  return [...byId.values()].sort((left, right) => left.revision - right.revision)
}

function routeProject(): string {
  return new URLSearchParams(window.location.search).get('project') ?? ''
}

function apiError(error: unknown): ApiError | null {
  return error instanceof DeveloperApiError || error instanceof ReportApiError ? error : null
}

function statusLabel(status: ReportStatus) {
  return statuses.find(option => option.value === status)?.label ?? status
}

function statusColor(status: ReportStatus): 'info' | 'warning' | 'success' | 'secondary' {
  if (status === 'OPEN') return 'info'
  if (status === 'IN_PROGRESS') return 'warning'
  if (status === 'READY_FOR_QA') return 'success'
  return 'secondary'
}

function userLabel(id: string, sessionUserId: string) {
  return id === sessionUserId ? 'you' : id
}

export function DeveloperWorkspace({
  sessionUserId,
  theme,
  onThemeChange,
  onSessionExpired,
  onSignOut,
  signingOut,
  signOutError,
  onChooseWorkspace,
}: {
  sessionUserId: string
  theme: 'light' | 'dark'
  onThemeChange: (theme: 'light' | 'dark') => void
  onSessionExpired: () => void
  onSignOut: () => void
  signingOut: boolean
  signOutError: string | null
  onChooseWorkspace?: () => void
}) {
  const initialProject = routeProject()
  const [area, setArea] = useState<Area>('bugs')
  const [destination, setDestination] = useState<Destination>('mine')
  const [projectId, setProjectId] = useState(isCanonicalUuid(initialProject) ? initialProject : '')
  const [projectInput, setProjectInput] = useState(initialProject)
  const [projectError, setProjectError] = useState(initialProject && !isCanonicalUuid(initialProject) ? 'Enter a project ID in canonical UUID format.' : '')
  const [context, setContext] = useState<DeveloperContext | null>(null)
  const [contextState, setContextState] = useState<'idle' | 'loading' | 'denied' | 'unavailable'>(projectId ? 'loading' : 'idle')
  const [statusFilter, setStatusFilter] = useState<ReportStatus | ''>('')
  const [reports, setReports] = useState<ReportSummary[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [listState, setListState] = useState<'idle' | 'loading' | 'ready' | 'unavailable'>('idle')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<ReportDetail | null>(null)
  const [detailState, setDetailState] = useState<'idle' | 'loading' | 'ready' | 'denied' | 'unavailable'>('idle')
  const [notes, setNotes] = useState<InvestigationNote[]>([])
  const [notesNextCursor, setNotesNextCursor] = useState<string | null>(null)
  const [notesState, setNotesState] = useState<'idle' | 'loading' | 'ready' | 'denied' | 'unavailable'>('idle')
  const [noteDrafts, setNoteDrafts] = useState<Record<string, string>>({})
  const [noteAttempts, setNoteAttempts] = useState<Record<string, { body: string; key: string }>>({})
  const [editDrafts, setEditDrafts] = useState<Record<string, string>>({})
  const [editingNoteId, setEditingNoteId] = useState<string | null>(null)
  const [resolutions, setResolutions] = useState<ProposedResolution[]>([])
  const [resolutionsState, setResolutionsState] = useState<'idle' | 'loading' | 'ready' | 'unavailable'>('idle')
  const [resolutionDrafts, setResolutionDrafts] = useState<Record<string, ResolutionFields>>({})
  const [resolutionNeedsRefresh, setResolutionNeedsRefresh] = useState(false)
  const [investigationChanged, setInvestigationChanged] = useState(false)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [actionBusy, setActionBusy] = useState(false)
  const [releaseReasons, setReleaseReasons] = useState<Record<string, string>>({})
  const contextAbort = useRef<AbortController | null>(null)
  const listAbort = useRef<AbortController | null>(null)
  const detailAbort = useRef<AbortController | null>(null)
  const notesAbort = useRef<AbortController | null>(null)
  const resolutionsAbort = useRef<AbortController | null>(null)
  const revalidationAbort = useRef<AbortController | null>(null)
  const mutationAbort = useRef<AbortController | null>(null)
  const mutationScope = useRef(0)
  const detailRef = useRef<ReportDetail | null>(null)
  const notesRef = useRef<InvestigationNote[]>([])
  const projectInputRef = useRef<HTMLInputElement>(null)

  const draftKey = projectId && selectedId ? `${projectId}:${selectedId}` : ''
  const noteDraft = draftKey ? noteDrafts[draftKey] ?? '' : ''
  const assigned = Boolean(detail && detail.assignedDeveloperId === sessionUserId)
  const canWriteNotes = Boolean(assigned && detail && (detail.status === 'IN_PROGRESS' || detail.status === 'READY_FOR_QA'))
  const canPropose = Boolean(assigned && detail?.status === 'IN_PROGRESS' && notesState !== 'denied')
  const resolutionDraft = draftKey ? resolutionDrafts[draftKey] ?? emptyResolution : emptyResolution
  const releaseReason = draftKey ? releaseReasons[draftKey] ?? '' : ''

  useEffect(() => { detailRef.current = detail }, [detail])
  useEffect(() => { notesRef.current = notes }, [notes])

  const invalidateMutations = useCallback(() => {
    mutationScope.current += 1
    mutationAbort.current?.abort()
    mutationAbort.current = null
    setActionBusy(false)
  }, [])

  const clearReportState = useCallback(() => {
    listAbort.current?.abort()
    detailAbort.current?.abort()
    notesAbort.current?.abort()
    resolutionsAbort.current?.abort()
    revalidationAbort.current?.abort()
    invalidateMutations()
    setResolutions([])
    setResolutionsState('idle')
    setResolutionDrafts({})
    setResolutionNeedsRefresh(false)
    setInvestigationChanged(false)
    setReports([])
    setNextCursor(null)
    setListState('idle')
    setSelectedId(null)
    setDetail(null)
    setDetailState('idle')
    setNotes([])
    setNotesNextCursor(null)
    setNotesState('idle')
    setEditingNoteId(null)
    setNoteDrafts({})
    setNoteAttempts({})
    setEditDrafts({})
    setReleaseReasons({})
    setActionMessage(null)
    setActionBusy(false)
  }, [invalidateMutations])

  const handleAuthFailure = useCallback((error: unknown) => {
    const failure = apiError(error)
    if (failure?.status === 401) {
      onSessionExpired()
      return 'session' as const
    }
    if (failure?.status === 403 || failure?.status === 404) return 'denied' as const
    return 'unavailable' as const
  }, [onSessionExpired])

  useEffect(() => () => {
    contextAbort.current?.abort()
    listAbort.current?.abort()
    detailAbort.current?.abort()
    notesAbort.current?.abort()
    resolutionsAbort.current?.abort()
    revalidationAbort.current?.abort()
    revalidationAbort.current?.abort()
    mutationAbort.current?.abort()
  }, [])

  const loadContext = useCallback(async () => {
    if (!projectId) return
    contextAbort.current?.abort()
    const controller = new AbortController()
    contextAbort.current = controller
    setContextState('loading')
    setContext(null)
    try {
      const result = await fetchDeveloperContext(projectId, controller.signal)
      if (controller.signal.aborted) return
      setContext(result)
      setContextState('idle')
    } catch (error) {
      if (controller.signal.aborted) return
      const outcome = handleAuthFailure(error)
      if (outcome !== 'session') setContextState(outcome)
    }
  }, [handleAuthFailure, projectId])

  useEffect(() => {
    let active = true
    queueMicrotask(() => { if (active) void loadContext() })
    return () => { active = false }
  }, [loadContext])

  const loadReports = useCallback(async (cursor: string | null = null, append = false) => {
    if (!context) return
    listAbort.current?.abort()
    const controller = new AbortController()
    listAbort.current = controller
    setListState('loading')
    try {
      const page = await fetchDeveloperReports(projectId, destination === 'mine' ? 'assigned-to-me' : 'all', statusFilter, cursor, controller.signal)
      if (controller.signal.aborted) return
      setReports(previous => append ? [...previous, ...page.items] : page.items)
      setNextCursor(page.nextCursor)
      setListState('ready')
    } catch (error) {
      if (controller.signal.aborted) return
      const outcome = handleAuthFailure(error)
      if (outcome === 'denied') {
        setContext(null)
        setContextState('denied')
        clearReportState()
      } else if (outcome === 'unavailable') setListState('unavailable')
    }
  }, [clearReportState, context, destination, handleAuthFailure, projectId, statusFilter])

  useEffect(() => {
    if (!context) return
    let active = true
    queueMicrotask(() => {
      if (!active) return
      setReports([])
      setNextCursor(null)
      setSelectedId(null)
      setDetail(null)
      setNotes([])
      setNotesNextCursor(null)
      setNoteDrafts({})
      setNoteAttempts({})
      setEditDrafts({})
      setResolutions([])
      setResolutionDrafts({})
      void loadReports()
    })
    return () => { active = false }
  }, [context, destination, loadReports, statusFilter])

  const loadResolutions = useCallback(async (reportId: string) => {
    resolutionsAbort.current?.abort()
    const controller = new AbortController()
    resolutionsAbort.current = controller
    setResolutions([])
    setResolutionsState('loading')
    try {
      const page = await fetchResolutions(projectId, reportId, controller.signal)
      if (controller.signal.aborted) return
      setResolutions(previous => mergeResolutions(previous, page.items))
      setResolutionsState('ready')
    } catch (error) {
      if (controller.signal.aborted) return
      if (handleAuthFailure(error) !== 'session') setResolutionsState('unavailable')
    }
  }, [handleAuthFailure, projectId])

  const loadDetail = useCallback(async (reportId: string) => {
    detailAbort.current?.abort()
    notesAbort.current?.abort()
    const controller = new AbortController()
    detailAbort.current = controller
    setDetailState('loading')
    setActionMessage(null)
    setResolutionNeedsRefresh(false)
    try {
      const result = await fetchDeveloperReport(projectId, reportId, controller.signal)
      if (controller.signal.aborted || reportId !== selectedId) return
      setDetail(result)
      setDetailState('ready')
      void loadResolutions(reportId)
      setNotes([])
      setNotesNextCursor(null)
      setEditingNoteId(null)
      if (result.assignedDeveloperId === sessionUserId) {
        const noteController = new AbortController()
        notesAbort.current = noteController
        setNotesState('loading')
        try {
          const page = await fetchNotes(projectId, reportId, null, noteController.signal)
          if (noteController.signal.aborted || reportId !== selectedId) return
          setNotes(page.items)
          setNotesNextCursor(page.nextCursor)
          setNotesState('ready')
        } catch (error) {
          if (noteController.signal.aborted) return
          const outcome = handleAuthFailure(error)
          if (outcome === 'denied') {
            setNotes([])
            setNotesState('denied')
            setActionMessage('Your assignment changed. Private notes and investigation actions are no longer available.')
            const refreshed = await fetchDeveloperReport(projectId, reportId).catch(() => null)
            if (refreshed) setDetail(refreshed)
          } else if (outcome === 'unavailable') setNotesState('unavailable')
        }
      } else setNotesState('idle')
    } catch (error) {
      if (controller.signal.aborted) return
      const outcome = handleAuthFailure(error)
      if (outcome !== 'session') setDetailState(outcome)
    }
  }, [handleAuthFailure, loadResolutions, projectId, selectedId, sessionUserId])

  const revalidateSelectedDetail = useCallback(async () => {
    const current = detailRef.current
    if (!current || current.id !== selectedId || current.assignedDeveloperId !== sessionUserId) return
    revalidationAbort.current?.abort()
    const controller = new AbortController()
    revalidationAbort.current = controller
    try {
      const refreshed = await fetchDeveloperReport(projectId, current.id, controller.signal)
      if (controller.signal.aborted || detailRef.current?.id !== current.id) return
      if (refreshed.assignedDeveloperId !== sessionUserId) {
        setNotesState('denied')
        setActionMessage('Your assignment changed. Private notes and investigation actions are no longer available.')
        return
      }
      const page = await fetchNotes(projectId, current.id, null, controller.signal)
      if (controller.signal.aborted || detailRef.current?.id !== current.id) return
      const reportChanged = refreshed.version !== current.version || refreshed.status !== current.status ||
        refreshed.assignedDeveloperId !== current.assignedDeveloperId
      const currentNotes = notesRef.current
      const notesChanged = currentNotes.length !== page.items.length || currentNotes.some((note, index) => {
        const fresh = page.items[index]
        return !fresh || fresh.id !== note.id || fresh.version !== note.version
      })
      if (reportChanged || notesChanged) setInvestigationChanged(true)
    } catch (error) {
      if (controller.signal.aborted) return
      const outcome = handleAuthFailure(error)
      if (outcome === 'denied') {
        setNotesState('denied')
        setActionMessage('Your access changed. Private notes and investigation actions are no longer available.')
      }
    }
  }, [handleAuthFailure, projectId, selectedId, sessionUserId])

  useEffect(() => {
    if (!selectedId) return
    let active = true
    queueMicrotask(() => { if (active) void loadDetail(selectedId) })
    return () => { active = false }
  }, [loadDetail, selectedId])

  useEffect(() => {
    if (!selectedId || detailState !== 'ready') return
    const revalidate = () => { void revalidateSelectedDetail() }
    window.addEventListener('focus', revalidate)
    return () => window.removeEventListener('focus', revalidate)
  }, [detailState, revalidateSelectedDetail, selectedId])

  function openProject(event: React.FormEvent) {
    event.preventDefault()
    const next = projectInput.trim()
    if (!isCanonicalUuid(next)) {
      setProjectError('Enter a project ID in canonical UUID format.')
      projectInputRef.current?.focus()
      return
    }
    contextAbort.current?.abort()
    clearReportState()
    setContext(null)
    setContextState('loading')
    setProjectError('')
    if (next === projectId) {
      void loadContext()
      return
    }
    setProjectId(next)
    const url = new URL(window.location.href)
    url.searchParams.set('workspace', 'developer')
    url.searchParams.set('project', next)
    window.history.replaceState(null, '', url)
  }

  async function loadMoreNotes() {
    if (!detail || !notesNextCursor || notesState === 'loading') return
    const controller = new AbortController()
    notesAbort.current = controller
    setNotesState('loading')
    try {
      const page = await fetchNotes(projectId, detail.id, notesNextCursor, controller.signal)
      if (controller.signal.aborted) return
      setNotes(previous => [...previous, ...page.items])
      setNotesNextCursor(page.nextCursor)
      setNotesState('ready')
    } catch (error) {
      if (controller.signal.aborted) return
      const outcome = handleAuthFailure(error)
      if (outcome === 'denied') await recoverAfterDenied('Your assignment changed. Private notes are no longer available.', () => !controller.signal.aborted)
      else if (outcome === 'unavailable') setNotesState('unavailable')
    }
  }

  function retryDetail() {
    if (selectedId) {
      setInvestigationChanged(false)
      void loadDetail(selectedId)
    }
  }

  const selectReport = useCallback((reportId: string | null) => {
    detailAbort.current?.abort()
    notesAbort.current?.abort()
    resolutionsAbort.current?.abort()
    invalidateMutations()
    setNoteDrafts({})
    setNoteAttempts({})
    setEditDrafts({})
    setResolutions([])
    setResolutionsState('idle')
    setResolutionDrafts({})
    setResolutionNeedsRefresh(false)
    setInvestigationChanged(false)
    setSelectedId(reportId)
  }, [invalidateMutations])

  const selectDestination = useCallback((next: Destination, dismiss: () => void) => {
    selectReport(null)
    setDestination(next)
    setArea('bugs')
    dismiss()
  }, [selectReport, setArea, setDestination])

  function beginMutation() {
    mutationAbort.current?.abort()
    const controller = new AbortController()
    mutationAbort.current = controller
    return { controller, scope: mutationScope.current }
  }

  function mutationIsCurrent(controller: AbortController, scope: number) {
    return !controller.signal.aborted && mutationAbort.current === controller && mutationScope.current === scope
  }

  function changeProject() {
    contextAbort.current?.abort()
    clearReportState()
    setContext(null)
    setContextState('idle')
    if (onChooseWorkspace) {
      onChooseWorkspace()
      return
    }
    setProjectId('')
    setProjectInput('')
    setProjectError('')
    const url = new URL(window.location.href)
    url.searchParams.delete('project')
    window.history.replaceState(null, '', url)
    queueMicrotask(() => projectInputRef.current?.focus())
  }

  async function recoverAfterDenied(message: string, stillCurrent: () => boolean = () => true) {
    if (!stillCurrent()) return
    setActionMessage(message)
    setNotes([])
    setNotesState('denied')
    if (!selectedId) return
    const refreshed = await fetchDeveloperReport(projectId, selectedId).catch(() => null)
    if (!stillCurrent()) return
    if (refreshed) setDetail(refreshed)
    await loadReports()
  }

  async function start() {
    if (!detail || !assigned || actionBusy) return
    setActionBusy(true)
    setActionMessage(null)
    const { controller, scope } = beginMutation()
    try {
      const result = await startInvestigation(projectId, detail.id, detail.version, controller.signal)
      if (!mutationIsCurrent(controller, scope)) return
      setDetail(result)
      setActionMessage('Investigation started after the server confirmed the change.')
      await loadReports()
    } catch (error) {
      if (!mutationIsCurrent(controller, scope)) return
      const failure = apiError(error)
      const outcome = handleAuthFailure(error)
      if (outcome === 'denied') await recoverAfterDenied('Your assignment changed. The investigation was not started.', () => mutationIsCurrent(controller, scope))
      else if (outcome === 'unavailable' || failure?.code === 'STALE_VERSION' || failure?.code === 'INVALID_STATE') {
        if (failure?.code === 'STALE_VERSION' || failure?.code === 'INVALID_STATE') setInvestigationChanged(true)
        setActionMessage('The investigation could not be confirmed. Refresh this report before trying again.')
      }
    } finally {
      if (mutationIsCurrent(controller, scope)) {
        mutationAbort.current = null
        setActionBusy(false)
      }
    }
  }

  async function claim() {
    if (!detail || detail.status !== 'OPEN' || detail.assignedDeveloperId || actionBusy) return
    setActionBusy(true); setActionMessage(null)
    const { controller, scope } = beginMutation()
    try {
      const result = await claimReport(projectId, detail.id, detail.version, sessionUserId, controller.signal)
      if (!mutationIsCurrent(controller, scope)) return
      setDetail(current => current?.id === detail.id ? { ...current, ...result } : current)
      setNotesState('ready')
      setActionMessage('You claimed this bug after the server confirmed the change.')
      await loadReports()
    } catch (error) {
      if (!mutationIsCurrent(controller, scope)) return
      const failure = apiError(error); const outcome = handleAuthFailure(error)
      if (outcome === 'denied') await recoverAfterDenied('This bug could not be claimed.', () => mutationIsCurrent(controller, scope))
      else if (outcome === 'unavailable' || failure?.code === 'STALE_VERSION' || failure?.code === 'INVALID_STATE') {
        setInvestigationChanged(true); setActionMessage('This claim could not be confirmed. Refresh the report before trying again.')
      }
    } finally { if (mutationIsCurrent(controller, scope)) { mutationAbort.current = null; setActionBusy(false) } }
  }

  async function release() {
    if (!detail || !assigned || actionBusy) return
    const reason = releaseReason
    if (!reason.trim() || Array.from(reason).length > 500 || new TextEncoder().encode(reason).length > 2000) {
      setActionMessage('Enter a release reason of at most 500 characters and 2,000 bytes.'); return
    }
    setActionBusy(true); setActionMessage(null)
    const { controller, scope } = beginMutation()
    try {
      const result = await releaseReport(projectId, detail.id, detail.version, reason, controller.signal)
      if (!mutationIsCurrent(controller, scope)) return
      setDetail(current => current?.id === detail.id ? { ...current, ...result } : current)
      setNotes([]); setNotesState('denied')
      setReleaseReasons(current => ({ ...current, [draftKey]: '' }))
      setActionMessage('You released this bug back to the queue.')
      await loadReports()
    } catch (error) {
      if (!mutationIsCurrent(controller, scope)) return
      const failure = apiError(error); const outcome = handleAuthFailure(error)
      if (outcome === 'denied') await recoverAfterDenied('Your assignment changed. The bug was not released.', () => mutationIsCurrent(controller, scope))
      else if (outcome === 'unavailable' || failure?.code === 'STALE_VERSION' || failure?.code === 'INVALID_STATE') {
        setInvestigationChanged(true); setActionMessage('The release could not be confirmed. Your reason is preserved; refresh before trying again.')
      } else if (failure?.code === 'VALIDATION_ERROR') setActionMessage('Check the release reason and try again.')
    } finally { if (mutationIsCurrent(controller, scope)) { mutationAbort.current = null; setActionBusy(false) } }
  }

  async function addNote() {
    if (!detail || !draftKey || !canWriteNotes || actionBusy) return
    const body = noteDraft
    if (!body.trim() || Array.from(body).length > 8_000) {
      setActionMessage('Enter a note between 1 and 8,000 characters.')
      return
    }
    setActionBusy(true)
    setActionMessage(null)
    const { controller, scope } = beginMutation()
    try {
      const priorAttempt = noteAttempts[draftKey]
      const attempt = priorAttempt?.body === body ? priorAttempt : { body, key: crypto.randomUUID() }
      setNoteAttempts(previous => ({ ...previous, [draftKey]: attempt }))
      const created = await createNote(projectId, detail.id, body, attempt.key, controller.signal)
      if (!mutationIsCurrent(controller, scope)) return
      setNotes(previous => [...previous, created])
      setNoteDrafts(previous => ({ ...previous, [draftKey]: '' }))
      setNoteAttempts(previous => { const next = { ...previous }; delete next[draftKey]; return next })
      setActionMessage('Private note saved after server confirmation.')
    } catch (error) {
      if (!mutationIsCurrent(controller, scope)) return
      const failure = apiError(error)
      const outcome = handleAuthFailure(error)
      if (outcome === 'denied') await recoverAfterDenied('Your assignment changed. Your unsaved note remains in the composer.', () => mutationIsCurrent(controller, scope))
      else if (outcome === 'unavailable' || failure?.code === 'INVALID_STATE') setActionMessage('The note was not confirmed. Your text is preserved; refresh and try again.')
      else setActionMessage('The note could not be saved. Your text is preserved.')
    } finally {
      if (mutationIsCurrent(controller, scope)) {
        mutationAbort.current = null
        setActionBusy(false)
      }
    }
  }

  async function saveAiDraft(body: string): Promise<boolean> {
    if (!detail || !draftKey || !canWriteNotes || actionBusy || !body.trim() || Array.from(body).length > 8_000) return false
    setActionBusy(true)
    setActionMessage(null)
    const { controller, scope } = beginMutation()
    const attemptKey = `${draftKey}:ai`
    try {
      const priorAttempt = noteAttempts[attemptKey]
      const attempt = priorAttempt?.body === body ? priorAttempt : { body, key: crypto.randomUUID() }
      setNoteAttempts(previous => ({ ...previous, [attemptKey]: attempt }))
      const created = await createNote(projectId, detail.id, body, attempt.key, controller.signal)
      if (!mutationIsCurrent(controller, scope)) return false
      setNotes(previous => [...previous, created])
      setNoteAttempts(previous => { const next = { ...previous }; delete next[attemptKey]; return next })
      setActionMessage('Private note saved after server confirmation.')
      return true
    } catch (error) {
      if (!mutationIsCurrent(controller, scope)) return false
      const failure = apiError(error)
      const outcome = handleAuthFailure(error)
      if (outcome === 'denied') await recoverAfterDenied('Your assignment changed. Your AI-assisted draft remains only in this page until you leave it.', () => mutationIsCurrent(controller, scope))
      else if (outcome === 'unavailable' || failure?.code === 'INVALID_STATE') setActionMessage('The note was not confirmed. Your draft is preserved; refresh and try again.')
      else setActionMessage('The note could not be saved. Your draft is preserved.')
      return false
    } finally {
      if (mutationIsCurrent(controller, scope)) {
        mutationAbort.current = null
        setActionBusy(false)
      }
    }
  }

  function handleAiProtectedFailure(error: DeveloperApiError) {
    const outcome = handleAuthFailure(error)
    if (outcome === 'denied') void recoverAfterDenied('Your access changed. Private notes and investigation actions are no longer available.')
  }

  async function saveNote(note: InvestigationNote) {
    const body = editDrafts[note.id] ?? note.body
    if (!body.trim() || Array.from(body).length > 8_000) {
      setActionMessage('Enter a note between 1 and 8,000 characters.')
      return
    }
    setActionBusy(true)
    setActionMessage(null)
    const { controller, scope } = beginMutation()
    try {
      const saved = await updateNote(projectId, note.reportId, note.id, body, note.version, controller.signal)
      if (!mutationIsCurrent(controller, scope)) return
      setNotes(previous => previous.map(item => item.id === saved.id ? saved : item))
      setEditingNoteId(null)
      setEditDrafts(previous => { const next = { ...previous }; delete next[note.id]; return next })
      setActionMessage('Private note updated after server confirmation.')
    } catch (error) {
      if (!mutationIsCurrent(controller, scope)) return
      const failure = apiError(error)
      const outcome = handleAuthFailure(error)
      if (outcome === 'denied') await recoverAfterDenied('Your assignment changed. Your edited text remains only in this page until you leave it.', () => mutationIsCurrent(controller, scope))
      else if (failure?.code === 'STALE_VERSION') {
        setInvestigationChanged(true)
        setActionMessage('This note changed on the server. Your edited text is preserved; refresh before saving again.')
      }
      else setActionMessage('The update was not confirmed. Your edited text is preserved.')
    } finally {
      if (mutationIsCurrent(controller, scope)) {
        mutationAbort.current = null
        setActionBusy(false)
      }
    }
  }

  async function removeNote(note: InvestigationNote) {
    if (!window.confirm('Delete this private note? This cannot be undone.')) return
    setActionBusy(true)
    setActionMessage(null)
    const { controller, scope } = beginMutation()
    try {
      await deleteNote(projectId, note.reportId, note.id, note.version, controller.signal)
      if (!mutationIsCurrent(controller, scope)) return
      setNotes(previous => previous.filter(item => item.id !== note.id))
      setActionMessage('Private note deleted after server confirmation.')
    } catch (error) {
      if (!mutationIsCurrent(controller, scope)) return
      const failure = apiError(error)
      const outcome = handleAuthFailure(error)
      if (outcome === 'denied') await recoverAfterDenied('Your assignment changed. The note was not deleted.', () => mutationIsCurrent(controller, scope))
      else if (failure?.code === 'STALE_VERSION') {
        setInvestigationChanged(true)
        setActionMessage('This note changed on the server. Refresh before deleting it.')
      }
      else setActionMessage('The delete could not be confirmed. Refresh before trying again.')
    } finally {
      if (mutationIsCurrent(controller, scope)) {
        mutationAbort.current = null
        setActionBusy(false)
      }
    }
  }

  async function submitResolution() {
    if (!detail || !draftKey || !canPropose || actionBusy) return
    const fields = resolutionDraft
    const textValid = resolutionTextFields.every(({ field }) => fields[field].trim() && Array.from(fields[field]).length <= 4_000)
    if (!textValid || Array.from(fields.fixReference).length > 200) {
      setActionMessage('Complete root cause, change summary and verification steps (up to 4,000 characters each). The fix reference can be up to 200 characters.')
      return
    }
    setActionBusy(true)
    setActionMessage(null)
    setResolutionNeedsRefresh(false)
    const { controller, scope } = beginMutation()
    try {
      const result = await proposeResolution(projectId, detail.id, fields, detail.version, controller.signal)
      if (!mutationIsCurrent(controller, scope)) return
      setDetail(result.report)
      setResolutions(previous => mergeResolutions(previous, [result.resolution]))
      setResolutionDrafts(previous => { const next = { ...previous }; delete next[draftKey]; return next })
      setActionMessage('Proposed resolution sent to QA after server confirmation.')
      await loadReports()
    } catch (error) {
      if (!mutationIsCurrent(controller, scope)) return
      const failure = apiError(error)
      const outcome = handleAuthFailure(error)
      if (outcome === 'denied') await recoverAfterDenied('Your assignment changed. The proposed resolution was not sent to QA.', () => mutationIsCurrent(controller, scope))
      else if (failure?.code === 'VALIDATION_ERROR') setActionMessage('The server rejected these fields. Your text is preserved; check it and try again.')
      else if (failure?.code === 'RESOLUTION_LIMIT') setActionMessage('This bug has reached its limit of 50 proposed resolutions. Your text is preserved.')
      else if (outcome === 'unavailable') {
        setResolutionNeedsRefresh(true)
        if (failure?.code === 'STALE_VERSION' || failure?.code === 'INVALID_STATE') setInvestigationChanged(true)
        setActionMessage('The proposed resolution was not confirmed. Your text is preserved; refresh this bug before sending it again.')
      }
    } finally {
      if (mutationIsCurrent(controller, scope)) {
        mutationAbort.current = null
        setActionBusy(false)
      }
    }
  }

  const sidebar = {
    title: 'Developer',
    renderItems: (dismiss: () => void) => <SidebarGroup title="Investigation">
      <SidebarItem label="My work" icon={<FileDocument />} selected={destination === 'mine'} onSelect={() => selectDestination('mine', dismiss)} />
      <SidebarItem label="All bugs" icon={<Bug />} selected={destination === 'all'} onSelect={() => selectDestination('all', dismiss)} />
    </SidebarGroup>,
  }

  function renderProjectGate() {
    const denied = contextState === 'denied'
    const unavailable = contextState === 'unavailable'
    return <div className="developer-gate"><section className="developer-notice" role={denied ? 'alert' : undefined}>
      <h1>{denied ? 'Project unavailable' : unavailable ? 'Developer workspace unavailable' : onChooseWorkspace ? 'Opening project' : 'Open a project'}</h1>
      <p>{denied ? 'This project is unavailable to your Developer account.' : unavailable ? onChooseWorkspace ? 'TraceDesk could not verify this Developer workspace. Retry or choose another project.' : 'TraceDesk could not verify this Developer workspace. Retry without changing your project ID.' : onChooseWorkspace ? 'Checking your Developer access…' : 'Enter the project ID supplied by your team. The server verifies your membership and Developer role.'}</p>
      {onChooseWorkspace ? <form className="developer-project-form" onSubmit={openProject}>
        <div className="developer-project-row">
          {unavailable && <Button color="primary" type="submit">Retry</Button>}
          <Button color="secondary" variant="outline" type="button" onClick={changeProject}>Choose another project</Button>
        </div>
      </form> : <form className="developer-project-form" onSubmit={openProject}>
        <label htmlFor="developer-project">Project ID</label>
        <div className="developer-project-row">
          <Input ref={projectInputRef} id="developer-project" value={projectInput} onChange={event => setProjectInput(event.target.value)} invalid={Boolean(projectError)} aria-describedby={projectError ? 'developer-project-error' : undefined} />
          <Button color="primary" type="submit" loading={contextState === 'loading'}>{unavailable ? 'Retry' : 'Open project'}</Button>
        </div>
        {projectError && <p id="developer-project-error" className="developer-error">{projectError}</p>}
      </form>}
    </section></div>
  }

  function renderList() {
    return <section className="developer-library" aria-labelledby="developer-list-title">
      <div className="developer-page-heading"><div><h1 id="developer-list-title">{destination === 'mine' ? 'My work' : 'All bugs'}</h1><p className="muted">{destination === 'mine' ? 'Reports currently assigned to you.' : 'Shared reports in this project.'}</p></div><Button color="secondary" variant="ghost" onClick={changeProject}>Change project</Button></div>
      <div className="developer-list-controls"><label htmlFor="developer-status">Filter by status</label><select id="developer-status" value={statusFilter} onChange={event => setStatusFilter(event.target.value as ReportStatus | '')}>{statuses.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</select></div>
      {listState === 'loading' && reports.length === 0 && <p className="developer-status" role="status">Loading bugs…</p>}
      {listState === 'unavailable' && <div className="developer-inline-notice" role="alert"><p>Bugs are temporarily unavailable. Your current selection has not been replaced.</p><Button color="secondary" variant="outline" onClick={() => void loadReports()}>Retry</Button></div>}
      {listState === 'ready' && reports.length === 0 && <div className="developer-empty"><h2>{statusFilter ? 'No bugs match this status' : destination === 'mine' ? 'No assigned bugs' : 'No bugs in this project'}</h2><p>{destination === 'mine' ? 'Bugs assigned to you will appear here.' : 'Try another status or return later.'}</p></div>}
      {reports.length > 0 && <div className="developer-list" aria-label={destination === 'mine' ? 'My assigned bugs' : 'All project bugs'}>{reports.map(report => <button className="developer-row" type="button" key={report.id} onClick={() => selectReport(report.id)}><span className="developer-row-title">{report.title}</span><Badge color={statusColor(report.status)}>{statusLabel(report.status)}</Badge><span className="developer-assignment">{report.assignedDeveloperId === sessionUserId ? 'Assigned to you' : report.assignedDeveloperId ? 'Assigned' : 'Unassigned'}</span></button>)}</div>}
      {nextCursor && listState !== 'loading' && <Button color="secondary" variant="outline" onClick={() => void loadReports(nextCursor, true)}>Load more</Button>}
    </section>
  }

  function renderNotes() {
    if (!assigned) return <section className="developer-notes" aria-labelledby="private-notes-title"><div className="developer-section-heading"><div><h2 id="private-notes-title">Private notes</h2><p>Visible only to the current assigned Developer.</p></div></div><p className="muted">Private investigation notes are available only to the current assignee.</p></section>
    if (notesState === 'denied') return <section className="developer-notes" role="alert" aria-labelledby="private-notes-title"><div className="developer-section-heading"><div><h2 id="private-notes-title">Private notes unavailable</h2><p>Visible only to the current assigned Developer.</p></div></div><p className="muted">Your assignment changed. Private notes and investigation actions are no longer available.</p></section>
    return <section className="developer-notes" aria-labelledby="private-notes-title"><div className="developer-section-heading"><div><h2 id="private-notes-title">Private notes</h2><p>Visible only to the current assigned Developer.</p></div></div>
      {notesState === 'loading' && <p role="status">Loading private notes…</p>}
      {notesState === 'unavailable' && <div className="developer-inline-notice" role="alert"><p>Private notes are temporarily unavailable.</p><Button color="secondary" variant="outline" onClick={() => selectedId && void loadDetail(selectedId)}>Retry</Button></div>}
      {notesState === 'ready' && notes.length === 0 && <p className="muted">No private notes yet.</p>}
      <ol className="developer-note-list">{notes.map(note => <li key={note.id} className="developer-note">
        <p className="developer-note-byline">Created by {userLabel(note.authorUserId, sessionUserId)}{note.lastEditedByUserId ? ` · edited by ${userLabel(note.lastEditedByUserId, sessionUserId)}` : ''}</p>
        {editingNoteId === note.id ? <><label htmlFor={`edit-note-${note.id}`}>Edit private note</label><Textarea id={`edit-note-${note.id}`} rows={4} value={editDrafts[note.id] ?? note.body} disabled={actionBusy} onChange={event => setEditDrafts(previous => ({ ...previous, [note.id]: event.target.value }))} /><div className="developer-note-actions"><Button color="secondary" variant="ghost" disabled={actionBusy} onClick={() => setEditingNoteId(null)}>Cancel</Button><Button color="primary" disabled={actionBusy} onClick={() => void saveNote(note)}>Save note</Button></div></> : <><p className="developer-note-body">{note.body}</p>{canWriteNotes && <div className="developer-note-actions"><Button color="secondary" variant="ghost" disabled={actionBusy} onClick={() => { setEditingNoteId(note.id); setEditDrafts(previous => ({ ...previous, [note.id]: note.body })) }}>Edit</Button><Button color="secondary" variant="outline" disabled={actionBusy} onClick={() => void removeNote(note)}>Delete</Button></div>}</>}
      </li>)}</ol>
      {notesNextCursor && notesState !== 'loading' && <Button color="secondary" variant="outline" onClick={() => void loadMoreNotes()}>Load more notes</Button>}
      {canWriteNotes && <div className="developer-note-composer"><label htmlFor="developer-note-draft">Add a private note</label><Textarea id="developer-note-draft" rows={5} value={noteDraft} disabled={actionBusy} onChange={event => setNoteDrafts(previous => ({ ...previous, [draftKey]: event.target.value }))} /><div className="developer-composer-foot"><span>{Array.from(noteDraft).length.toLocaleString()} / 8,000</span><Button color="primary" disabled={actionBusy || !noteDraft.trim()} loading={actionBusy} onClick={() => void addNote()}>Save note</Button></div></div>}
    </section>
  }

  function renderResolutionRevision(resolution: ProposedResolution) {
    return <div className="developer-note">
      <p className="developer-note-byline">Revision {resolution.revision} · Proposed by {userLabel(resolution.authorUserId, sessionUserId)}</p>
      <dl className="developer-resolution-fields">
        {resolutionTextFields.map(({ field, label }) => <div key={field}><dt>{label}</dt><dd>{resolution[field]}</dd></div>)}
        {resolution.fixReference && <div><dt>Fix reference</dt><dd>{resolution.fixReference}</dd></div>}
        {resolution.rejection && <div><dt>QA send-back reason</dt><dd>{resolution.rejection.reason}</dd></div>}
      </dl>
    </div>
  }

  function updateResolutionDraft(field: keyof ResolutionFields, value: string) {
    setResolutionDrafts(previous => ({ ...previous, [draftKey]: { ...(previous[draftKey] ?? emptyResolution), [field]: value } }))
  }

  function renderResolutions() {
    const current = resolutions[resolutions.length - 1]
    const earlier = resolutions.slice(0, -1).reverse()
    return <section className="developer-notes" aria-labelledby="proposed-resolution-title"><div className="developer-section-heading"><div><h2 id="proposed-resolution-title">Proposed resolution</h2><p>Visible to Developers and QA reviewers in this project.</p></div></div>
      {resolutionsState === 'loading' && <p role="status">Loading proposed resolutions…</p>}
      {resolutionsState === 'unavailable' && <div className="developer-inline-notice" role="alert"><p>Proposed resolutions are temporarily unavailable.</p><Button color="secondary" variant="outline" onClick={() => selectedId && void loadResolutions(selectedId)}>Retry</Button></div>}
      {resolutionsState === 'ready' && !current && <p className="muted">No resolution has been proposed yet.</p>}
      {current && renderResolutionRevision(current)}
      {earlier.length > 0 && <Disclosure label={`Earlier revisions (${earlier.length})`}><ol className="developer-note-list">{earlier.map(resolution => <li key={resolution.id}>{renderResolutionRevision(resolution)}</li>)}</ol></Disclosure>}
      {canPropose && <form className="developer-resolution-form" onSubmit={event => { event.preventDefault(); void submitResolution() }}>
        {resolutionTextFields.map(({ field, label }) => <div key={field} className="developer-note-composer"><label htmlFor={`resolution-${field}`}>{label}</label><Textarea id={`resolution-${field}`} rows={4} value={resolutionDraft[field]} disabled={actionBusy} onChange={event => updateResolutionDraft(field, event.target.value)} /><div className="developer-composer-foot"><span>{Array.from(resolutionDraft[field]).length.toLocaleString()} / 4,000</span></div></div>)}
        <div className="developer-note-composer"><label htmlFor="resolution-fixReference">Fix reference (optional)</label><Input id="resolution-fixReference" value={resolutionDraft.fixReference} disabled={actionBusy} onChange={event => updateResolutionDraft('fixReference', event.target.value)} onKeyDown={event => { if (event.key === 'Enter') event.preventDefault() }} /></div>
        <div className="developer-note-actions">{resolutionNeedsRefresh && <Button color="secondary" variant="outline" type="button" disabled={actionBusy} onClick={retryDetail}>Refresh bug</Button>}<Button color="primary" type="submit" disabled={actionBusy || resolutionTextFields.some(({ field }) => !resolutionDraft[field].trim())} loading={actionBusy}>Send to QA</Button></div>
      </form>}
    </section>
  }

  function renderDetail() {
    if (detailState === 'loading') return <p className="developer-status" role="status">Loading bug…</p>
    if (detailState === 'denied' || detailState === 'unavailable') return <section className="developer-notice" role="alert"><h1>{detailState === 'denied' ? 'Bug unavailable' : 'Bug temporarily unavailable'}</h1><p>{detailState === 'denied' ? 'This bug is unavailable to your account.' : 'TraceDesk could not load this bug.'}</p><Button color="secondary" variant="outline" onClick={retryDetail}>Retry</Button><Button color="secondary" variant="ghost" onClick={() => selectReport(null)}>Back to bugs</Button></section>
    if (!detail) return null
    const releaseTooLong = Array.from(releaseReason).length > 500 || new TextEncoder().encode(releaseReason).length > 2000
    return <div className="developer-detail"><ReportDetailLayout report={{
      id: detail.id, title: detail.title, statusLabel: statusLabel(detail.status), statusColor: statusColor(detail.status),
      projectName: context?.project.name ?? detail.projectId, description: detail.description, environment: detail.environment,
      expectedBehavior: detail.expectedBehavior, actualBehavior: detail.actualBehavior,
      reproductionSteps: detail.reproductionSteps, textLogs: detail.textLogs,
    }} metadata={<><span>{assigned ? 'Assigned to you' : detail.assignedDeveloperId ? 'Assigned to another Developer' : 'Unassigned'}</span><span>Version {detail.version}</span></>}
      topActions={<><Button color="secondary" variant="ghost" onClick={() => selectReport(null)}>Back to bugs</Button>
        {!detail.assignedDeveloperId && detail.status === 'OPEN' && <Button color="primary" disabled={actionBusy} loading={actionBusy} onClick={() => void claim()}>Claim</Button>}
        {assigned && notesState !== 'denied' && detail.status === 'OPEN' && <Button color="primary" disabled={actionBusy} loading={actionBusy} onClick={() => void start()}>Start investigation</Button>}</>}
      panels={<>
        {(assigned && (detail.status === 'OPEN' || detail.status === 'IN_PROGRESS')) && <section className="developer-notes" aria-labelledby="developer-release-heading"><div className="developer-section-heading"><div><h2 id="developer-release-heading">Release assignment</h2><p>Return this bug to the queue with a reason.</p></div></div><label htmlFor="developer-release-reason">Reason</label><Textarea id="developer-release-reason" rows={2} value={releaseReason} invalid={releaseTooLong} disabled={actionBusy} onChange={event => setReleaseReasons(current => ({ ...current, [draftKey]: event.target.value }))} />{releaseTooLong && <p role="alert">Use at most 500 characters and 2,000 bytes.</p>}<div className="developer-note-actions"><Button color="secondary" variant="outline" disabled={actionBusy || !releaseReason.trim() || releaseTooLong} onClick={() => void release()}>Release</Button></div></section>}
        {actionMessage && <p className="developer-action-message" role="status">{actionMessage}</p>}
        {investigationChanged && <div className="developer-inline-notice developer-freshness-warning" role="alert"><p>The investigation changed since it was last refreshed. Your drafts are preserved.</p><Button color="secondary" variant="outline" onClick={retryDetail}>Refresh</Button></div>}
        {assigned && detail.status === 'IN_PROGRESS' && notesState !== 'denied' && <DeveloperAiPanel key={`${projectId}:${detail.id}:${sessionUserId}`} projectId={projectId} reportId={detail.id} reportVersion={detail.version} notes={notes} disabled={actionBusy} onSaveDraft={saveAiDraft} onProtectedFailure={handleAiProtectedFailure} onSourceStale={() => setInvestigationChanged(true)} />}
        {renderResolutions()}{renderNotes()}
      </>} />
    </div>
  }

  function renderSettings() {
    return <section className="developer-settings"><h1>Settings</h1><section><h2>Appearance</h2><p className="muted">Choose how TraceDesk looks on this device.</p><div className="developer-theme-buttons"><Button color="secondary" variant={theme === 'light' ? 'soft' : 'outline'} aria-pressed={theme === 'light'} onClick={() => onThemeChange('light')}>Light theme</Button><Button color="secondary" variant={theme === 'dark' ? 'soft' : 'outline'} aria-pressed={theme === 'dark'} onClick={() => onThemeChange('dark')}>Dark theme</Button></div></section><section><h2>Account</h2><p className="muted">Your identity and Developer role are verified by the TraceDesk server.</p>{signOutError && <p className="developer-error" role="alert">{signOutError}</p>}<Button color="secondary" variant="outline" className="developer-sign-out" disabled={signingOut} onClick={onSignOut}>{signingOut ? 'Signing out…' : 'Sign out'}</Button></section></section>
  }

  if (!context) return renderProjectGate()
  return <AppShell items={trayItems} activeId={area} onSelect={id => setArea(id as Area)} sidebar={sidebar} headerContent={<div className="developer-shell-heading"><span>{context.project.name}</span><span>Developer</span></div>}>
    {area === 'settings' ? renderSettings() : selectedId ? renderDetail() : renderList()}
  </AppShell>
}
