import { useRef, useState, type FormEvent } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { ChevronRight, Eye, EyeOff, Moon, Sun } from '@openai/apps-sdk-ui/components/Icon'
import beetle from '../../public/brand/tracedesk-beetle.svg?raw'
import { demoAccounts as previewAccounts, demoPassword as previewPassword } from './fixtures/demoAccounts'
import { useMascotMotion } from './useMascotMotion'
import { AuthApiError } from '../auth/sessionApi'

const demoAccounts = import.meta.env.DEV ? previewAccounts : []
const demoPassword = import.meta.env.DEV ? previewPassword : ''
const mascotMarkup = { __html: beetle }

type DemoAccount = typeof demoAccounts[number]

type SignInProps = {
  theme: 'dark' | 'light'
  onToggleTheme: () => void
  onSignIn: (login: string, password: string) => Promise<void>
  serviceNotice: string | null
  onRetryConnection: () => void
}

function errorMessage(error: unknown): string {
  if (error instanceof AuthApiError) {
    if (error.code === 'INVALID_CREDENTIALS') return 'Email or password is incorrect.'
    if (error.status === 403) return 'Your request could not be verified. Please try again.'
    if (error.status === 503 || error.status >= 500) return 'TraceDesk is temporarily unavailable. Try again.'
    if (error.code === 'UNAUTHENTICATED') return 'Sign-in could not be confirmed. Please try again.'
  }
  return 'Unable to sign in. Check your connection and try again.'
}

export function SignIn({ theme, onToggleTheme, onSignIn, serviceNotice, onRetryConnection }: SignInProps) {
  const { screen, mascot } = useMascotMotion()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [signInError, setSignInError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const submitButton = useRef<HTMLButtonElement>(null)
  const demoAccountDisclosure = useRef<HTMLDetailsElement>(null)

  async function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)
    setSignInError(null)
    try {
      await onSignIn(email, password)
    } catch (error) {
      setSignInError(errorMessage(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  function fillDemoAccount(account: DemoAccount) {
    setEmail(account.email)
    setPassword(demoPassword)
    setShowPassword(false)
    setSignInError(null)
    demoAccountDisclosure.current!.open = false
    submitButton.current?.focus()
  }

  return (
    <main className="sign-in" ref={screen}>
      <header className="sign-in-header" aria-label="TraceDesk">
        <span className="sign-in-logo">
          <img src="/brand/tracedesk-logo.svg" alt="" />
        </span>
        <span>TraceDesk</span>
        <Button
          className="sign-in-theme"
          color="secondary"
          variant="ghost"
          uniform
          aria-label="Toggle theme"
          onClick={onToggleTheme}
        >
          {theme === 'dark' ? <Sun /> : <Moon />}
        </Button>
      </header>
      <div className="sign-in-card">
        <div className="sign-in-stage" aria-hidden="true">
          <div className="sign-in-shadow" />
          <div className="sign-in-mascot-hop">
            <div
              className="sign-in-mascot"
              ref={mascot}
              dangerouslySetInnerHTML={mascotMarkup}
            />
          </div>
        </div>
        <section className="sign-in-panel" aria-labelledby="sign-in-title">
          <div className="sign-in-content">
            <h1 id="sign-in-title">Sign in</h1>
            <p className="sign-in-welcome">Welcome back.</p>
            {serviceNotice && (
              <p className="sign-in-notice" role="status">
                {serviceNotice}{' '}
                <button type="button" className="sign-in-retry" onClick={onRetryConnection}>
                  Retry connection
                </button>
              </p>
            )}
            <form className="sign-in-form" onSubmit={event => void signIn(event)} aria-busy={isSubmitting}>
              <div className="sign-in-field">
                <label htmlFor="sign-in-email">Email</label>
                <Input
                  id="sign-in-email"
                  type="email"
                  size="xl"
                  required
                  autoComplete="username"
                  disabled={isSubmitting}
                  value={email}
                  onChange={event => {
                    setEmail(event.target.value)
                    setSignInError(null)
                  }}
                />
              </div>
              <div className="sign-in-field" data-password>
                <label htmlFor="sign-in-password">Password</label>
                <Input
                  id="sign-in-password"
                  type={showPassword ? 'text' : 'password'}
                  size="xl"
                  required
                  autoComplete="current-password"
                  allowAutofillExtensions={false}
                  disabled={isSubmitting}
                  value={password}
                  onChange={event => {
                    setPassword(event.target.value)
                    setSignInError(null)
                  }}
                  endAdornment={
                    <Button
                      type="button"
                      color="secondary"
                      variant="ghost"
                      size="sm"
                      uniform
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                      onClick={() => setShowPassword(value => !value)}
                    >
                      {showPassword ? <EyeOff /> : <Eye />}
                    </Button>
                  }
                />
              </div>
              {signInError && <p className="sign-in-error" role="alert">{signInError}</p>}
              <Button
                ref={submitButton}
                type="submit"
                color="primary"
                variant="solid"
                size="xl"
                block
                disabled={isSubmitting}
              >
                {isSubmitting ? 'Signing in…' : 'Sign in'}
              </Button>
            </form>
            {import.meta.env.DEV && (
              <details className="sign-in-demo" ref={demoAccountDisclosure}>
                <summary><ChevronRight aria-hidden="true" />Demo accounts</summary>
                <div className="sign-in-demo-list" aria-label="Fill in a demo account">
                  {demoAccounts.map(account => (
                    <Button
                      key={account.email}
                      type="button"
                      size="sm"
                      color="secondary"
                      variant="outline"
                      onClick={() => fillDemoAccount(account)}
                    >
                      {account.role}
                    </Button>
                  ))}
                </div>
              </details>
            )}
          </div>
        </section>
      </div>
    </main>
  )
}
