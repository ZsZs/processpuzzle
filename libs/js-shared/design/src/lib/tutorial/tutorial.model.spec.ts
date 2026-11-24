import { describe, expect, it } from 'vitest';
import { pickTutorial, TutorialRow } from './tutorial.model';

const row = (language: string): TutorialRow => ({ id: language, payload: { tutorialKey: 'entity-designer', language, title: language } });

describe('pickTutorial', () => {
  it('prefers the requested language', () => {
    expect(pickTutorial([row('en'), row('hu')], 'hu')?.id).toBe('hu');
  });

  it('falls back to English when the language has no row', () => {
    expect(pickTutorial([row('en'), row('de')], 'fr')?.id).toBe('en');
  });

  it('falls back to the first row when neither the language nor English exists', () => {
    expect(pickTutorial([row('de')], 'fr')?.id).toBe('de');
  });

  it('is undefined when there are no rows', () => {
    expect(pickTutorial([], 'en')).toBeUndefined();
  });
});
