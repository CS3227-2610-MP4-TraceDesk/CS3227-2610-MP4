import { useRef, useState, type FormEvent } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { ChevronRight, Eye, EyeOff, Moon, Sun } from '@openai/apps-sdk-ui/components/Icon'
import beetle from '../../public/brand/tracedesk-beetle.svg?raw'
import { demoAccounts as previewAccounts, demoPassword as previewPassword } from './fixtures/demoAccounts'
import { useMascotMotion } from './useMascotMotion'
import { AuthApiError, type RegistrationRole } from '../auth/sessionApi'

const demoAccounts = import.meta.env.DEV ? previewAccounts : []
const demoPassword = import.meta.env.DEV ? previewPassword : ''
const mascotMarkup = { __html: beetle }

type DemoAccount = typeof demoAccounts[number]

type SignInProps = {
  theme: 'dark' | 'light'
  onToggleTheme: () => void
  onSignIn: (login: string, password: string) => Promise<void>
  onRegister: (login: string, password: string, role: RegistrationRole) => Promise<void>
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

function registrationErrorMessage(error: unknown): string {
  if (error instanceof AuthApiError) {
    if (error.code === 'ACCOUNT_UNAVAILABLE') return 'Account could not be created with these details.'
    if (error.code === 'ALREADY_AUTHENTICATED') return 'Sign out before creating another account.'
    if (error.code === 'INVALID_REQUEST') return 'Check the information and try again.'
    if (error.code === 'CSRF_INVALID' || error.status === 403) return 'Your request could not be verified. Please try again.'
    if (error.status >= 500) return 'TraceDesk is temporarily unavailable. Try again.'
  }
  return 'TraceDesk could not confirm account creation. Check your connection before trying again.'
}

export function SignIn({ theme, onToggleTheme, onSignIn, onRegister, serviceNotice, onRetryConnection }: SignInProps) {
  const { screen, mascot } = useMascotMotion()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [signInError, setSignInError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [mode, setMode] = useState<'sign-in' | 'register'>('sign-in')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [role, setRole] = useState<RegistrationRole>('REPORTER')
  const [registrationError, setRegistrationError] = useState<string | null>(null)
  const [registrationSuccess, setRegistrationSuccess] = useState(false)
  const submitting = useRef(false)
  const submitButton = useRef<HTMLButtonElement>(null)
  const demoAccountDisclosure = useRef<HTMLDetailsElement>(null)

  async function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) return
    submitting.current = true
    setIsSubmitting(true)
    setSignInError(null)
    try {
      await onSignIn(email, password)
    } catch (error) {
      setSignInError(errorMessage(error))
    } finally {
      submitting.current = false
      setIsSubmitting(false)
    }
  }

  async function createAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) return
    if (password !== confirmPassword) {
      setRegistrationError('Passwords do not match.')
      return
    }
    if (Array.from(password).length < 12) {
      setRegistrationError('Password must be at least 12 characters.')
      return
    }
    if (Array.from(password).length > 128 || new TextEncoder().encode(password).length > 512) {
      setRegistrationError('Password must be 128 characters or fewer.')
      return
    }
    submitting.current = true
    setIsSubmitting(true)
    setRegistrationError(null)
    try {
      await onRegister(email, password, role)
      setPassword('')
      setConfirmPassword('')
      setShowPassword(false)
      setMode('sign-in')
      setRegistrationSuccess(true)
    } catch (error) {
      setRegistrationError(registrationErrorMessage(error))
    } finally {
      submitting.current = false
      setIsSubmitting(false)
    }
  }

  function changeMode(next: 'sign-in' | 'register') {
    setMode(next)
    setPassword('')
    setConfirmPassword('')
    setShowPassword(false)
    setSignInError(null)
    setRegistrationError(null)
    setRegistrationSuccess(false)
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
            <h1 id="sign-in-title">{mode === 'sign-in' ? 'Sign in' : 'Create account'}</h1>
            <p className="sign-in-welcome">{mode === 'sign-in' ? 'Welcome back.' : 'Create your TraceDesk account.'}</p>
            {registrationSuccess && mode === 'sign-in' && <p className="sign-in-success" role="status">Account created. Sign in to continue.</p>}
            {serviceNotice && (
              <p className="sign-in-notice" role="status">
                {serviceNotice}{' '}
                <button type="button" className="sign-in-retry" onClick={onRetryConnection}>
                  Retry connection
                </button>
              </p>
            )}
            <form className="sign-in-form" onSubmit={event => void (mode === 'sign-in' ? signIn(event) : createAccount(event))} aria-busy={isSubmitting}>
              <div className="sign-in-field">
                <label htmlFor="sign-in-email">Email</label>
                <Input
                  id="sign-in-email"
                  type="email"
                  size="xl"
                  required
                  autoComplete="email"
                  disabled={isSubmitting}
                  value={email}
                  onChange={event => {
                    setEmail(event.target.value)
                    setSignInError(null)
                    setRegistrationError(null)
                    setRegistrationSuccess(false)
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
                  autoComplete={mode === 'sign-in' ? 'current-password' : 'new-password'}
                  minLength={mode === 'register' ? 12 : undefined}
                  allowAutofillExtensions={false}
                  disabled={isSubmitting}
                  value={password}
                  onChange={event => {
                    setPassword(event.target.value)
                    setSignInError(null)
                    setRegistrationError(null)
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
              {mode === 'register' && <>
                <p className="sign-in-welcome sign-in-password-help">Password must be at least 12 characters.</p>
                <div className="sign-in-field">
                  <label htmlFor="sign-in-confirm-password">Confirm password</label>
                  <Input id="sign-in-confirm-password" type="password" size="xl" required autoComplete="new-password"
                    disabled={isSubmitting} value={confirmPassword} onChange={event => { setConfirmPassword(event.target.value); setRegistrationError(null) }} />
                </div>
                <div className="sign-in-field">
                  <label htmlFor="sign-in-role">Account role</label>
                  <select id="sign-in-role" className="sign-in-role" required disabled={isSubmitting} value={role}
                    onChange={event => setRole(event.target.value as RegistrationRole)}>
                    <option value="REPORTER">Reporter</option>
                    <option value="DEVELOPER">Developer</option>
                    <option value="QA_REVIEWER">QA reviewer</option>
                  </select>
                </div>
              </>}
              {(signInError || registrationError) && <p className="sign-in-error" role="alert">{mode === 'sign-in' ? signInError : registrationError}</p>}
              <Button
                ref={submitButton}
                type="submit"
                color="primary"
                variant="solid"
                size="xl"
                block
                disabled={isSubmitting}
              >
                {isSubmitting ? (mode === 'sign-in' ? 'Signing in…' : 'Creating account…') : (mode === 'sign-in' ? 'Sign in' : 'Create account')}
              </Button>
            </form>
            <p className="sign-in-mode-switch">
              {mode === 'sign-in' ? 'New to TraceDesk?' : 'Already have an account?'}{' '}
              <button type="button" disabled={isSubmitting} onClick={() => changeMode(mode === 'sign-in' ? 'register' : 'sign-in')}>
                {mode === 'sign-in' ? 'Create account' : 'Sign in'}
              </button>
            </p>
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
