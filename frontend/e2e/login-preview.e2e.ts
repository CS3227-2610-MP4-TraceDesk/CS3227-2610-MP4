import { expect, test } from '@playwright/test'
import { mockAuthApi } from './helpers/authApi'

test.beforeEach(async ({ page }) => {
  await mockAuthApi(page)
})

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

test('development demo accounts use the real session flow and sign out on the server', async ({ page }) => {
  const requests = await mockAuthApi(page)
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
    await expect(page.getByRole('heading', { name: 'Choose a project' })).toBeVisible()
    expect(requests.filter(request => request.path === '/api/session/login').at(-1)).toMatchObject({
      method: 'POST',
      headers: expect.objectContaining({ 'x-csrf-token': 'synthetic-csrf-token' }),
      body: JSON.stringify({ login: email, password: 'tracedesk-demo' }),
    })
    const sessionCookie = (await page.context().cookies()).find(cookie => cookie.name === 'TDSESSION')
    expect(sessionCookie).toMatchObject({ secure: true, httpOnly: true, sameSite: 'Lax' })
    expect(requests.filter(request => request.path === '/api/session').at(-1)?.headers.cookie)
      .toContain('TDSESSION=synthetic-session')
    await page.getByRole('button', { name: 'Sign out' }).click()
    await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
    expect(requests.filter(request => request.path === '/api/session/logout').at(-1)).toMatchObject({
      method: 'POST',
      headers: expect.objectContaining({ 'x-csrf-token': 'synthetic-csrf-token' }),
    })
    expect((await page.context().cookies()).some(cookie => cookie.name === 'TDSESSION')).toBe(false)
  }
  expect(requests.filter(request => request.path === '/api/session/login')).toHaveLength(3)
})

test('invalid credentials stay on the form with a generic message', async ({ page }) => {
  await mockAuthApi(page, { loginStatus: 401 })
  await page.goto('/')
  await page.getByLabel('Email', { exact: true }).fill('qa@tracedesk.test')
  await page.getByLabel('Password', { exact: true }).fill('incorrect')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Email or password is incorrect.')
})

test('registration sends the selected role and returns to sign in without retaining the password', async ({ page }) => {
  const requests = await mockAuthApi(page)
  await page.setViewportSize({ width: 320, height: 760 })
  await page.goto('/')
  await page.getByRole('button', { name: 'Create account', exact: true }).click()
  await expect(page.getByLabel('Account role')).toBeVisible()
  await page.getByLabel('Email').fill('new@example.test')
  await page.getByRole('textbox', { name: 'Password', exact: true }).fill('long-password-123')
  await page.getByLabel('Confirm password').fill('long-password-123')
  await page.getByLabel('Account role').selectOption('QA_REVIEWER')
  await page.getByRole('button', { name: 'Create account', exact: true }).click()

  await expect(page.getByRole('status')).toHaveText('Account created. Sign in to continue.')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await expect(page.getByLabel('Email')).toHaveValue('new@example.test')
  await expect(page.getByRole('textbox', { name: 'Password', exact: true })).toHaveValue('')
  expect(requests.find(request => request.path === '/api/session/register')).toMatchObject({
    method: 'POST',
    headers: expect.objectContaining({ 'x-csrf-token': 'synthetic-csrf-token' }),
    body: JSON.stringify({ login: 'new@example.test', password: 'long-password-123', role: 'QA_REVIEWER' }),
  })
  await expect(page.locator('body')).not.toHaveCSS('overflow-x', 'scroll')
})

test('session restored after a reload remains server-confirmed', async ({ page }) => {
  const requests = await mockAuthApi(page)
  await page.goto('/')
  await page.getByText('Demo accounts', { exact: true }).click()
  await page.getByRole('button', { name: 'Reporter', exact: true }).click()
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Choose a project' })).toBeVisible()

  await page.reload()

  await expect(page.getByRole('heading', { name: 'Choose a project' })).toBeVisible()
  expect(requests.filter(request => request.path === '/api/session').at(-1)?.headers.cookie)
    .toContain('TDSESSION=synthetic-session')
})

test('unavailable startup stays signed out and offers a retry', async ({ page }) => {
  // React StrictMode may perform the initial read twice in development.
  const sessionStatuses = [503, 503]
  const requests = await mockAuthApi(page, { sessionStatuses })
  await page.goto('/')

  await expect(page.getByRole('status')).toContainText('temporarily unavailable')
  await expect.poll(() => requests.filter(request =>
    request.method === 'GET' && request.path === '/api/session',
  ).length).toBeGreaterThanOrEqual(1)
  // StrictMode can cancel its first read before it reaches the mock. Restore
  // availability explicitly instead of depending on the number of reads.
  sessionStatuses.fill(401)
  await page.getByRole('button', { name: 'Retry connection' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await expect(page.getByRole('status')).toHaveCount(0)
})

test('logout outage keeps the signed-in state and reports failure', async ({ page }) => {
  await mockAuthApi(page, { logoutStatus: 503 })
  await page.goto('/')
  await page.getByText('Demo accounts', { exact: true }).click()
  await page.getByRole('button', { name: 'Reporter', exact: true }).click()
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.getByRole('heading', { name: 'Choose a project' }).waitFor()
  await page.getByRole('button', { name: 'Sign out' }).click()

  await expect(page.getByRole('heading', { name: 'Choose a project', exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('could not confirm sign out')
})

for (const width of [320, 390]) {
  test(`login stacks without overflow at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 })
    await page.goto('/')
    await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor()
    // Read both rectangles in one frame while the card may still be entering.
    const { stageBottom, formTop } = await page.evaluate(() => ({
      stageBottom: document.querySelector('.sign-in-stage')!.getBoundingClientRect().bottom,
      formTop: document.querySelector('.sign-in-panel')!.getBoundingClientRect().top,
    }))
    expect(stageBottom).toBeLessThanOrEqual(formTop + 0.1) // Subpixel layout rounding may differ by a few thousandths of a CSS pixel.
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
