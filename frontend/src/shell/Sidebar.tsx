import type { ReactNode, Ref } from 'react'
import { Disclosure } from '../shared/Disclosure'

export type SidebarContent = {
  title: string
  children: ReactNode
  footer?: ReactNode
}

type SidebarProps = SidebarContent & {
  id: string
  hidden: boolean
  inactive: boolean
  sidebarRef: Ref<HTMLElement>
  resizeHandle?: ReactNode
}

export function Sidebar({ id, title, children, footer, hidden, inactive, sidebarRef, resizeHandle }: SidebarProps) {
  return (
    <aside
      id={id}
      ref={sidebarRef}
      className="shell-sidebar-track"
      aria-label={title}
      tabIndex={-1}
      hidden={hidden}
      inert={inactive}
    >
      <div className="shell-sidebar-clip">
        <div className="shell-sidebar">
          <div className="shell-sidebar-title">{title}</div>
          <nav className="shell-sidebar-items" aria-label={`Items in ${title}`}>{children}</nav>
          {footer && <div className="shell-sidebar-footer">{footer}</div>}
        </div>
      </div>
      {resizeHandle}
    </aside>
  )
}

export function SidebarGroup({ title, children, collapsible, defaultOpen = true }: {
  title: string
  children: ReactNode
  collapsible?: boolean
  defaultOpen?: boolean
}) {
  if (collapsible) return (
    <section className="shell-sidebar-group">
      <Disclosure className="shell-sidebar-group-disclosure" label={<h2>{title}</h2>} open={defaultOpen}>
        {children}
      </Disclosure>
    </section>
  )
  return (
    <section className="shell-sidebar-group">
      <h2>{title}</h2>
      {children}
    </section>
  )
}

type SidebarItemProps = {
  label: string
  icon: ReactNode
  selected: boolean
  onSelect: () => void
}

export function SidebarItem({ label, icon, selected, onSelect }: SidebarItemProps) {
  return (
    <button
      className="shell-sidebar-item"
      type="button"
      aria-current={selected ? 'page' : undefined}
      title={label}
      onClick={onSelect}
    >
      {icon}
      <span>{label}</span>
    </button>
  )
}
