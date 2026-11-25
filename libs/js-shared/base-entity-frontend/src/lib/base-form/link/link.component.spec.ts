import { describe, expect, it } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { LinkComponent, openableUrl } from './link.component';

async function setupLink(value?: string) {
  const config = new BaseEntityAttrDescriptor('website', FormControlType.LINK, 'Website');
  const entity = new TestEntity('1', 'name');
  if (value !== undefined) Reflect.set(entity, 'website', value);
  const harness = await setupFormControlTest(LinkComponent, config, entity);
  harness.fixture.detectChanges();
  const element = harness.fixture.nativeElement as HTMLElement;
  const input = element.querySelector<HTMLInputElement>('[data-testid="link-input"]');
  if (!input) throw new Error('the link input was not rendered');
  return { ...harness, component: harness.component as LinkComponent<TestEntity>, element, input };
}

describe('openableUrl', () => {
  it('opens an absolute http or https address', () => {
    expect(openableUrl('https://processpuzzle.com/docs')).toBe('https://processpuzzle.com/docs');
    expect(openableUrl('  http://example.org  ')).toBe('http://example.org/');
  });

  it('never offers other schemes, bare hosts or empty text', () => {
    expect(openableUrl('javascript:alert(1)')).toBeUndefined();
    expect(openableUrl('mailto:someone@example.org')).toBeUndefined();
    expect(openableUrl('example.org')).toBeUndefined();
    expect(openableUrl('   ')).toBeUndefined();
    expect(openableUrl(undefined)).toBeUndefined();
    expect(openableUrl(42)).toBeUndefined();
  });
});

describe('LinkComponent', () => {
  it('is read-only until the field gets the focus, and read-only again once it loses it', async () => {
    const { input, component, fixture } = await setupLink('https://processpuzzle.com');

    expect(input.readOnly).toBe(true);
    expect(input.classList).toContain('pp-link--display');

    input.dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    expect(component.editing()).toBe(true);
    expect(input.readOnly).toBe(false);
    expect(input.classList).not.toContain('pp-link--display');

    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(input.readOnly).toBe(true);
  });

  it('offers an open icon for an http or https value, opening it in a new tab', async () => {
    const { element } = await setupLink('https://processpuzzle.com');

    const open = element.querySelector<HTMLAnchorElement>('[data-testid="link-open"]');
    expect(open?.href).toBe('https://processpuzzle.com/');
    expect(open?.target).toBe('_blank');
    expect(open?.rel).toContain('noopener');
  });

  it('offers no open icon for text that is not an http or https address', async () => {
    const { element } = await setupLink('javascript:alert(1)');

    expect(element.querySelector('[data-testid="link-open"]')).toBeNull();
  });
});
