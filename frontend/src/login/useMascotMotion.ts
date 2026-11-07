import { useEffect, useRef } from 'react'

const neutralPose = {
  x: 0,
  y: 0,
  tilt: 0,
  head: 0,
  leftPupilX: 0,
  leftPupilY: 0,
  rightPupilX: 0,
  rightPupilY: 0,
}
const settleEasing = 'cubic-bezier(.2, .65, .25, 1)'

const headBaseY = 273
const minimumGazeDistance = 78
const leftEyeGeometry = {
  centerX: 299,
  centerY: 204,
  rangeX: 26,
  rangeY: 28,
  pupilOffsetX: 28.2,
  pupilOffsetY: 6,
}
const rightEyeGeometry = {
  centerX: 390,
  centerY: 185,
  rangeX: 20,
  rangeY: 27,
  pupilOffsetX: 23.1,
  pupilOffsetY: 6.8,
}
const maximumFrameIntervalMs = 40
const initialFrameIntervalMs = 16
const poseSettleThreshold = 0.05
const bodyResponseMs = 95
const pupilResponseMs = 45
const patDurationMs = 850
const approachDurationMs = 410
const headTurnDelayMs = 100
const blinkIdleDelayMs = 750
const blinkDurationMs = 180
const blinkIntervalMs = 4600
const antennaTwitchDurationMs = 440
const antennaTwitchIntervalMs = 1900

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
    const leftPupil = svg.querySelector('#beetle-pupil-left')!
    const rightPupil = svg.querySelector('#beetle-pupil-right')!
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
    let animationFrameId = 0
    let previousFrameTime = 0
    let pointerIsClose = false
    let pointerIsOverBeetle = false
    let hasPendingPat = false
    let isLookingAway = false
    let headTurnStartsAt = 0
    let lastPointerMoveTime = -Infinity
    let pointer: { x: number; y: number } | undefined
    let reactionAnimations: Animation[] = []
    let blinkAnimation: Animation | undefined
    let antennaTwitchAnimation: Animation | undefined

    function renderPose() {
      figure.style.transform = `translate(${pose.x}px, ${pose.y}px) rotate(${pose.tilt}deg)`
      // Lean above the shell seam while keeping the head's base attached.
      const lean = Math.tan(-pose.head * Math.PI / 180)
      head.setAttribute('transform', `matrix(1 0 ${lean} 1 ${-headBaseY * lean} 0)`)
      leftPupil.setAttribute('transform', `translate(${pose.leftPupilX} ${pose.leftPupilY})`)
      rightPupil.setAttribute('transform', `translate(${pose.rightPupilX} ${pose.rightPupilY})`)
    }

    function animatePose(time: number) {
      animationFrameId = 0
      const elapsed = previousFrameTime
        ? Math.min(time - previousFrameTime, maximumFrameIntervalMs)
        : initialFrameIntervalMs
      previousFrameTime = time
      let moving = false
      for (const key of Object.keys(pose) as (keyof typeof pose)[]) {
        const difference = target[key] - pose[key]
        if (Math.abs(difference) < poseSettleThreshold) {
          pose[key] = target[key]
        } else {
          moving = true
          if (key === 'head' && time < headTurnStartsAt) continue
          const response = key === 'x' || key === 'y' || key === 'tilt' || key === 'head'
            ? bodyResponseMs
            : pupilResponseMs
          pose[key] += difference * (1 - Math.exp(-elapsed / response))
        }
      }
      renderPose()
      if (moving) animationFrameId = requestAnimationFrame(animatePose)
      else previousFrameTime = 0
    }

    function cancelReaction() {
      reactionAnimations.forEach(animation => animation.cancel())
      reactionAnimations = []
      blinkAnimation?.cancel()
      antennaTwitchAnimation?.cancel()
    }

    function canPlayReaction() {
      return !isLookingAway && !reducedMotion.matches && !document.hidden
    }

    function isReactionPlaying() {
      return reactionAnimations.some(animation => animation.playState === 'running')
    }

    function animateAntennae(duration: number, degrees: number) {
      return antennae.animate([
        { transform: 'rotate(0deg)' },
        { transform: `rotate(${-degrees}deg)` },
        { transform: `rotate(${degrees * .65}deg)` },
        { transform: 'rotate(0deg)' },
      ], { duration, easing: settleEasing })
    }

    function playPatReaction() {
      if (!hasPendingPat || !canPlayReaction() || isReactionPlaying()) return
      hasPendingPat = false
      cancelReaction()
      const duration = patDurationMs
      const timing = { duration, easing: settleEasing }
      reactionAnimations = [
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
        animateAntennae(duration, 12),
      ]
      reactionAnimations[0].onfinish = playPatReaction
    }

    function playApproachHop() {
      if (isReactionPlaying()) return
      cancelReaction()
      const duration = approachDurationMs
      const timing = { duration }
      const hop = hopLayer.animate([
        { transform: 'translateY(0)', easing: 'ease-out' },
        { transform: 'translateY(-18px)', offset: .39, easing: 'ease-in' },
        { transform: 'translateY(0)', offset: .77 },
        { transform: 'translateY(0)' },
      ], timing)
      reactionAnimations = [
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
        animateAntennae(duration, 6),
      ]
      hop.onfinish = playPatReaction
    }

    function updateTargetPose(allowHop = false) {
      const passwordFocused = passwordField.contains(document.activeElement)
      const still = reducedMotion.matches
      if (passwordFocused !== isLookingAway) {
        isLookingAway = passwordFocused
        headTurnStartsAt = isLookingAway ? performance.now() + headTurnDelayMs : 0
      }
      Object.assign(target, neutralPose)

      if (isLookingAway) {
        Object.assign(target, {
          head: still ? 0 : -5,
          leftPupilX: -46,
          leftPupilY: 6,
          rightPupilX: -37,
          rightPupilY: 7,
        })
      } else if (pointer && !still) {
        const bounds = stage.getBoundingClientRect()
        const width = figure.offsetWidth
        const horizontalDistance = pointer.x - (bounds.left + bounds.width / 2)
        const verticalDistance = pointer.y - (bounds.top + bounds.height / 2)
        target.x = Math.max(-24, Math.min(24, horizontalDistance * 0.14))
        target.y = Math.max(-12, Math.min(12, verticalDistance * 0.09))
        target.tilt = target.x / 5

        const matrix = svg.getScreenCTM()
        if (matrix) {
          const point = new DOMPoint(pointer.x, pointer.y).matrixTransform(matrix.inverse())
          function calculateGaze(centerX: number, centerY: number, rangeX: number, rangeY: number) {
            const horizontalOffset = point.x - centerX
            const verticalOffset = point.y - centerY
            const distance = Math.max(minimumGazeDistance, Math.hypot(horizontalOffset, verticalOffset))
            return {
              x: horizontalOffset / distance * rangeX,
              y: verticalOffset / distance * rangeY,
            }
          }
          const leftGaze = calculateGaze(
            leftEyeGeometry.centerX,
            leftEyeGeometry.centerY,
            leftEyeGeometry.rangeX,
            leftEyeGeometry.rangeY,
          )
          const rightGaze = calculateGaze(
            rightEyeGeometry.centerX,
            rightEyeGeometry.centerY,
            rightEyeGeometry.rangeX,
            rightEyeGeometry.rangeY,
          )
          Object.assign(target, {
            leftPupilX: leftGaze.x - leftEyeGeometry.pupilOffsetX,
            leftPupilY: leftGaze.y + leftEyeGeometry.pupilOffsetY,
            rightPupilX: rightGaze.x - rightEyeGeometry.pupilOffsetX,
            rightPupilY: rightGaze.y + rightEyeGeometry.pupilOffsetY,
          })
        }

        const distance = Math.hypot(horizontalDistance, verticalDistance)
        if (!pointerIsClose && distance < width * 0.54 && allowHop) {
          pointerIsClose = true
          playApproachHop()
        } else if (distance > width * 0.68) {
          pointerIsClose = false
        }
      }

      if (isLookingAway || still) {
        hasPendingPat = false
        cancelReaction()
      }
      if (still) {
        cancelAnimationFrame(animationFrameId)
        animationFrameId = 0
        previousFrameTime = 0
        Object.assign(pose, target)
        renderPose()
      } else if (!animationFrameId) {
        animationFrameId = requestAnimationFrame(animatePose)
      }
    }

    function handlePointerMove(event: PointerEvent) {
      if (event.pointerType === 'touch') return
      lastPointerMoveTime = performance.now()
      pointer = { x: event.clientX, y: event.clientY }
      const onPaint = event.target instanceof SVGGeometryElement && svg.contains(event.target)
      if (onPaint && !pointerIsOverBeetle) hasPendingPat = true
      if (!onPaint) hasPendingPat = false
      pointerIsOverBeetle = onPaint
      updateTargetPose(true)
      playPatReaction()
    }

    function handlePointerLeave() {
      pointer = undefined
      pointerIsClose = false
      pointerIsOverBeetle = false
      hasPendingPat = false
      updateTargetPose()
    }

    function handleFocusChange() {
      queueMicrotask(() => {
        if (surface.isConnected) updateTargetPose()
      })
    }

    const blinkTimer = window.setInterval(() => {
      if (!canPlayReaction() || isReactionPlaying() || performance.now() - lastPointerMoveTime < blinkIdleDelayMs) return
      blinkAnimation = eyes.animate([
        { transform: 'scaleY(1)' },
        { transform: 'scaleY(.08)', offset: .43 },
        { transform: 'scaleY(1)' },
      ], { duration: blinkDurationMs, easing: 'ease-in-out' })
    }, blinkIntervalMs)
    const twitchTimer = window.setInterval(() => {
      if (canPlayReaction() && !isReactionPlaying()) antennaTwitchAnimation = animateAntennae(antennaTwitchDurationMs, 3.5)
    }, antennaTwitchIntervalMs)

    surface.addEventListener('pointermove', handlePointerMove)
    surface.addEventListener('pointerleave', handlePointerLeave)
    surface.addEventListener('focusin', handleFocusChange)
    surface.addEventListener('focusout', handleFocusChange)
    reducedMotion.addEventListener('change', handleFocusChange)
    return () => {
      surface.removeEventListener('pointermove', handlePointerMove)
      surface.removeEventListener('pointerleave', handlePointerLeave)
      surface.removeEventListener('focusin', handleFocusChange)
      surface.removeEventListener('focusout', handleFocusChange)
      reducedMotion.removeEventListener('change', handleFocusChange)
      clearInterval(blinkTimer)
      clearInterval(twitchTimer)
      cancelAnimationFrame(animationFrameId)
      cancelReaction()
      happyEyes.remove()
    }
  }, [])

  return { screen, mascot }
}
