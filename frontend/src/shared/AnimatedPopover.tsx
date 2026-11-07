import type { ComponentProps, ReactElement, ReactNode } from 'react'
import { Popover } from 'radix-ui'
import './opening-motion.css'

type AnimatedPopoverProps = {
  trigger: ReactElement
  children: ReactNode
  label: string
  className?: string
  minWidth?: number
  width?: number
  align?: 'start' | 'center' | 'end'
  open?: boolean
  onOpenChange?: (open: boolean) => void
  onCloseAutoFocus?: ComponentProps<typeof Popover.Content>['onCloseAutoFocus']
}

export function AnimatedPopover({ trigger, children, label, className, minWidth = 180, width, align = 'end', open, onOpenChange, onCloseAutoFocus }: AnimatedPopoverProps) {
  return (
    <Popover.Root open={open} onOpenChange={onOpenChange}>
      <Popover.Trigger asChild>{trigger}</Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          aria-label={label}
          className={className ? `shared-popover ${className}` : 'shared-popover'}
          style={{
            minWidth: `min(${minWidth}px, var(--radix-popover-content-available-width))`,
            width: width ? `min(${width}px, var(--radix-popover-content-available-width))` : undefined,
          }}
          align={align}
          onCloseAutoFocus={onCloseAutoFocus}
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
