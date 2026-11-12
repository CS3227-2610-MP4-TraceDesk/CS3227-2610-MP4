import { useRef, useState, type ReactNode } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Check, Code, FileDocument } from '@openai/apps-sdk-ui/components/Icon'
import { AnimatedPopover } from '../shared/AnimatedPopover'

type QaContextShortcutsProps = {
  hasLogs: boolean
  hasResolution: boolean
  onNavigate: (sectionId: string) => void
}

type Shortcut = {
  label: string
  sectionId: string
  icon: ReactNode
}

export function QaContextShortcuts({ hasLogs, hasResolution, onNavigate }: QaContextShortcutsProps) {
  const [open, setOpen] = useState(false)
  const destination = useRef<string | null>(null)

  const shortcuts: Shortcut[] = [
    { label: 'Original report', sectionId: 'qa-review-report', icon: <FileDocument /> },
    ...(hasLogs ? [{ label: 'Report logs', sectionId: 'qa-report-logs', icon: <FileDocument /> }] : []),
    ...(hasResolution ? [
      { label: 'Developer resolutions', sectionId: 'qa-review-fix', icon: <Code /> },
      { label: 'Verification', sectionId: 'qa-verification-section', icon: <Check /> },
    ] : []),
  ]

  return (
    <AnimatedPopover
      label="Bug overview"
      className="ticket-summary"
      width={300}
      open={open}
      onOpenChange={setOpen}
      trigger={
        <Button
          className="qa-overview-trigger"
          color="secondary"
          variant="ghost"
          uniform
          size="sm"
          aria-label="Bug overview"
        >
          <svg
            aria-hidden="true"
            viewBox="0 0 20 20"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.25"
            strokeLinecap="round"
          >
            <circle cx="4.5" cy="5.5" r="2" />
            <circle cx="4.5" cy="14.5" r="2" />
            <path d="M10 5.5h7m-7 9h7" />
          </svg>
        </Button>
      }
      onCloseAutoFocus={(event) => {
        const sectionId = destination.current
        destination.current = null
        if (!sectionId) return

        event.preventDefault()
        onNavigate(sectionId)
      }}
    >
      <section aria-label="Review items">
        <h2>Review</h2>
        <ul>
          {shortcuts.map(({ label, sectionId, icon }) => (
            <li key={sectionId}>
              <button
                type="button"
                className="summary-item"
                onClick={(event) => {
                  event.preventDefault()
                  destination.current = sectionId
                  setOpen(false)
                }}
              >
                {icon}
                <span>{label}</span>
              </button>
            </li>
          ))}
        </ul>
      </section>
    </AnimatedPopover>
  )
}
