import { describe, expect, it } from 'vitest';
import de from '../assets/i18n/base_starter/de.json';
import en from '../assets/i18n/base_starter/en.json';
import es from '../assets/i18n/base_starter/es.json';
import fr from '../assets/i18n/base_starter/fr.json';
import hu from '../assets/i18n/base_starter/hu.json';
import { DefinitionKind } from './domain/starter';

function keysOf(bundle: object, prefix = ''): string[] {
  return Object.entries(bundle).flatMap(([key, value]) => (value && typeof value === 'object' ? keysOf(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

describe('base-starter translations', () => {
  const english = keysOf(en).sort();

  it.each([
    ['de', de],
    ['es', es],
    ['fr', fr],
    ['hu', hu],
  ])('%s has exactly the keys English has', (_, bundle) => {
    expect(keysOf(bundle).sort()).toEqual(english);
  });

  it('labels every definition kind, status and action the report can show', () => {
    const kinds: DefinitionKind[] = ['entity', 'state', 'rule', 'widget', 'document', 'workflow', 'app'];
    for (const kind of kinds) expect(english).toContain(`import.kind.${kind}`);
    for (const status of ['applied', 'would-apply', 'rejected']) expect(english).toContain(`import.status.${status}`);
    for (const action of ['create', 'update']) expect(english).toContain(`import.action.${action}`);
    expect(en.import.summary).toContain('{{created}}');
    expect(en.import.summary).toContain('{{updated}}');
  });
});
