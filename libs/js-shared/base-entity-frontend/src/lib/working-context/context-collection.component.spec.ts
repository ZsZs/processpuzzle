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
import { WorkingContextService } from './working-context.service';

const roundDescriptor = new BaseEntityDescriptor({
  entityName: 'Round',
  attrDescriptors: [new BaseEntityAttrDescriptor('number', FormControlType.TEXT_BOX, 'Number'), new BaseEntityAttrDescriptor('label', FormControlType.TEXT_BOX, 'Label')],
});

describe('ContextCollectionComponent', () => {
  const activeId = signal<string | undefined>('r1');
  let dialogAnswer: unknown;
  let service: { list: ReturnType<typeof vi.fn>; append: ReturnType<typeof vi.fn>; patch: ReturnType<typeof vi.fn>; remove: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    activeId.set('r1');
    dialogAnswer = undefined;
    service = {
      list: vi.fn(async () => ({ items: [{ id: 'a', number: 1, label: 'first' }], version: '"5"' })),
      append: vi.fn(async () => ({})),
      patch: vi.fn(async () => ({})),
      remove: vi.fn(async () => undefined),
    };
    TestBed.configureTestingModule({
      providers: [
        provideTranslocoTesting({ translations: {} }),
        { provide: ActivatedRoute, useValue: { snapshot: { data: { [CONTEXT_COLLECTION_CONTEXT_KEY]: 'race', [CONTEXT_COLLECTION_PATH_KEY]: 'rounds', [CONTEXT_COLLECTION_ENTITY_KEY]: 'Round' } } } },
        { provide: EmbeddedCollectionService, useValue: service },
        { provide: WorkingContextService, useValue: { current: () => (activeId() ? { name: 'race', id: activeId(), definition: { name: 'race', entityName: 'Race' } } : undefined) } },
        { provide: BaseEntityDescriptorRegistry, useValue: { getDescriptor: () => roundDescriptor, getStore: () => undefined } },
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

  it('adds an item to the active entity, without any selection of it, and shows what is stored then', async () => {
    const { fixture, host } = await setup();
    dialogAnswer = { number: 2, label: 'second' };

    await click(fixture, host.querySelector('[data-testid="round-context-collection-add"]'));

    expect(service.append).toHaveBeenCalledWith('Race', 'r1', 'rounds', { number: 2, label: 'second' });
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('does nothing when the add dialog is cancelled', async () => {
    const { fixture, host } = await setup();

    await click(fixture, host.querySelector('[data-testid="round-context-collection-add"]'));

    expect(service.append).not.toHaveBeenCalled();
  });

  it('sends only what changed when an item is edited, and nothing when nothing did', async () => {
    const { fixture, host } = await setup();

    dialogAnswer = { id: 'a', number: 1, label: 'renamed' };
    await click(fixture, host.querySelector('[data-testid="round-context-collection-edit"]'));
    expect(service.patch).toHaveBeenCalledWith('Race', 'r1', 'rounds', 'a', { label: 'renamed' });

    service.patch.mockClear();
    dialogAnswer = { id: 'a', number: 1, label: 'first' };
    await click(fixture, host.querySelector('[data-testid="round-context-collection-edit"]'));
    expect(service.patch).not.toHaveBeenCalled();
  });

  it('removes an item once the user confirms', async () => {
    const { fixture, host } = await setup();

    dialogAnswer = false;
    await click(fixture, host.querySelector('[data-testid="round-context-collection-delete"]'));
    expect(service.remove).not.toHaveBeenCalled();

    dialogAnswer = true;
    await click(fixture, host.querySelector('[data-testid="round-context-collection-delete"]'));
    expect(service.remove).toHaveBeenCalledWith('Race', 'r1', 'rounds', 'a');
  });

  it('says so on a conflict, and reloads rather than trusting what it had', async () => {
    const { fixture, host } = await setup();
    service.append.mockRejectedValue(new HttpErrorResponse({ status: 409 }));
    dialogAnswer = { number: 3 };

    await click(fixture, host.querySelector('[data-testid="round-context-collection-add"]'));

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
