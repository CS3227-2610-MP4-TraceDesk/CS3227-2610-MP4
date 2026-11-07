import { useEffect, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { ArrowLeft, Bug, Chat, ChevronDown, FileDocument, Folder, Plus, Search } from '@openai/apps-sdk-ui/components/Icon'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { Popover } from '@openai/apps-sdk-ui/components/Popover'
import { AppShell } from '../shell/AppShell'
import { SidebarGroup, SidebarItem } from '../shell/Sidebar'
import { QaQueue } from './qa/QaQueue'
import { QaBugOverview } from './qa/QaBugOverview'
import { QaBugReview } from './qa/QaBugReview'
import { QaTestTemplates } from './qa/QaTestTemplates'
import { qaQueues } from './qa/qaFixtures'
import { useQaPreview } from './qa/useQaPreview'
import { previewAreas, settingsArea, type PreviewGroup, type PreviewRole } from './workspaceFixtures'
import './workspace-preview.css'
import './qa/qa-preview.css'
import './qa/qa-review-workspace.css'

type WorkspacePreviewProps = {
  role: PreviewRole
  theme: 'dark' | 'light'
  onToggleTheme: () => void
  onExit: () => void
}

type PreviewSidebarItemsProps = {
  groups: readonly PreviewGroup[]
  selectedId?: string
  onSelect: (id: string) => void
  search?: { value: string; onChange: (value: string) => void }
}

function PreviewSidebarItems({ groups, selectedId, onSelect, search }: PreviewSidebarItemsProps) {
  const query = search?.value.trim().toLowerCase() ?? ''

  return (
    <>
      {search && (
        <div className="preview-sidebar-search">
          <Input
            variant="soft"
            size="sm"
            pill
            type="search"
            startAdornment={<Search />}
            aria-label="Search review queue"
            placeholder="Search bugs"
            value={search.value}
            onChange={event => search.onChange(event.target.value)}
          />
        </div>
      )}
      {groups.map(({ label, rows, emptyMessage }) => {
        const matchingRows = rows.filter(row => `${row.id} ${row.label}`.toLowerCase().includes(query))

        return (
          <SidebarGroup key={label} title={label}>
            {matchingRows.map(row => (
              <SidebarItem
                key={row.id}
                label={row.label}
                icon={row.icon}
                selected={selectedId === row.id}
                onSelect={() => onSelect(row.id)}
              />
            ))}
            {matchingRows.length === 0 && (
              <p>{query ? 'No matching bugs' : emptyMessage}</p>
            )}
          </SidebarGroup>
        )
      })}
    </>
  )
}

function WorkspacePreview({ role, theme, onToggleTheme, onExit }: WorkspacePreviewProps) {
  const areas = previewAreas[role]
  const [activeId, setActiveId] = useState(areas[0].id)
  const [selections, setSelections] = useState<Partial<Record<string, string>>>({})
  const [qaSearch, setQaSearch] = useState('')
  const area = areas.find(item => item.id === activeId) ?? settingsArea
  const qa = useQaPreview()
  const inQaQueue = area.id === 'verification'
  const inQaTemplates = area.id === 'templates'
  let groups = area.groups
  if (inQaQueue) {
    groups = qaQueues.slice(0, 2).map((queue, index) => ({
      label: queue.label,
      rows: qa.bugs.filter(queue.matches).map(bug => ({ id: bug.id, label: bug.title, icon: <Bug /> })),
      emptyMessage: index === 0 ? 'Nothing awaiting verification' : 'No completed reviews yet',
    }))
  }
  if (inQaTemplates) {
    groups = [{
      label: 'Saved templates',
      rows: qa.templates.map(template => ({ id: template.id, label: template.name, icon: <FileDocument /> })),
    }]
  }
  const selectedId = selections[activeId] ?? (activeId === 'settings' ? 'appearance' : undefined)
  const selectedRow = groups?.flatMap(group => group.rows).find(row => row.id === selectedId)
  const isNewReport = activeId === 'reports' && selectedId === 'new-report'
  const title = isNewReport ? 'New report' : selectedRow?.label ?? area.label
  const reviewBug = inQaQueue ? qa.bugs.find(bug => bug.id === selectedId) : undefined

  function selectItem(id?: string) {
    setSelections(current => ({ ...current, [activeId]: id }))
  }

  return (
    <AppShell
      items={areas}
      activeId={activeId}
      onSelect={setActiveId}
      headerContent={reviewBug && (
        <div className="qa-bug-header">
          <Button color="secondary" variant="ghost" uniform pill={false} aria-label="Back to verification queue" title="Back to verification queue" onClick={() => selectItem()}>
            <ArrowLeft />
          </Button>
          <Folder aria-hidden="true" />
          <span title={reviewBug.title}>{reviewBug.title}</span>
          <QaBugOverview key={reviewBug.id} bug={reviewBug}
            onTabChange={tab => qa.updateReview(reviewBug.id, current => ({ ...current, tab }))} />
          <Button color="secondary" variant="ghost" size="sm" uniform
            aria-label={qa.reviews[reviewBug.id]?.chatOpen ? 'Hide review chat' : 'Ask about this bug'}
            aria-expanded={qa.reviews[reviewBug.id]?.chatOpen ?? false}
            onClick={() => qa.updateReview(reviewBug.id, current => ({ ...current, chatOpen: !current.chatOpen }))}>
            <Chat />
          </Button>
        </div>
      )}
      sidebar={groups && {
        title: area.label,
        renderItems: dismissOverlay => (
          <PreviewSidebarItems
            key={area.id}
            groups={groups}
            selectedId={selectedId}
            search={inQaQueue ? { value: qaSearch, onChange: setQaSearch } : undefined}
            onSelect={id => {
              selectItem(id)
              dismissOverlay()
            }}
          />
        ),
        footer: (
          <Popover>
            <Popover.Trigger>
              <button className="preview-account" type="button" aria-label="Account menu">
                <span className="preview-avatar" aria-hidden="true">{role[0]}</span>
                {role}
                <ChevronDown />
              </button>
            </Popover.Trigger>
            <Popover.Content className="preview-account-menu" side="top" align="start" width={240}>
              <Button color="secondary" variant="ghost" block onClick={onExit}>Exit preview</Button>
            </Popover.Content>
          </Popover>
        ),
      }}
    >
      {inQaQueue ? (
        <>
          <QaQueue items={qa.bugs} search={qaSearch} onSearch={setQaSearch} onOpenBug={selectItem} hidden={Boolean(reviewBug)} />
          {reviewBug && <QaBugReview key={reviewBug.id} bug={reviewBug} qa={qa} />}
        </>
      ) : inQaTemplates ? (
        <QaTestTemplates items={qa.templates} onChange={qa.setTemplates} selectedId={selectedId ?? ''} onSelect={selectItem} />
      ) : <section className="workspace-preview" aria-label="Development preview">
        <div className="preview-heading">
          <h1>{title}</h1>
          {activeId === 'reports' && !isNewReport && (
            <Button color="primary" size="md" onClick={() => selectItem('new-report')}>
              <Plus />New report
            </Button>
          )}
        </div>
        <p className="muted">{role} · Local workspace preview</p>
        {isNewReport && (
          <Button color="secondary" variant="outline" onClick={() => selectItem()}>
            Back to reports
          </Button>
        )}
        {activeId === 'settings' && (
          selectedId === 'account'
            ? <Button color="secondary" variant="outline" onClick={onExit}>Exit preview</Button>
            : <Button color="secondary" variant="outline" onClick={onToggleTheme}>
                {theme === 'dark' ? 'Light appearance' : 'Dark appearance'}
              </Button>
        )}
      </section>}
    </AppShell>
  )
}

const previewRoles: Readonly<Partial<Record<string, PreviewRole>>> = {
  reporter: 'Reporter',
  developer: 'Developer',
  qa: 'QA reviewer',
}

export default function WorkspacePreviewPage() {
  const [theme, setTheme] = useState<'dark' | 'light'>('dark')
  const roleKey = new URLSearchParams(window.location.search).get('workspace-preview') ?? ''
  const role = Object.hasOwn(previewRoles, roleKey) ? previewRoles[roleKey] : undefined

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])

  if (!role) {
    return (
      <main className="workspace-preview">
        <h1>Workspace preview</h1>
        <p>Choose a sample workspace.</p>
        {Object.entries(previewRoles).map(([key, label]) => (
          <a key={key} href={`?workspace-preview=${key}`}>{label}</a>
        ))}
        <a href="/">Back to TraceDesk</a>
      </main>
    )
  }

  return (
    <WorkspacePreview
      role={role}
      theme={theme}
      onToggleTheme={() => setTheme(current => current === 'dark' ? 'light' : 'dark')}
      onExit={() => window.location.assign('/')}
    />
  )
}
