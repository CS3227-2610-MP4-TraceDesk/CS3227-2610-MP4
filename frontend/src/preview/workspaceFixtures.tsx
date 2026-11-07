import { Bug, Check, FileDocument, Folder, Home, Settings, Sun } from '@openai/apps-sdk-ui/components/Icon'
import type { TrayItem } from '../shell/IconTray'
import type { demoAccounts } from '../login/fixtures/demoAccounts'
import { qaBugs, qaQueues } from './qa/qaFixtures'

export type PreviewRole = typeof demoAccounts[number]['role']

export type PreviewGroup = {
  label: string
  rows: readonly TrayItem[]
  emptyMessage?: string
}

type PreviewArea = TrayItem & {
  groups?: readonly PreviewGroup[]
}

const environmentIcon = (
  <svg aria-hidden="true" viewBox="0 0 20 20" fill="none" stroke="currentColor"
    strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="3" width="16" height="11" rx="2" />
    <path d="M7 17h6m-3-3v3" />
  </svg>
)

const refundReport = { id: 'TD-023', label: 'Refund totals are duplicated', icon: <Bug /> }

export const previewAreas: Record<PreviewRole, readonly [PreviewArea, ...PreviewArea[]]> = {
  Reporter: [
    {
      id: 'reports', label: 'Reports', icon: <Bug />,
      groups: [
        { label: 'Submitted', rows: [
          refundReport,
          { id: 'TD-019', label: 'Search resets on page change', icon: <Bug /> },
        ] },
        { label: 'Drafts', rows: [], emptyMessage: 'No saved drafts' },
      ],
    },
    {
      id: 'environments', label: 'Environments', icon: environmentIcon,
      groups: [{ label: 'Saved setups', rows: [
        { id: 'desktop-chrome', label: 'Desktop Chrome', icon: environmentIcon },
      ] }],
    },
  ],
  Developer: [
    {
      id: 'my-work', label: 'My work', icon: <Home />,
      groups: [
        { label: 'Manage', rows: [
          { id: 'assigned', label: 'Assigned to me', icon: <Bug /> },
          { id: 'reopened', label: 'Reopened', icon: <Check /> },
          { id: 'awaiting-qa', label: 'Awaiting QA', icon: <Check /> },
        ] },
        { label: 'Projects', rows: [
          { id: 'payments', label: 'Payments', icon: <Folder /> },
        ] },
      ],
    },
    { id: 'all-bugs', label: 'All bugs', icon: <Bug /> },
  ],
  'QA reviewer': [
    {
      id: 'verification', label: 'Verification', icon: <Check />,
      groups: [
        { label: qaQueues[0].label, rows: qaBugs.filter(qaQueues[0].matches).map(bug => ({ id: bug.id, label: bug.title, icon: <Bug /> })), emptyMessage: 'Nothing awaiting verification' },
        { label: qaQueues[1].label, rows: qaBugs.filter(qaQueues[1].matches).map(bug => ({ id: bug.id, label: bug.title, icon: <Bug /> })), emptyMessage: 'No completed reviews yet' },
      ],
    },
    {
      id: 'templates', label: 'Test templates', icon: <FileDocument />,
      groups: [{ label: 'Saved templates', rows: [
        { id: 'export', label: 'Export checks', icon: <FileDocument /> },
        { id: 'search', label: 'Search checks', icon: <FileDocument /> },
      ] }],
    },
  ],
}

export const settingsArea: PreviewArea = {
  id: 'settings', label: 'Settings', icon: <Settings />,
  groups: [{ label: 'Preferences', rows: [
    { id: 'appearance', label: 'Appearance', icon: <Sun /> },
    { id: 'account', label: 'Account', icon: <Settings /> },
  ] }],
}
