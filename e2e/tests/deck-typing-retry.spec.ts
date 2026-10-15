import { test, expect, type Page } from '@playwright/test';

/**
 * Deck study, typing mode: after "Không biết" (or a wrong answer) the card flips to the
 * answer; flipping it back must show the input again so the word can be retyped.
 * Uses the free B2 deck seeded by scripts/migrations/002_seed_b2_deck.sql. See e2e/README.md.
 */
const API = process.env.API_URL ?? 'http://localhost:8000';
const DECK = '22222222-2222-2222-2222-222222222204';

async function newUser(page: Page) {
  const email = `e2e_typing_${Date.now()}@test.local`;
  const r = await page.request.post(`${API}/auth/register`, { data: { email, password: 'Passw0rd!e2e', cefrLevel: 'B2' } });
  expect(r.status()).toBe(201);
  const a = await r.json();
  await page.addInitScript((a) => {
    window.localStorage.setItem(
      'parroto-auth',
      JSON.stringify({ state: { user: { userId: a.userId, email: a.email, cefrLevel: a.cefrLevel }, token: a.token, refreshToken: a.refreshToken }, version: 0 })
    );
  }, a);
}

test('typing mode: flip back after "Không biết" → can type again', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await newUser(page);
  // P2-anki: the server picks the session's card (new cards in a per-visit seeded order), so read it
  // from the page's own study request.
  const session = page.waitForResponse((r) => r.url().startsWith(`${API}/vocabs/decks/${DECK}/study`) && r.ok());
  await page.goto(`/vocabulary/decks/${DECK}`);
  const word: string = (await (await session).json()).card.word;
  await page.getByRole('switch', { name: /Chế độ gõ từ/ }).click();
  const input = page.getByLabel('Nhập từ tiếng Anh');
  await expect(input).toBeVisible();

  // 1) "Không biết" → answer shown, input gone
  await page.getByRole('button', { name: 'Không biết' }).click();
  await expect(page.getByRole('status')).toContainText(word);
  await expect(input).toHaveCount(0);

  // 2) flip back with the keyboard → input is back, focused and empty
  await page.keyboard.press('Space');
  await expect(input).toBeVisible();
  await expect(input).toBeFocused();
  await expect(input).toHaveValue('');

  // 3) type the word correctly → correct, but still suggested "Học lại" because the answer was seen
  await input.fill(word);
  await input.press('Enter');
  await expect(page.getByRole('status')).toContainText('Bạn đã xem đáp án trước đó');

  // 4) flip back by clicking the answer face → input again, still typeable
  await page.getByRole('status').waitFor();
  await page.locator('[aria-hidden="false"]').filter({ hasText: word }).first().click();
  await expect(input).toBeVisible();
  await input.fill('wrong');
  await expect(input).toHaveValue('wrong');
  expect(errors).toEqual([]);
});
