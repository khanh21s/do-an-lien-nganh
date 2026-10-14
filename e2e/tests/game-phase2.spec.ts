import { test, expect, type Page } from '@playwright/test';

/**
 * T16 "Dịch nhanh" phase 2 + 3: Daily challenge (10 shared words, one attempt, leaderboard),
 * XP / level / daily goal (counts for the streak), badges; Survival is Premium-only, loses a life
 * on a miss or when the 15 s word timer runs out.
 * PREMIUM_EMAIL / PREMIUM_PASSWORD: an account with an active subscription (see e2e/README.md).
 */
const API = process.env.API_URL ?? 'http://localhost:8000';

async function signIn(page: Page, a: { userId: string; email: string; cefrLevel: string; token: string; refreshToken: string }) {
  await page.addInitScript((a) => {
    if (!window.localStorage.getItem('parroto-auth'))
      window.localStorage.setItem(
        'parroto-auth',
        JSON.stringify({ state: { user: { userId: a.userId, email: a.email, cefrLevel: a.cefrLevel }, token: a.token, refreshToken: a.refreshToken }, version: 0 })
      );
  }, a);
}

async function newUser(page: Page) {
  const email = `e2e_daily_${Date.now()}@test.local`;
  const r = await page.request.post(`${API}/auth/register`, { data: { email, password: 'Passw0rd!e2e', cefrLevel: 'B1' } });
  expect(r.status()).toBe(201);
  const a = await r.json();
  await signIn(page, a);
  return a;
}

/** meaning → word over all decks this user can open (free decks). */
async function answerKey(page: Page, token: string): Promise<Map<string, string>> {
  const key = new Map<string, string>();
  const decks = await (await page.request.get(`${API}/vocabs/decks?limit=100`)).json();
  for (const d of decks.data) {
    const r = await page.request.get(`${API}/vocabs/decks/${d.id}/cards?limit=1000`, { headers: { Authorization: `Bearer ${token}` } });
    if (r.ok()) for (const c of (await r.json()).data) key.set(c.meaning, c.word);
  }
  return key;
}

async function answerCurrent(page: Page, key: Map<string, string>) {
  const meaning = (await page.getByTestId('game-meaning').textContent())!.trim();
  await page.getByLabel('Từ tiếng Anh').fill(key.get(meaning) ?? 'zzzz');
  await page.keyboard.press('Enter');
}

test('daily challenge, XP, goal → streak, badges, leaderboard, survival locked for free users', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  const a = await newUser(page);
  const key = await answerKey(page, a.token);

  await page.goto('/games');
  await expect(page.getByTestId('game-progress')).toContainText('Cấp 1');
  await expect(page.getByTestId('goal-message')).toContainText('0/200');
  await expect(page.getByTestId('badges').locator('[data-earned="true"]')).toHaveCount(0);

  // Survival needs Premium
  await page.getByRole('radio', { name: /Sinh tồn/ }).click();
  await expect(page.getByRole('link', { name: 'Nâng cấp Premium' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Bắt đầu Sinh tồn' })).toBeDisabled();

  // Daily challenge: 10 words, per-word timer
  await page.getByRole('radio', { name: /Thử thách hôm nay/ }).click();
  await page.getByRole('button', { name: 'Chơi thử thách' }).click();
  await expect(page.getByTestId('game-progress-count')).toHaveText('1/10');
  await expect(page.getByLabel('Thời gian cho từ này')).toContainText(/0:(19|20)/);
  await page.screenshot({ path: 'screenshots/17-game-daily.png', fullPage: true });
  for (let i = 1; i <= 10; i++) {
    await answerCurrent(page, key);
    if (i < 10) await expect(page.getByTestId('game-progress-count')).toHaveText(`${i + 1}/10`);
  }

  // summary: XP, goal met, rank, first badges; no replay for the daily challenge
  await expect(page.getByTestId('summary-score')).toBeVisible();
  await expect(page.getByTestId('summary-progress')).toContainText('XP');
  await expect(page.getByTestId('summary-rank')).toContainText('Hôm nay');
  await expect(page.getByText(/vừa đạt mục tiêu hôm nay/)).toBeVisible();
  await expect(page.getByTestId('new-badges')).toContainText('Khởi động');
  await expect(page.getByTestId('new-badges')).toContainText('Hoàn hảo');
  await expect(page.getByRole('button', { name: 'Chơi lại' })).toHaveCount(0);
  await page.screenshot({ path: 'screenshots/17-game-daily-summary.png', fullPage: true });

  // lobby: already played, on the leaderboard, badges earned, goal met
  await page.getByRole('button', { name: 'Về sảnh trò chơi' }).click();
  await expect(page.getByTestId('daily-status')).toContainText('Bạn đã chơi thử thách hôm nay');
  await expect(page.getByRole('button', { name: 'Chơi thử thách' })).toBeDisabled();
  await page.getByRole('tab', { name: 'Hôm nay' }).click();
  await expect(page.getByTestId('leaderboard').locator('[aria-current="true"]')).toContainText('(bạn)');
  await expect(page.getByTestId('badges').locator('[data-earned="true"]')).not.toHaveCount(0);
  await expect(page.getByTestId('goal-message')).toContainText('Đã đạt mục tiêu');
  await page.screenshot({ path: 'screenshots/17-game-lobby.png', fullPage: true });

  // a second attempt is refused by the server too
  const again = await page.request.post(`${API}/vocabs/games/sessions`, {
    data: { mode: 'DAILY' },
    headers: { Authorization: `Bearer ${a.token}` },
  });
  expect(again.status()).toBe(409);

  // raising the goal: not met any more
  await page.getByLabel('Mục tiêu điểm mỗi ngày').selectOption('500');
  await expect(page.getByTestId('goal-message')).toContainText('Còn');

  // dashboard: the goal day counts for the streak (no lesson practised), game widget
  await page.goto('/dashboard');
  await expect(page.getByText('1 ngày').first()).toBeVisible();
  await expect(page.getByTestId('dashboard-daily')).toContainText('điểm');
  expect(errors).toEqual([]);
});

test('survival (Premium): lives, word timer, harder words', async ({ page }) => {
  test.setTimeout(120_000);
  const email = process.env.PREMIUM_EMAIL;
  test.skip(!email, 'set PREMIUM_EMAIL / PREMIUM_PASSWORD (e2e/README.md)');
  const r = await page.request.post(`${API}/auth/login`, { data: { email, password: process.env.PREMIUM_PASSWORD } });
  expect(r.status()).toBe(200);
  const a = await r.json();
  await signIn(page, a);
  const key = await answerKey(page, a.token);

  await page.goto('/games');
  await page.getByRole('radio', { name: /Sinh tồn/ }).click();
  await page.getByRole('button', { name: 'Bắt đầu Sinh tồn' }).click();
  await expect(page.getByLabel('Còn 3 mạng')).toBeVisible();
  await answerCurrent(page, key);
  await expect(page.getByTestId('game-feedback')).toContainText('Chính xác');

  // let the 15 s word timer run out → skipped automatically, one life lost
  await expect(page.getByTestId('game-feedback')).toContainText('Hết giờ', { timeout: 20_000 });
  await expect(page.getByLabel('Còn 2 mạng')).toBeVisible();
  await page.screenshot({ path: 'screenshots/17-game-survival.png', fullPage: true });

  await page.getByLabel('Từ tiếng Anh').fill('zzzz');
  await page.keyboard.press('Enter');
  await expect(page.getByLabel('Còn 1 mạng')).toBeVisible();
  await page.keyboard.press('Tab'); // skip → last life
  await expect(page.getByTestId('summary-score')).toBeVisible();
  await expect(page.getByText(/❤️ Sinh tồn/)).toBeVisible();
  await expect(page.getByTestId('summary-rank')).toContainText('Sinh tồn tuần');
});
