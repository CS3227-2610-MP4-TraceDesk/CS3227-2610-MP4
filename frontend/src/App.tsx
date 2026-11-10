import { useCallback, useEffect, useState } from 'react'
import { SignIn } from './login/SignIn'
import { AuthApiError, getSession, login, logout } from './auth/sessionApi'
import { ReporterWorkspace } from './reports/ReporterWorkspace'
import { DeveloperWorkspace } from './developer/DeveloperWorkspace'
import { QaWorkspace } from './qa/QaWorkspace'
import { WorkspacePicker } from './workspaces/WorkspacePicker'
import { fetchWorkspaces, type WorkspaceMembership, type WorkspacePage } from './workspaces/workspaceApi'

type AuthState = 'checking' | 'signed-out' | 'signed-in'

function removeProjectRoute() {
  const url = new URL(window.location.href)
  url.searchParams.delete('project')
  url.searchParams.delete('workspace')
  window.history.replaceState(null, '', url)
}

export function App() {
  const [theme, setTheme] = useState<'dark' | 'light'>('dark')
  const [authState, setAuthState] = useState<AuthState>('checking')
  const [serviceNotice, setServiceNotice] = useState<string | null>(null)
  const [logoutError, setLogoutError] = useState<string | null>(null)
  const [logoutBusy, setLogoutBusy] = useState(false)
  const [sessionUserId, setSessionUserId] = useState<string | null>(null)
  const [workspacePage, setWorkspacePage] = useState<WorkspacePage | null>(null)
  const [workspaceState, setWorkspaceState] = useState<'idle' | 'loading' | 'ready' | 'unavailable'>('idle')
  const [selectedProjectId, setSelectedProjectId] = useState(() => new URLSearchParams(window.location.search).get('project') ?? '')
  const [workspaceMessage, setWorkspaceMessage] = useState<string | undefined>()
  const [workspaceReload, setWorkspaceReload] = useState(0)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])

  const restoreSession = useCallback(async (signal?: AbortSignal) => {
    try {
      const session = await getSession(signal)
      if (signal?.aborted) return
      setSessionUserId(session.userId)
      setServiceNotice(null)
      setAuthState('signed-in')
    } catch (error) {
      if (signal?.aborted) return
      if (error instanceof AuthApiError && error.status === 401) {
        setServiceNotice(null)
      } else {
        setServiceNotice('TraceDesk is temporarily unavailable. Check your connection and retry.')
      }
      setAuthState('signed-out')
    }
  }, [])

  const clearSignedInState = useCallback(() => {
    removeProjectRoute()
    setSelectedProjectId('')
    setSessionUserId(null)
    setWorkspacePage(null)
    setWorkspaceState('idle')
    setWorkspaceMessage(undefined)
    setLogoutError(null)
    setServiceNotice(null)
    setAuthState('signed-out')
  }, [])

  useEffect(() => {
    if (authState !== 'signed-in' || !sessionUserId) return
    const controller = new AbortController()
    void fetchWorkspaces(controller.signal).then(page => {
      if (controller.signal.aborted) return
      setWorkspacePage(page)
      setWorkspaceState('ready')
    }, error => {
      if (controller.signal.aborted) return
      if (error instanceof AuthApiError && error.status === 401) {
        clearSignedInState()
      } else {
        setWorkspacePage(null)
        setWorkspaceState('unavailable')
      }
    })
    return () => controller.abort()
  }, [authState, sessionUserId, workspaceReload, clearSignedInState])

  useEffect(() => {
    const controller = new AbortController()
    void getSession(controller.signal).then(session => {
      if (controller.signal.aborted) return
      setSessionUserId(session.userId)
      setServiceNotice(null)
      setAuthState('signed-in')
    }, error => {
      if (controller.signal.aborted) return
      setServiceNotice(error instanceof AuthApiError && error.status === 401
        ? null
        : 'TraceDesk is temporarily unavailable. Check your connection and retry.')
      setAuthState('signed-out')
    })
    return () => controller.abort()
  }, [])

  async function handleSignIn(email: string, password: string) {
    await login(email, password)
    const session = await getSession()
    setSessionUserId(session.userId)
    setServiceNotice(null)
    setAuthState('signed-in')
  }

  async function handleSignOut() {
    setLogoutBusy(true)
    setLogoutError(null)
    try {
      await logout()
      clearSignedInState()
    } catch (error) {
      if (error instanceof AuthApiError && error.status === 401) {
        clearSignedInState()
      } else {
        setLogoutError('TraceDesk could not confirm sign out. Try again.')
      }
    } finally {
      setLogoutBusy(false)
    }
  }

  const chooseWorkspace = useCallback((workspace: WorkspaceMembership) => {
    const url = new URL(window.location.href)
    url.searchParams.set('project', workspace.project.id)
    url.searchParams.delete('workspace')
    window.history.replaceState(null, '', url)
    setSelectedProjectId(workspace.project.id)
    setWorkspaceMessage(undefined)
  }, [])

  const showWorkspacePicker = useCallback(() => {
    removeProjectRoute()
    setSelectedProjectId('')
    setWorkspaceMessage(undefined)
    setWorkspaceState('loading')
    setWorkspaceReload(value => value + 1)
  }, [])

  if (authState === 'checking') {
    return <main className="auth-loading" role="status">Checking your session…</main>
  }

  if (authState === 'signed-in') {
    if (!sessionUserId) return <main className="auth-loading" role="status">Loading your workspace…</main>
    const selectedWorkspace = workspacePage?.items.find(item => item.project.id === selectedProjectId)
    if (workspaceState !== 'ready' || !selectedWorkspace) {
      const routeWasUnavailable = workspaceState === 'ready' && Boolean(selectedProjectId) && !selectedWorkspace
      return <WorkspacePicker
        page={workspacePage}
        state={workspaceState === 'idle' ? 'loading' : workspaceState}
        message={routeWasUnavailable
          ? workspacePage?.hasMore
            ? 'That project is not in the list shown. Only your first 100 active projects can be opened here.'
            : 'That project is not available to your account. Choose one of your active memberships.'
          : workspaceMessage}
        onSelect={chooseWorkspace}
        onRetry={() => { setWorkspaceState('loading'); setWorkspaceReload(value => value + 1) }}
        onSignOut={() => void handleSignOut()}
        signingOut={logoutBusy}
        signOutError={logoutError}
      />
    }
    const workspaceProps = {
      theme,
      onThemeChange: setTheme,
      onSessionExpired: clearSignedInState,
      onSignOut: () => void handleSignOut(),
      signingOut: logoutBusy,
      signOutError: logoutError,
      onChooseWorkspace: showWorkspacePicker,
    }
    if (selectedWorkspace.role === 'QA_REVIEWER') {
      return <QaWorkspace key={`${sessionUserId}:${selectedWorkspace.project.id}`}
        project={selectedWorkspace.project} {...workspaceProps} />
    }
    if (selectedWorkspace.role === 'DEVELOPER') {
      return <DeveloperWorkspace key={sessionUserId} {...workspaceProps} sessionUserId={sessionUserId} />
    }
    return (
      <ReporterWorkspace
        key={sessionUserId}
        {...workspaceProps}
      />
    )
  }

  return (
    <SignIn
      theme={theme}
      onToggleTheme={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
      onSignIn={handleSignIn}
      serviceNotice={serviceNotice}
      onRetryConnection={() => void restoreSession()}
    />
  )
}
