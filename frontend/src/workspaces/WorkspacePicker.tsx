import { Button } from '@openai/apps-sdk-ui/components/Button'
import type { WorkspaceMembership, WorkspacePage, WorkspaceRole } from './workspaceApi'

const groups: { role: WorkspaceRole; label: string; description: string }[] = [
  { role: 'REPORTER', label: 'Reporter', description: 'Submit reports and follow their progress.' },
  { role: 'DEVELOPER', label: 'Developer', description: 'Investigate assigned bugs and maintain private notes.' },
  { role: 'QA_REVIEWER', label: 'QA reviewer', description: 'Verify proposed resolutions.' },
]

export function WorkspacePicker({
  page,
  state,
  message,
  onSelect,
  onRetry,
  onSignOut,
  signingOut,
  signOutError,
}: {
  page: WorkspacePage | null
  state: 'loading' | 'ready' | 'unavailable'
  message?: string
  onSelect: (workspace: WorkspaceMembership) => void
  onRetry: () => void
  onSignOut: () => void
  signingOut: boolean
  signOutError: string | null
}) {
  return <main className="workspace-picker" aria-labelledby="workspace-picker-title">
    <section className="workspace-picker-card">
      <div className="workspace-picker-heading">
        <div><p className="workspace-picker-eyebrow">TraceDesk</p><h1 id="workspace-picker-title">Choose a project</h1></div>
        <Button color="secondary" variant="ghost" disabled={signingOut} onClick={onSignOut}>{signingOut ? 'Signing out…' : 'Sign out'}</Button>
      </div>
      <p className="workspace-picker-intro">Your role comes from your active membership in each project.</p>
      {signOutError && <p className="workspace-picker-error" role="alert">{signOutError}</p>}
      {message && <p className="workspace-picker-message" role="status">{message}</p>}
      {state === 'loading' && <p className="workspace-picker-status" role="status">Loading your projects…</p>}
      {state === 'unavailable' && <div className="workspace-picker-notice" role="alert"><p>TraceDesk could not load your projects. No workspace data has been shown.</p><Button color="secondary" variant="outline" onClick={onRetry}>Retry</Button></div>}
      {state === 'ready' && page?.items.length === 0 && <div className="workspace-picker-notice"><h2>No projects available</h2><p>Your account has no active project memberships.</p></div>}
      {state === 'ready' && page && page.items.length > 0 && <div className="workspace-picker-groups">
        {groups.map(group => {
          const items = page.items.filter(item => item.role === group.role)
          if (items.length === 0) return null
          return <section className="workspace-picker-group" key={group.role} aria-labelledby={`workspace-role-${group.role}`}>
            <div><h2 id={`workspace-role-${group.role}`}>{group.label}</h2><p>{group.description}</p></div>
            <div className="workspace-picker-list">{items.map(item => <Button
              key={item.project.id}
              color="secondary"
              variant="outline"
              onClick={() => onSelect(item)}
            >{item.project.name}</Button>)}</div>
          </section>
        })}
      </div>}
      {state === 'ready' && page?.hasMore && <p className="workspace-picker-message" role="status">Only the first 100 active projects are shown.</p>}
    </section>
  </main>
}
