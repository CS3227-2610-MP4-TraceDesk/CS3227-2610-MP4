import { useEffect, useId, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Sidebar as SidebarIcon } from '@openai/apps-sdk-ui/components/Icon'
import { IconTray, type TrayItem } from './IconTray'
import { Sidebar, type SidebarContent } from './Sidebar'
import { SidebarResizeHandle } from './SidebarResizeHandle'
import './app-shell.css'

const mobileSidebarQuery = '(max-width: 700px)'
const defaultSidebarWidth = 275
const minSidebarWidth = 242
const maxSidebarWidth = 472
const minWorkspaceWidth = 240
const desktopShellChrome = 54 // 48px tray, 4px gutter and two 1px frame borders.

function getSidebarWidthLimit() {
  return Math.max(minSidebarWidth, Math.min(
    maxSidebarWidth,
    window.innerWidth - desktopShellChrome - minWorkspaceWidth,
  ))
}

type AppShellProps = {
  items: readonly TrayItem[]
  activeId: string
  onSelect: (id: string) => void
  sidebar?: Omit<SidebarContent, 'children'> & {
    renderItems: (dismissOverlay: () => void) => ReactNode
  }
  headerContent?: ReactNode
  children: ReactNode
}

export function AppShell({ items, activeId, onSelect, sidebar, headerContent, children }: AppShellProps) {
  const sidebarId = useId()
  const sidebarRef = useRef<HTMLElement>(null)
  const toggleRef = useRef<HTMLButtonElement>(null)
  const [isMobile, setIsMobile] = useState(() => window.matchMedia(mobileSidebarQuery).matches)
  const [desktopExpanded, setDesktopExpanded] = useState(true)
  const [preferredWidth, setPreferredWidth] = useState(defaultSidebarWidth)
  const [widthLimit, setWidthLimit] = useState(getSidebarWidthLimit)
  const [overlayState, setOverlayState] = useState<'closed' | 'open' | 'dismissed'>('closed')
  const expanded = Boolean(sidebar) && (isMobile ? overlayState === 'open' : desktopExpanded)
  const overlayVisible = isMobile && expanded
  const sidebarWidth = Math.min(preferredWidth, widthLimit)
  const frameStyle = { '--shell-sidebar-width': `${sidebarWidth}px` } as CSSProperties

  useEffect(() => {
    const query = window.matchMedia(mobileSidebarQuery)
    function syncViewport() {
      setIsMobile(query.matches)
      setOverlayState('closed')
    }
    function syncWidthLimit() {
      setWidthLimit(getSidebarWidthLimit())
    }

    query.addEventListener('change', syncViewport)
    window.addEventListener('resize', syncWidthLimit)
    return () => {
      query.removeEventListener('change', syncViewport)
      window.removeEventListener('resize', syncWidthLimit)
    }
  }, [])

  useEffect(() => {
    if (overlayVisible) sidebarRef.current?.focus()
    else if (overlayState === 'dismissed') toggleRef.current?.focus()
  }, [overlayVisible, overlayState])

  function dismissOverlay() {
    if (!overlayVisible) return
    setOverlayState('dismissed')
  }

  function selectArea(id: string) {
    setOverlayState('closed')
    onSelect(id)
  }

  return (
    <div className="app-shell" onKeyDown={event => {
      if (event.key === 'Escape' && !event.defaultPrevented && overlayVisible) {
        event.preventDefault()
        dismissOverlay()
      }
    }}>
      <IconTray items={items} activeId={activeId} onSelect={selectArea} />
      <div
        className="app-frame"
        data-sidebar-expanded={expanded && !isMobile}
        style={frameStyle}
      >
        {overlayVisible && (
          <button
            className="shell-scrim"
            type="button"
            tabIndex={-1}
            aria-label="Close navigation"
            onClick={dismissOverlay}
          />
        )}
        {sidebar && (
          <Sidebar
            id={sidebarId}
            title={sidebar.title}
            footer={sidebar.footer}
            hidden={isMobile && !expanded}
            inactive={!expanded}
            sidebarRef={sidebarRef}
            resizeHandle={expanded && !isMobile && (
              <SidebarResizeHandle
                sidebarId={sidebarId}
                width={sidebarWidth}
                min={minSidebarWidth}
                max={widthLimit}
                onResize={setPreferredWidth}
              />
            )}
          >
            {sidebar.renderItems(dismissOverlay)}
          </Sidebar>
        )}
        <div className="shell-workspace">
          <header className="shell-header">
            {sidebar && (
              <Button
                ref={toggleRef}
                color="secondary"
                variant="ghost"
                uniform
                pill={false}
                aria-label="Toggle sidebar"
                title="Toggle sidebar"
                aria-controls={sidebarId}
                aria-expanded={expanded}
                onClick={() => {
                  if (overlayVisible) dismissOverlay()
                  else if (isMobile) setOverlayState('open')
                  else setDesktopExpanded(value => !value)
                }}
              >
                <SidebarIcon />
              </Button>
            )}
            {headerContent}
          </header>
          <main className="shell-content" inert={overlayVisible}>
            {children}
          </main>
        </div>
      </div>
    </div>
  )
}
