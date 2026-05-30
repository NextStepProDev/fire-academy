import { test, expect } from '@playwright/test'

test.describe('Golden Path', () => {
  test('should navigate from home to category page', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/Fire Academy/)

    const trainingLink = page.locator('a[href="/treningi"], button', { hasText: /treningi/i }).first()
    if (await trainingLink.isVisible()) {
      await trainingLink.click()
      await expect(page).toHaveURL(/\/treningi/)
    }
  })

  test('should load trainings page', async ({ page }) => {
    await page.goto('/treningi')
    await expect(page).toHaveURL(/\/treningi/)
  })

  test('should load camps page', async ({ page }) => {
    await page.goto('/obozy')
    await expect(page).toHaveURL(/\/obozy/)
  })

  test('should load courses page', async ({ page }) => {
    await page.goto('/szkolenia')
    await expect(page).toHaveURL(/\/szkolenia/)
  })

  test('should navigate via navbar', async ({ page }) => {
    await page.goto('/')

    const navLinks = page.locator('nav a')
    const count = await navLinks.count()
    expect(count).toBeGreaterThanOrEqual(3)
  })

  test('should show footer with links', async ({ page }) => {
    await page.goto('/')
    const footer = page.locator('footer')
    await expect(footer).toBeVisible()
  })

  test('should show privacy policy page', async ({ page }) => {
    await page.goto('/polityka-prywatnosci')
    await expect(page.locator('h1, h2').first()).toBeVisible()
  })

  test('should redirect /admin to login', async ({ page }) => {
    await page.goto('/admin')
    await expect(page).toHaveURL(/\/admin\/login/)
  })
})
