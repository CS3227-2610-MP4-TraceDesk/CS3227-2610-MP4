import { useEffect, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { ArrowLeft, Bug, Check, FileDocument, Folder, Search } from '@openai/apps-sdk-ui/components/Icon'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { QaQueue } from '../preview/qa/QaQueue'
import { matchesBugSearch } from '../preview/qa/qaFixtures'
import { fetchReports, ReportApiError, type ReportSummary } from '../reports/reportApi'
import { AppShell } from '../shell/AppShell'
import { SidebarGroup, SidebarItem } from '../shell/Sidebar'
import { QaReportSummary } from './QaReportSummary'
import { QaReviewHistory, type QaHistoryEntry } from './QaReviewHistory'
import { QaHistoricalReviewPanel } from './QaHistoricalReviewPanel'
import { fetchReviewedByMe } from './qaHistoryApi'
import { QaTemplateLibrary } from './QaTemplateLibrary'
import { fetchQaTemplates, type QaTemplateRecord } from './qaTemplatesApi'
import { emptyQaReviewDraft, type QaReviewDraft } from './qaReviewDraft'
import '../preview/qa/qa-preview.css'
import '../preview/workspace-preview.css'
import './qa-workspace.css'

const items = [
  { id: 'verification', label: 'Verification', icon: <Check /> },
  { id: 'templates', label: 'Test templates', icon: <FileDocument /> },
]
const statusLabels: Record<ReportSummary['status'], 'Open' | 'Investigating' | 'Ready for QA' | 'Closed'> = {
  OPEN: 'Open', IN_PROGRESS: 'Investigating', READY_FOR_QA: 'Ready for QA', CLOSED: 'Closed',
}

type QaWorkspaceProps = {
  project: { id: string; name: string }
  theme: 'dark' | 'light'
  onThemeChange: (theme: 'dark' | 'light') => void
  onSessionExpired: () => void
  onSignOut: () => void
  onChooseWorkspace: () => void
  signingOut: boolean
  signOutError: string | null
}

export function QaWorkspace({
  project, theme, onThemeChange, onSessionExpired, onSignOut, onChooseWorkspace, signingOut, signOutError,
}: QaWorkspaceProps) {
  const [area, setArea] = useState('verification')
  const [destination, setDestination] = useState<'awaiting' | 'reviewed' | 'all'>('awaiting')
  const [templates, setTemplates] = useState<QaTemplateRecord[]>([])
  const [templateId, setTemplateId] = useState('')
  const [templatesStatus, setTemplatesStatus] = useState<'loading' | 'ready' | 'failed'>('loading')
  const [templateReload, setTemplateReload] = useState(0)
  const [search, setSearch] = useState('')
  const [decisionNotice, setDecisionNotice] = useState('')
  const [selectedId, setSelectedId] = useState('')
  const [history, setHistory] = useState<QaHistoryEntry[]>([])
  const [historicalReview, setHistoricalReview] = useState<QaHistoryEntry | null>(null)
  const [drafts, setDrafts] = useState<Record<string, QaReviewDraft>>({})
  const [reports, setReports] = useState<ReportSummary[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [cursor, setCursor] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [state, setState] = useState<'loading' | 'ready' | 'failed' | 'denied'>('loading')

  useEffect(() => {
    const controller = new AbortController()
    if (destination === 'reviewed') {
      void fetchReviewedByMe(project.id, cursor, controller.signal).then(page => {
        if (controller.signal.aborted) return
        setHistory(current => cursor ? [...current, ...page.items] : page.items)
        setNextCursor(page.nextCursor === cursor ? null : page.nextCursor)
        setState('ready')
      }, failure => {
        if (controller.signal.aborted) return
        if (failure instanceof ReportApiError && failure.status === 401) onSessionExpired()
        else if (failure instanceof ReportApiError && [403, 404].includes(failure.status)) {
          setHistory([])
          setHistoricalReview(null)
          setNextCursor(null)
          setState('denied')
        } else setState('failed')
      })
      return () => controller.abort()
    }
    void fetchReports(project.id, destination === 'awaiting' ? 'READY_FOR_QA' : '', cursor, controller.signal).then(page => {
      if (controller.signal.aborted) return
      const ready = destination === 'awaiting' ? page.items.filter(report => report.status === 'READY_FOR_QA') : page.items
      setReports(current => cursor
        ? [...current, ...ready.filter(report => !current.some(item => item.id === report.id))]
        : ready)
      setNextCursor(page.nextCursor === cursor ? null : page.nextCursor)
      setState('ready')
    }, error => {
      if (controller.signal.aborted) return
      if (error instanceof ReportApiError && error.status === 401) {
        onSessionExpired()
      } else if (error instanceof ReportApiError && [403, 404].includes(error.status)) {
        setReports([])
        setDrafts({})
        setSelectedId('')
        setNextCursor(null)
        setState('denied')
      } else {
        setState('failed')
      }
    })
    return () => controller.abort()
  }, [project.id, cursor, destination, reload, onSessionExpired])

  useEffect(() => {
    let active = true
    void fetchQaTemplates(project.id).then(records => {
      if (!active) return
      setTemplates(records)
      setTemplatesStatus('ready')
    }, failure => {
      if (!active) return
      if (failure instanceof ReportApiError && failure.status === 401) onSessionExpired()
      else {
        if (failure instanceof ReportApiError && [403, 404].includes(failure.status)) setTemplates([])
        setTemplatesStatus('failed')
      }
    })
    return () => { active = false }
  }, [project.id, templateReload, onSessionExpired])

  const selected = reports.find(report => report.id === selectedId)
  const bugs = reports.map(report => ({
    id: report.id, title: report.title, status: statusLabels[report.status], lastActivity: report.updatedAt,
  }))
  const matching = bugs.filter(bug => matchesBugSearch(bug, search))
  const settings = area === 'settings'
  const library = area === 'templates'
  const queueName = destination === 'all' ? 'All bugs' : destination === 'reviewed' ? 'Reviewed by me' : 'Awaiting verification'

  function clearDraft(reportId: string) {
    setDrafts(current => {
      if (!(reportId in current)) return current
      const remaining = { ...current }
      delete remaining[reportId]
      return remaining
    })
  }

  function selectDestination(next: typeof destination) {
    setSelectedId('')
    setHistoricalReview(null)
    if (destination === next) return
    setDestination(next)
    setSearch('')
    setReports([])
    setHistory([])
    setNextCursor(null)
    setCursor(null)
    setState('loading')
  }

  const pageControls = <div className="qa-page-controls">
    {state === 'loading' && <p role="status">Loading reportsâ€¦</p>}
    {state === 'failed' && <>
      <p role="alert">{'Could not load ' + queueName.toLowerCase() + '.'}</p>
      <Button color="secondary" variant="outline" onClick={() => {
        setState('loading')
        setReload(value => value + 1)
      }}>Retry</Button>
    </>}
    {state === 'ready' && nextCursor && <Button color="secondary" variant="outline" onClick={() => {
      setState('loading')
      setCursor(nextCursor)
    }}>{destination === 'reviewed' ? 'Load more reviews' : 'Load more reports'}</Button>}
  </div>

  return <AppShell items={items} activeId={area} onSelect={setArea}
    headerContent={<div className="qa-bug-header">
      {historicalReview && !settings && !library && <Button color="secondary" variant="ghost" uniform
        aria-label="Back to reviewed by me" onClick={() => setHistoricalReview(null)}><ArrowLeft /></Button>}
      {selected && !settings && !library && <Button color="secondary" variant="ghost" uniform
        aria-label={destination === 'all' ? 'Back to all bugs' : 'Back to verification queue'} onClick={() => setSelectedId('')}><ArrowLeft /></Button>}
      <Folder aria-hidden="true" />
      <span>{!settings && !library ? historicalReview?.title ?? selected?.title ?? project.name : project.name}</span>
    </div>}
    sidebar={library ? {
      title: 'Test templates',
      renderItems: dismiss => <SidebarGroup title="Saved templates">
        <SidebarItem label="All templates" icon={<FileDocument />} selected={!templateId}
          onSelect={() => { setTemplateId(''); dismiss() }} />
        {templates.map(template => <SidebarItem key={template.id} label={template.name} icon={<FileDocument />}
          selected={templateId === template.id} onSelect={() => { setTemplateId(template.id); dismiss() }} />)}
      </SidebarGroup>,
    } : !settings ? {
      title: 'Verification',
      renderItems: dismiss => <>
        {destination !== 'reviewed' && <div className="preview-sidebar-search">
          <Input type="search" size="sm" pill variant="soft" startAdornment={<Search />}
            aria-label="Search review queue" placeholder="Search bugs" value={search}
            onChange={event => setSearch(event.target.value)} />
        </div>}
        <SidebarItem label="Awaiting verification" icon={<Check />} selected={!selectedId && destination === 'awaiting'}
          onSelect={() => { selectDestination('awaiting'); dismiss() }} />
        <SidebarItem label="Reviewed by me" icon={<Check />} selected={destination === 'reviewed'}
          onSelect={() => { selectDestination('reviewed'); dismiss() }} />
        <SidebarItem label="All bugs" icon={<Bug />} selected={!selectedId && destination === 'all'}
          onSelect={() => { selectDestination('all'); dismiss() }} />
        {destination === 'reviewed' ? <SidebarGroup title={queueName}>
          {history.map(entry => <SidebarItem key={entry.resolutionId} label={entry.title + ' · Revision ' + entry.revision}
            icon={<Check />} selected={historicalReview?.resolutionId === entry.resolutionId}
            onSelect={() => { setHistoricalReview(entry); dismiss() }} />)}
        </SidebarGroup> : <SidebarGroup title={queueName}>
          {matching.map(bug => <SidebarItem key={bug.id} label={bug.title} icon={<Bug />}
            selected={selectedId === bug.id} onSelect={() => { setSelectedId(bug.id); dismiss() }} />)}
        </SidebarGroup>}
      </>,
    } : undefined}
  >
    {settings ? <section className="qa-queue-view" aria-label="QA settings">
      <h1>Settings</h1>
      <div className="qa-page-controls">
        <Button color="secondary" variant="outline" onClick={() => onThemeChange(theme === 'dark' ? 'light' : 'dark')}>
          {theme === 'dark' ? 'Light theme' : 'Dark theme'}
        </Button>
        <Button color="secondary" variant="outline" onClick={onChooseWorkspace}>Choose another project</Button>
        <Button color="secondary" variant="ghost" onClick={onSignOut} disabled={signingOut}>
          {signingOut ? 'Signing outâ€¦' : 'Sign out'}
        </Button>
      </div>
      {signOutError && <p role="alert">{signOutError}</p>}
    </section> : library ? <>
      {templatesStatus === 'loading' && <p className="qa-queue-view" role="status">Loading test templates…</p>}
      {templatesStatus === 'failed' && <div className="qa-queue-view">
        <p role="alert">Could not load test templates.</p>
        <Button color="secondary" variant="soft" onClick={() => {
          setTemplatesStatus('loading')
          setTemplateReload(value => value + 1)
        }}>Retry</Button>
      </div>}
      {templatesStatus === 'ready' && <QaTemplateLibrary projectId={project.id} templates={templates}
        onTemplatesChange={setTemplates} selectedId={templateId} onSelect={setTemplateId}
        onSessionExpired={onSessionExpired} />}
    </> : <>
      {!selected && decisionNotice && <p className="qa-queue-view" role="status">{decisionNotice}</p>}
      {state === 'denied' ? <section className="qa-queue-view">
        <h1>Verification</h1>
        <p role="alert">This project is no longer available to your account.</p>
        <Button color="secondary" variant="outline" onClick={onChooseWorkspace}>Choose another project</Button>
      </section> : destination === 'reviewed' ? (
        historicalReview ? <QaHistoricalReviewPanel key={historicalReview.resolutionId} projectId={project.id}
          reportId={historicalReview.reportId} resolutionId={historicalReview.resolutionId}
          onBack={() => setHistoricalReview(null)} onSessionExpired={onSessionExpired} />
          : <section className="qa-queue-view">
            <h1>Reviewed by me</h1>
            {(state === 'ready' || history.length > 0) && <QaReviewHistory entries={history} onOpen={setHistoricalReview} />}
            {pageControls}
          </section>
      ) : reports.length || state === 'ready' ? (
        <QaQueue items={bugs} search={search} onSearch={setSearch} onOpenBug={setSelectedId}
          hidden={Boolean(selected)} awaitingOnly allBugs={destination === 'all'} footer={pageControls} />
      ) : <section className="qa-queue-view"><h1>{destination === 'all' ? queueName : 'Verification'}</h1>{pageControls}</section>}
      {selected && <QaReportSummary key={selected.id} project={project} reportId={selected.id} templates={templates}
        draft={drafts[selected.id] ?? emptyQaReviewDraft}
        onDraftChange={patch => setDrafts(current => ({
          ...current, [selected.id]: { ...(current[selected.id] ?? emptyQaReviewDraft), ...patch },
        }))}
        onAccessDenied={() => clearDraft(selected.id)}
        onSessionExpired={onSessionExpired} onDecision={({ status, version }) => {
          clearDraft(selected.id)
          setReports(current => destination === 'awaiting'
            ? current.filter(report => report.id !== selected.id)
            : current.map(report => report.id === selected.id && version > report.version
              ? { ...report, status, version }
              : report))
          setSelectedId('')
          setDecisionNotice(status === 'CLOSED' ? 'Bug verified and closed.' : 'Bug sent back to the Developer.')
        }} onAssignmentConfirmed={(id, result) => {
          setReports(current => current.map(report => report.id === id && result.version > report.version ? { ...report, ...result } : report))
        }} />}
    </>}
  </AppShell>
}
