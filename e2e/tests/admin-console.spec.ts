import { test, expect, type Page } from '@playwright/test';

/**
 * T14 admin console: the admin lands in /admin (no learner UI), learner pages redirect to
 * /admin, user lock/unlock blocks login, plan on/off hides it from the public list,
 * transactions page renders, non-admins are sent back to /dashboard.
 * Needs ADMIN_EMAIL / ADMIN_PASSWORD (from .env) in the environment. See e2e/README.md.
 */
const API = process.env.API_URL ?? 'http://localhost:8000';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@parroto.com';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? '';
const SHOTS = process.env.SHOTS_DIR ?? 'screenshots';

test.describe.configure({ mode: 'serial' });

async function uiLogin(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
}

test('admin console: separate shell, users, plans, transactions, role redirects', async ({ page }) => {
  expect(ADMIN_PASSWORD, 'set ADMIN_PASSWORD').not.toBe('');
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));

  // a learner account to manage
  const learner = `e2e_admin_target_${Date.now()}@test.local`;
  expect((await page.request.post(`${API}/auth/register`, { data: { email: learner, password: 'Passw0rd!e2e', cefrLevel: 'B1' } })).status()).toBe(201);

  // 1) admin login → /admin overview, no learner navigation
  await uiLogin(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await expect(page).toHaveURL(/\/admin$/);
  await expect(page.getByRole('heading', { name: 'Tổng quan', level: 1 })).toBeVisible();
  await expect(page.getByRole('link', { name: /Lịch sử luyện tập/ })).toHaveCount(0);
  await expect(page.getByRole('link', { name: /Ôn tập/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Nâng cấp/ })).toHaveCount(0);
  await page.screenshot({ path: `${SHOTS}/15-admin-overview.png`, fullPage: true });

  // 2) learner pages bounce back to /admin
  for (const path of ['/dashboard', '/vocabulary', '/lessons']) {
    await page.goto(path);
    await expect(page, path).toHaveURL(/\/admin$/);
  }

  // 3) users: find, lock (login blocked), unlock
  await page.goto('/admin/users');
  await page.getByRole('searchbox', { name: 'Tìm theo email' }).or(page.getByLabel('Tìm theo email')).first().fill(learner);
  const lock = page.getByRole('button', { name: `Khoá ${learner}` });
  await expect(lock).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/15-admin-users.png`, fullPage: true });
  await lock.click();
  await page.getByRole('dialog').getByRole('button', { name: 'Khoá', exact: true }).click();
  await expect(page.getByRole('button', { name: `Mở khoá ${learner}` })).toBeVisible();
  let r = await page.request.post(`${API}/auth/login`, { data: { email: learner, password: 'Passw0rd!e2e' } });
  expect(r.status()).toBe(403);
  expect((await r.json()).error).toBe('Tài khoản đã bị khoá');
  await page.getByRole('button', { name: `Mở khoá ${learner}` }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Mở khoá', exact: true }).click();
  await expect(page.getByRole('button', { name: `Khoá ${learner}` })).toBeVisible();
  r = await page.request.post(`${API}/auth/login`, { data: { email: learner, password: 'Passw0rd!e2e' } });
  expect(r.status()).toBe(200);
  // admins cannot be locked
  await page.getByLabel('Tìm theo email').first().fill(ADMIN_EMAIL);
  await expect(page.getByRole('button', { name: new RegExp(`^Khoá ${ADMIN_EMAIL.replace('.', '\\.')}`) })).toBeDisabled();

  // 4) plans: stop selling 1-month → hidden from the public list → sell again
  await page.goto('/admin/plans');
  await expect(page.getByRole('heading', { name: 'Gói Premium', level: 1 })).toBeVisible();
  const planName = '1 tháng Premium';
  await page.getByRole('switch', { name: `Ngừng bán ${planName}` }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Ngừng bán', exact: true }).click();
  await expect(page.getByRole('switch', { name: `Mở bán ${planName}` })).toBeVisible();
  let plans = await (await page.request.get(`${API}/subscriptions/plans`)).json();
  expect(JSON.stringify(plans)).not.toContain('plan-1month');
  await page.getByRole('switch', { name: `Mở bán ${planName}` }).click();
  await expect(page.getByRole('switch', { name: `Ngừng bán ${planName}` })).toBeVisible();
  plans = await (await page.request.get(`${API}/subscriptions/plans`)).json();
  expect(JSON.stringify(plans)).toContain('plan-1month');
  await page.screenshot({ path: `${SHOTS}/15-admin-plans.png`, fullPage: true });

  // 5) transactions page
  await page.goto('/admin/transactions');
  await expect(page.getByRole('heading', { name: 'Giao dịch', level: 1 })).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/15-admin-transactions.png`, fullPage: true });
  expect(errors).toEqual([]);
});

test('non-admin visiting /admin goes back to /dashboard', async ({ page }) => {
  const email = `e2e_nonadmin_${Date.now()}@test.local`;
  await page.request.post(`${API}/auth/register`, { data: { email, password: 'Passw0rd!e2e', cefrLevel: 'B1' } });
  await uiLogin(page, email, 'Passw0rd!e2e');
  await expect(page).toHaveURL(/\/dashboard/);
  await page.goto('/admin/users');
  await expect(page).toHaveURL(/\/dashboard/);
});
