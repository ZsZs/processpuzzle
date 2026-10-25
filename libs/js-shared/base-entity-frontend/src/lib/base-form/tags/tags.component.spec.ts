import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { Provider } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { TagsComponent } from './tags.component';
import { TAG_SUGGESTION_SOURCES } from './tag-suggestions';
import { Observable, of, throwError } from 'rxjs';

function makeConfig(): BaseEntityAttrDescriptor {
  return new BaseEntityAttrDescriptor('tags', FormControlType.TAGS, 'Tags');
}

function makeEntity(tags?: unknown): TestEntity {
  const entity = new TestEntity('1', 'name');
  if (tags !== undefined) Reflect.set(entity, 'tags', tags);
  return entity;
}

async function setupTags(config: BaseEntityAttrDescriptor, tagValue?: unknown, providers: Provider[] = []) {
  const entity = makeEntity(tagValue);
  const harness = await setupFormControlTest(TagsComponent, config, entity, providers);
  return { ...harness, entity, component: harness.component as TagsComponent<TestEntity> };
}

describe('TagsComponent', () => {
  describe('separatorKeysCodes', () => {
    it('exposes ENTER and COMMA as the chip separator keys', async () => {
      const { component } = await setupTags(makeConfig(), []);

      expect(component.separatorKeysCodes).toEqual([ENTER, COMMA]);
    });
  });

  describe('tags()', () => {
    it('returns the current array form value', async () => {
      const { component } = await setupTags(makeConfig(), ['alpha', 'beta']);

      expect(component.tags()).toEqual(['alpha', 'beta']);
    });

    it('returns an empty array when the form value is not an array', async () => {
      const { component } = await setupTags(makeConfig(), 'not-an-array');

      expect(component.tags()).toEqual([]);
    });

    it('returns an empty array when the form value is null', async () => {
      const { component } = await setupTags(makeConfig(), null);

      expect(component.tags()).toEqual([]);
    });
  });

  describe('add()', () => {
    it('appends the trimmed value to the existing tags and clears the chip input', async () => {
      const { component } = await setupTags(makeConfig(), ['alpha']);
      const clear = vi.fn();

      component.add({ value: '  beta  ', chipInput: { clear } } as never);

      expect(component.tags()).toEqual(['alpha', 'beta']);
      expect(clear).toHaveBeenCalled();
      expect(component.formGroup.get('tags')?.dirty).toBe(true);
      expect(component.formGroup.get('tags')?.touched).toBe(true);
    });

    it('clears the chip input and does nothing when the value is empty', async () => {
      const { component } = await setupTags(makeConfig(), ['alpha']);
      const clear = vi.fn();

      component.add({ value: '   ', chipInput: { clear } } as never);

      expect(component.tags()).toEqual(['alpha']);
      expect(clear).toHaveBeenCalled();
      expect(component.formGroup.get('tags')?.dirty).toBe(false);
    });

    it('handles a missing chipInput reference without throwing', async () => {
      const { component } = await setupTags(makeConfig(), []);

      expect(() => component.add({ value: 'gamma' } as never)).not.toThrow();
      expect(component.tags()).toEqual(['gamma']);
    });

    it('treats a missing value as an empty string and skips the append', async () => {
      const { component } = await setupTags(makeConfig(), ['alpha']);

      component.add({ chipInput: { clear: vi.fn() } } as never);

      expect(component.tags()).toEqual(['alpha']);
    });
  });

  describe('remove()', () => {
    it('removes the tag at the given index and marks the control dirty', async () => {
      const { component } = await setupTags(makeConfig(), ['alpha', 'beta', 'gamma']);

      component.remove(1);

      expect(component.tags()).toEqual(['alpha', 'gamma']);
      expect(component.formGroup.get('tags')?.dirty).toBe(true);
    });
  });

  describe('edit()', () => {
    it('replaces the tag at the given index with the trimmed value', async () => {
      const { component } = await setupTags(makeConfig(), ['alpha', 'beta']);

      component.edit(1, { value: '  delta  ' } as never);

      expect(component.tags()).toEqual(['alpha', 'delta']);
      expect(component.formGroup.get('tags')?.dirty).toBe(true);
    });

    it('removes the tag when the edited value is empty', async () => {
      const { component } = await setupTags(makeConfig(), ['alpha', 'beta', 'gamma']);

      component.edit(0, { value: '   ' } as never);

      expect(component.tags()).toEqual(['beta', 'gamma']);
    });

    it('removes the tag when the edited value is missing', async () => {
      const { component } = await setupTags(makeConfig(), ['alpha', 'beta']);

      component.edit(0, {} as never);

      expect(component.tags()).toEqual(['beta']);
    });
  });

  describe('template', () => {
    it('renders one chip per tag with a remove button when visible', async () => {
      const config = makeConfig();
      const { fixture } = await setupTags(config, ['alpha', 'beta']);
      const host = fixture.nativeElement as HTMLElement;

      const chips = host.querySelectorAll('mat-chip-row');
      expect(chips).toHaveLength(2);
      expect(host.querySelector('button[aria-label="remove alpha"]')).not.toBeNull();
      expect(host.querySelector('button[aria-label="remove beta"]')).not.toBeNull();
    });

    it('renders nothing when the descriptor is not visible', async () => {
      const config = makeConfig();
      config.visible = false;
      const { fixture } = await setupTags(config, ['alpha']);
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('mat-form-field')).toBeNull();
      expect(host.querySelector('mat-chip-row')).toBeNull();
    });
  });

  describe('suggestions', () => {
    function suggested(source: () => Observable<readonly string[]>): Provider[] {
      return [{ provide: TAG_SUGGESTION_SOURCES, multi: true, useValue: { key: 'realm-roles', suggestions: source } }];
    }
    function suggestingConfig(): BaseEntityAttrDescriptor {
      const config = makeConfig();
      config.tagSuggestions = 'realm-roles';
      return config;
    }

    it('offers what the named source knows, minus what is already picked and filtered by what is typed', async () => {
      const { component, fixture } = await setupTags(suggestingConfig(), ['editor'], suggested(() => of(['editor', 'reviewer', 'publisher'])));
      await fixture.whenStable();

      expect(component.options()).toEqual(['reviewer', 'publisher']);
      component.typed.set('REV');
      expect(component.options()).toEqual(['reviewer']);
    });

    it('marks a value the source does not know as new', async () => {
      const { component, fixture } = await setupTags(suggestingConfig(), [], suggested(() => of(['editor'])));
      await fixture.whenStable();

      expect(component.isNew('editor')).toBe(false);
      expect(component.isNew('typo')).toBe(true);
    });

    it('marks nothing as new when the attribute names no source', async () => {
      const { component, fixture } = await setupTags(makeConfig(), []);
      await fixture.whenStable();

      expect(component.isNew('anything')).toBe(false);
    });

    it('stays a free-text control when the source fails', async () => {
      const { component, fixture } = await setupTags(suggestingConfig(), [], suggested(() => throwError(() => new Error('down'))));
      await fixture.whenStable();

      expect(component.options()).toEqual([]);
      expect(component.isNew('anything')).toBe(false);
    });

    it('adds a picked suggestion and clears the input', async () => {
      const { component, fixture } = await setupTags(suggestingConfig(), ['editor'], suggested(() => of(['editor', 'reviewer'])));
      await fixture.whenStable();
      const input = { value: 'rev' } as HTMLInputElement;

      component.pick({ option: { value: 'reviewer' } } as never, input);

      expect(component.tags()).toEqual(['editor', 'reviewer']);
      expect(input.value).toBe('');
      expect(component.typed()).toBe('');
    });
  });
});
