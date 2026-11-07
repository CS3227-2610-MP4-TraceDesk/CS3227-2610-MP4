import { Badge } from '@openai/apps-sdk-ui/components/Badge'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { SegmentedControl } from '@openai/apps-sdk-ui/components/SegmentedControl'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { Check, Clock, CloseBold } from '@openai/apps-sdk-ui/components/Icon'
import type { VerificationCheck } from './qaFixtures'

const columns = [
  { result: 'Pending', title: 'Pending', empty: 'No pending checks', color: 'secondary', icon: Clock },
  { result: 'Pass', title: 'Passed', empty: 'No passed checks', color: 'success', icon: Check },
  { result: 'Fail', title: 'Failed', empty: 'No failed checks', color: 'danger', icon: CloseBold },
] as const

export function VerificationBoard({
  checks,
  selectedIndex,
  onSelect,
}: {
  checks: VerificationCheck[]
  selectedIndex: number | null
  onSelect: (index: number) => void
}) {
  return (
    <div className="qa-board" aria-label="Verification board">
      {columns.map((column) => {
        const items = checks
          .map((check, index) => ({ check, index }))
          .filter((item) => item.check.result === column.result)
        const Icon = column.icon
        return (
          <section className="qa-board-column" key={column.result} aria-label={column.title}>
            <div className="qa-board-column-heading">
              <h3>
                <Icon />
                {column.title}
              </h3>
              <Badge color={column.color}>{items.length}</Badge>
            </div>
            {items.map(({ check, index }) => (
              <Button
                className="qa-board-card"
                data-result={check.result}
                key={index}
                color="secondary"
                variant="ghost"
                block
                pill={false}
                aria-pressed={selectedIndex === index}
                onClick={() => onSelect(index)}
              >
                <span className="qa-board-card-content">
                  <span>{check.title || 'Untitled check'}</span>
                  <Badge color={column.color}>
                    <Icon />
                    {column.title}
                  </Badge>
                </span>
              </Button>
            ))}
            {!items.length && <p className="muted">{column.empty}</p>}
          </section>
        )
      })}
    </div>
  )
}

export function VerificationCheckInspector({
  checks,
  selectedIndex,
  editable,
  onChecksChange,
  onViewThread,
  onDeselect,
}: {
  checks: VerificationCheck[]
  selectedIndex: number | null
  editable: boolean
  onChecksChange: (checks: VerificationCheck[]) => void
  onViewThread: () => void
  onDeselect: () => void
}) {
  const selected = selectedIndex === null ? undefined : checks[selectedIndex]

  function updateSelected(change: Partial<VerificationCheck>) {
    if (!editable || selectedIndex === null || !selected) return
    onChecksChange(checks.map((check, index) => (index === selectedIndex ? { ...check, ...change } : check)))
  }

  if (!selected) return null

  return (
    <section className="qa-check-inspector" aria-label="Selected check details">
      <div className="qa-inspector-heading">
        <h3>Check details</h3>
        <Button
          className="qa-toolbar-action"
          color="secondary"
          variant="ghost"
          size="sm"
          onClick={onViewThread}
        >
          Open discussion
        </Button>
      </div>
      <label htmlFor="selected-check-title">Check</label>
      <Input
        id="selected-check-title"
        aria-label="Selected check title"
        variant="soft"
        size="lg"
        value={selected.title}
        readOnly={!editable}
        onChange={(event) => updateSelected({ title: event.target.value, result: 'Pending' })}
      />
      {selected.preconditions && (
        <div className="qa-check-guidance">
          <h4>Preconditions</h4>
          <p>{selected.preconditions}</p>
        </div>
      )}
      {selected.steps && (
        <div className="qa-check-guidance">
          <h4>Steps</h4>
          <p>{selected.steps}</p>
        </div>
      )}
      {selected.expectedResult && (
        <div className="qa-check-guidance">
          <h4>Expected result</h4>
          <p>{selected.expectedResult}</p>
        </div>
      )}
      <SegmentedControl
        className="qa-result-control"
        size="sm"
        pill={false}
        aria-label="Selected check result"
        value={selected.result}
        disabled={!editable}
        onChange={(result) => updateSelected({ result })}
      >
        <SegmentedControl.Option value="Pending">Pending</SegmentedControl.Option>
        <SegmentedControl.Option value="Pass">Pass</SegmentedControl.Option>
        <SegmentedControl.Option value="Fail">Fail</SegmentedControl.Option>
      </SegmentedControl>
      <label htmlFor="selected-check-notes">Check notes</label>
      <Textarea
        id="selected-check-notes"
        variant="soft"
        size="md"
        rows={3}
        value={selected.notes ?? ''}
        readOnly={!editable}
        onChange={(event) => updateSelected({ notes: event.target.value })}
      />
      {editable && (
        <Button
          color="danger"
          variant="ghost"
          size="sm"
          onClick={() => {
            onChecksChange(checks.filter((_, index) => index !== selectedIndex))
            onDeselect()
          }}
        >
          Remove check
        </Button>
      )}
    </section>
  )
}
