import { useRef, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Check, Code, FileDocument } from '@openai/apps-sdk-ui/components/Icon'
import { AnimatedPopover } from '../../shared/AnimatedPopover'
import type { QaBug } from './qaFixtures'
import type { QATab } from './QaReviewWorkspace'

export function QaBugOverview({ bug, onTabChange }: { bug: QaBug; onTabChange: (tab: QATab) => void }) {
  const [open, setOpen] = useState(false)
  const destination = useRef<string | null>(null)
  const shortcuts = [
    { label: 'Original report', id: 'qa-review-report', icon: <FileDocument />, tab: 'Summary' as const },
    ...(bug.logs
      ? [{ label: 'Report logs', id: 'qa-report-logs', icon: <FileDocument />, tab: 'Summary' as const }]
      : []),
    ...(bug.entries.some((entry) => entry.title === 'Proposed fix')
      ? [{ label: 'Proposed fix', id: 'qa-review-fix', icon: <Code />, tab: 'Summary' as const }]
      : []),
    { label: 'Verification', id: 'qa-tab-1', icon: <Check />, tab: 'Verification' as const },
  ] satisfies { label: string; id: string; icon: React.ReactNode; tab: QATab }[]

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
        const id = destination.current
        destination.current = null
        const target = id && document.getElementById(id)
        if (!target) return
        event.preventDefault()
        if (target instanceof HTMLDetailsElement) {
          target.open = true
          target.querySelector('summary')?.focus()
        } else {
          target.focus()
        }
      }}
    >
      <section aria-label="Review items">
        <h2>Review</h2>
        <ul>
          {shortcuts.map(({ label, id, icon, tab }) => (
            <li key={id}>
              <button
                type="button"
                className="summary-item"
                onClick={() => {
                  destination.current = id
                  onTabChange(tab)
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
