import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import './qa-discussion.css'
import { QaDiscussionApiError, fetchQaComments, postQaComment, type QaComment } from './qaDiscussionApi'

type QaDiscussionProps = {
  projectId: string
  reportId: string
  onSessionExpired: () => void
}

function byCreatedAt(left: QaComment, right: QaComment): number {
  return Date.parse(left.createdAt) - Date.parse(right.createdAt)
}

function mergeComments(current: QaComment[], incoming: QaComment[]): QaComment[] {
  const unique = new Map(current.map(comment => [comment.id, comment]))
  for (const comment of incoming) unique.set(comment.id, comment)
  return [...unique.values()].sort(byCreatedAt)
}

function accessDenied(error: unknown): boolean {
  return error instanceof QaDiscussionApiError && [403, 404].includes(error.status) && error.code !== 'CSRF_INVALID'
}

function messageFor(error: unknown, action: 'load' | 'post'): string {
  if (accessDenied(error)) {
    return 'Discussion is not available for this report.'
  }
  return action === 'load' ? 'Could not load discussion comments.' : 'Could not post your comment. Your draft is still here.'
}

export function QaDiscussion(props: QaDiscussionProps) {
  return <DiscussionForReport key={props.projectId + '/' + props.reportId} {...props} />
}

function DiscussionForReport({ projectId, reportId, onSessionExpired }: QaDiscussionProps) {
  const [comments, setComments] = useState<QaComment[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [olderLoading, setOlderLoading] = useState(false)
  const [olderError, setOlderError] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [posting, setPosting] = useState(false)
  const [postError, setPostError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const olderRequest = useRef<AbortController | null>(null)
  const postRequest = useRef<AbortController | null>(null)
  const sessionExpiredHandler = useRef(onSessionExpired)
  useEffect(() => { sessionExpiredHandler.current = onSessionExpired }, [onSessionExpired])

  useEffect(() => {
    const controller = new AbortController()
    void fetchQaComments(projectId, reportId, controller.signal).then(page => {
      if (controller.signal.aborted) return
      setComments(mergeComments([], page.items))
      setNextCursor(page.nextCursor)
    }).catch(error => {
      if (controller.signal.aborted) return
      if (error instanceof QaDiscussionApiError && error.status === 401) sessionExpiredHandler.current()
      setLoadError(messageFor(error, 'load'))
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false)
    })

    return () => {
      controller.abort()
      olderRequest.current?.abort()
      postRequest.current?.abort()
    }
  }, [projectId, reportId, reload])

  async function loadOlder() {
    if (!nextCursor || olderLoading) return
    const controller = new AbortController()
    olderRequest.current?.abort()
    olderRequest.current = controller
    setOlderLoading(true)
    setOlderError(null)

    try {
      const page = await fetchQaComments(projectId, reportId, controller.signal, nextCursor)
      if (controller.signal.aborted) return
      setComments(current => mergeComments(current, page.items))
      setNextCursor(page.nextCursor)
    } catch (error) {
      if (controller.signal.aborted) return
      if (error instanceof QaDiscussionApiError && error.status === 401) sessionExpiredHandler.current()
      if (accessDenied(error)) {
        setComments([])
        setNextCursor(null)
        setDraft('')
        setLoadError(messageFor(error, 'load'))
      } else setOlderError(messageFor(error, 'load'))
    } finally {
      if (!controller.signal.aborted) {
        olderRequest.current = null
        setOlderLoading(false)
      }
    }
  }

  async function submitComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const body = draft.trim()
    if (!body || posting) return

    const controller = new AbortController()
    postRequest.current?.abort()
    postRequest.current = controller
    setPosting(true)
    setPostError(null)

    try {
      const comment = await postQaComment(projectId, reportId, body, controller.signal)
      if (controller.signal.aborted) return
      setComments(current => mergeComments(current, [comment]))
      setDraft(current => current === draft ? '' : current)
    } catch (error) {
      if (controller.signal.aborted) return
      if (error instanceof QaDiscussionApiError && error.status === 401) sessionExpiredHandler.current()
      if (accessDenied(error)) {
        setComments([])
        setNextCursor(null)
        setDraft('')
        setLoadError(messageFor(error, 'post'))
      } else setPostError(messageFor(error, 'post'))
    } finally {
      if (!controller.signal.aborted) {
        postRequest.current = null
        setPosting(false)
      }
    }
  }

  return (
    <section className="qa-discussion" aria-labelledby="qa-discussion-title">
      <h3 id="qa-discussion-title">Discussion</h3>

      {loading ? <p role="status">Loading comments…</p> : loadError ? <div role="alert">
        <p>{loadError}</p>
        <Button type="button" color="secondary" variant="outline" onClick={() => {
          setLoadError(null)
          setLoading(true)
          setReload(value => value + 1)
        }}>
          Retry
        </Button>
      </div> : <>
        {nextCursor && <div className="qa-discussion-older">
          <Button type="button" color="secondary" variant="ghost" disabled={olderLoading} loading={olderLoading} onClick={() => void loadOlder()}>
            Load older comments
          </Button>
          {olderError && <p role="alert">{olderError}</p>}
        </div>}

        {comments.length === 0 ? <p className="muted">No comments yet.</p> : <ol className="qa-discussion-list" aria-label="Discussion comments">
          {comments.map(comment => <li key={comment.id} className="qa-discussion-comment">
            <header>
              <span className="qa-discussion-author" title={comment.authorUserId}>QA reviewer · {comment.authorUserId}</span>
              <time dateTime={comment.createdAt}>{new Date(comment.createdAt).toLocaleString()}</time>
            </header>
            <p>{comment.body}</p>
          </li>)}
        </ol>}
      </>}

      <form className="qa-discussion-composer" onSubmit={event => void submitComment(event)}>
        <label htmlFor="qa-discussion-draft">Add a review comment</label>
        <Textarea id="qa-discussion-draft" rows={3} maxLength={2000} value={draft} disabled={loading || Boolean(loadError) || posting}
          onChange={event => setDraft(event.target.value)} />
        {postError && <p role="alert">{postError}</p>}
        <Button type="submit" color="secondary" variant="soft" disabled={!draft.trim() || loading || Boolean(loadError) || posting} loading={posting}>
          Post comment
        </Button>
      </form>
    </section>
  )
}
