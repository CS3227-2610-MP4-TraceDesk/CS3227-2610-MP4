import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

test('role fixtures open the shared shell without an authentication request', async ({ page }) => {
  const apiRequests: string[] = []
  page.on('request', request => {
    const { pathname } = new URL(request.url())
    if (pathname.startsWith('/api/')) apiRequests.push(pathname)
  })

  for (const [role, primary, secondary] of [
    ['reporter', 'Reports', 'Environments'],
    ['developer', 'My work', 'All bugs'],
    ['qa', 'Verification', 'Test templates'],
  ]) {
    await page.goto(`/?workspace-preview=${role}`)
    const tray = page.getByRole('navigation', { name: 'Main navigation' })
    await expect(tray.getByRole('button', { name: primary })).toHaveAttribute('aria-current', 'page')
    await tray.getByRole('button', { name: secondary }).click()
    await expect(page.getByRole('heading', { name: secondary, exact: true })).toBeVisible()
    if (role === 'developer') {
      await expect(page.getByRole('button', { name: 'Toggle sidebar' })).toHaveCount(0)
    }
    await expect(tray.getByRole('button', { name: 'Settings' })).toBeVisible()
  }
  expect(apiRequests).toEqual([])
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
