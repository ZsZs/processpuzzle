import { describe, expect, it } from 'vitest';
import { BaseWorkflow } from './base-workflow';

describe('BaseWorkflow', () => {
  it('exposes the name passed to the constructor', () => {
    const workflow = new BaseWorkflow('onboarding');

    expect(workflow.name).toBe('onboarding');
  });

  it('defaults steps to an empty array when not provided', () => {
    const workflow = new BaseWorkflow('onboarding');

    expect(workflow.steps).toEqual([]);
    expect(workflow.stepCount()).toBe(0);
  });

  it('reports the number of steps the workflow was created with', () => {
    const workflow = new BaseWorkflow('onboarding', ['greet', 'verify', 'activate']);

    expect(workflow.stepCount()).toBe(3);
  });
});
