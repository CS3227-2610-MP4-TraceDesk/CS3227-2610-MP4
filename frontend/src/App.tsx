import { useCallback, useEffect, useState } from 'react'
import { SignIn } from './login/SignIn'
import { AuthApiError, getSession, login, logout } from './auth/sessionApi'
import { ReporterWorkspace } from './reports/ReporterWorkspace'

type AuthState = 'checking' | 'signed-out' | 'signed-in'

export function App() {
  const [theme, setTheme] = useState<'dark' | 'light'>('dark')
  const [authState, setAuthState] = useState<AuthState>('checking')
  const [serviceNotice, setServiceNotice] = useState<string | null>(null)
  const [logoutError, setLogoutError] = useState<string | null>(null)
  const [logoutBusy, setLogoutBusy] = useState(false)
  const [sessionUserId, setSessionUserId] = useState<string | null>(null)

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
      setSessionUserId(null)
      setAuthState('signed-out')
      setServiceNotice(null)
    } catch (error) {
      if (error instanceof AuthApiError && error.status === 401) {
        setSessionUserId(null)
        setAuthState('signed-out')
        setServiceNotice(null)
      } else {
        setLogoutError('TraceDesk could not confirm sign out. Try again.')
      }
    } finally {
      setLogoutBusy(false)
    }
  }

  const handleSessionExpired = useCallback(() => {
    setSessionUserId(null)
    setLogoutError(null)
    setAuthState('signed-out')
    setServiceNotice(null)
  }, [])

  if (authState === 'checking') {
    return <main className="auth-loading" role="status">Checking your session…</main>
  }

  if (authState === 'signed-in') {
    if (!sessionUserId) return <main className="auth-loading" role="status">Loading your workspace…</main>
    return (
      <ReporterWorkspace
        key={sessionUserId}
        theme={theme}
        onThemeChange={setTheme}
        onSessionExpired={handleSessionExpired}
        onSignOut={() => void handleSignOut()}
        signingOut={logoutBusy}
        signOutError={logoutError}
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
