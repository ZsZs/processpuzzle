import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { ContextCollectionComponent, CONTEXT_COLLECTION_CONTEXT_KEY, CONTEXT_COLLECTION_ENTITY_KEY, CONTEXT_COLLECTION_PATH_KEY } from './context-collection.component';
import { EmbeddedCollectionService } from './embedded-collection.service';
import { BaseFormNavigatorSingletonStore } from '../base-form-navigator/base-form-navigator.store';
import { WorkingContextService } from './working-context.service';

const roundDescriptor = new BaseEntityDescriptor({
  entityName: 'Round',
  attrDescriptors: [new BaseEntityAttrDescriptor('number', FormControlType.TEXT_BOX, 'Number'), new BaseEntityAttrDescriptor('label', FormControlType.TEXT_BOX, 'Label')],
});

/** A `Race` whose `rounds` attribute says its rows are identified by `identifyingField`. */
function ownerDescriptor(identifyingField: string): BaseEntityDescriptor {
  const rounds = new BaseEntityAttrDescriptor('rounds', FormControlType.EMBEDDED_COMPONENTS);
  rounds.linkedEntityType = 'Round';
  rounds.referenceIdField = identifyingField;
  return new BaseEntityDescriptor({ entityName: 'Race', attrDescriptors: [rounds] });
}

describe('ContextCollectionComponent', () => {
  const activeId = signal<string | undefined>('r1');
  let dialogAnswer: unknown;
  const navigator = { navigateToUrl: vi.fn(async () => undefined) };
  let descriptors: Record<string, BaseEntityDescriptor>;
  let service: { list: ReturnType<typeof vi.fn>; append: ReturnType<typeof vi.fn>; patch: ReturnType<typeof vi.fn>; remove: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    activeId.set('r1');
    dialogAnswer = undefined;
    navigator.navigateToUrl.mockClear();
    descriptors = {};
    service = {
      list: vi.fn(async () => ({ items: [{ id: 'a', number: 1, label: 'first', itemVersion: 1 }], version: '"5"' })),
      append: vi.fn(async () => ({})),
      patch: vi.fn(async () => ({})),
      remove: vi.fn(async () => undefined),
    };
    TestBed.configureTestingModule({
      providers: [
        provideTranslocoTesting({ translations: {} }),
        { provide: ActivatedRoute, useValue: { snapshot: { data: { [CONTEXT_COLLECTION_CONTEXT_KEY]: 'race', [CONTEXT_COLLECTION_PATH_KEY]: 'rounds', [CONTEXT_COLLECTION_ENTITY_KEY]: 'Round' } } } },
        { provide: EmbeddedCollectionService, useValue: service },
        { provide: WorkingContextService, useValue: { current: () => (activeId() ? { name: 'race', id: activeId(), url: `/ctx/race/${activeId()}`, definition: { name: 'race', entityName: 'Race' } } : undefined) } },
        { provide: BaseFormNavigatorSingletonStore, useValue: navigator },
        { provide: BaseEntityDescriptorRegistry, useValue: { getDescriptor: (name: string) => descriptors[name] ?? roundDescriptor, getStore: () => undefined } },
        { provide: MatDialog, useValue: { open: vi.fn(() => ({ afterClosed: () => of(dialogAnswer) })) } },
      ],
    });
  });

  async function setup() {
    const fixture = TestBed.createComponent(ContextCollectionComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, host: fixture.nativeElement as HTMLElement };
  }

  const click = async (fixture: { detectChanges(): void; whenStable(): Promise<unknown> }, element: Element | null) => {
    (element as HTMLElement).click();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  it('lists the collection of the active entity, one column per attribute', async () => {
    const { host } = await setup();

    expect(service.list).toHaveBeenCalledWith('Race', 'r1', 'rounds');
    expect(host.querySelectorAll('tr.mat-mdc-row')).toHaveLength(1);
    expect(host.querySelector('tr.mat-mdc-row')?.textContent).toContain('first');
  });

  it('opens a new row\'s form below the context, which saves it on its own and returns here', async () => {
    const { fixture, host } = await setup();

    await click(fixture, host.querySelector('[data-testid="round-context-collection-add"]'));

    expect(navigator.navigateToUrl).toHaveBeenCalledWith('/ctx/race/r1/round/new/details');
  });

  it('opens a row\'s own form below the context, with the owner in the URL', async () => {
    const { fixture, host } = await setup();

    await click(fixture, host.querySelector('[data-testid="round-context-collection-edit"]'));

    expect(navigator.navigateToUrl).toHaveBeenCalledWith('/ctx/race/r1/round/a/details');
  });

  it('addresses a row by the attribute the owner\'s descriptor says identifies it', async () => {
    service.list.mockResolvedValue({ items: [{ id: 'x', code: 'R-7', itemVersion: 1 }], version: undefined });
    descriptors['Race'] = ownerDescriptor('code');
    const { fixture, host } = await setup();

    await click(fixture, host.querySelector('[data-testid="round-context-collection-edit"]'));

    expect(navigator.navigateToUrl).toHaveBeenCalledWith('/ctx/race/r1/round/R-7/details');
  });

  it('removes an item once the user confirms', async () => {
    const { fixture, host } = await setup();

    dialogAnswer = false;
    await click(fixture, host.querySelector('[data-testid="round-context-collection-delete"]'));
    expect(service.remove).not.toHaveBeenCalled();

    dialogAnswer = true;
    await click(fixture, host.querySelector('[data-testid="round-context-collection-delete"]'));
    expect(service.remove).toHaveBeenCalledWith('Race', 'r1', 'rounds', 'a', '1');
  });

  it('says so on a conflict, and reloads rather than trusting what it had', async () => {
    const { fixture, host } = await setup();
    service.remove.mockRejectedValue(new HttpErrorResponse({ status: 409 }));
    dialogAnswer = true;

    await click(fixture, host.querySelector('[data-testid="round-context-collection-delete"]'));

    expect(host.querySelector('[data-testid="round-context-collection-error"]')).not.toBeNull();
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('follows the context: another active entity means another list', async () => {
    const { fixture } = await setup();

    activeId.set('r2');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(service.list).toHaveBeenLastCalledWith('Race', 'r2', 'rounds');
  });
});
