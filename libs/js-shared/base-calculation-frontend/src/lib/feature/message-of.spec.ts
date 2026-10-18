import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { messageOf } from './message-of';

describe('messageOf', () => {
  it('prefers the backend errorText', () => {
    expect(messageOf(new HttpErrorResponse({ error: { errorId: 'x', errorText: 'Calculation is invalid' }, status: 400 }))).toBe('Calculation is invalid');
  });

  it('falls back to the error message or the value itself', () => {
    expect(messageOf(new Error('boom'))).toBe('boom');
    expect(messageOf('plain')).toBe('plain');
    expect(messageOf(undefined)).toBe('undefined');
  });
});
