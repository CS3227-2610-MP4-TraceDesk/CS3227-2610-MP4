import type { ReactNode } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Settings } from '@openai/apps-sdk-ui/components/Icon'

export type TrayItem = {
  id: string
  label: string
  icon: ReactNode
}

type IconTrayProps = {
  items: readonly TrayItem[]
  activeId: string
  onSelect: (id: string) => void
}

export function IconTray({ items, activeId, onSelect }: IconTrayProps) {
  const settings = { id: 'settings', label: 'Settings', icon: <Settings /> }

  return (
    <nav className="icon-tray" aria-label="Main navigation">
      {[...items, settings].map(({ id, label, icon }) => (
        <Button
          key={id}
          className={id === settings.id ? 'icon-tray-settings' : undefined}
          color="secondary"
          variant="ghost"
          uniform
          selected={activeId === id}
          aria-current={activeId === id ? 'page' : undefined}
          aria-label={label}
          title={label}
          onClick={() => onSelect(id)}
        >
          {icon}
        </Button>
      ))}
    </nav>
  )
}
