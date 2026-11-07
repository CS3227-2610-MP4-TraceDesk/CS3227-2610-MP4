import { VerificationBoard, VerificationCheckInspector } from './QaVerificationBoard'
import { SegmentedControl } from '@openai/apps-sdk-ui/components/SegmentedControl'
import { AnimatedPopover } from '../../shared/AnimatedPopover'
import { Select } from '@openai/apps-sdk-ui/components/Select'
import { useState, type ReactNode } from 'react'
import { QaReviewSummary } from './QaReviewSummary'
import type { QaBug, VerificationCheck } from './qaFixtures'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Badge } from '@openai/apps-sdk-ui/components/Badge'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { Check, Clock, CloseBold, Plus } from '@openai/apps-sdk-ui/components/Icon'
import { canCloseReview } from './useQaPreview'
import type { TestTemplate } from './qaTemplateFixtures'

export type QATab = 'Summary' | 'Verification'
export type CheckLayout = 'Board' | 'List'
const tabs: QATab[] = ['Summary', 'Verification']
const normalized = (title: string) => title.trim().toLowerCase()

export function QaReviewWorkspace({
  bug,
  templates,
  tab,
  onTabChange,
  onReviewWithAI,
  chat,
  comment,
  view,
  onViewChange,
  onChecksChange,
  onNotesChange,
  onFinish,
}: {
  bug: QaBug
  templates: TestTemplate[]
  tab: QATab
  onTabChange: (tab: QATab) => void
  view: CheckLayout
  onViewChange: (view: CheckLayout) => void
  chat: ReactNode
  comment: ReactNode
  onReviewWithAI: () => void
  onChecksChange: (checks: VerificationCheck[]) => void
  onNotesChange: (notes: string) => void
  onFinish: (close: boolean, reason: string) => void
}) {
  const [selectedCheckIndex, setSelectedCheckIndex] = useState<number | null>(null)
  const [addingCheck, setAddingCheck] = useState(false)
  const [newCheck, setNewCheck] = useState('')
  const [templateId, setTemplateId] = useState('')
  const [reopening, setReopening] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const editable = bug.status === 'Ready for QA'
  const completed = bug.checks.filter((check) => check.result !== 'Pending').length
  const validTitles =
    bug.checks.every((check) => check.title.trim()) &&
    new Set(bug.checks.map((check) => normalized(check.title))).size === bug.checks.length
  const allPassed = canCloseReview(bug.checks)
  const template = templates.find((item) => item.id === templateId)
  const missing = (titles: string[]) =>
    titles.filter((title) => !bug.checks.some((check) => normalized(check.title) === normalized(title)))
  function appendChecks(titles: string[], sourceTemplate?: TestTemplate) {
    const seen = new Set(bug.checks.map((check) => normalized(check.title)))
    const additions = titles
      .map((title) => title.trim())
      .filter((title) => {
        if (!title || seen.has(normalized(title))) return false
        seen.add(normalized(title))
        return true
      })
    if (additions.length)
      onChecksChange([
        ...bug.checks,
        ...additions.map((title) => {
          const testCase = sourceTemplate?.testCases?.find(
            (item) => normalized(item.name) === normalized(title),
          )
          return {
            title,
            result: 'Pending' as const,
            preconditions: sourceTemplate?.preconditions,
            steps: testCase?.steps,
            expectedResult: testCase?.expectedResult,
          }
        }),
      ])
  }

  return (
    <div className={`qa-review-layout ${chat ? 'has-review-chat' : ''}`}>
      {chat}
      <section className="qa-review-document" aria-label="Bug review">
        <div className="qa-review-toolbar">
          <div className="qa-section-tabs" role="tablist" aria-label="Bug review sections">
            {tabs.map((item, index) => (
              <Button
                key={item}
                id={`qa-tab-${index}`}
                role="tab"
                aria-controls={`qa-panel-${index}`}
                aria-selected={tab === item}
                tabIndex={tab === item ? 0 : -1}
                color="secondary"
                variant="ghost"
                size="sm"
                selected={tab === item}
                onClick={() => {
                  setAddingCheck(false)
                  onTabChange(item)
                }}
                onKeyDown={(event) => {
                  if (!['ArrowRight', 'ArrowLeft', 'Home', 'End'].includes(event.key)) return
                  event.preventDefault()
                  const next = event.key === 'Home' ? 0 : event.key === 'End' ? 1 : 1 - index
                  setAddingCheck(false)
                  onTabChange(tabs[next])
                  document.getElementById(`qa-tab-${next}`)?.focus()
                }}
              >
                {item}
              </Button>
            ))}
          </div>
          <div className="qa-review-action">
            <Button
              className="qa-toolbar-action"
              color="secondary"
              variant="ghost"
              size="sm"
              onClick={onReviewWithAI}
            >
              Review with AI
            </Button>
          </div>
        </div>
        <div
          className={tab === 'Verification' ? 'qa-verification-content' : 'qa-review-content'}
          role="tabpanel"
          id={`qa-panel-${tabs.indexOf(tab)}`}
          aria-labelledby={`qa-tab-${tabs.indexOf(tab)}`}
          tabIndex={0}
        >
          {tab === 'Summary' ? (
            <QaReviewSummary bug={bug} comment={comment} showChangeMap={!chat} />
          ) : (
            <>
              <div className="qa-review-heading">
                <div className="qa-view-controls">
                  <h2>Verification</h2>
                  <SegmentedControl
                    aria-label="Verification view"
                    value={view}
                    onChange={onViewChange}
                    size="sm"
                    className="qa-view-switch"
                    pill={false}
                  >
                    <SegmentedControl.Option value="Board">Board</SegmentedControl.Option>
                    <SegmentedControl.Option value="List">List</SegmentedControl.Option>
                  </SegmentedControl>
                </div>
                <span className="qa-progress" aria-live="polite">
                  <span>
                    {completed}/{bug.checks.length} checked
                  </span>
                  {bug.checks.some((check) => check.result === 'Fail') && (
                    <Badge color="danger" pill>
                      {bug.checks.filter((check) => check.result === 'Fail').length} failed
                    </Badge>
                  )}
                </span>
              </div>
              {!editable && (
                <p className="muted qa-instruction">
                  This review is {bug.status.toLowerCase()}. Results are read-only.
                </p>
              )}
              {editable && (
                <div className="qa-tools">
                  <div className="qa-tools-leading">
                    <AnimatedPopover
                      open={addingCheck}
                      onOpenChange={(open) => {
                        setAddingCheck(open)
                        if (open) setError('')
                      }}
                      label="Add check"
                      className="qa-add-popover"
                      width={300}
                      align="start"
                      trigger={
                        <Button
                          className="qa-toolbar-action"
                          color="secondary"
                          variant="ghost"
                          size="sm"
                          pill={false}
                        >
                          <Plus />
                          Add check
                        </Button>
                      }
                    >
                      <form
                        className="qa-add-check"
                        onSubmit={(event) => {
                          event.preventDefault()
                          if (!newCheck.trim()) return
                          if (bug.checks.some((check) => normalized(check.title) === normalized(newCheck))) {
                            setError('This check is already in the checklist.')
                            return
                          }
                          appendChecks([newCheck])
                          setNewCheck('')
                          setError('')
                          setAddingCheck(false)
                        }}
                      >
                        <Input
                          variant="soft"
                          id="new-verification-check"
                          aria-label="New check"
                          placeholder="Add a verification check"
                          value={newCheck}
                          onChange={(event) => {
                            setNewCheck(event.target.value)
                            setError('')
                          }}
                        />
                        <Button
                          color="secondary"
                          variant="ghost"
                          uniform
                          type="submit"
                          aria-label="Save new check"
                          disabled={!newCheck.trim()}
                        >
                          <Plus />
                        </Button>
                      </form>
                      {error && (
                        <p role="alert" className="qa-validation">
                          {error}
                        </p>
                      )}
                    </AnimatedPopover>
                  </div>
                  <div className="qa-template-picker" role="group" aria-label="Test template">
                    <label className="sr-only" htmlFor="review-template">
                      Test template
                    </label>
                    <Select
                      id="review-template"
                      value={templateId}
                      options={templates.map((item) => ({ value: item.id, label: item.name }))}
                      onChange={(item) => setTemplateId(item.value)}
                      placeholder="Choose template"
                      variant="ghost"
                      pill={false}
                      dropdownIconType="chevronDown"
                      size="sm"
                      block={false}
                      listMinWidth={180}
                      listMaxWidth={280}
                    />
                    <Button
                      className="qa-toolbar-action"
                      color="secondary"
                      variant="ghost"
                      size="sm"
                      pill={false}
                      disabled={!template || missing(template.checks).length === 0}
                      onClick={() => {
                        if (template) appendChecks(template.checks, template)
                      }}
                    >
                      Apply
                    </Button>
                  </div>
                </div>
              )}
              {view === 'Board' ? (
                <VerificationBoard
                  checks={bug.checks}
                  selectedIndex={selectedCheckIndex}
                  onSelect={setSelectedCheckIndex}
                />
              ) : (
                <div className="qa-checklist qa-content-list" aria-label="Verification checklist">
                  {bug.checks.map((check, index) => (
                    <div
                      className="qa-content-row"
                      key={index}
                      data-selected={selectedCheckIndex === index || undefined}
                    >
                      <Button
                        className="qa-check-title"
                        color="secondary"
                        variant="ghost"
                        pill={false}
                        block
                        aria-label={`Select check ${index + 1}: ${check.title || 'Untitled check'}`}
                        aria-pressed={selectedCheckIndex === index}
                        onClick={() => setSelectedCheckIndex(index)}
                      >
                        <span className="qa-check-title-content">
                          <span className={`qa-result-icon result-${check.result.toLowerCase()}`}>
                            {check.result === 'Pass' ? (
                              <Check />
                            ) : check.result === 'Fail' ? (
                              <CloseBold />
                            ) : (
                              <Clock />
                            )}
                          </span>
                          <span>{check.title || 'Untitled check'}</span>
                        </span>
                      </Button>
                      <div role="group" aria-label={`Result for check ${index + 1}`}>
                        <Select
                          value={check.result}
                          size="sm"
                          pill={false}
                          variant="ghost"
                          dropdownIconType="chevronDown"
                          disabled={!editable}
                          options={(['Pending', 'Pass', 'Fail'] as const).map((value) => ({
                            value,
                            label: value,
                          }))}
                          onChange={(option) =>
                            onChecksChange(
                              bug.checks.map((item, i) =>
                                i === index ? { ...item, result: option.value } : item,
                              ),
                            )
                          }
                          listMinWidth={120}
                        />
                      </div>
                    </div>
                  ))}
                  {!bug.checks.length && (
                    <p className="muted">Add a check, apply a template, or review AI suggestions.</p>
                  )}
                </div>
              )}
              <VerificationCheckInspector
                checks={bug.checks}
                selectedIndex={selectedCheckIndex}
                editable={editable}
                onChecksChange={onChecksChange}
                onViewThread={() => onTabChange('Summary')}
                onDeselect={() => setSelectedCheckIndex(null)}
              />
              <div className="qa-notes">
                <label htmlFor="verification-notes">Verification notes</label>
                <Textarea
                  variant="soft"
                  id="verification-notes"
                  rows={3}
                  placeholder="Tested build, observed results, and supporting evidence…"
                  readOnly={!editable}
                  value={bug.verificationNotes ?? ''}
                  onChange={(event) => onNotesChange(event.target.value)}
                />
              </div>
              {editable && (
                <>
                  <div className="qa-decision">
                    <Button
                      className="qa-toolbar-action"
                      color="secondary"
                      variant="ghost"
                      size="sm"
                      pill={false}
                      onClick={() => setReopening(!reopening)}
                    >
                      Reopen
                    </Button>
                    <Button
                      className="qa-primary-action"
                      color="primary"
                      size="sm"
                      disabled={!allPassed}
                      onClick={() => onFinish(true, '')}
                    >
                      Verify and close
                    </Button>
                  </div>
                  {!allPassed && (
                    <p className="muted qa-validation">
                      {validTitles
                        ? 'Every check must pass before closing the bug.'
                        : 'Each check needs a unique, nonempty description.'}
                    </p>
                  )}
                  {reopening && (
                    <div className="qa-reopen">
                      <label htmlFor="reopening-reason">Reason for reopening</label>
                      <Textarea
                        variant="soft"
                        id="reopening-reason"
                        rows={3}
                        value={reason}
                        onChange={(event) => setReason(event.target.value)}
                        placeholder="Explain what failed or which evidence is missing…"
                      />
                      <Button
                        color="danger"
                        disabled={!reason.trim()}
                        onClick={() => onFinish(false, reason.trim())}
                      >
                        Confirm reopen
                      </Button>
                    </div>
                  )}
                </>
              )}
            </>
          )}
        </div>
      </section>
    </div>
  )
}
