import { describe, expect, it } from 'vitest';
import { classifyMediaError, RecorderError } from './recorder';

const named = (name: string) => ({ name, message: name });

describe('classifyMediaError', () => {
  it('maps permission errors', () => {
    expect(classifyMediaError(named('NotAllowedError'))).toBe('permission-denied');
    expect(classifyMediaError(named('SecurityError'))).toBe('permission-denied');
  });

  it('maps missing / busy devices', () => {
    expect(classifyMediaError(named('NotFoundError'))).toBe('no-device');
    expect(classifyMediaError(named('OverconstrainedError'))).toBe('no-device');
    expect(classifyMediaError(named('NotReadableError'))).toBe('busy');
  });

  it('keeps RecorderError codes and falls back to unknown', () => {
    expect(classifyMediaError(new RecorderError('insecure'))).toBe('insecure');
    expect(classifyMediaError(new Error('boom'))).toBe('unknown');
    expect(classifyMediaError(null)).toBe('unknown');
  });
});
