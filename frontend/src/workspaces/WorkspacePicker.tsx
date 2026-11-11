import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { AuthApiError } from '../auth/sessionApi'
import {
  createProject, fetchProjectDirectory, joinProject, normalizedProjectName,
  validProjectName, type DirectoryProject, type ProjectDirectoryPage,
} from './projectApi'
import type { WorkspaceMembership, WorkspacePage, WorkspaceRole } from './workspaceApi'

const roleNames: Record<WorkspaceRole, string> = {
  REPORTER: 'Reporter',
  DEVELOPER: 'Developer',
  QA_REVIEWER: 'QA reviewer',
}

function safeActionMessage(error: unknown, action: 'create' | 'join'): string {
  if (error instanceof AuthApiError) {
    if (error.status === 401) return 'Your session expired. Sign in again to continue.'
    if (error.status === 403) return 'TraceDesk could not confirm this request. Retry the action.'
    if (error.status === 400) return action === 'create' ? 'Enter a valid project name.' : 'This project link is invalid.'
    if (error.status === 409) return action === 'join'
      ? 'This membership is paused and cannot be reactivated here.'
      : 'This create request conflicted. Retry with the same project name.'
  }
  return action === 'create' ? 'TraceDesk could not create this project. Your name is still here; retry.'
    : 'TraceDesk could not join this project. Retry.'
}

export function WorkspacePicker({
  page,
  state,
  message,
  role,
  onSelect,
  onRetry,
  onSignOut,
  onSessionExpired,
  signingOut,
  signOutError,
}: {
  page: WorkspacePage | null
  state: 'loading' | 'ready' | 'unavailable'
  message?: string
  role: WorkspaceRole
  onSelect: (workspace: WorkspaceMembership) => void
  onRetry: () => void
  onSignOut: () => void
  onSessionExpired: () => void
  signingOut: boolean
  signOutError: string | null
}) {
  const [projects, setProjects] = useState<ProjectDirectoryPage | null>(null)
  const [directoryState, setDirectoryState] = useState<'loading' | 'ready' | 'unavailable'>('loading')
  const [directoryError, setDirectoryError] = useState<string | null>(null)
  const [directoryRetry, setDirectoryRetry] = useState(0)
  const [projectName, setProjectName] = useState('')
  const createKey = useRef<{ name: string; key: string } | null>(null)
  const [createError, setCreateError] = useState<string | null>(null)
  const [createBusy, setCreateBusy] = useState(false)
  const [joiningProject, setJoiningProject] = useState<string | null>(null)
  const [joinError, setJoinError] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)

  useEffect(() => {
    if (state === 'loading') return
    const controller = new AbortController()
    void fetchProjectDirectory(null, controller.signal).then(result => {
      if (controller.signal.aborted) return
      setProjects(result)
      setDirectoryState('ready')
    }, error => {
      if (controller.signal.aborted) return
      if (error instanceof AuthApiError && error.status === 401) onSessionExpired()
      setProjects(null)
      setDirectoryError('TraceDesk could not load the project directory.')
      setDirectoryState('unavailable')
    })
    return () => controller.abort()
  }, [directoryRetry, onSessionExpired, state])

  async function loadMore() {
    const cursor = projects?.nextCursor
    if (!cursor || loadingMore) return
    setLoadingMore(true)
    setDirectoryError(null)
    try {
      const next = await fetchProjectDirectory(cursor)
      setProjects(current => {
        if (!current) return next
        const seen = new Set(current.items.map(item => item.id))
        return { ...next, items: [...current.items, ...next.items.filter(item => !seen.has(item.id))] }
      })
    } catch (error) {
      if (error instanceof AuthApiError && error.status === 401) onSessionExpired()
      setDirectoryError('More projects are temporarily unavailable. Retry to continue.')
    } finally {
      setLoadingMore(false)
    }
  }

  async function submitProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalized = normalizedProjectName(projectName)
    if (!validProjectName(projectName)) {
      setCreateError('Use 1–120 characters for the project name, with no control characters.')
      return
    }
    setCreateBusy(true)
    setCreateError(null)
    try {
      if (createKey.current?.name !== normalized) createKey.current = { name: normalized, key: crypto.randomUUID() }
      const project = await createProject(normalized, createKey.current.key)
      createKey.current = null
      setProjectName('')
      onSelect({ project, role })
    } catch (error) {
      if (error instanceof AuthApiError && error.status === 401) onSessionExpired()
      setCreateError(safeActionMessage(error, 'create'))
    } finally {
      setCreateBusy(false)
    }
  }

  async function join(project: DirectoryProject) {
    setJoiningProject(project.id)
    setJoinError(null)
    try {
      const joined = await joinProject(project.id)
      onSelect({ project: joined, role })
    } catch (error) {
      if (error instanceof AuthApiError && error.status === 401) onSessionExpired()
      setJoinError(safeActionMessage(error, 'join'))
      if (error instanceof AuthApiError && error.status === 409) setDirectoryRetry(value => value + 1)
    } finally {
      setJoiningProject(null)
    }
  }

  const membershipCount = page?.items.length ?? 0
  const directoryItems: DirectoryProject[] = [...(projects?.items ?? [])]
  const seenProjectIds = new Set(directoryItems.map(project => project.id))
  for (const membership of page?.items ?? []) {
    if (!seenProjectIds.has(membership.project.id)) {
      directoryItems.push({ ...membership.project, membership: 'ACTIVE' })
      seenProjectIds.add(membership.project.id)
    }
  }

  return <main className="workspace-picker" aria-labelledby="workspace-picker-title">
    <section className="workspace-picker-card">
      <div className="workspace-picker-heading">
        <div><p className="workspace-picker-eyebrow">TraceDesk</p><h1 id="workspace-picker-title">Choose a project</h1></div>
        <Button color="secondary" variant="ghost" disabled={signingOut} onClick={onSignOut}>{signingOut ? 'Signing out…' : 'Sign out'}</Button>
      </div>
      <p className="workspace-picker-intro">Your role is assigned to your account. You join projects as a {roleNames[role]}.</p>
      {signOutError && <p className="workspace-picker-error" role="alert">{signOutError}</p>}
      {message && <p className="workspace-picker-message" role="status">{message}</p>}

      <section className="workspace-picker-section" aria-labelledby="create-project-title">
        <h2 id="create-project-title">Create a project</h2>
        <p className="workspace-picker-message">All three roles can create projects. You will join with your account role.</p>
        <form className="workspace-create-form" onSubmit={event => void submitProject(event)}>
          <label htmlFor="new-project-name">Project name</label>
          <Input id="new-project-name" value={projectName} maxLength={240} autoComplete="off"
            onChange={event => {
              if (normalizedProjectName(event.target.value) !== normalizedProjectName(projectName)) createKey.current = null
              setProjectName(event.target.value)
              setCreateError(null)
            }}
            aria-invalid={Boolean(createError)} aria-describedby={createError ? 'project-create-error' : undefined} />
          <Button color="primary" type="submit" disabled={createBusy}>
            {createBusy ? 'Creating…' : 'Create project'}
          </Button>
        </form>
        {createError && <p id="project-create-error" className="workspace-picker-error" role="alert">{createError}</p>}
      </section>

      <section className="workspace-picker-section" aria-labelledby="project-directory-title">
        <div className="workspace-directory-heading">
          <div><h2 id="project-directory-title">Project directory</h2><p className="workspace-picker-message">Anyone signed in can see project names. Join to access that project’s workspace.</p></div>
          {directoryState === 'unavailable' && <Button color="secondary" variant="outline" onClick={() => {
            setDirectoryError(null)
            setDirectoryState('loading')
            setDirectoryRetry(value => value + 1)
          }}>Retry</Button>}
        </div>
        {directoryState === 'loading' && <p className="workspace-picker-status" role="status">Loading projects…</p>}
        {directoryState === 'unavailable' && <p className="workspace-picker-error" role="status">{directoryError} No reports, drafts or project data are shown.</p>}
        {directoryState === 'ready' && directoryItems.length === 0 && <p className="workspace-picker-message">No active projects yet. Create the first one above.</p>}
        {directoryItems.length > 0 && <ul className="workspace-directory-list">
          {directoryItems.map(project => <li key={project.id}>
            <div className="workspace-directory-project">
              <span className="workspace-directory-name">{project.name}</span>
              <span className="workspace-directory-state">{project.membership === 'ACTIVE' ? 'Joined' : project.membership === 'DISABLED' ? 'Membership paused' : 'Not joined'}</span>
            </div>
            {project.membership === 'ACTIVE'
              ? <Button color="secondary" variant="outline" aria-label={`Open ${project.name}`}
                onClick={() => onSelect({ project: { id: project.id, name: project.name }, role })}>Open</Button>
              : project.membership === 'DISABLED'
                ? <Button color="secondary" variant="outline" disabled aria-label={`Membership paused for ${project.name}`}>Unavailable</Button>
                : <Button color="primary" onClick={() => void join(project)} disabled={joiningProject !== null}>
                  {joiningProject === project.id ? 'Joining…' : 'Join'}
                </Button>}
          </li>)}
        </ul>}
        {joinError && <p className="workspace-picker-error" role="alert">{joinError}</p>}
        {directoryError && directoryState === 'ready' && <p className="workspace-picker-error" role="alert">{directoryError}</p>}
        {directoryState === 'ready' && projects?.hasMore && <Button color="secondary" variant="outline" onClick={() => void loadMore()} disabled={loadingMore}>
          {loadingMore ? 'Loading…' : 'Load more projects'}
        </Button>}
      </section>

      {state === 'loading' && <p className="workspace-picker-status" role="status">Loading your memberships…</p>}
      {state === 'unavailable' && <div className="workspace-picker-notice" role="alert"><p>TraceDesk could not load your memberships.</p><Button color="secondary" variant="outline" onClick={onRetry}>Retry memberships</Button></div>}
      {state === 'ready' && membershipCount === 0 && <p className="workspace-picker-message">You have not joined any projects yet.</p>}
      {state === 'ready' && page?.hasMore && <p className="workspace-picker-message" role="status">Your membership list is limited. Use the directory above to find other projects.</p>}
    </section>
  </main>
}
