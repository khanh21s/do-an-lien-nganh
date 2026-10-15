import { describe, expect, it } from 'vitest';
import {
  checkInHint,
  checkInLabel,
  gainMessages,
  levelPercent,
  missionPercent,
  missionsDone,
} from './gamification';
import type { GamificationCheckInState, GamificationMission } from './types';

const mission = (progress: number, target: number, completed = false): GamificationMission => ({
  code: 'DICTATION_5',
  icon: '✍️',
  title: 'Làm 5 câu Dictation',
  target,
  progress,
  completed,
  reward: 30,
});

const checkIn = (over: Partial<GamificationCheckInState> = {}): GamificationCheckInState => ({
  checkedInToday: false,
  streak: 0,
  lastCheckInDay: null,
  todayReward: 50,
  daysToBonus: 7,
  ...over,
});

describe('gamification helpers', () => {
  it('computes level and mission percentages, clamped', () => {
    expect(levelPercent({ levelXp: 42, levelXpNeeded: 300 })).toBe(14);
    expect(levelPercent({ levelXp: 0, levelXpNeeded: 0 })).toBe(0);
    expect(missionPercent(mission(3, 5))).toBe(60);
    expect(missionPercent(mission(9, 5))).toBe(100);
    expect(missionsDone([mission(5, 5, true), mission(1, 3), mission(1, 1, true)])).toBe(2);
  });

  it('labels the check-in button and the 7-day bonus', () => {
    expect(checkInLabel(checkIn())).toBe('Điểm danh +50 💎');
    expect(checkInLabel(checkIn({ checkedInToday: true, streak: 4 }))).toBe('Đã điểm danh · chuỗi 4 ngày');
    expect(checkInHint(checkIn({ daysToBonus: 1, todayReward: 150 }))).toContain('ngày thứ 7');
    expect(checkInHint(checkIn({ checkedInToday: true, daysToBonus: 1 }))).toContain('Ngày mai');
    expect(checkInHint(checkIn({ daysToBonus: 3 }))).toBe('Còn 3 ngày điểm danh liên tiếp để nhận thêm 100 💎.');
  });

  it('turns increases between two reads into toast messages', () => {
    expect(gainMessages(undefined, { xp: 10, level: 1, diamonds: 0 })).toEqual([]);
    expect(gainMessages({ xp: 10, level: 1, diamonds: 0 }, { xp: 15, level: 1, diamonds: 0 })).toEqual(['+5 XP']);
    expect(gainMessages({ xp: 95, level: 1, diamonds: 50 }, { xp: 105, level: 2, diamonds: 80 })).toEqual([
      '+10 XP',
      '🎉 Lên cấp 2!',
      '+30 💎',
    ]);
    expect(gainMessages({ xp: 500, level: 4, diamonds: 100 }, { xp: 20, level: 1, diamonds: 0 })).toEqual([]);
  });
});
