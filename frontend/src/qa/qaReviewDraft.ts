import type { Verification } from './verificationApi'

export type QaReviewDraft = {
  verifying: boolean
  review: Verification | null
  saved: Verification | null
  title: string
  reason: string
}

export const emptyQaReviewDraft: QaReviewDraft = {
  verifying: false,
  review: null,
  saved: null,
  title: '',
  reason: '',
}

export function hasQaVerificationEdits(draft: QaReviewDraft): boolean {
  return draft.review !== draft.saved || Boolean(draft.title || draft.reason)
}
