import { test, expect, type Page } from '@playwright/test';

/**
 * T13 browser E2E: users add their own words to the notebook (notebook page + dictation quick-add).
 * Self-contained (registers its own user); run with `e2e/run.sh` or
 * `npx playwright test t13-notebook-add` inside the Playwright image. See e2e/README.md.
 */
const API = process.env.API_URL ?? 'http://localhost:8000';
const SHOTS = process.env.SHOTS_DIR ?? 'screenshots';
const LESSON = '11111111-1111-1111-1111-111111111101';

test.describe.configure({ mode: 'serial' });

async function newUser(page: Page) {
  const email = `e2e_t13_${Date.now()}@test.local`;
  const r = await page.request.post(`${API}/auth/register`, { data: { email, password: 'Passw0rd!e2e', cefrLevel: 'B1' } });
  expect(r.status()).toBe(201);
  const a = await r.json();
  await page.addInitScript((a) => {
    window.localStorage.setItem(
      'parroto-auth',
      JSON.stringify({ state: { user: { userId: a.userId, email: a.email, cefrLevel: a.cefrLevel }, token: a.token, refreshToken: a.refreshToken }, version: 0 })
    );
  }, a);
}

test('T13-1 notebook: add, duplicate error, edit a custom word', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await newUser(page);
  await page.goto('/vocabulary/notebook');

  await page.getByRole('button', { name: /^\+ Thêm từ/ }).first().click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Từ', { exact: true }).fill('serendipity');
  await dialog.getByLabel('Nghĩa', { exact: true }).fill('sự tình cờ may mắn');
  await dialog.getByLabel('Phiên âm (IPA)').fill('/ˌserənˈdɪpəti/');
  await dialog.getByLabel('Ví dụ', { exact: true }).fill('It was pure serendipity.');
  await dialog.getByRole('button', { name: 'Thêm vào sổ tay' }).click();
  await expect(page.getByText('Đã thêm vào sổ tay')).toBeVisible();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByText('serendipity', { exact: true })).toBeVisible();
  await expect(page.getByText('Tự thêm')).toBeVisible();

  // duplicate (different case) → backend 409 text inside the dialog
  await page.getByRole('button', { name: /^\+ Thêm từ/ }).first().click();
  await dialog.getByLabel('Từ', { exact: true }).fill('  Serendipity ');
  await dialog.getByLabel('Nghĩa', { exact: true }).fill('x');
  await dialog.getByRole('button', { name: 'Thêm vào sổ tay' }).click();
  await expect(dialog.getByText('Từ này đã có trong sổ tay')).toBeVisible();
  await dialog.getByRole('button', { name: 'Huỷ' }).click();

  // edit
  await page.getByRole('button', { name: 'Sửa "serendipity"' }).click();
  await expect(dialog.getByLabel('Từ', { exact: true })).toHaveValue('serendipity');
  await dialog.getByLabel('Nghĩa', { exact: true }).fill('điều may mắn bất ngờ');
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await expect(page.getByText('Đã cập nhật')).toBeVisible();
  await expect(page.getByText('điều may mắn bất ngờ')).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/13-notebook-custom-word.png`, fullPage: true });
  expect(errors).toEqual([]);
});

test('T13-2 dictation: click a word in the answer → prefilled modal → saved to notebook', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await newUser(page);
  const lesson = await (await page.request.get(`${API}/content/lessons/${LESSON}`)).json();
  const s0 = lesson.sentences[0] as { id: string; text: string };
  await page.addInitScript(() => window.localStorage.setItem('parroto:dictation-difficulty', JSON.stringify('Hard')));
  await page.goto(`/lessons/${LESSON}/dictation/${s0.id}`);

  const ta = page.getByLabel('Câu bạn nghe được');
  await ta.fill(s0.text);
  await ta.press('Enter');
  await expect(page.getByText('🎉 Chính xác!')).toBeVisible();

  // P2-C: a word opens the dictionary sheet first, "Thêm vào sổ tay / bộ thẻ" opens the prefilled form.
  const wordBtn = page.getByRole('button', { name: /^Tra từ '.+'$/ }).nth(1);
  await wordBtn.click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: '+ Thêm vào sổ tay / bộ thẻ' }).click();
  // The form holds the clicked word, or its lemma when the dictionary resolved one ("running" → "run").
  const word = await dialog.getByLabel('Từ', { exact: true }).inputValue();
  expect(word.length).toBeGreaterThan(0);
  await expect(dialog.getByLabel('Ví dụ', { exact: true })).toHaveValue(s0.text);
  await dialog.getByLabel('Nghĩa', { exact: true }).fill('nghĩa thử nghiệm');
  await page.screenshot({ path: `${SHOTS}/13-dictation-quick-add.png`, fullPage: true });
  await dialog.getByRole('button', { name: 'Thêm vào sổ tay' }).click();
  await expect(page.getByText('Đã thêm vào sổ tay')).toBeVisible();

  await page.goto('/vocabulary/notebook');
  await expect(page.getByText(word, { exact: true })).toBeVisible();
  expect(errors).toEqual([]);
});
