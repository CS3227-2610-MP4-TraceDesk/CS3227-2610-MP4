import { useCallback, useEffect, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { SignIn } from './login/SignIn'
import { AuthApiError, getSession, login, logout } from './auth/sessionApi'

type AuthState = 'checking' | 'signed-out' | 'signed-in'

function SignedInWorkspace({
  busy,
  error,
  onSignOut,
}: {
  busy: boolean
  error: string | null
  onSignOut: () => void
}) {
  return (
    <main className="demo-workspace" aria-label="Signed-in state">
      <p className="eyebrow">TraceDesk</p>
      <h1>You’re signed in</h1>
      <p>
        Your session is active. Your role workspace will appear here when it is
        available.
      </p>
      {error && <p className="sign-in-error" role="alert">{error}</p>}
      <Button color="secondary" variant="outline" disabled={busy} onClick={onSignOut}>
        {busy ? 'Signing out…' : 'Sign out'}
      </Button>
    </main>
  )
}

export function App() {
  const [theme, setTheme] = useState<'dark' | 'light'>('dark')
  const [authState, setAuthState] = useState<AuthState>('checking')
  const [serviceNotice, setServiceNotice] = useState<string | null>(null)
  const [logoutError, setLogoutError] = useState<string | null>(null)
  const [logoutBusy, setLogoutBusy] = useState(false)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])

  const restoreSession = useCallback(async (signal?: AbortSignal) => {
    try {
      await getSession(signal)
      if (signal?.aborted) return
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
    void getSession(controller.signal).then(() => {
      if (controller.signal.aborted) return
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
    await getSession()
    setServiceNotice(null)
    setAuthState('signed-in')
  }

  async function handleSignOut() {
    setLogoutBusy(true)
    setLogoutError(null)
    try {
      await logout()
      setAuthState('signed-out')
      setServiceNotice(null)
    } catch (error) {
      if (error instanceof AuthApiError && error.status === 401) {
        setAuthState('signed-out')
        setServiceNotice(null)
      } else {
        setLogoutError('TraceDesk could not confirm sign out. Try again.')
      }
    } finally {
      setLogoutBusy(false)
    }
  }

  if (authState === 'checking') {
    return <main className="auth-loading" role="status">Checking your session…</main>
  }

  if (authState === 'signed-in') {
    return <SignedInWorkspace busy={logoutBusy} error={logoutError} onSignOut={() => void handleSignOut()} />
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
