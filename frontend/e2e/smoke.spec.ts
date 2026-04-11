import { test, expect } from '@playwright/test';

test('homepage loads with sidebar', async ({ page }) => {
  await page.goto('/');
  // Verify sidebar renders
  await expect(page.getByText('Tro chuyen')).toBeVisible();
  await expect(page.getByText('Quan ly nguon')).toBeVisible();
});

test('admin sources page loads', async ({ page }) => {
  await page.goto('/sources');
  await expect(page.getByText('Quan ly nguon')).toBeVisible();
});

test('admin parameters page loads', async ({ page }) => {
  await page.goto('/parameters');
  await expect(page.getByText('Bo tham so AI')).toBeVisible();
});
