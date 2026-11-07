import type { ReactElement, ReactNode } from 'react'
import { Popover } from 'radix-ui'
import './opening-motion.css'

type AnimatedPopoverProps = {
  trigger: ReactElement
  children: ReactNode
  label: string
  className?: string
  minWidth?: number
}

export function AnimatedPopover({ trigger, children, label, className, minWidth = 180 }: AnimatedPopoverProps) {
  return (
    <Popover.Root>
      <Popover.Trigger asChild>{trigger}</Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          aria-label={label}
          className={className ? `shared-popover ${className}` : 'shared-popover'}
          style={{ minWidth: `min(${minWidth}px, var(--radix-popover-content-available-width))` }}
          align="end"
          sideOffset={8}
          collisionPadding={20}
          hideWhenDetached
        >
          {children}
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  )
}
