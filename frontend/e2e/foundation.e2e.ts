import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { mockAuthApi } from './helpers/authApi'

test('login supports production appearance, controls and keyboard access', async ({ page }) => {
  await mockAuthApi(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
  await expect(page.getByText('Demo accounts', { exact: true })).toHaveCount(0)
  await expect(page.locator('.sign-in-card')).toHaveCSS('border-radius', '24px')
  await expect(page.locator('.sign-in-card')).toHaveCSS('border-top-width', '2px')

  await page.getByLabel('Email', { exact: true }).fill('person@example.test')
  await page.getByLabel('Password', { exact: true }).fill('a-test-password')
  await page.getByRole('button', { name: 'Show password' }).click()
  await expect(page.getByLabel('Password', { exact: true })).toHaveAttribute('type', 'text')
  await page.getByRole('button', { name: 'Hide password' }).click()
  await expect(page.getByLabel('Password', { exact: true })).toHaveAttribute('type', 'password')

  const results = await new AxeBuilder({ page }).analyze()
  expect(results.violations).toEqual([])
  await page.getByRole('button', { name: 'Toggle theme' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  const lightResults = await new AxeBuilder({ page }).analyze()
  expect(lightResults.violations).toEqual([])
})

test('production sends credentials to the backend and never fakes a signed-in state', async ({ page }) => {
  const requests = await mockAuthApi(page, { loginStatus: 401 })
  await page.goto('/')
  await page.getByLabel('Email', { exact: true }).fill('qa@tracedesk.test')
  await page.getByLabel('Password', { exact: true }).fill('tracedesk-demo')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('Email or password is incorrect.')
  await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
  expect(requests.find(request => request.path === '/api/session/login')).toMatchObject({
    method: 'POST',
    headers: expect.objectContaining({ 'x-csrf-token': 'synthetic-csrf-token' }),
    body: JSON.stringify({ login: 'qa@tracedesk.test', password: 'tracedesk-demo' }),
  })
  expect(await page.getByRole('heading', { name: 'You’re signed in' }).count()).toBe(0)
})
