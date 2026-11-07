import { useEffect, useRef } from 'react'

const neutralPose = { x: 0, y: 0, tilt: 0, head: 0, lx: 0, ly: 0, rx: 0, ry: 0 }
const settle = 'cubic-bezier(.2, .65, .25, 1)'

export function useMascotMotion() {
  const screen = useRef<HTMLElement>(null)
  const mascot = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const surface = screen.current!
    const figure = mascot.current!
    const hopLayer = figure.parentElement!
    const stage = hopLayer.parentElement!
    const shadow = stage.querySelector('.sign-in-shadow')!
    const passwordField = surface.querySelector('[data-password]')!
    const svg = figure.querySelector('svg')!
    const body = svg.querySelector<SVGGElement>('#beetle-body')!
    const head = svg.querySelector<SVGGElement>('#beetle-head')!
    const eyes = svg.querySelector('#beetle-eyes')!
    const legs = svg.querySelector('#beetle-legs')!
    const left = svg.querySelector('#beetle-pupil-left')!
    const right = svg.querySelector('#beetle-pupil-right')!
    const antennae = svg.querySelector('#beetle-antennae')!
    const happyEyes = document.createElementNS('http://www.w3.org/2000/svg', 'path')
    happyEyes.setAttribute('data-mascot-happy-eyes', '')
    happyEyes.setAttribute('d', 'M 277 208 Q 298 179 319 208 M 371 190 Q 389 165 407 190')
    happyEyes.setAttribute('fill', 'none')
    happyEyes.setAttribute('stroke', 'var(--beetle-face, #fcfaf7)')
    happyEyes.setAttribute('stroke-width', '8')
    happyEyes.setAttribute('stroke-linecap', 'round')
    happyEyes.setAttribute('opacity', '0')
    happyEyes.style.pointerEvents = 'none'
    head.append(happyEyes)

    const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)')
    const pose = { ...neutralPose }
    const target = { ...pose }
    let frame = 0
    let previousTime = 0
    let close = false
    let overBeetle = false
    let pendingPat = false
    let lookingAway = false
    let turnAt = 0
    let lastMove = -Infinity
    let pointer: { x: number; y: number } | undefined
    let reaction: Animation[] = []
    let blink: Animation | undefined
    let twitch: Animation | undefined

    function draw() {
      figure.style.transform = `translate(${pose.x}px, ${pose.y}px) rotate(${pose.tilt}deg)`
      // Lean above the shell seam while keeping the head's base attached.
      const lean = Math.tan(-pose.head * Math.PI / 180)
      head.setAttribute('transform', `matrix(1 0 ${lean} 1 ${-273 * lean} 0)`)
      left.setAttribute('transform', `translate(${pose.lx} ${pose.ly})`)
      right.setAttribute('transform', `translate(${pose.rx} ${pose.ry})`)
    }

    function tick(time: number) {
      frame = 0
      const elapsed = previousTime ? Math.min(time - previousTime, 40) : 16
      previousTime = time
      let moving = false
      for (const key of Object.keys(pose) as (keyof typeof pose)[]) {
        const difference = target[key] - pose[key]
        if (Math.abs(difference) < 0.05) {
          pose[key] = target[key]
        } else {
          moving = true
          if (key === 'head' && time < turnAt) continue
          const response = key === 'x' || key === 'y' || key === 'tilt' || key === 'head' ? 95 : 45
          pose[key] += difference * (1 - Math.exp(-elapsed / response))
        }
      }
      draw()
      if (moving) frame = requestAnimationFrame(tick)
      else previousTime = 0
    }

    function cancelReaction() {
      reaction.forEach(animation => animation.cancel())
      reaction = []
      blink?.cancel()
      twitch?.cancel()
    }

    function canReact() {
      return !lookingAway && !reducedMotion.matches && !document.hidden
    }

    function reacting() {
      return reaction.some(animation => animation.playState === 'running')
    }

    function wiggle(duration: number, degrees: number) {
      return antennae.animate([
        { transform: 'rotate(0deg)' },
        { transform: `rotate(${-degrees}deg)` },
        { transform: `rotate(${degrees * .65}deg)` },
        { transform: 'rotate(0deg)' },
      ], { duration, easing: settle })
    }

    function pat() {
      if (!pendingPat || !canReact() || reacting()) return
      pendingPat = false
      cancelReaction()
      const duration = 850
      const timing = { duration, easing: settle }
      reaction = [
        body.animate([
          { transform: 'scale(1)' },
          { transform: 'scale(1.04, .94)', offset: .25 },
          { transform: 'scale(1.025, .965)', offset: .53 },
          { transform: 'scale(1)' },
        ], timing),
        eyes.animate([
          { opacity: 1, transform: 'scaleY(1)' },
          { opacity: 0, transform: 'scaleY(.1)', offset: .15 },
          { opacity: 0, transform: 'scaleY(.1)', offset: .59 },
          { opacity: 1, transform: 'scaleY(1)', offset: .9 },
        ], { ...timing, easing: 'linear' }),
        happyEyes.animate([
          { opacity: 0 },
          { opacity: 1, offset: .17 },
          { opacity: 1, offset: .56 },
          { opacity: 0, offset: .83 },
        ], { ...timing, easing: 'linear' }),
        shadow.animate([
          { transform: 'scale(1)' },
          { transform: 'scale(1.10, 1.04)', offset: .28 },
          { transform: 'scale(1)' },
        ], timing),
        wiggle(duration, 12),
      ]
      reaction[0].onfinish = pat
    }

    function approach() {
      if (reacting()) return
      cancelReaction()
      const duration = 410
      const timing = { duration }
      const hop = hopLayer.animate([
        { transform: 'translateY(0)', easing: 'ease-out' },
        { transform: 'translateY(-18px)', offset: .39, easing: 'ease-in' },
        { transform: 'translateY(0)', offset: .77 },
        { transform: 'translateY(0)' },
      ], timing)
      reaction = [
        hop,
        legs.animate([
          { transform: 'scale(1)' },
          { transform: 'scale(.92, .92)', offset: .39 },
          { transform: 'scale(1)', offset: .77 },
        ], timing),
        shadow.animate([
          { transform: 'scale(1)', opacity: 1 },
          { transform: 'scale(.74, .84)', opacity: .48, offset: .39 },
          { transform: 'scale(1.17, 1.08)', opacity: 1, offset: .79 },
          { transform: 'scale(1)', opacity: 1 },
        ], timing),
        wiggle(duration, 6),
      ]
      hop.onfinish = pat
    }

    function update(allowHop = false) {
      const passwordFocused = passwordField.contains(document.activeElement)
      const still = reducedMotion.matches
      if (passwordFocused !== lookingAway) {
        lookingAway = passwordFocused
        turnAt = lookingAway ? performance.now() + 100 : 0
      }
      Object.assign(target, neutralPose)

      if (lookingAway) {
        Object.assign(target, { head: still ? 0 : -5, lx: -46, ly: 6, rx: -37, ry: 7 })
      } else if (pointer && !still) {
        const bounds = stage.getBoundingClientRect()
        const width = figure.offsetWidth
        const dx = pointer.x - (bounds.left + bounds.width / 2)
        const dy = pointer.y - (bounds.top + bounds.height / 2)
        target.x = Math.max(-24, Math.min(24, dx * 0.14))
        target.y = Math.max(-12, Math.min(12, dy * 0.09))
        target.tilt = target.x / 5

        const matrix = svg.getScreenCTM()
        if (matrix) {
          const point = new DOMPoint(pointer.x, pointer.y).matrixTransform(matrix.inverse())
          function gaze(cx: number, cy: number, rx: number, ry: number) {
            const x = point.x - cx
            const y = point.y - cy
            const distance = Math.max(78, Math.hypot(x, y))
            return { x: x / distance * rx, y: y / distance * ry }
          }
          const l = gaze(299, 204, 26, 28)
          const r = gaze(390, 185, 20, 27)
          Object.assign(target, { lx: l.x - 28.2, ly: l.y + 6, rx: r.x - 23.1, ry: r.y + 6.8 })
        }

        const distance = Math.hypot(dx, dy)
        if (!close && distance < width * 0.54 && allowHop) {
          close = true
          approach()
        } else if (distance > width * 0.68) {
          close = false
        }
      }

      if (lookingAway || still) {
        pendingPat = false
        cancelReaction()
      }
      if (still) {
        cancelAnimationFrame(frame)
        frame = 0
        previousTime = 0
        Object.assign(pose, target)
        draw()
      } else if (!frame) {
        frame = requestAnimationFrame(tick)
      }
    }

    function move(event: PointerEvent) {
      if (event.pointerType === 'touch') return
      lastMove = performance.now()
      pointer = { x: event.clientX, y: event.clientY }
      const onPaint = event.target instanceof SVGGeometryElement && svg.contains(event.target)
      if (onPaint && !overBeetle) pendingPat = true
      if (!onPaint) pendingPat = false
      overBeetle = onPaint
      update(true)
      pat()
    }

    function leave() {
      pointer = undefined
      close = false
      overBeetle = false
      pendingPat = false
      update()
    }

    function focusChanged() {
      queueMicrotask(() => {
        if (surface.isConnected) update()
      })
    }

    const blinkTimer = window.setInterval(() => {
      if (!canReact() || reacting() || performance.now() - lastMove < 750) return
      blink = eyes.animate([
        { transform: 'scaleY(1)' },
        { transform: 'scaleY(.08)', offset: .43 },
        { transform: 'scaleY(1)' },
      ], { duration: 180, easing: 'ease-in-out' })
    }, 4600)
    const twitchTimer = window.setInterval(() => {
      if (canReact() && !reacting()) twitch = wiggle(440, 3.5)
    }, 1900)

    surface.addEventListener('pointermove', move)
    surface.addEventListener('pointerleave', leave)
    surface.addEventListener('focusin', focusChanged)
    surface.addEventListener('focusout', focusChanged)
    reducedMotion.addEventListener('change', focusChanged)
    return () => {
      surface.removeEventListener('pointermove', move)
      surface.removeEventListener('pointerleave', leave)
      surface.removeEventListener('focusin', focusChanged)
      surface.removeEventListener('focusout', focusChanged)
      reducedMotion.removeEventListener('change', focusChanged)
      clearInterval(blinkTimer)
      clearInterval(twitchTimer)
      cancelAnimationFrame(frame)
      cancelReaction()
      happyEyes.remove()
    }
  }, [])

  return { screen, mascot }
}
