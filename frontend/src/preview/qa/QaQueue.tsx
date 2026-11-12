import { useState } from 'react'
import { Badge } from '@openai/apps-sdk-ui/components/Badge'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Checkbox } from '@openai/apps-sdk-ui/components/Checkbox'
import { Bug, Filter, Grid, Search, SettingsSlider } from '@openai/apps-sdk-ui/components/Icon'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { AnimatedPopover } from '../../shared/AnimatedPopover'
import { matchesBugSearch, qaQueues, type QaBug } from './qaFixtures'

const activityDate = new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', timeZone: 'UTC' })

function ListIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round">
      <path d="M7 4h10M7 10h10M7 16h10" />
      <circle cx="3" cy="4" r=".5" /><circle cx="3" cy="10" r=".5" /><circle cx="3" cy="16" r=".5" />
    </svg>
  )
}

type QaQueueProps = {
  items: readonly Pick<QaBug, 'id' | 'title' | 'status' | 'lastActivity' | 'reviewedByMe'>[]
  awaitingOnly?: boolean
  allBugs?: boolean
  footer?: React.ReactNode
  filters?: React.ReactNode
  search: string
  onSearch: (value: string) => void
  onOpenBug: (id: string) => void
  hidden: boolean
}

export function QaQueue({ items, search, onSearch, onOpenBug, hidden, awaitingOnly, allBugs, footer, filters }: QaQueueProps) {
  const [queue, setQueue] = useState<(typeof qaQueues)[number]>(qaQueues[0])
  const [layout, setLayout] = useState<'list' | 'grid'>('list')
  const [sort, setSort] = useState<'recent' | 'name'>('recent')
  const [showStatus, setShowStatus] = useState(true)
  const bugs = items.filter(bug => (allBugs || queue.matches(bug)) && matchesBugSearch(bug, search))
    .sort((left, right) => sort === 'name'
      ? left.title.localeCompare(right.title)
      : right.lastActivity.localeCompare(left.lastActivity))

  return (
    <section className="qa-queue-view" aria-labelledby="qa-queue-title" hidden={hidden}>
      <header className="qa-queue-heading">
        <h1 id="qa-queue-title">{allBugs ? 'All bugs' : 'Verification'}</h1>
        <div className="qa-queue-controls">
          <AnimatedPopover label="Sort bugs" className="qa-library-options" trigger={
            <Button color="secondary" variant="ghost" size="md" uniform aria-label="Sort bugs" title="Sort bugs"><Filter /></Button>
          }>
            <section>
              <h2>Sort by</h2>
              <Button color="secondary" variant="ghost" block selected={sort === 'recent'} aria-pressed={sort === 'recent'} onClick={() => setSort('recent')}>Last activity</Button>
              <Button color="secondary" variant="ghost" block selected={sort === 'name'} aria-pressed={sort === 'name'} onClick={() => setSort('name')}>Name</Button>
            </section>
          </AnimatedPopover>
          <div className="qa-view-switch" role="group" aria-label="Bug layout">
            <Button color="secondary" variant="ghost" size="md" uniform selected={layout === 'grid'} aria-pressed={layout === 'grid'} aria-label="Grid view" title="Grid view" onClick={() => setLayout('grid')}><Grid /></Button>
            <Button color="secondary" variant="ghost" size="md" uniform selected={layout === 'list'} aria-pressed={layout === 'list'} aria-label="List view" title="List view" onClick={() => setLayout('list')}><ListIcon /></Button>
          </div>
          <Input className="qa-library-search" type="search" size="md" pill startAdornment={<Search />} aria-label="Find bug" placeholder="Search bugs" value={search} onChange={event => onSearch(event.target.value)} />
          <AnimatedPopover label="Display options" className="qa-library-options" minWidth={200} trigger={
            <Button color="secondary" variant="ghost" size="md" uniform aria-label="Display options" title="Display options"><SettingsSlider /></Button>
          }>
            <section>
              <h2>Display</h2>
              <Checkbox label="Show ticket status" checked={showStatus} onCheckedChange={value => setShowStatus(value === true)} />
            </section>
          </AnimatedPopover>
        </div>
      </header>
      {filters ?? (!awaitingOnly && <div className="qa-queue-tabs" role="group" aria-label="Bug queues">
        {qaQueues.map(item => (
          <Button key={item.label} color="secondary" variant="ghost" size="lg" selected={queue === item} aria-pressed={queue === item} onClick={() => setQueue(item)}>{item.label}</Button>
        ))}
      </div>)}
      {layout === 'list' && <div className="qa-ticket-columns"><span>Name</span><span>Last activity</span></div>}
      <div className={layout === 'grid' ? 'qa-ticket-list qa-ticket-grid' : 'qa-ticket-list'}>
        {bugs.map(bug => (
          <button key={bug.id} type="button" className="qa-ticket-row" onClick={() => onOpenBug(bug.id)}>
            <span className="qa-library-item-icon" aria-hidden="true"><Bug /></span>
            <span className="qa-ticket-name">
              <span title={bug.title}>{bug.title}</span>
              {showStatus && <Badge color={bug.status === 'Ready for QA' || bug.status === 'Needs clarification' ? 'warning' : bug.status === 'Closed' ? 'success' : 'secondary'} pill>{bug.status}</Badge>}
            </span>
            <time dateTime={bug.lastActivity}>{activityDate.format(new Date(bug.lastActivity))}</time>
            <span className="sr-only">{bug.id}</span>
          </button>
        ))}
        {!bugs.length && <p className="muted" role="status">{search.trim() ? 'No matching bugs.' : 'No tickets in this queue.'}</p>}
      </div>
      {footer}
    </section>
  )
}
