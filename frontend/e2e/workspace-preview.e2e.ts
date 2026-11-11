import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

test('role fixtures open the shared shell without an authentication request', async ({ page }) => {
  const apiRequests: string[] = []
  page.on('request', request => {
    const { pathname } = new URL(request.url())
    if (pathname.startsWith('/api/')) apiRequests.push(pathname)
  })

  for (const [role, primary, secondary] of [
    ['reporter', 'Reports', 'Settings'],
    ['developer', 'My work', 'All bugs'],
    ['qa', 'Verification', 'Test templates'],
  ]) {
    await page.goto(`/?workspace-preview=${role}`)
    const tray = page.getByRole('navigation', { name: 'Main navigation' })
    await expect(tray.getByRole('button', { name: primary })).toHaveAttribute('aria-current', 'page')
    if (role === 'reporter') {
      await expect(tray.getByRole('button', { name: 'Environments' })).toHaveCount(0)
      for (const width of [1440, 390, 320]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(tray.getByRole('button', { name: 'Reports' })).toBeVisible()
        expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
        await page.screenshot({ path: test.info().outputPath(`reporter-mvp-${width}.png`) })
      }
      await page.setViewportSize({ width: 1440, height: 900 })
    }
    await tray.getByRole('button', { name: secondary }).click()
    await expect(page.getByRole('heading', { name: role === 'reporter' ? 'Appearance' : secondary, exact: true })).toBeVisible()
    if (role === 'developer') {
      await expect(page.getByRole('button', { name: 'Toggle sidebar' })).toHaveCount(0)
    }
    await expect(tray.getByRole('button', { name: 'Settings' })).toBeVisible()
  }
  expect(apiRequests).toEqual([])
})

test('QA queue filters and opens a summary while preserving queue context', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/?workspace-preview=qa')
  const queue = page.getByRole('region', { name: 'Verification' })
  const summary = page.getByRole('article', { name: 'Bug review summary' })
  const map = summary.getByRole('region', { name: 'Change map' })
  const search = queue.getByRole('searchbox', { name: 'Find bug' })
  const sidebar = page.getByRole('complementary', { name: 'Verification' })

  await queue.getByRole('button', { name: 'Reviewed by me' }).click()
  await expect(queue.getByRole('status')).toHaveText('No tickets in this queue.')
  await queue.getByRole('button', { name: 'All bugs' }).click()
  await search.fill('TD-023')
  await expect(sidebar.getByRole('searchbox')).toHaveValue('TD-023')
  await queue.getByRole('button', { name: /Refund totals/ }).press('Enter')
  await expect(summary.getByRole('heading', { name: 'Refund totals are duplicated' })).toBeVisible()
  await summary.getByText('Original report', { exact: true }).press('Enter')
  await expect(summary.getByText('Each refund appears once.')).toBeVisible()
  await expect(map).toHaveCSS('width', '300px')
  await expect(map.getByText('refund-total.ts', { exact: true })).toBeVisible()
  await expect(summary).toHaveCSS('max-width', '1152px')
  await expect(summary).toHaveCSS('column-gap', '40px')

  await page.setViewportSize({ width: 1118, height: 900 })
  await expect(map).toBeHidden()
  await expect(summary.getByText('Each refund appears once.')).toBeVisible()
  await page.getByRole('separator', { name: 'Resize sidebar' }).press('Home')
  await expect(map).toBeVisible()
  await page.getByRole('button', { name: 'Back to verification queue' }).press('Enter')
  await expect(search).toHaveValue('TD-023')
  await expect(queue.getByRole('button', { name: 'All bugs' })).toHaveAttribute('aria-pressed', 'true')

  await search.fill('TD-024')
  await queue.getByRole('button', { name: /CSV export fails/ }).click()
  await expect(map.getByText('No patch attached.')).toBeVisible()
  await expect(summary.getByText('Each refund appears once.')).toHaveCount(0)
})

test('mobile QA sidebar opens a readable bug summary and dismisses the overlay', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 844 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/?workspace-preview=qa')
  const toggle = page.getByRole('button', { name: 'Toggle sidebar' })
  const sidebar = page.getByRole('complementary', { name: 'Verification' })
  await toggle.press('Enter')
  await sidebar.getByRole('button', { name: 'Refund totals are duplicated' }).click()
  await expect(toggle).toBeFocused()
  await expect(sidebar).toBeHidden()
  await expect(page.getByRole('heading', { name: 'Refund totals are duplicated' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Change map' })).toBeHidden()
  expect(await page.locator('.shell-content').evaluate(element => element.scrollWidth)).toBeLessThanOrEqual(274)
})

test('QA disclosures and popovers animate with shared timings and honor reduced motion', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.goto('/?workspace-preview=qa')
  const sort = page.getByRole('button', { name: 'Sort bugs' })
  await sort.press('Enter')
  const menu = page.getByRole('dialog', { name: 'Sort bugs' })
  await expect(menu).toHaveCSS('transition-duration', '0.14s, 0.14s')
  await expect(menu).toHaveCSS('opacity', '1')
  await menu.evaluate(element => {
    element.addEventListener('transitionrun', event => {
      if ((event as TransitionEvent).propertyName === 'opacity' && element.getAttribute('data-state') === 'closed') {
        document.documentElement.dataset.menuExitDuration = getComputedStyle(element).transitionDuration
      }
    })
  })
  await page.keyboard.press('Escape')
  await expect(menu).toHaveCount(0)
  await expect(page.locator('html')).toHaveAttribute('data-menu-exit-duration', '0.1s')
  await expect(sort).toBeFocused()

  await page.getByRole('button', { name: /Refund totals.*TD-023/ }).press('Enter')
  const report = page.locator('#qa-review-report')
  const trigger = report.locator('summary')
  await trigger.press('Enter')
  await expect(report).toHaveAttribute('open', '')
  expect(await report.evaluate(element =>
    getComputedStyle(element, '::details-content').transitionDuration,
  )).toBe('0.18s, 0.18s')
  await expect(report.getByText('Each refund appears once.')).toBeVisible()
  await trigger.press('Enter')
  await expect(report).not.toHaveAttribute('open')
  expect(await report.evaluate(element =>
    getComputedStyle(element, '::details-content').transitionDuration,
  )).toBe('0.15s, 0.15s')

  await page.emulateMedia({ reducedMotion: 'reduce' })
  await trigger.press('Enter')
  await expect(report.getByText('Each refund appears once.')).toBeVisible()
  expect(await report.evaluate(element => element.getAnimations({ subtree: true }).length)).toBe(0)
  await page.getByRole('button', { name: 'Back to verification queue' }).click()
  const display = page.getByRole('button', { name: 'Display options' })
  await display.press('Enter')
  await expect(page.getByRole('dialog', { name: 'Display options' })).toHaveCSS('transition-duration', '0s')
  await page.keyboard.press('Escape')
  await expect(display).toBeFocused()
})

test('desktop shell matches the reference dimensions and preserves sidebar selection', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/?workspace-preview=qa')
  const tray = page.getByRole('navigation', { name: 'Main navigation' })
  const sidebar = page.getByRole('complementary', { name: 'Verification' })
  const toggle = page.getByRole('button', { name: 'Toggle sidebar' })
  const search = sidebar.getByRole('searchbox', { name: 'Search review queue' })

  await expect(tray).toHaveCSS('width', '48px')
  await expect(tray.getByRole('button', { name: 'Verification' })).toHaveCSS('width', '34px')
  await expect(sidebar).toHaveCSS('width', '275px')
  expect(await page.locator('.app-frame').boundingBox()).toEqual({ x: 48, y: 4, width: 1388, height: 892 })
  await search.fill('TD-023')
  const report = sidebar.getByRole('button', { name: 'Refund totals are duplicated' })
  await report.click()
  await toggle.press('Enter')
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await expect(sidebar).toHaveAttribute('inert', '')
  await expect(page.locator('.shell-sidebar-track')).toHaveCSS('width', '0px')
  await expect(page.getByRole('heading', { name: 'Refund totals are duplicated' })).toBeVisible()
  await toggle.press('Space')
  await expect(search).toHaveValue('TD-023')
  await expect(report).toHaveAttribute('aria-current', 'page')
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])

  await tray.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Light appearance' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
})

test('desktop sidebar resizes within its limits and restores its width on reopen', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/?workspace-preview=qa')
  const resize = page.getByRole('separator', { name: 'Resize sidebar' })
  const sidebar = page.getByRole('complementary', { name: 'Verification' })
  const toggle = page.getByRole('button', { name: 'Toggle sidebar' })
  const handle = (await resize.boundingBox())!
  const startX = handle.x + handle.width / 2
  const y = handle.y + handle.height / 2

  await page.mouse.move(startX, y)
  await page.mouse.down()
  await page.mouse.move(startX + 50, y)
  await expect(sidebar).toHaveCSS('width', '325px')
  await page.mouse.move(1000, y)
  await expect(sidebar).toHaveCSS('width', '472px')
  await page.mouse.move(10, y)
  await expect(sidebar).toHaveCSS('width', '242px')
  await page.mouse.up()

  await resize.press('ArrowRight')
  await expect(resize).toHaveAttribute('aria-valuenow', '258')
  await resize.press('End')
  await expect(sidebar).toHaveCSS('width', '472px')
  await toggle.press('Enter')
  await expect(resize).toHaveCount(0)
  await toggle.press('Enter')
  await expect(sidebar).toHaveCSS('width', '472px')

  await page.setViewportSize({ width: 701, height: 900 })
  await expect(resize).toHaveAttribute('aria-valuemax', '407')
  await expect(sidebar).toHaveCSS('width', '407px')
  await expect(page.locator('.shell-workspace')).toHaveCSS('width', '240px')
  await page.setViewportSize({ width: 1440, height: 900 })
  await expect(sidebar).toHaveCSS('width', '472px')
  await resize.press('Home')
  await expect(sidebar).toHaveCSS('width', '242px')
})

test('desktop sidebar moves the workspace and fades during a reversible collapse', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.goto('/?workspace-preview=qa')
  const frame = page.locator('.app-frame')
  const toggle = page.getByRole('button', { name: 'Toggle sidebar' })
  await toggle.press('Enter')
  const midway = await frame.evaluate(element => {
    const transition = element.getAnimations()[0]
    transition.pause()
    transition.currentTime = 75
    return {
      trackWidth: element.querySelector('.shell-sidebar-track')!.getBoundingClientRect().width,
      opacity: Number(getComputedStyle(element.querySelector('.shell-sidebar-clip')!).opacity),
      workspaceLeft: element.querySelector('.shell-workspace')!.getBoundingClientRect().left,
    }
  })
  expect(midway.trackWidth).toBeGreaterThan(0)
  expect(midway.trackWidth).toBeLessThan(275)
  expect(midway.opacity).toBeGreaterThan(0)
  expect(midway.opacity).toBeLessThan(1)
  expect(midway.workspaceLeft).toBeCloseTo(49 + midway.trackWidth)

  await toggle.press('Enter')
  await expect(frame.locator('.shell-sidebar-track')).toHaveCSS('width', '275px')
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await toggle.press('Enter')
  await expect(frame.locator('.shell-sidebar-track')).toHaveCSS('width', '0px')
  expect(await frame.evaluate(element => element.getAnimations().length)).toBe(0)
})

for (const width of [320, 390]) {
  test(`mobile shell at ${width}px overlays the sidebar and returns keyboard focus`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 })
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto('/?workspace-preview=reporter')
    const tray = page.getByRole('navigation', { name: 'Main navigation' })
    const toggle = page.getByRole('button', { name: 'Toggle sidebar' })
    const sidebar = page.getByRole('complementary', { name: 'Reports' })

    await expect(tray).toHaveCSS('width', '40px')
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await toggle.press('Enter')
    await expect(sidebar).toBeFocused()
    await expect(page.getByRole('separator', { name: 'Resize sidebar' })).toHaveCount(0)
    await page.keyboard.press('Escape')
    await expect(toggle).toBeFocused()
    await expect(sidebar).toBeHidden()
    await toggle.press('Enter')
    await sidebar.getByRole('button', { name: 'Refund totals are duplicated' }).click()
    await expect(toggle).toBeFocused()
    await expect(page.getByRole('heading', { name: 'Refund totals are duplicated' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
  })
}

test('QA review keeps discussion, AI and checklist changes with the selected bug', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/?workspace-preview=qa')
  const queue = page.getByRole('region', { name: 'Verification', exact: true })
  const refund = queue.getByRole('button', { name: /Refund totals/ })
  await queue.getByRole('button', { name: 'All bugs' }).click()
  await refund.click()
  const overview = page.getByRole('button', { name: 'Bug overview' })
  const summary = page.getByRole('article', { name: 'Bug review summary' })
  await overview.click()
  await page.getByRole('dialog', { name: 'Bug overview' }).getByRole('button', { name: 'Original report' }).click()
  await expect(page.locator('#qa-review-report summary')).toBeFocused()
  await expect(summary.getByText('Each refund appears once.')).toBeVisible()
  await page.getByRole('textbox', { name: 'Review comment' }).fill('Confirmed the totals on staging.')
  await page.getByRole('button', { name: 'Post comment' }).click()
  await expect(summary.getByText('Review note')).toBeVisible()

  await page.getByRole('button', { name: 'Review with AI' }).click()
  const chat = page.getByRole('region', { name: 'AI review for TD-023' })
  await expect(chat.getByText('Simulated AI')).toBeVisible()
  await expect(page.getByRole('region', { name: 'Change map' })).toHaveCount(0)
  await chat.getByRole('checkbox', { name: /Repeat the operation/ }).check()
  await chat.getByRole('button', { name: 'Add selected checks' }).click()
  await page.getByRole('tab', { name: 'Verification' }).click()
  await expect(page.getByText('3/6 checked')).toBeVisible()
  await chat.getByRole('textbox').fill('Keep this draft')
  await page.getByRole('button', { name: 'Back to verification queue' }).click()
  await queue.getByRole('button', { name: /CSV export fails/ }).click()
  await expect(chat).toHaveCount(0)
  await expect(page.getByRole('tab', { name: 'Summary' })).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('button', { name: 'Back to verification queue' }).click()
  await refund.click()
  await expect(chat.getByRole('textbox')).toHaveValue('Keep this draft')
  await expect(page.getByText('3/6 checked')).toBeVisible()
  await chat.getByRole('button', { name: 'Collapse AI review' }).click()
  await page.getByRole('tab', { name: 'Summary' }).click()
  await expect(page.getByRole('region', { name: 'Change map' })).toBeVisible()
})

test('QA verification copies template guidance and reopens a bug with a reason', async ({ page }) => {
  await page.goto('/?workspace-preview=qa')
  const tray = page.getByRole('navigation', { name: 'Main navigation' })
  await tray.getByRole('button', { name: 'Test templates' }).click()
  await page.getByRole('button', { name: 'New template' }).click()
  await page.getByLabel('Name', { exact: true }).fill('Refund regression')
  await page.getByLabel('Preconditions').fill('Use the staging build')
  await page.getByLabel('Check name').fill('Refresh after two refunds')
  await page.getByLabel('Steps').fill('Reload the payment details')
  await page.getByLabel('Expected result').fill('Both refunds appear once')
  await page.getByRole('button', { name: 'Save template' }).click()
  await tray.getByRole('button', { name: 'Verification' }).click()
  const queue = page.getByRole('region', { name: 'Verification', exact: true })
  await queue.getByRole('button', { name: /Refund totals/ }).click()
  await page.getByRole('tab', { name: 'Verification' }).click()
  await page.getByText('Choose template', { exact: true }).click()
  await page.getByText('Refund regression', { exact: true }).click()
  await page.getByRole('button', { name: 'Apply', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Apply', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: /Refresh after two refunds/ }).click()
  const check = page.getByRole('region', { name: 'Selected check details' })
  await expect(check.getByText('Use the staging build')).toBeVisible()
  await expect(check.getByText('Reload the payment details')).toBeVisible()
  await expect(check.getByText('Both refunds appear once')).toBeVisible()
  await tray.getByRole('button', { name: 'Test templates' }).click()
  await page.getByRole('complementary').getByRole('button', { name: 'Refund regression' }).click()
  await page.getByRole('button', { name: 'Delete template' }).click()
  await page.getByRole('button', { name: 'Delete template' }).click()
  await tray.getByRole('button', { name: 'Verification' }).click()
  await page.getByRole('button', { name: /Refresh after two refunds/ }).click()
  await expect(check.getByText('Both refunds appear once')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Verify and close' })).toBeDisabled()
  await page.getByRole('button', { name: 'Reopen', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Confirm reopen' })).toBeDisabled()
  await page.getByLabel('Reason for reopening').fill('The total still duplicates after refresh.')
  await page.getByRole('button', { name: 'Confirm reopen' }).click()
  await expect(page.getByText('This review is reopened. Results are read-only.')).toBeVisible()
  await page.getByRole('button', { name: 'Back to verification queue' }).click()
  await queue.getByRole('button', { name: 'Reviewed by me' }).click()
  await expect(queue.getByRole('button', { name: /Refund totals.*Reopened/ })).toBeVisible()
})

test('QA mobile review stacks chat and verifies a bug after all checks pass', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/?workspace-preview=qa')
  await page.getByRole('region', { name: 'Verification', exact: true })
    .getByRole('button', { name: /Refund totals/ }).click()
  await page.getByRole('button', { name: 'Review with AI' }).click()
  const chat = page.getByRole('region', { name: 'AI review for TD-023' })
  const document = page.getByRole('region', { name: 'Bug review', exact: true })
  expect((await chat.boundingBox())!.y).toBeLessThan((await document.boundingBox())!.y)
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(390)
  await chat.getByRole('button', { name: 'Collapse AI review' }).click()
  await page.getByRole('tab', { name: 'Verification' }).click()
  const inspector = page.getByRole('region', { name: 'Selected check details' })
  for (const check of ['Repeat refresh', 'Multiple refunds', 'Normal payment flow']) {
    await page.getByRole('button', { name: new RegExp(check) }).click()
    await inspector.getByText('Pass', { exact: true }).click()
  }
  await expect(page.getByText('5/5 checked')).toBeVisible()
  await page.getByLabel('Verification notes').fill('Retested on build 1.4.2.')
  await page.getByRole('button', { name: 'Verify and close' }).click()
  await expect(page.getByText('This review is closed. Results are read-only.')).toBeVisible()
  await page.getByRole('tab', { name: 'Summary' }).click()
  await page.getByText('Verified and closed').click()
  await expect(page.getByText(/Retested on build 1.4.2./)).toBeVisible()
})
