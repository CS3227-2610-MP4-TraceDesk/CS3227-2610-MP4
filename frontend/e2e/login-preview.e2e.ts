import { expect, test } from '@playwright/test'

test('login entrance wakes the beetle and keeps the form immediately usable', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.addInitScript(() => {
    const startedAt = performance.now()
    function sampleEntrance() {
      const eyes = document.querySelector('#beetle-eyes')
      if (eyes) {
        const scale = new DOMMatrix(getComputedStyle(eyes).transform).d
        const previous = Number(document.documentElement.dataset.minimumEyeScale ?? 1)
        document.documentElement.dataset.minimumEyeScale = String(Math.min(previous, scale))
      }
      if (performance.now() - startedAt < 1500) requestAnimationFrame(sampleEntrance)
    }
    requestAnimationFrame(sampleEntrance)
  })
  await page.goto('/')
  await expect.poll(() => page.evaluate(() => Number(document.documentElement.dataset.minimumEyeScale ?? 1)))
    .toBeLessThan(.9)
  await page.getByLabel('Email', { exact: true }).fill('qa@tracedesk.test')
  await expect(page.getByLabel('Email', { exact: true })).toHaveValue('qa@tracedesk.test')
  await page.getByLabel('Password', { exact: true }).focus()
  await expect(page.locator('#beetle-pupil-left')).toHaveAttribute('transform', 'translate(-46 6)')
  await expect(page.locator('#beetle-eyes')).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, 0)')
  await expect(page.locator('.sign-in-shadow')).toHaveCSS('opacity', '1')
  await expect(page.locator('.sign-in-card')).toHaveCSS('opacity', '1')
})

test('development preview retains the three demo accounts and local sign-out', async ({ page }) => {
  const mutations: string[] = []
  page.on('request', request => {
    if (request.method() !== 'GET') mutations.push(request.url())
  })
  for (const [role, email] of [
    ['Reporter', 'reporter@tracedesk.test'],
    ['Developer', 'developer@tracedesk.test'],
    ['QA reviewer', 'qa@tracedesk.test'],
  ]) {
    await page.goto('/')
    await page.getByText('Demo accounts', { exact: true }).click()
    await page.getByRole('button', { name: role, exact: true }).click()
    await expect(page.getByLabel('Email', { exact: true })).toHaveValue(email)
    await expect(page.getByLabel('Password', { exact: true })).toHaveValue('tracedesk-demo')
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(page.getByLabel('Development preview')).toContainText(role)
    await page.getByRole('button', { name: 'Sign out' }).click()
    await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
  }
  expect(mutations).toEqual([])
  expect(await page.context().cookies()).toEqual([])
})

test('invalid demo credentials stay on the form', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Email', { exact: true }).fill('qa@tracedesk.test')
  await page.getByLabel('Password', { exact: true }).fill('incorrect')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('didn’t match a demo account')
})

for (const width of [320, 390]) {
  test(`login stacks without overflow at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 })
    await page.goto('/')
    // Read both rectangles in one frame while the card may still be entering.
    const { stageBottom, formTop } = await page.evaluate(() => ({
      stageBottom: document.querySelector('.sign-in-stage')!.getBoundingClientRect().bottom,
      formTop: document.querySelector('.sign-in-panel')!.getBoundingClientRect().top,
    }))
    expect(stageBottom).toBeLessThanOrEqual(formTop)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
  })
}

test('reduced motion disables reactions and keeps the password look-away', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/')
  await expect(page.locator('.sign-in-card')).toHaveCSS('animation-name', 'none')
  const body = page.locator('#beetle-body')
  await body.hover()
  expect(await page.locator('.sign-in-stage').evaluate(el => el.getAnimations({ subtree: true }).length)).toBe(0)
  await page.getByLabel('Password', { exact: true }).focus()
  await expect(page.locator('#beetle-pupil-left')).toHaveAttribute('transform', 'translate(-46 6)')
})
test('enabling reduced motion during wake-up immediately restores the resting pose', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  const startTime = new Date('2026-11-07T00:00:00Z')
  await page.clock.install({ time: startTime })
  await page.clock.pauseAt(new Date(startTime.getTime() + 1000))
  await page.goto('/')
  await page.clock.runFor(16)
  expect(await page.locator('#beetle-eyes').evaluate(el => new DOMMatrix(getComputedStyle(el).transform).d))
    .toBeLessThan(1)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await expect(page.locator('.sign-in-card')).toHaveCSS('animation-name', 'none')
  await expect(page.locator('#beetle-eyes')).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, 0)')
  await expect(page.locator('.sign-in-mascot-hop')).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, 0)')
  await expect(page.locator('.sign-in-shadow')).toHaveCSS('opacity', '1')
})

test('approach and pat settle while password focus interrupts the reaction', async ({ page }) => {
  await page.goto('/')
  const stage = page.locator('.sign-in-stage')
  const bounds = (await stage.boundingBox())!
  const x = bounds.x + bounds.width / 2
  const y = bounds.y + bounds.height / 2
  await page.mouse.move(x - 150, y)
  await page.mouse.move(x - 90, y)
  await expect.poll(() => page.locator('.sign-in-shadow').evaluate(el => el.getAnimations().length)).toBeGreaterThan(0)
  await expect.poll(() => page.locator('.sign-in-shadow').evaluate(el => el.getAnimations().length)).toBe(0)

  await page.mouse.move(x + 10, y + 16)
  const happyEyes = page.locator('[data-mascot-happy-eyes]')
  await expect.poll(() => happyEyes.evaluate(el => Number(getComputedStyle(el).opacity)), { intervals: [25] }).toBeGreaterThan(.9)
  await expect.poll(() => happyEyes.evaluate(el => el.getAnimations().length)).toBe(0)
  await expect(happyEyes).toHaveCSS('opacity', '0')
  await expect(page.locator('.sign-in-shadow')).toHaveCSS('opacity', '1')
  await expect(page.locator('.sign-in-shadow')).toHaveCSS('background-color', 'rgba(0, 0, 0, 0.27)')

  await page.mouse.move(x - 150, y)
  await page.mouse.move(x + 10, y + 16)
  await expect.poll(() => happyEyes.evaluate(el => Number(getComputedStyle(el).opacity)), { intervals: [25] }).toBeGreaterThan(.9)
  await page.getByLabel('Password', { exact: true }).focus()
  await expect.poll(() => happyEyes.evaluate(el => el.getAnimations().length)).toBe(0)
  await expect(page.locator('#beetle-pupil-left')).toHaveAttribute('transform', 'translate(-46 6)')
  await expect.poll(() => page.locator('#beetle-head').getAttribute('transform')).toContain('matrix(1 0 0.087')
})
