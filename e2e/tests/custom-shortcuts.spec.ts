import { test, expect, type Page } from '@playwright/test';

/**
 * Customisable shortcuts (/profile/shortcuts): record a new combo, conflict + typing-key
 * validation, the new key works on the deck page and shows in the "?" modal, reset to default.
 */
const API = process.env.API_URL ?? 'http://localhost:8000';
const DECK = '22222222-2222-2222-2222-222222222204';

async function newUser(page: Page) {
  const email = `e2e_keys_${Date.now()}@test.local`;
  const r = await page.request.post(`${API}/auth/register`, { data: { email, password: 'Passw0rd!e2e', cefrLevel: 'B2' } });
  expect(r.status()).toBe(201);
  const a = await r.json();
  await page.addInitScript((a) => {
    if (!window.localStorage.getItem('parroto-auth'))
      window.localStorage.setItem(
        'parroto-auth',
        JSON.stringify({ state: { user: { userId: a.userId, email: a.email, cefrLevel: a.cefrLevel }, token: a.token, refreshToken: a.refreshToken }, version: 0 })
      );
  }, a);
}

test('customise shortcuts: record, validate, use, reset', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await newUser(page);

  // entry point from the profile page
  await page.goto('/profile');
  await page.getByRole('link', { name: 'Tuỳ chỉnh phím tắt' }).click();
  await expect(page).toHaveURL(/\/profile\/shortcuts$/);
  await expect(page.getByRole('heading', { name: 'Phím tắt', level: 1 })).toBeVisible();

  const flip = page.locator('[data-action="flashcard.flip"]');
  const again = page.locator('[data-action="flashcard.again"]');
  const replay = page.locator('[data-action="dictation.replay"]');

  // 1) flashcard "Lật thẻ": replace Space by F
  await flip.getByRole('button', { name: /^Đổi phím Space/ }).click();
  await expect(flip.getByText('Nhấn phím…')).toBeVisible();
  await page.keyboard.press('f');
  await expect(flip.getByText('(đã đổi)')).toBeVisible();
  await expect(flip.locator('kbd', { hasText: /^F$/ })).toBeVisible();

  // 2) conflict: "Học lại" → 2 (used by "Khó") is refused, Esc cancels
  await again.getByRole('button', { name: /^Đổi phím 1/ }).click();
  await page.keyboard.press('2');
  await expect(again.getByRole('alert')).toContainText('Trùng với "Khó (Hard)"');
  await page.keyboard.press('Escape');
  await expect(again.getByText('Nhấn phím…')).toHaveCount(0);

  // 3) dictation: a bare letter would type into the answer box → refused; Alt+R accepted
  await replay.getByRole('button', { name: /Thêm phím|^Đổi phím `/ }).first().click();
  await page.keyboard.press('r');
  await expect(replay.getByRole('alert')).toContainText('gõ chữ vào ô đáp án');
  await page.keyboard.press('Alt+r');
  await expect(replay.locator('kbd', { hasText: /^Alt$/ })).toBeVisible();
  await page.screenshot({ path: 'screenshots/14-custom-shortcuts.png', fullPage: true });

  // 4) the new key works on the deck page, Space no longer flips, "?" shows F
  await page.goto(`/vocabulary/decks/${DECK}`);
  const flipButton = page.getByRole('button', { name: 'Lật thẻ' });
  await expect(flipButton).toBeVisible();
  await page.keyboard.press('Space');
  await expect(flipButton).toBeVisible(); // still front side
  await page.keyboard.press('f');
  await expect(page.getByText('Bạn nhớ từ này ở mức nào?')).toBeVisible();
  await page.getByRole('button', { name: 'Xem phím tắt' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.locator('li', { hasText: 'Lật thẻ' }).locator('kbd', { hasText: /^F$/ })).toBeVisible();
  await dialog.getByRole('link', { name: /Tuỳ chỉnh phím tắt/ }).click();
  await expect(page).toHaveURL(/\/profile\/shortcuts$/);

  // 5) reset flashcard scope → Space again, persisted across reload
  await page.reload();
  await expect(flip.locator('kbd', { hasText: /^F$/ })).toBeVisible();
  await flip.getByRole('button', { name: /Khôi phục phím mặc định/ }).click();
  await expect(flip.locator('kbd', { hasText: /^Space$/ })).toBeVisible();
  await expect(flip.getByText('(đã đổi)')).toHaveCount(0);
  expect(errors).toEqual([]);
});
