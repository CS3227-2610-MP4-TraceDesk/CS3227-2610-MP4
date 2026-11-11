import { useEffect, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { ArrowLeft, Bug, Check, Folder, Search } from '@openai/apps-sdk-ui/components/Icon'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { QaQueue } from '../preview/qa/QaQueue'
import { matchesBugSearch } from '../preview/qa/qaFixtures'
import { fetchReports, ReportApiError, type ReportSummary } from '../reports/reportApi'
import { AppShell } from '../shell/AppShell'
import { SidebarGroup, SidebarItem } from '../shell/Sidebar'
import { QaReportSummary } from './QaReportSummary'
import '../preview/qa/qa-preview.css'
import '../preview/workspace-preview.css'
import './qa-workspace.css'

const items = [{ id: 'verification', label: 'Verification', icon: <Check /> }]
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
  const [destination, setDestination] = useState<'awaiting' | 'all'>('awaiting')
  const [search, setSearch] = useState('')
  const [selectedId, setSelectedId] = useState('')
  const [reports, setReports] = useState<ReportSummary[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [cursor, setCursor] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [state, setState] = useState<'loading' | 'ready' | 'failed' | 'denied'>('loading')

  useEffect(() => {
    const controller = new AbortController()
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
        setSelectedId('')
        setNextCursor(null)
        setState('denied')
      } else {
        setState('failed')
      }
    })
    return () => controller.abort()
  }, [project.id, cursor, destination, reload, onSessionExpired])

  const selected = reports.find(report => report.id === selectedId)
  const bugs = reports.map(report => ({
    id: report.id, title: report.title, status: statusLabels[report.status], lastActivity: report.updatedAt,
  }))
  const matching = bugs.filter(bug => matchesBugSearch(bug, search))
  const settings = area === 'settings'
  const queueName = destination === 'all' ? 'All bugs' : 'Awaiting verification'

  function selectDestination(next: typeof destination) {
    setSelectedId('')
    if (destination === next) return
    setDestination(next)
    setSearch('')
    setReports([])
    setNextCursor(null)
    setCursor(null)
    setState('loading')
  }

  const pageControls = <div className="qa-page-controls">
    {state === 'loading' && <p role="status">Loading reports…</p>}
    {state === 'failed' && <>
      <p role="alert">{destination === 'all' ? 'Could not load project bugs.' : 'Could not load reports awaiting verification.'}</p>
      <Button color="secondary" variant="outline" onClick={() => {
        setState('loading')
        setReload(value => value + 1)
      }}>Retry</Button>
    </>}
    {state === 'ready' && nextCursor && <Button color="secondary" variant="outline" onClick={() => {
      setState('loading')
      setCursor(nextCursor)
    }}>Load more reports</Button>}
  </div>

  return <AppShell items={items} activeId={area} onSelect={setArea}
    headerContent={<div className="qa-bug-header">
      {selected && !settings && <Button color="secondary" variant="ghost" uniform
        aria-label={destination === 'all' ? 'Back to all bugs' : 'Back to verification queue'} onClick={() => setSelectedId('')}><ArrowLeft /></Button>}
      <Folder aria-hidden="true" />
      <span>{!settings && selected ? selected.title : project.name}</span>
    </div>}
    sidebar={!settings ? {
      title: 'Verification',
      renderItems: dismiss => <>
        <div className="preview-sidebar-search">
          <Input type="search" size="sm" pill variant="soft" startAdornment={<Search />}
            aria-label="Search review queue" placeholder="Search bugs" value={search}
            onChange={event => setSearch(event.target.value)} />
        </div>
        <SidebarItem label="Awaiting verification" icon={<Check />} selected={!selectedId && destination === 'awaiting'}
          onSelect={() => { selectDestination('awaiting'); dismiss() }} />
        <SidebarItem label="All bugs" icon={<Bug />} selected={!selectedId && destination === 'all'}
          onSelect={() => { selectDestination('all'); dismiss() }} />
        <SidebarGroup title={queueName}>
          {matching.map(bug => <SidebarItem key={bug.id} label={bug.title} icon={<Bug />}
            selected={selectedId === bug.id} onSelect={() => { setSelectedId(bug.id); dismiss() }} />)}
        </SidebarGroup>
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
          {signingOut ? 'Signing out…' : 'Sign out'}
        </Button>
      </div>
      {signOutError && <p role="alert">{signOutError}</p>}
    </section> : <>
      {state === 'denied' ? <section className="qa-queue-view">
        <h1>Verification</h1>
        <p role="alert">This project is no longer available to your account.</p>
        <Button color="secondary" variant="outline" onClick={onChooseWorkspace}>Choose another project</Button>
      </section> : reports.length || state === 'ready' ? (
        <QaQueue items={bugs} search={search} onSearch={setSearch} onOpenBug={setSelectedId}
          hidden={Boolean(selected)} awaitingOnly allBugs={destination === 'all'} footer={pageControls} />
      ) : <section className="qa-queue-view"><h1>{destination === 'all' ? queueName : 'Verification'}</h1>{pageControls}</section>}
      {selected && <QaReportSummary key={selected.id} project={project} reportId={selected.id}
        onSessionExpired={onSessionExpired} onAssignmentConfirmed={(id, result) => {
          setReports(current => current.map(report => report.id === id && result.version > report.version ? { ...report, ...result } : report))
        }} />}
    </>}
  </AppShell>
}
