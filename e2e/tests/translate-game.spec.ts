import { test, expect, type Page } from '@playwright/test';

/**
 * T15 "Dịch nhanh": lobby → 60 s round (correct answers build a combo, hint, skip, wrong answer)
 * → summary (record, words to review, add to notebook) → replay → notebook source;
 * plus a round that runs out of time on its own.
 */
const API = process.env.API_URL ?? 'http://localhost:8000';
const DECK = '22222222-2222-2222-2222-222222222204'; // B2 deck

async function newUser(page: Page) {
  const email = `e2e_game_${Date.now()}@test.local`;
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
  return a.token as string;
}

/** meaning → word of the deck (the test "knows" the answers). */
async function answerKey(page: Page, token: string): Promise<Map<string, string>> {
  const r = await page.request.get(`${API}/vocabs/decks/${DECK}/cards?limit=1000`, { headers: { Authorization: `Bearer ${token}` } });
  const body = await r.json();
  return new Map(body.data.map((c: { meaning: string; word: string }) => [c.meaning, c.word]));
}

async function answerCurrent(page: Page, key: Map<string, string>) {
  const meaning = (await page.getByTestId('game-meaning').textContent())!.trim();
  const seq = await page.getByText(/^Câu \d+$/).textContent();
  await page.getByLabel('Từ tiếng Anh').fill(key.get(meaning) ?? 'zzzz');
  await page.keyboard.press('Enter');
  await expect(page.getByText(/^Câu \d+$/)).not.toHaveText(seq!); // next question shown
}

test('play a round: combo, hint, skip, summary, notebook, replay', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  const token = await newUser(page);
  const key = await answerKey(page, token);

  await page.goto('/dashboard');
  await page.getByRole('link', { name: /Dịch nhanh/ }).first().click();
  await expect(page).toHaveURL(/\/games$/);
  await expect(page.getByRole('heading', { name: /Dịch nhanh/, level: 1 })).toBeVisible();
  await expect(page.getByText('Chưa có ván nào')).toBeVisible();

  await page.getByRole('radio', { name: /Bộ từ/ }).click();
  await page.getByRole('combobox').selectOption(DECK);
  await page.screenshot({ path: 'screenshots/16-game-lobby.png', fullPage: true });
  await page.getByRole('button', { name: /Bắt đầu/ }).click();

  // 5 correct answers in a row → combo 5, multiplier ×1.5
  await expect(page.getByTestId('game-meaning')).toBeVisible();
  for (let i = 0; i < 5; i++) await answerCurrent(page, key);
  await expect(page.getByTestId('game-combo')).toContainText('Combo 5');
  await expect(page.getByTestId('game-combo')).toContainText('×1.5');
  await expect(page.getByTestId('game-feedback')).toContainText('Chính xác');
  const scoreAfter5 = Number(await page.getByTestId('game-score').textContent());
  expect(scoreAfter5).toBeGreaterThanOrEqual(5 * 20 + 10); // 4×20 + 30 at least, plus speed bonuses

  // hint (Ctrl+H) then skip (Tab): mask shown, combo reset
  await page.keyboard.press('Control+h');
  await expect(page.getByTestId('game-mask')).toHaveText(/^_( _)*/);
  await page.keyboard.press('Control+h');
  await expect(page.getByTestId('game-mask')).toHaveText(/^[a-z]/i);
  await page.screenshot({ path: 'screenshots/16-game-play.png', fullPage: true });
  await page.keyboard.press('Tab');
  await expect(page.getByTestId('game-feedback')).toContainText('Đã bỏ qua');
  await expect(page.getByTestId('game-combo')).toContainText('Combo 0');

  // wrong answer
  await page.getByLabel('Từ tiếng Anh').fill('zzzz');
  await page.keyboard.press('Enter');
  await expect(page.getByTestId('game-feedback')).toContainText('Chưa đúng');
  const score = await page.getByTestId('game-score').textContent();

  // finish early → summary
  await page.getByRole('button', { name: 'Kết thúc sớm' }).click();
  await expect(page.getByTestId('summary-score')).toHaveText(score!);
  await expect(page.getByTestId('summary-record')).toContainText('Kỷ lục đầu tiên');
  await expect(page.getByTestId('review-word')).toHaveCount(2); // skipped + wrong
  await page.screenshot({ path: 'screenshots/16-game-summary.png', fullPage: true });
  await page.getByRole('button', { name: /Thêm tất cả vào sổ tay/ }).click();
  await expect(page.getByText('✓ Trong sổ tay')).toHaveCount(2);

  // replay the same source
  await page.getByRole('button', { name: 'Chơi lại' }).click();
  await expect(page.getByTestId('game-meaning')).toBeVisible();
  await page.getByRole('button', { name: 'Kết thúc sớm' }).click();
  await expect(page.getByTestId('summary-record')).toContainText(`kỷ lục ${score}`);

  // notebook source: the 2 words just added
  await page.getByRole('button', { name: 'Đổi nguồn từ' }).click();
  await expect(page.getByText(/1 ván · tổng|2 ván · tổng/)).toBeVisible();
  await page.getByRole('radio', { name: /Từ của tôi/ }).click();
  await page.getByRole('button', { name: /Bắt đầu/ }).click();
  const notebook = await (await page.request.get(`${API}/vocabs/user/learned`, { headers: { Authorization: `Bearer ${token}` } })).json();
  const meanings = notebook.data.map((w: { meaning: string }) => w.meaning);
  expect(meanings).toContain((await page.getByTestId('game-meaning').textContent())!.trim());
  await page.getByRole('button', { name: 'Kết thúc sớm' }).click();
  await expect(page.getByTestId('summary-score')).toHaveText('0');

  expect(errors).toEqual([]);
});

test('the round ends by itself when the 60 s are up', async ({ page }) => {
  test.setTimeout(120_000);
  await newUser(page);
  await page.goto('/games');
  await page.getByRole('radio', { name: /Theo trình độ/ }).click();
  await page.getByRole('radio', { name: 'A1' }).click();
  await page.getByRole('button', { name: /Bắt đầu/ }).click();
  await expect(page.getByTestId('game-meaning')).toBeVisible();
  await expect(page.getByLabel('Thời gian còn lại')).toContainText(/0:5\d|1:00/);
  await expect(page.getByTestId('summary-score')).toBeVisible({ timeout: 75_000 });
  await expect(page.getByText('Trình độ A1 · Nước rút 60 giây')).toBeVisible();
});
