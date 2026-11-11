import { Bug, Check, FileDocument, Folder, Home, Settings, Sun } from '@openai/apps-sdk-ui/components/Icon'
import type { TrayItem } from '../shell/IconTray'
import type { demoAccounts } from '../login/fixtures/demoAccounts'

export type PreviewRole = typeof demoAccounts[number]['role']

export type PreviewGroup = {
  label: string
  rows: readonly TrayItem[]
  emptyMessage?: string
}

type PreviewArea = TrayItem & {
  groups?: readonly PreviewGroup[]
}

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
    { id: 'verification', label: 'Verification', icon: <Check /> },
    { id: 'templates', label: 'Test templates', icon: <FileDocument /> },
  ],
}

export const settingsArea: PreviewArea = {
  id: 'settings', label: 'Settings', icon: <Settings />,
  groups: [{ label: 'Preferences', rows: [
    { id: 'appearance', label: 'Appearance', icon: <Sun /> },
    { id: 'account', label: 'Account', icon: <Settings /> },
  ] }],
}
