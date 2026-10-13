import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import * as fs from 'fs';

/**
 * Phase 1 browser E2E (review-final). Runs serially against the docker-compose stack:
 * FE http://localhost:3000, gateway http://localhost:8000. See e2e/README.md.
 */
const API = process.env.API_URL ?? 'http://localhost:8000';
const SHOTS = process.env.SHOTS_DIR ?? 'screenshots';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@parroto.com';
const PASSWORD = 'Passw0rd!e2e';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? PASSWORD;
const DEMO_CODE = process.env.DEMO_CODE ?? 'PROMO-DEMO1-MONTH';
const STATE = process.env.STATE_FILE ?? 'state.json';
const saved = fs.existsSync(STATE) ? JSON.parse(fs.readFileSync(STATE, 'utf8')) : null;
const stamp = saved?.stamp ?? Date.now();
const userEmail = `e2e_${stamp}@test.local`;

fs.mkdirSync(SHOTS, { recursive: true });
const errorsLog: { test: string; kind: string; text: string; url: string }[] = [];

function watch(page: Page, name: string) {
  page.on('pageerror', (e) => errorsLog.push({ test: name, kind: 'pageerror', text: String(e), url: page.url() }));
  page.on('console', (m) => {
    if (m.type() === 'error') errorsLog.push({ test: name, kind: 'console', text: m.text(), url: page.url() });
  });
}

async function shot(page: Page, name: string) {
  await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: true });
}

async function apiLogin(request: APIRequestContext, email: string, register = false, password = PASSWORD) {
  if (register) {
    const r = await request.post(`${API}/auth/register`, { data: { email, password } });
    if (r.status() === 201) return r.json();
  }
  const r = await request.post(`${API}/auth/login`, { data: { email, password } });
  expect(r.status(), `login ${email}`).toBe(200);
  return r.json();
}

/** Puts tokens into the zustand persist store (`parroto-auth`) before the app boots. */
async function loginAs(page: Page, auth: { userId: string; email: string; cefrLevel: string; token: string; refreshToken: string }) {
  await page.addInitScript((a) => {
    if (!window.localStorage.getItem('parroto-auth')) {
      window.localStorage.setItem(
        'parroto-auth',
        JSON.stringify({ state: { user: { userId: a.userId, email: a.email, cefrLevel: a.cefrLevel }, token: a.token, refreshToken: a.refreshToken }, version: 0 })
      );
    }
  }, auth);
}

function wordsOf(text: string): string[] {
  const re = /^([^\p{L}\p{N}]*)(.*?)([^\p{L}\p{N}]*)$/su;
  return text.trim().split(/\s+/).map((c) => re.exec(c)?.[2] ?? '').filter(Boolean);
}

async function noHorizontalScroll(page: Page) {
  return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
}

let userAuth: any = saved?.userAuth;

test.describe.configure({ mode: 'serial' });

test('01 register → dashboard, heatmap, weekly chart, topbar streak', async ({ page, request }) => {
  watch(page, '01');
  await page.goto('/register');
  await page.locator('input[type=email]').fill(userEmail);
  const pw = page.locator('input[type=password]');
  await pw.nth(0).fill(PASSWORD);
  await pw.nth(1).fill(PASSWORD);
  await page.getByRole('button', { name: 'Đăng ký' }).click();
  await expect(page).toHaveURL(/\/dashboard/);
  // ReviewReminder may not show for a new user; close any dialog if present.
  await expect(page.getByRole('img', { name: /Độ chính xác theo tuần/ })).toBeVisible();
  await expect(page.locator('[role=img][aria-label$="lượt"]')).toHaveCount(90);
  await expect(page.getByLabel(/Chuỗi \d+ ngày học liên tiếp/)).toBeVisible();
  await shot(page, '01-dashboard-new-user');
  userAuth = await apiLogin(request, userEmail);
  fs.writeFileSync(STATE, JSON.stringify({ stamp, userAuth }));
});

test('02 lessons search + level filter, open lesson', async ({ page }) => {
  watch(page, '02');
  await loginAs(page, userAuth);
  await page.goto('/lessons');
  const cards = page.locator('a[href^="/lessons/"]');
  await expect(cards.first()).toBeVisible();
  const total = await cards.count();
  await page.getByRole('group', { name: 'Lọc theo trình độ' }).getByRole('button', { name: /^A1/ }).click();
  await expect(page).toHaveURL(/level=A1/);
  await page.waitForTimeout(800);
  const a1 = await page.locator('a[href^="/lessons/"]').count();
  expect(a1).toBeGreaterThan(0);
  expect(a1).toBeLessThanOrEqual(total);
  await page.getByRole('group', { name: 'Lọc theo trình độ' }).getByRole('button', { name: /^A1/ }).click();
  await page.getByLabel('Tìm bài học').fill('Greet');
  await expect(page).toHaveURL(/q=Greet/);
  await expect(page.getByText('Greetings and Introductions').first()).toBeVisible();
  await shot(page, '02-lessons-search');
  await page.getByText('Greetings and Introductions').first().click();
  await expect(page).toHaveURL(/\/lessons\/11111111-1111-1111-1111-111111111101/);
  await expect(page.getByRole('link', { name: /Luyện Dictation/ })).toBeVisible();
  await shot(page, '02-lesson-detail');
});

test('03 dictation Normal + Hard, Enter submit, next shortcut, result page', async ({ page, request }) => {
  watch(page, '03');
  await loginAs(page, userAuth);
  const lesson = await (await request.get(`${API}/content/lessons/11111111-1111-1111-1111-111111111101`)).json();
  const sentences = lesson.sentences as { id: string; text: string }[];
  await page.addInitScript(() => window.localStorage.setItem('parroto:dictation-difficulty', JSON.stringify('Normal')));
  await page.goto('/lessons/11111111-1111-1111-1111-111111111101');
  await page.getByRole('link', { name: /Luyện Dictation/ }).click();
  await expect(page).toHaveURL(new RegExp(`/dictation/${sentences[0].id}`));
  // Normal: fill blanks
  // sentence audio must be loadable from the FE origin (CORP regression)
  const audioOk = await page.evaluate(
    (url) =>
      new Promise<string>((res) => {
        const a = new Audio();
        a.oncanplay = () => res('ok');
        a.onerror = () => res('error ' + (a.error?.code ?? ''));
        a.src = url;
        a.load();
        setTimeout(() => res('timeout'), 8000);
      }),
    `${API}/content/sentences/${sentences[0].id}/audio`
  );
  expect(audioOk).toBe('ok');
  await expect(page.getByText('Audio chưa khả dụng')).toHaveCount(0);
  const words = wordsOf(sentences[0].text);
  const inputs = page.locator('input[aria-label^="Từ thứ "]');
  await expect(inputs.first()).toBeVisible();
  const n = await inputs.count();
  for (let k = 0; k < n; k++) {
    const label = await inputs.nth(k).getAttribute('aria-label');
    const idx = Number(label!.replace('Từ thứ ', '')) - 1;
    await inputs.nth(k).fill(words[idx]);
  }
  await inputs.last().press('Enter');
  await expect(page.getByText('🎉 Chính xác!')).toBeVisible();
  await expect(page.getByText('/80')).toBeVisible();
  await expect(page.getByText(/^\/\//)).toHaveCount(0); // IPA not double-slashed
  await shot(page, '03-dictation-normal-correct');
  // Switch to Hard and redo the same sentence
  await page.getByTitle('Cài đặt độ khó').click();
  await page.getByRole('radio', { name: /Hard/ }).click();
  const ta = page.getByLabel('Câu bạn nghe được');
  await expect(ta).toBeVisible();
  await ta.fill(sentences[0].text);
  await ta.press('Enter');
  await expect(page.getByText('🎉 Chính xác!')).toBeVisible();
  await expect(page.getByText('/100')).toBeVisible();
  await shot(page, '03-dictation-hard-correct');
  // shortcut: Shift+ArrowRight → next sentence
  await ta.press('Shift+ArrowRight');
  await expect(page).toHaveURL(new RegExp(`/dictation/${sentences[1].id}`));
  // wrong answer shows feedback
  const ta2 = page.getByLabel('Câu bạn nghe được');
  await ta2.fill('completely wrong words here');
  await ta2.press('Enter');
  await expect(page.getByText('Chưa đúng, thử lại')).toBeVisible();
  // jump to last sentence and finish
  await page.goto(`/lessons/${lesson.id}/dictation/${sentences[sentences.length - 1].id}`);
  await page.getByRole('button', { name: /Bỏ qua & kết thúc/ }).click();
  await expect(page).toHaveURL(/\/result\?mode=dictation/);
  await expect(page.getByRole('heading', { name: /Kết quả/ })).toBeVisible();
  await expect(page.getByText(/Đã làm \d+\/\d+ câu/)).toBeVisible();
  await shot(page, '03-dictation-result');
});

test('04 shadowing record 2s (fake mic) → word scores + demo badge; history playback', async ({ page }) => {
  watch(page, '04');
  await loginAs(page, userAuth);
  await page.goto('/lessons/11111111-1111-1111-1111-111111111101/shadowing');
  await expect(page).toHaveURL(/\/shadowing\/[0-9a-f-]{36}/);
  await page.getByRole('button', { name: 'Bắt đầu ghi âm' }).click();
  await page.waitForTimeout(2200);
  await page.getByRole('button', { name: 'Dừng ghi âm' }).click();
  await expect(page.getByText('🧪 Chế độ demo – điểm mô phỏng')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText(/Bản ghi của bạn \(\d/)).toBeVisible();
  await shot(page, '04-shadowing-result');
  // History → open shadowing attempt → recording plays from a blob URL
  await page.goto('/practice/history');
  await expect(page.getByText(/Shadowing/).first()).toBeVisible();
  await page.locator('tbody tr', { hasText: 'Shadowing' }).first().click();
  const dlg = page.getByRole('dialog');
  await expect(dlg).toBeVisible();
  const audio = dlg.locator('audio');
  await expect(audio).toHaveAttribute('src', /^blob:/);
  const played = await audio.evaluate(async (el: HTMLAudioElement) => {
    try {
      await el.play();
      await new Promise((r) => setTimeout(r, 400));
      return !el.paused || el.currentTime > 0;
    } catch (e) {
      return String(e);
    }
  });
  expect(played).toBe(true);
  await shot(page, '04-history-recording');
});

test('05 vocabulary: deck study with keys, review with flip + 1–4, notebook schedule', async ({ page, request }) => {
  watch(page, '05');
  await loginAs(page, userAuth);
  const decks = (await (await request.get(`${API}/vocabs/decks`)).json()).data as any[];
  const deck = decks.find((d) => !d.isProOnly);
  await page.goto(`/vocabulary/decks/${deck.id}`);
  await expect(page.getByRole('button', { name: 'Lật thẻ' })).toBeVisible();
  for (const key of ['1', '2', '3', '4']) {
    await page.keyboard.press('Space');
    await expect(page.getByText('Bạn nhớ từ này ở mức nào?')).toBeVisible();
    await page.keyboard.press(key);
    await page.waitForTimeout(600);
  }
  await shot(page, '05-deck-study');
  // Make the 4 learned cards due now so the review queue is not empty (direct DB tweak done by the runner).
  test.info().annotations.push({ type: 'note', description: 'cards made due via SQL by runner' });
  const learned = await (await request.get(`${API}/vocabs/user/learned`, { headers: { Authorization: `Bearer ${userAuth.token}` } })).json();
  const list = (learned.data ?? learned.content ?? learned) as any[];
  expect(list.length).toBeGreaterThanOrEqual(4);
});

test('05b vocabulary review session + notebook', async ({ page }) => {
  watch(page, '05b');
  await loginAs(page, userAuth);
  await page.goto('/vocabulary/review');
  await expect(page.getByRole('heading', { name: 'Ôn tập từ vựng' })).toBeVisible();
  const flip = page.getByRole('button', { name: 'Lật thẻ' });
  await expect(flip).toBeVisible();
  await shot(page, '05-review-front');
  let rated = 0;
  for (const key of ['1', '2', '3', '4']) {
    if (!(await flip.isVisible().catch(() => false))) break;
    await page.keyboard.press('Space');
    await expect(page.getByText('Bạn nhớ từ này ở mức nào?')).toBeVisible();
    if (rated === 0) await shot(page, '05-review-back');
    await page.keyboard.press(key);
    rated++;
    await page.waitForTimeout(700);
  }
  expect(rated).toBeGreaterThanOrEqual(1);
  await shot(page, '05-review-summary');
  await page.goto('/vocabulary/notebook');
  await expect(page.getByText(/Ôn tiếp|Đến hạn ôn/).first()).toBeVisible();
  await expect(page.getByText(/Chu kỳ \d+ ngày/).first()).toBeVisible();
  await shot(page, '05-notebook');
});

test('06 pricing + activate demo code → PRO; reuse → error', async ({ page }) => {
  watch(page, '06');
  await loginAs(page, userAuth);
  await page.goto('/pricing');
  for (const p of ['79.000đ', '169.000đ', '499.000đ']) await expect(page.getByText(p).first()).toBeVisible();
  await shot(page, '06-pricing');
  await page.goto('/subscription');
  const input = page.getByLabel('Mã kích hoạt');
  await input.fill(DEMO_CODE);
  await page.getByRole('button', { name: 'Kích hoạt' }).click();
  await expect(page.locator('header').getByText('PRO', { exact: true })).toBeVisible();
  await shot(page, '06-subscription-pro');
  await input.fill(DEMO_CODE);
  await page.getByRole('button', { name: 'Kích hoạt' }).click();
  await expect(page.getByText('Mã đã được sử dụng').first()).toBeVisible();
});

test('07 admin: edit lesson title, create codes; non-admin redirected', async ({ page, browser, request }) => {
  watch(page, '07');
  const admin = await apiLogin(request, ADMIN_EMAIL, true, ADMIN_PASSWORD);
  await loginAs(page, admin);
  // create a throw-away lesson via API so seed data stays untouched
  const created = await request.post(`${API}/content/lessons`, {
    headers: { Authorization: `Bearer ${admin.token}` },
    data: { title: `E2E lesson ${stamp}`, cefrLevel: 'A1', topicTag: 'greetings', description: 'e2e', audioUrl: 'local:seed/lesson-1/full.mp3' },
  });
  expect(created.status()).toBe(201);
  const lesson = await created.json();
  await page.goto(`/admin/lessons/${lesson.id}`);
  const title = page.locator('form input').first();
  await expect(title).toHaveValue(`E2E lesson ${stamp}`);
  await title.fill(`E2E lesson ${stamp} edited`);
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await expect
    .poll(async () => (await (await request.get(`${API}/content/lessons/${lesson.id}`)).json()).title)
    .toBe(`E2E lesson ${stamp} edited`);
  await shot(page, '07-admin-lesson-edit');
  await page.goto('/admin/activation-codes');
  await page.getByRole('button', { name: 'Tạo mã' }).click();
  await expect(page.getByText(/PROMO-[A-Z0-9]{5}-[A-Z0-9]{5}/).first()).toBeVisible();
  await shot(page, '07-admin-codes');
  await request.delete(`${API}/content/lessons/${lesson.id}`, { headers: { Authorization: `Bearer ${admin.token}` } });
  // non-admin
  const ctx = await browser.newContext();
  const p2 = await ctx.newPage();
  watch(p2, '07-nonadmin');
  await loginAs(p2, userAuth);
  await p2.goto('/admin/lessons');
  await expect(p2).toHaveURL(/\/dashboard/);
  await ctx.close();
});

test('08 mobile 375×812 tab bar, Thêm drawer; 360px no horizontal scroll', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true });
  const page = await ctx.newPage();
  watch(page, '08');
  await loginAs(page, userAuth);
  await page.goto('/dashboard');
  const tabbar = page.getByRole('navigation', { name: 'Điều hướng di động' });
  await expect(tabbar).toBeVisible();
  await shot(page, '08-mobile-dashboard');
  await tabbar.getByRole('button', { name: /Thêm/ }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await shot(page, '08-mobile-drawer');
  await page.keyboard.press('Escape');
  await page.setViewportSize({ width: 360, height: 780 });
  const bad: string[] = [];
  for (const path of ['/dashboard', '/lessons', '/lessons/11111111-1111-1111-1111-111111111101', '/vocabulary', '/vocabulary/notebook', '/vocabulary/review', '/practice/history', '/profile', '/subscription', '/pricing']) {
    await page.goto(path);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(300);
    if (!(await noHorizontalScroll(page))) {
      const w = await page.evaluate(() => document.documentElement.scrollWidth);
      bad.push(`${path} scrollWidth=${w}`);
      await shot(page, `08-overflow-${path.replace(/\//g, '_')}`);
    }
  }
  expect(bad, bad.join('; ')).toEqual([]);
  // tab bar hidden on practice screens
  const lesson = await (await page.request.get(`${API}/content/lessons/11111111-1111-1111-1111-111111111101`)).json();
  await page.goto(`/lessons/${lesson.id}/dictation/${lesson.sentences[0].id}`);
  await expect(page.getByLabel('Câu bạn nghe được').or(page.locator('input[aria-label^="Từ thứ "]').first())).toBeVisible();
  await expect(tabbar).toBeHidden();
  await shot(page, '08-mobile-dictation');
  await ctx.close();
});

test('09 token refresh with invalid access token', async ({ page }) => {
  watch(page, '09');
  // log in fresh in the UI so we have a valid refresh token we do not revoke elsewhere
  await page.goto('/login');
  await page.locator('input[type=email]').fill(userEmail);
  await page.locator('input[type=password]').fill(PASSWORD);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await expect(page).toHaveURL(/\/dashboard/);
  await page.evaluate(() => {
    const s = JSON.parse(localStorage.getItem('parroto-auth')!);
    s.state.token = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4IiwiZXhwIjoxfQ.invalid';
    localStorage.setItem('parroto-auth', JSON.stringify(s));
  });
  let refreshCalls = 0;
  page.on('request', (r) => {
    if (r.url().includes('/auth/refresh-token')) refreshCalls++;
  });
  await page.goto('/vocabulary/notebook');
  await expect(page).toHaveURL(/\/vocabulary\/notebook/);
  await expect(page.getByText(/Ôn tiếp|Đến hạn ôn/).first()).toBeVisible();
  await page.goto('/dashboard');
  await expect(page.getByRole('img', { name: /Độ chính xác theo tuần/ })).toBeVisible();
  expect(refreshCalls).toBe(1);
  const tok = await page.evaluate(() => JSON.parse(localStorage.getItem('parroto-auth')!).state.token);
  expect(tok).not.toContain('invalid');
  // log out from the UI → refresh token revoked
  const before = await page.evaluate(() => JSON.parse(localStorage.getItem('parroto-auth')!).state.refreshToken);
  await page.getByLabel('Menu tài khoản').click();
  await page.getByRole('menuitem', { name: 'Đăng xuất' }).or(page.getByRole('button', { name: 'Đăng xuất' })).first().click();
  await expect(page).toHaveURL(/\/login|\/$/);
  const r = await page.request.post(`${API}/auth/refresh-token`, { data: { refreshToken: before } });
  expect(r.status()).toBe(401);
});

test.afterAll(() => {
  const f = 'console-errors.json';
  const prev = fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')) : [];
  fs.writeFileSync(f, JSON.stringify([...prev, ...errorsLog], null, 2));
});
