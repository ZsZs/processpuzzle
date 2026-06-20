import { describe, expect, it } from 'vitest';
import { BaseProcess } from './base-process';

describe('BaseProcess', () => {
  it('exposes the name passed to the constructor', () => {
    const process = new BaseProcess('onboarding');

    expect(process.name).toBe('onboarding');
  });

  it('defaults steps to an empty array when not provided', () => {
    const process = new BaseProcess('onboarding');

    expect(process.steps).toEqual([]);
    expect(process.stepCount()).toBe(0);
  });

  it('reports the number of steps the process was created with', () => {
    const process = new BaseProcess('onboarding', ['greet', 'verify', 'activate']);

    expect(process.stepCount()).toBe(3);
  });
});
