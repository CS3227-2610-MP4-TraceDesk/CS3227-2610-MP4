import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'

const keyboardStep = 16

type SidebarResizeHandleProps = {
  sidebarId: string
  width: number
  min: number
  max: number
  onResize: (width: number) => void
}

export function SidebarResizeHandle({ sidebarId, width, min, max, onResize }: SidebarResizeHandleProps) {
  const drag = useRef<{ pointerId: number; startX: number; startWidth: number } | null>(null)
  const [dragging, setDragging] = useState(false)

  function resize(nextWidth: number) {
    onResize(Math.max(min, Math.min(max, Math.round(nextWidth))))
  }

  function startDrag(event: PointerEvent<HTMLDivElement>) {
    if (event.button !== 0 || drag.current) return
    const handle = event.currentTarget
    const startWidth = handle.parentElement?.getBoundingClientRect().width ?? width
    handle.setPointerCapture(event.pointerId)
    drag.current = { pointerId: event.pointerId, startX: event.clientX, startWidth }
    resize(startWidth)
    setDragging(true)
  }

  function moveDrag(event: PointerEvent<HTMLDivElement>) {
    const activeDrag = drag.current
    if (activeDrag?.pointerId !== event.pointerId) return
    resize(activeDrag.startWidth + event.clientX - activeDrag.startX)
  }

  function endDrag(event: PointerEvent<HTMLDivElement>) {
    if (drag.current?.pointerId !== event.pointerId) return
    drag.current = null
    setDragging(false)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    switch (event.key) {
      case 'ArrowLeft': resize(width - keyboardStep); break
      case 'ArrowRight': resize(width + keyboardStep); break
      case 'Home': resize(min); break
      case 'End': resize(max); break
      default: return
    }
    event.preventDefault()
  }

  return (
    <div
      className="shell-sidebar-resizer"
      data-dragging={dragging}
      role="separator"
      tabIndex={0}
      aria-label="Resize sidebar"
      aria-orientation="vertical"
      aria-controls={sidebarId}
      aria-valuemin={min}
      aria-valuemax={max}
      aria-valuenow={width}
      aria-valuetext={`${width} pixels`}
      onPointerDown={startDrag}
      onPointerMove={moveDrag}
      onLostPointerCapture={endDrag}
      onKeyDown={handleKeyDown}
    />
  )
}
