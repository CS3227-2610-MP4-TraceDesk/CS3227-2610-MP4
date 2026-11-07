import { useEffect, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { ChevronDown, Plus, Search } from '@openai/apps-sdk-ui/components/Icon'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { Popover } from '@openai/apps-sdk-ui/components/Popover'
import { AppShell } from '../shell/AppShell'
import { SidebarGroup, SidebarItem } from '../shell/Sidebar'
import { previewAreas, settingsArea, type PreviewGroup, type PreviewRole } from './workspaceFixtures'
import './workspace-preview.css'

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
  searchable: boolean
}

function PreviewSidebarItems({ groups, selectedId, onSelect, searchable }: PreviewSidebarItemsProps) {
  const [search, setSearch] = useState('')
  const query = search.trim().toLowerCase()

  return (
    <>
      {searchable && (
        <div className="preview-sidebar-search">
          <Input
            variant="soft"
            size="sm"
            pill
            type="search"
            startAdornment={<Search />}
            aria-label="Search review queue"
            placeholder="Search bugs"
            value={search}
            onChange={event => setSearch(event.target.value)}
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
  const area = areas.find(item => item.id === activeId) ?? settingsArea
  const { groups } = area
  const selectedId = selections[activeId] ?? (activeId === 'settings' ? 'appearance' : undefined)
  const selectedRow = groups?.flatMap(group => group.rows).find(row => row.id === selectedId)
  const isNewReport = activeId === 'reports' && selectedId === 'new-report'
  const title = isNewReport ? 'New report' : selectedRow?.label ?? area.label

  function selectItem(id?: string) {
    setSelections(current => ({ ...current, [activeId]: id }))
  }

  return (
    <AppShell
      items={areas}
      activeId={activeId}
      onSelect={setActiveId}
      sidebar={groups && {
        title: area.label,
        renderItems: dismissOverlay => (
          <PreviewSidebarItems
            key={area.id}
            groups={groups}
            selectedId={selectedId}
            searchable={area.id === 'verification'}
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
      <section className="workspace-preview" aria-label="Development preview">
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
      </section>
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
