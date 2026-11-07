import { useState } from 'react'
import type { ReviewTurn } from './QaReviewChat'
import type { CheckLayout, QATab } from './QaReviewWorkspace'
import { qaBugs, type QaBug, type VerificationCheck } from './qaFixtures'
import { sampleTemplates } from './qaTemplateFixtures'

type ReviewState = {
  tab: QATab
  view: CheckLayout
  chatOpen: boolean
  draft: string
  turns: ReviewTurn[]
  comment: string
  asking: boolean
}

export const initialReview: ReviewState = {
  tab: 'Summary',
  view: 'Board',
  chatOpen: false,
  draft: '',
  turns: [],
  comment: '',
  asking: false,
}

const normalize = (title: string) => title.trim().toLowerCase()

export function canCloseReview(checks: VerificationCheck[]) {
  const names = checks.map((check) => normalize(check.title))
  return (
    names.length > 0 &&
    names.every(Boolean) &&
    new Set(names).size === names.length &&
    checks.every((check) => check.result === 'Pass')
  )
}

function draftReview(bug: QaBug): ReviewTurn {
  const fix = bug.entries.filter((entry) => entry.title === 'Proposed fix').at(-1)
  const answer = [
    fix ? `Proposed fix: ${fix.text}` : 'Ask for a proposed fix before verification.',
    `Start with the original reproduction steps: ${bug.steps}`,
    !bug.logs?.trim() && 'No reporter logs were supplied.',
    fix &&
      !/build|test|confirmed|verified/i.test(fix.text) &&
      'The proposed fix does not specify testing evidence.',
  ]
    .filter(Boolean)
    .join(' ')

  return {
    question: `Review the fix for ${bug.id}. Suggest regression checks and flag missing evidence.`,
    answer,
    checks: [
      `Reproduce: ${bug.steps}`,
      `Verify: ${bug.expected}`,
      'Repeat the operation and check for duplicate or stale results',
    ],
  }
}

export function useQaPreview() {
  const [bugs, setBugs] = useState(qaBugs)
  const [templates, setTemplates] = useState(sampleTemplates)
  const [reviews, setReviews] = useState<Partial<Record<string, ReviewState>>>({})

  function updateBug(id: string, update: (bug: QaBug) => QaBug) {
    setBugs((current) => current.map((bug) => (bug.id === id ? update(bug) : bug)))
  }

  function updateReview(id: string, update: (review: ReviewState) => ReviewState) {
    setReviews((current) => ({ ...current, [id]: update(current[id] ?? initialReview) }))
  }

  function openReview(bug: QaBug) {
    updateReview(bug.id, (review) => ({
      ...review,
      chatOpen: true,
      turns: review.turns.some((turn) => turn.checks) ? review.turns : [...review.turns, draftReview(bug)],
    }))
  }

  function askAboutBug(bug: QaBug, question: string) {
    const summary = draftReview(bug)
    const turn = /check|test|regression/i.test(question)
      ? { ...summary, question }
      : { question, answer: `${summary.answer} Expected behavior: ${bug.expected}` }
    updateReview(bug.id, (review) => ({ ...review, draft: '', turns: [...review.turns, turn] }))
  }

  function addSuggestedChecks(id: string, titles: string[]) {
    updateBug(id, (bug) => {
      if (bug.status !== 'Ready for QA') return bug
      const existing = new Set(bug.checks.map((check) => normalize(check.title)))
      const additions: VerificationCheck[] = []
      for (const title of titles) {
        const name = normalize(title)
        if (!name || existing.has(name)) continue
        existing.add(name)
        additions.push({ title: title.trim(), result: 'Pending' })
      }
      return { ...bug, checks: [...bug.checks, ...additions] }
    })
  }

  function postComment(id: string, text: string, asking: boolean) {
    if (!text.trim()) return
    const title = asking ? 'Clarification requested' : 'Review note'
    updateBug(id, (bug) =>
      asking && bug.status !== 'Ready for QA'
        ? bug
        : {
            ...bug,
            entries: [...bug.entries, { role: 'QA reviewer', title, text: text.trim() }],
          },
    )
    updateReview(id, (review) => ({ ...review, comment: '', asking: false }))
  }

  function finishReview(id: string, close: boolean, reason: string) {
    updateBug(id, (bug) => {
      if (bug.status !== 'Ready for QA' || (close ? !canCloseReview(bug.checks) : !reason.trim())) return bug
      const results = bug.checks
        .map((check) => [`${check.result}: ${check.title}`, check.notes?.trim()].filter(Boolean).join('\n'))
        .join('\n')
      const text = [
        close ? 'All verification checks passed.' : reason.trim(),
        results,
        bug.verificationNotes?.trim(),
      ]
        .filter(Boolean)
        .join('\n\n')
      return {
        ...bug,
        status: close ? 'Closed' : 'Reopened',
        reviewedByMe: true,
        entries: [
          ...bug.entries,
          {
            role: 'QA reviewer',
            title: close ? 'Verified and closed' : 'Reopened',
            text,
          },
        ],
      }
    })
  }

  return {
    bugs,
    templates,
    setTemplates,
    reviews,
    updateBug,
    updateReview,
    openReview,
    askAboutBug,
    addSuggestedChecks,
    postComment,
    finishReview,
  }
}
