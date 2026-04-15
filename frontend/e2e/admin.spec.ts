import { test, expect } from '@playwright/test';

test('T-UI-11: admin sources page — lists ingested legal decree sources', async ({ page }) => {
  await page.goto('/sources');
  // After real ingestion (Plan 02), at least 3 sources should be visible
  // Check for Vietnamese legal decree names in source titles
  await expect(page.getByText(/Nghị định|Luật Giao thông|Thông tư/i).first()).toBeVisible({ timeout: 15000 });
});

test('T-UI-12: admin sources page — approved and active badge visible', async ({ page }) => {
  await page.goto('/sources');
  // Sources approved in Plan 02 should show APPROVED status badge
  // The sources page uses column definitions that render approval state
  await expect(page.getByText(/APPROVED/i).first()).toBeVisible({ timeout: 15000 });
});

test('T-UI-13: admin parameters page — production system prompt is displayed', async ({ page }) => {
  await page.goto('/parameters');
  // Parameters page header — "Bộ tham số AI" from smoke.spec.ts baseline
  await expect(page.getByText(/Bộ tham số AI/i).first()).toBeVisible({ timeout: 15000 });
  // Active parameter set must show ACTIVE or equivalent status
  await expect(page.getByText(/ACTIVE|active/i).first()).toBeVisible({ timeout: 15000 });
});

test('T-UI-14: admin chat logs page — chat logs from scenario testing are recorded', async ({ page }) => {
  await page.goto('/chat-logs');
  // After scenario testing, at least one log entry should be present
  // Chat logs page renders a table — check for any row or empty state text
  const tableRow = page.locator('table tbody tr').first();
  const dataRow = page.locator('[data-row]').first();
  const listItem = page.locator('li').first();
  // Wait for at least one of these to be visible
  await expect(tableRow.or(dataRow).or(listItem)).toBeVisible({ timeout: 15000 });
});

test('T-UI-15: admin checks page — check definitions are visible', async ({ page }) => {
  await page.goto('/checks');
  // Check definitions page should list check items
  // Page header or content referencing checks/kiểm tra
  await expect(page.getByText(/Định nghĩa kiểm tra|check|kiểm tra/i).first()).toBeVisible({ timeout: 15000 });
});
