import { expect, test } from '@playwright/test'

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
    const stage = await page.locator('.sign-in-stage').boundingBox()
    const form = await page.locator('.sign-in-panel').boundingBox()
    expect(stage!.y + stage!.height).toBeLessThanOrEqual(form!.y)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
  })
}

test('reduced motion disables reactions and keeps the password look-away', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/')
  const body = page.locator('#beetle-body')
  await body.hover()
  expect(await page.locator('.sign-in-stage').evaluate(el => el.getAnimations({ subtree: true }).length)).toBe(0)
  await page.getByLabel('Password', { exact: true }).focus()
  await expect(page.locator('#beetle-pupil-left')).toHaveAttribute('transform', 'translate(-46 6)')
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
