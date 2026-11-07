import { useRef, useState, type FormEvent } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { ChevronRight, Eye, EyeOff, Moon, Sun } from '@openai/apps-sdk-ui/components/Icon'
import beetle from '../../public/brand/tracedesk-beetle.svg?raw'
import { demoAccounts as previewAccounts, demoPassword as previewPassword } from './fixtures/demoAccounts'
import { useMascotMotion } from './useMascotMotion'

const demoAccounts = import.meta.env.DEV ? previewAccounts : []
const demoPassword = import.meta.env.DEV ? previewPassword : ''
const mascotMarkup = { __html: beetle }

type DemoAccount = typeof demoAccounts[number]

type SignInProps = {
  theme: 'dark' | 'light'
  onToggleTheme: () => void
  onSignIn: (role: DemoAccount['role']) => void
}

export function SignIn({ theme, onToggleTheme, onSignIn }: SignInProps) {
  const { screen, mascot } = useMascotMotion()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [hasSignInError, setHasSignInError] = useState(false)
  const submitButton = useRef<HTMLButtonElement>(null)
  const demoAccountDisclosure = useRef<HTMLDetailsElement>(null)

  function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const account = demoAccounts.find(item => item.email === email.trim().toLowerCase())

    if (import.meta.env.DEV && account && password === demoPassword) {
      onSignIn(account.role)
    } else {
      setHasSignInError(true)
    }
  }

  function fillDemoAccount(account: DemoAccount) {
    setEmail(account.email)
    setPassword(demoPassword)
    setShowPassword(false)
    setHasSignInError(false)
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
            <form className="sign-in-form" onSubmit={signIn}>
              <div className="sign-in-field">
                <label htmlFor="sign-in-email">Email</label>
                <Input
                  id="sign-in-email"
                  type="email"
                  size="xl"
                  required
                  autoComplete="off"
                  value={email}
                  onChange={event => {
                    setEmail(event.target.value)
                    setHasSignInError(false)
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
                  autoComplete="off"
                  allowAutofillExtensions={false}
                  value={password}
                  onChange={event => {
                    setPassword(event.target.value)
                    setHasSignInError(false)
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
              {hasSignInError && (
                <p className="sign-in-error" role="alert">
                  {import.meta.env.DEV
                    ? 'That email and password didn’t match a demo account.'
                    : 'Sign-in is not connected yet.'}
                </p>
              )}
              <Button
                ref={submitButton}
                type="submit"
                color="primary"
                variant="solid"
                size="xl"
                block
              >
                Sign in
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
