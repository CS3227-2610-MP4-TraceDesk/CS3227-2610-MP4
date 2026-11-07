import type { ComponentProps, ReactNode } from 'react'
import { ChevronRight } from '@openai/apps-sdk-ui/components/Icon'
import './opening-motion.css'

type DisclosureProps = Omit<ComponentProps<'details'>, 'children'> & {
  label: ReactNode
  children: ReactNode
}

export function Disclosure({ label, children, className, ...props }: DisclosureProps) {
  return (
    <details {...props} className={className ? `shared-disclosure ${className}` : 'shared-disclosure'}>
      <summary>
        <ChevronRight className="shared-disclosure-chevron" aria-hidden="true" />
        {label}
      </summary>
      {children}
    </details>
  )
}
