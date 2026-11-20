import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { parse } from 'yaml';
import de from '../../../assets/i18n/de.json';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import fr from '../../../assets/i18n/fr.json';
import hu from '../../../assets/i18n/hu.json';
import { CALENDAR_SAMPLES } from './calendar-samples';

const SEED = 'libs/java-shared/base-calendar-backend/src/main/resources/default-calendar-definitions/base-calendar/processpuzzle-testbed-calendar-definitions.yaml';

describe('the calendar samples', () => {
  it('only name calendars the testbed seeds, so a renamed seed cannot leave the page showing nothing', () => {
    const seeded = (parse(readFileSync(resolve(process.cwd(), SEED), 'utf-8')) as { 'calendar-definitions': Array<{ key: string }> })['calendar-definitions'].map((definition) => definition.key);

    for (const sample of CALENDAR_SAMPLES) {
      expect(seeded, sample.id).toEqual(expect.arrayContaining(sample.calendarKeys));
    }
  });

  it('start with a calendar an anonymous visitor can read, the only one that shows them anything', () => {
    expect(CALENDAR_SAMPLES[0].calendarKeys).toEqual(['public-events']);
  });

  it('have distinct ids', () => {
    expect(new Set(CALENDAR_SAMPLES.map((sample) => sample.id)).size).toBe(CALENDAR_SAMPLES.length);
  });
});

describe('the calendar page translations', () => {
  const languages = { de, en, es, fr, hu };
  const englishKeys = Object.keys(en['base-calendar']).sort();

  it.each(Object.keys(languages))('cover every English key in %s', (language) => {
    const block = (languages as Record<string, { 'base-calendar': object }>)[language]['base-calendar'];

    expect(Object.keys(block).sort()).toEqual(englishKeys);
  });

  it('name every sample', () => {
    for (const sample of CALENDAR_SAMPLES) {
      expect(englishKeys, sample.id).toContain(sample.labelKey);
    }
  });

  it.each(Object.keys(languages))('give the home card all its texts in %s', (language) => {
    const home = (languages as Record<string, { home: Record<string, string> }>)[language].home;

    for (const key of ['title', 'subtitle', 'content', 'content_1', 'content_2', 'content_3', 'button']) {
      expect(home[`base-calendar_card_${key}`], `${language} ${key}`).toBeTruthy();
    }
  });
});
