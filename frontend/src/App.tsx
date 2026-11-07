import { useEffect, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { SignIn } from './login/SignIn'

function StarterWorkspace() {
  return (
    <main className="demo-workspace">
      <p className="eyebrow">TraceDesk</p>
      <h1>Software bugs, from report to verification</h1>
      <p>
        Reporter, Developer, and QA reviewer workflows are being built on a shared
        security and testing foundation.
      </p>
      <section aria-labelledby="workflow-heading">
        <h2 id="workflow-heading">Planned workflow</h2>
        <ol>
          <li>Report a bug</li>
          <li>Investigate and propose a resolution</li>
          <li>Verify, close, or reopen</li>
        </ol>
      </section>
    </main>
  )
}

export function App() {
  const [theme, setTheme] = useState<'dark' | 'light'>('dark')
  const [previewRole, setPreviewRole] = useState<string | null>(null)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])

  if (import.meta.env.DEV && previewRole) {
    return <>
      <div className="demo-workspace" aria-label="Development preview">
        <p>Demo account · {previewRole} · Local preview</p>
        <Button color="secondary" variant="outline" onClick={() => setPreviewRole(null)}>Sign out</Button>
      </div>
      <StarterWorkspace />
    </>
  }

  return <SignIn theme={theme} onToggleTheme={() => setTheme(theme === 'dark' ? 'light' : 'dark')} onSignIn={setPreviewRole} />
}
