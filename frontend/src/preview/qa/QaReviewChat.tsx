import { useEffect, useRef, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Checkbox } from '@openai/apps-sdk-ui/components/Checkbox'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { ArrowUp, CloseBold, Sparkle } from '@openai/apps-sdk-ui/components/Icon'

export type ReviewTurn = { question: string; answer: string; checks?: string[] }

export function QaReviewChat({
  bugId,
  turns,
  draft,
  onDraftChange,
  onSend,
  onCollapse,
  onAddChecks,
  existingChecks,
  editable,
}: {
  bugId: string
  turns: ReviewTurn[]
  draft: string
  onDraftChange: (draft: string) => void
  onSend: (question: string) => void
  onCollapse: () => void
  onAddChecks: (checks: string[]) => void
  existingChecks: string[]
  editable: boolean
}) {
  const [selected, setSelected] = useState<Record<number, string[]>>({})
  const log = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (log.current) log.current.scrollTop = log.current.scrollHeight
  }, [turns.length])

  return (
    <section className="qa-review-chat" aria-label={`AI review for ${bugId}`}>
      <header className="qa-chat-header">
        <span className="qa-chat-tab">
          <Sparkle />
          Review {bugId}
        </span>
        <Button
          color="secondary"
          variant="ghost"
          size="xs"
          uniform
          aria-label="Collapse AI review"
          onClick={onCollapse}
        >
          <CloseBold />
        </Button>
      </header>
      <div className="qa-chat-messages" ref={log} role="log" aria-label="AI review conversation">
        {turns.map((turn, index) => {
          const available = (turn.checks ?? []).filter(
            (check) => !existingChecks.some((title) => title.trim().toLowerCase() === check.toLowerCase()),
          )
          const chosen = (selected[index] ?? []).filter((check) => available.includes(check))
          return (
            <div key={index} className="qa-chat-turn">
              <p className="qa-chat-question">{turn.question}</p>
              <div className="qa-chat-answer">
                <p className="qa-chat-attribution">Simulated AI</p>
                <p>{turn.answer}</p>
                {editable && available.length > 0 && (
                  <div className="qa-chat-checks">
                    {available.map((check) => (
                      <Checkbox
                        key={check}
                        label={check}
                        checked={chosen.includes(check)}
                        onCheckedChange={(checked) =>
                          setSelected((current) => ({
                            ...current,
                            [index]: checked ? [...chosen, check] : chosen.filter((item) => item !== check),
                          }))
                        }
                      />
                    ))}
                    <Button
                      color="secondary"
                      variant="soft"
                      size="sm"
                      disabled={!chosen.length}
                      onClick={() => onAddChecks(chosen)}
                    >
                      Add selected checks
                    </Button>
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>
      <form
        className="composer qa-chat-composer"
        onSubmit={(event) => {
          event.preventDefault()
          if (!draft.trim()) return
          onSend(draft.trim())
          onDraftChange('')
        }}
      >
        <Textarea
          variant="soft"
          rows={1}
          aria-label="Ask about this bug"
          placeholder="Ask about this bug…"
          value={draft}
          onChange={(event) => onDraftChange(event.target.value)}
        />
        <Button
          color="primary"
          size="sm"
          uniform
          type="submit"
          aria-label="Send AI question"
          disabled={!draft.trim()}
        >
          <ArrowUp />
        </Button>
      </form>
    </section>
  )
}
