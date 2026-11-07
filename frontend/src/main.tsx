import './style.css'
import React from 'react'
import ReactDOM from 'react-dom/client'
import { App } from './App'

const WorkspacePreview = import.meta.env.DEV
  && new URLSearchParams(window.location.search).has('workspace-preview')
  ? React.lazy(() => import('./preview/WorkspacePreview'))
  : null

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    {WorkspacePreview ? (
      <React.Suspense fallback={<main role="status">Opening workspace preview…</main>}>
        <WorkspacePreview />
      </React.Suspense>
    ) : <App />}
  </React.StrictMode>,
)
