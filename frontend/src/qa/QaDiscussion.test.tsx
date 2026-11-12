import { afterEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'

const mocks = vi.hoisted(() => ({ fetchQaComments: vi.fn(), postQaComment: vi.fn() }))
vi.mock('./qaDiscussionApi', async original => ({ ...(await original()), ...mocks }))

import { QaDiscussion } from './QaDiscussion'

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

it('keeps the draft when posting fails and displays posted text literally', async () => {
  const text = '<b>Retest with two refunds</b>'
  mocks.fetchQaComments.mockResolvedValue({ items: [], nextCursor: null })
  mocks.postQaComment.mockRejectedValueOnce(new Error('Connection interrupted')).mockResolvedValueOnce({
    id: 'comment', authorUserId: 'reviewer-1', body: text, createdAt: '2026-11-12T00:00:00Z',
  })
  const { container } = render(<QaDiscussion projectId="project" reportId="bug" onSessionExpired={vi.fn()} />)
  await screen.findByText('No comments yet.')
  const field = screen.getByRole('textbox', { name: 'Add a review comment' })
  fireEvent.change(field, { target: { value: text } })
  fireEvent.click(screen.getByRole('button', { name: 'Post comment' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Your draft is still here.')
  expect(field).toHaveValue(text)
  fireEvent.click(screen.getByRole('button', { name: 'Post comment' }))
  expect(await screen.findByText(text)).toBeVisible()
  expect(container.querySelector('.qa-discussion-comment b')).toBeNull()
  expect(field).toHaveValue('')
})
