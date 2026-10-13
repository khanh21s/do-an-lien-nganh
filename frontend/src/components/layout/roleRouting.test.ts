import { describe, expect, it } from 'vitest';
import { isAdminPath, postAuthPath, roleRedirect } from './roleRouting';

describe('isAdminPath', () => {
  it('matches /admin and its children only', () => {
    expect(isAdminPath('/admin')).toBe(true);
    expect(isAdminPath('/admin/users?page=2')).toBe(true);
    expect(isAdminPath('/admin/vocabulary/abc')).toBe(true);
    expect(isAdminPath('/administrator')).toBe(false);
    expect(isAdminPath('/dashboard')).toBe(false);
    expect(isAdminPath(null)).toBe(false);
  });
});

describe('postAuthPath (T14 login redirect)', () => {
  it('sends admins to the admin console', () => {
    expect(postAuthPath(true, null)).toBe('/admin');
    expect(postAuthPath(true, '/dashboard')).toBe('/admin');
    expect(postAuthPath(true, '/lessons/1/dictation/2')).toBe('/admin');
    expect(postAuthPath(true, '/admin/plans')).toBe('/admin/plans');
  });

  it('sends users to next or the dashboard', () => {
    expect(postAuthPath(false, null)).toBe('/dashboard');
    expect(postAuthPath(false, '/subscription')).toBe('/subscription');
    expect(postAuthPath(false, '/admin/users')).toBe('/dashboard');
  });
});

describe('roleRedirect', () => {
  it('keeps admins out of learner pages', () => {
    expect(roleRedirect('learner', true)).toBe('/admin');
    expect(roleRedirect('learner', false)).toBeNull();
  });

  it('keeps users out of the admin console', () => {
    expect(roleRedirect('admin', false)).toBe('/dashboard');
    expect(roleRedirect('admin', true)).toBeNull();
  });
});
