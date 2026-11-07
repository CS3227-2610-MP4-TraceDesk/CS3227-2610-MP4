import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { QaReviewChat } from './QaReviewChat'
import { QaReviewWorkspace } from './QaReviewWorkspace'
import { initialReview, type useQaPreview } from './useQaPreview'
import type { QaBug } from './qaFixtures'

export function QaBugReview({ bug, qa }: { bug: QaBug; qa: ReturnType<typeof useQaPreview> }) {
  const id = bug.id
  const review = qa.reviews[id] ?? initialReview
  const editable = bug.status === 'Ready for QA'
  const chat = review.chatOpen && (
    <QaReviewChat
      bugId={id}
      turns={review.turns}
      draft={review.draft}
      editable={editable}
      existingChecks={bug.checks.map((check) => check.title)}
      onDraftChange={(draft) => qa.updateReview(id, (current) => ({ ...current, draft }))}
      onSend={(question) => qa.askAboutBug(bug, question)}
      onCollapse={() => qa.updateReview(id, (current) => ({ ...current, chatOpen: false }))}
      onAddChecks={(titles) => qa.addSuggestedChecks(id, titles)}
    />
  )

  const comment = (
    <form
      className="qa-inline-comment"
      onSubmit={(event) => {
        event.preventDefault()
        qa.postComment(id, review.comment, review.asking)
      }}
    >
      <Textarea
        variant="soft"
        rows={2}
        aria-label={review.asking ? 'Clarification request' : 'Review comment'}
        placeholder={review.asking ? 'What needs clarification?' : 'Leave a comment…'}
        value={review.comment}
        onChange={(event) => {
          const value = event.target.value
          qa.updateReview(id, (current) => ({ ...current, comment: value }))
        }}
      />
      <div className="qa-comment-actions">
        <Button
          color="secondary"
          variant="ghost"
          size="sm"
          aria-pressed={review.asking}
          disabled={!editable}
          onClick={() => qa.updateReview(id, (current) => ({ ...current, asking: !current.asking }))}
        >
          Request clarification
        </Button>
        <Button color="primary" size="sm" type="submit" disabled={!review.comment.trim()}>
          {review.asking ? 'Send request' : 'Post comment'}
        </Button>
      </div>
    </form>
  )

  return (
    <QaReviewWorkspace
      bug={bug}
      templates={qa.templates}
      tab={review.tab}
      view={review.view}
      chat={chat}
      comment={comment}
      onTabChange={(tab) => qa.updateReview(id, (current) => ({ ...current, tab }))}
      onViewChange={(view) => qa.updateReview(id, (current) => ({ ...current, view }))}
      onReviewWithAI={() => qa.openReview(bug)}
      onChecksChange={(checks) =>
        qa.updateBug(id, (current) => (current.status === 'Ready for QA' ? { ...current, checks } : current))
      }
      onNotesChange={(verificationNotes) =>
        qa.updateBug(id, (current) =>
          current.status === 'Ready for QA' ? { ...current, verificationNotes } : current,
        )
      }
      onFinish={(close, reason) => qa.finishReview(id, close, reason)}
    />
  )
}
