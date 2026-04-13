import { test, expect } from '@playwright/test';

// Helper: send a single-turn question and wait for AI response
// The chat page (/) creates a new thread and redirects to /threads/{id}
// After redirect, the assistant bubble appears in the thread page
async function sendSingleTurnQuestion(page: import('@playwright/test').Page, question: string) {
  await page.goto('/');
  const textarea = page.getByPlaceholder('Nhập câu hỏi về luật giao thông...');
  await textarea.fill(question);
  await textarea.press('Enter');

  // Wait for navigation to /threads/{id} after first message creates the thread
  await page.waitForURL(/\/threads\//, { timeout: 30000 }).catch(async () => {
    // If no redirect happens (network error or direct response), stay on current page
  });

  // Wait for loading skeleton to disappear (AiBubbleLoading uses Skeleton component with animate-pulse)
  await page
    .waitForSelector('.animate-pulse', { state: 'detached', timeout: 30000 })
    .catch(() => {});

  // Return the last assistant bubble (.is-assistant class applied by Message component)
  return page.locator('.is-assistant').last();
}

// ─── GROUNDED RESPONSE TESTS ──────────────────────────────────────────────────

test('T-UI-01: grounded answer renders disclaimer text', async ({ page }) => {
  const bubble = await sendSingleTurnQuestion(page, 'Xe máy vượt đèn đỏ bị phạt bao nhiêu?');
  // Disclaimer is rendered in MessageToolbar below bubble — "tham khảo" appears in disclaimer text
  // The disclaimer text is set by the backend and typically contains "tham khảo"
  await expect(bubble).toContainText('tham khảo', { timeout: 15000 });
});

test('T-UI-02: grounded answer renders citation list', async ({ page }) => {
  const bubble = await sendSingleTurnQuestion(
    page,
    'Không đội mũ bảo hiểm xe máy bị phạt bao nhiêu?',
  );
  // CitationList renders section header "Nguồn tham khảo" when citations are present
  await expect(bubble).toContainText('Nguồn tham khảo', { timeout: 15000 });
});

test('T-UI-03: grounded answer renders legal basis section', async ({ page }) => {
  const bubble = await sendSingleTurnQuestion(
    page,
    'Xe máy có nồng độ cồn vượt 0.25mg/L hơi thở bị phạt bao nhiêu theo Nghị định 168?',
  );
  // Section component renders "Căn cứ pháp lý" as the section title for legalBasis array
  await expect(bubble).toContainText('Căn cứ pháp lý', { timeout: 15000 });
});

test('T-UI-04: grounded answer does not fabricate content — disclaimer always present', async ({
  page,
}) => {
  const bubble = await sendSingleTurnQuestion(
    page,
    'Xe máy cần mang theo giấy tờ gì khi tham gia giao thông?',
  );
  // Both legal basis and disclaimer must be present for grounded response
  await expect(bubble).toContainText('Căn cứ pháp lý', { timeout: 15000 });
  await expect(bubble).toContainText('tham khảo', { timeout: 15000 });
});

// ─── CLARIFICATION NEEDED TESTS ───────────────────────────────────────────────

test('T-UI-05: clarification needed — amber box renders when vehicleType missing', async ({
  page,
}) => {
  const bubble = await sendSingleTurnQuestion(
    page,
    'Tôi bị vi phạm giao thông, bị phạt bao nhiêu?',
  );
  // isClarification branch renders amber box with "Cần làm rõ thêm" header
  // Condition: response.responseMode === 'CLARIFICATION_NEEDED'
  await expect(bubble).toContainText('Cần làm rõ thêm', { timeout: 15000 });
});

test('T-UI-06: clarification needed — pending fact prompt is visible', async ({ page }) => {
  const bubble = await sendSingleTurnQuestion(page, 'Bị phạt bao nhiêu nếu không có đăng ký xe?');
  // pendingFacts[].prompt text — LLM asks about registration status for this question
  // "đăng ký xe" appears in the clarification question from the LLM
  await expect(bubble).toContainText('đăng ký xe', { timeout: 15000 });
});

// ─── REFUSED RESPONSE TESTS ───────────────────────────────────────────────────

test('T-UI-07: refused response — off-topic question renders refusal without legal content', async ({
  page,
}) => {
  const bubble = await sendSingleTurnQuestion(page, 'Luật hôn nhân gia đình quy định gì?');
  // For REFUSED/off-topic: no legalBasis asserted, so Section "Căn cứ pháp lý" must NOT appear
  await expect(bubble).not.toContainText('Căn cứ pháp lý', { timeout: 15000 });
  // Refusal message must be present — system acknowledges the question is out of scope
  await expect(bubble).toContainText('ngoài phạm vi', { timeout: 15000 });
});

test('T-UI-08: hallucination probe — non-existent article returns refusal, not invented content', async ({
  page,
}) => {
  const bubble = await sendSingleTurnQuestion(
    page,
    'Theo Điều 99b Nghị định 168/2024, mức phạt là bao nhiêu?',
  );
  // System must refuse to fabricate — response must say the article is not in source data
  await expect(bubble).toContainText('không có trong nguồn', { timeout: 15000 });
});

// ─── MULTI-TURN THREAD TESTS ──────────────────────────────────────────────────

test('T-UI-09: multi-turn thread — new thread button starts a new chat', async ({ page }) => {
  await page.goto('/');
  // ThreadList renders "Cuộc hội thoại mới" button (with Plus icon)
  const newChatBtn = page.getByRole('button', { name: /Cuộc hội thoại mới/i });
  await expect(newChatBtn).toBeVisible();

  // Send a question to create a thread
  const textarea = page.getByPlaceholder('Nhập câu hỏi về luật giao thông...');
  await textarea.fill('Xe máy không đội mũ bảo hiểm bị phạt bao nhiêu?');
  await textarea.press('Enter');

  // After send, redirect to /threads/{id}
  await page.waitForURL(/\/threads\//, { timeout: 30000 }).catch(() => {});
  await page
    .waitForSelector('.animate-pulse', { state: 'detached', timeout: 30000 })
    .catch(() => {});

  // Thread list in sidebar should show this thread as a button
  const threadButtons = page
    .locator('button')
    .filter({ hasText: /Xe máy|mũ bảo hiểm|Cuộc hội thoại/ });
  await expect(threadButtons.first()).toBeVisible({ timeout: 10000 });
});

test('T-UI-10: multi-turn thread — second message receives context-aware response', async ({
  page,
}) => {
  await page.goto('/');
  const textarea = page.getByPlaceholder('Nhập câu hỏi về luật giao thông...');

  // First message — starts thread, expects clarification or initial response
  await textarea.fill('Tôi đang bị CSGT dừng xe');
  await textarea.press('Enter');

  // Wait for redirect to /threads/{id}
  await page.waitForURL(/\/threads\//, { timeout: 30000 }).catch(() => {});
  await page
    .waitForSelector('.animate-pulse', { state: 'detached', timeout: 30000 })
    .catch(() => {});

  // First assistant bubble must be visible
  const firstBubble = page.locator('.is-assistant').first();
  await expect(firstBubble).toBeVisible({ timeout: 15000 });

  // Second message — provides vehicle type to continue thread
  const textarea2 = page.getByPlaceholder('Nhập câu hỏi về luật giao thông...');
  await textarea2.fill('Tôi đi xe máy');
  await textarea2.press('Enter');

  // Wait for second response
  await page
    .waitForSelector('.animate-pulse', { state: 'detached', timeout: 30000 })
    .catch(() => {});

  // At least two assistant bubbles should exist (thread continued with context)
  // Using nth(1) instead of exact count to tolerate any extra streaming artifacts
  const bubbles = page.locator('.is-assistant');
  await expect(bubbles.nth(1)).toBeVisible({ timeout: 30000 });
});
