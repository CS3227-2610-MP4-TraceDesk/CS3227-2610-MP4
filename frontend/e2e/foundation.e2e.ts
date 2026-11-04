import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

test('starter page explains the three-stage workflow accessibly', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Software bugs, from report to verification' })).toBeVisible()
  await expect(page.getByRole('listitem')).toHaveCount(3)
  const results = await new AxeBuilder({ page }).analyze()
  expect(results.violations).toEqual([])
})
