import { test, expect, type Page } from '@playwright/test';

/**
 * P2-anki: Anki-style deck session on a fresh personal deck with two cards.
 * New card Good → 10-minute step, Easy → 4 days, the learning card comes back (learn ahead),
 * Good again graduates (1 day) → "Đã xong hôm nay". Self-contained; see e2e/README.md.
 */
const API = process.env.API_URL ?? 'http://localhost:8000';
const SHOTS = process.env.SHOTS_DIR ?? 'screenshots';

async function newUser(page: Page) {
  const email = `e2e_anki_${Date.now()}@test.local`;
  const r = await page.request.post(`${API}/auth/register`, { data: { email, password: 'Passw0rd!e2e', cefrLevel: 'B1' } });
  expect(r.status()).toBe(201);
  const a = await r.json();
  await page.addInitScript((a) => {
    window.localStorage.setItem(
      'parroto-auth',
      JSON.stringify({ state: { user: { userId: a.userId, email: a.email, cefrLevel: a.cefrLevel }, token: a.token, refreshToken: a.refreshToken }, version: 0 })
    );
  }, a);
  return a.token as string;
}

async function previews(page: Page) {
  return Promise.all(['again', 'hard', 'good', 'easy'].map((n) => page.getByTestId(`preview-${n}`).innerText()));
}

test('deck session: counters, server previews, learning step, done screen', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  const token = await newUser(page);
  const headers = { Authorization: `Bearer ${token}` };
  const deck = await (await page.request.post(`${API}/vocabs/user/decks`, { headers, data: { name: 'Anki E2E' } })).json();
  for (const [word, meaning] of [['apple', 'quả táo'], ['river', 'dòng sông']]) {
    const r = await page.request.post(`${API}/vocabs/user/decks/${deck.id}/cards`, { headers, data: { word, meaning } });
    expect(r.status()).toBe(201);
  }

  await page.goto(`/vocabulary/decks/${deck.id}`);
  await expect(page.getByLabel('Mới: 2')).toBeVisible();
  await expect(page.getByLabel('Đang học: 0')).toBeVisible();
  await expect(page.getByLabel('Ôn: 0')).toBeVisible();

  // 1) new card: Good → 10-minute step
  await page.keyboard.press('Space');
  expect(await previews(page)).toEqual(['<1 phút', '6 phút', '10 phút', '4 ngày']);
  await page.screenshot({ path: `${SHOTS}/anki-new-card.png`, fullPage: true });
  await page.keyboard.press('3');
  await expect(page.getByText(/gặp lại sau 10 phút/)).toBeVisible();
  await expect(page.getByLabel('Mới: 1')).toBeVisible();
  await expect(page.getByLabel('Đang học: 1')).toBeVisible();

  // 2) second new card: Easy → 4 days
  await page.keyboard.press('Space');
  await page.getByRole('button', { name: /Dễ/ }).click();
  await expect(page.getByText(/gặp lại sau 4 ngày/)).toBeVisible();
  await expect(page.getByLabel('Mới: 0')).toBeVisible();

  // 3) only the learning card is left: shown early (learn ahead); Good graduates with 1 day
  await expect(page.getByText('Đang học', { exact: true }).first()).toBeVisible();
  await page.keyboard.press('Space');
  expect(await previews(page)).toEqual(['<1 phút', '10 phút', '1 ngày', '4 ngày']);
  await page.keyboard.press('3');

  // 4) done for today
  await expect(page.getByRole('heading', { name: 'Đã xong hôm nay!' })).toBeVisible();
  await expect(page.getByTestId('next-due')).toContainText('lúc 04:00');
  await expect(page.getByLabel('Đang học: 0')).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/anki-done.png`, fullPage: true });

  // the notebook lists both words with their intervals; the global session is done too
  await page.goto('/vocabulary/notebook');
  await expect(page.getByText('Chu kỳ 1 ngày')).toBeVisible();
  await expect(page.getByText('Chu kỳ 4 ngày')).toBeVisible();
  await page.goto('/vocabulary/review');
  await expect(page.getByRole('heading', { name: 'Ôn tập từ vựng' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Đã xong hôm nay!' })).toBeVisible();
  expect(errors).toEqual([]);
});
