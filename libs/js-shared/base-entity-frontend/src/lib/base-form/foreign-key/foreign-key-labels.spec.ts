import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { BaseEntityDescriptorRegistry } from '../../base-entity-facade/base-entity-descriptor.registry';
import { ForeignKeyLabels } from './foreign-key-labels';

describe('ForeignKeyLabels', () => {
  const boats = signal([{ id: 'b-1', sailNumber: 'CAN 603' }, { id: 'b-2', sailNumber: '' }, { id: 'b-3', sailNumber: 7 }]);
  const stores: Record<string, unknown> = {
    Boat: { loadById: (id: string) => boats().find((boat) => boat.id === id) },
    Race: { entities: () => [{ id: 'r-1', name: 'Autumn Regatta' }] },
    Bare: {},
  };
  const identifications: Record<string, string> = { Boat: 'sailNumber', Race: 'name' };
  let labels: ForeignKeyLabels;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: BaseEntityDescriptorRegistry,
          useValue: {
            getStore: (name: string) => stores[name],
            getDescriptor: (name: string) => (name in identifications ? { componentIdentification: () => identifications[name] } : undefined),
          },
        },
      ],
    });
    labels = TestBed.inject(ForeignKeyLabels);
  });

  it("shows the linked object's identifying attribute instead of its id", () => {
    expect(labels.labelOf('Boat', 'b-1')).toBe('CAN 603');
    expect(labels.labelOf('Race', 'r-1')).toBe('Autumn Regatta');
    expect(labels.labelOf('Boat', 'b-3')).toBe('7');
  });

  it('falls back to the id when the object, its store or its label is missing', () => {
    expect(labels.labelOf('Boat', 'b-9')).toBe('b-9');
    expect(labels.labelOf('Unregistered', 'x-1')).toBe('x-1');
    expect(labels.labelOf('Bare', 'x-1')).toBe('x-1');
    expect(labels.labelOf('Boat', 'b-2')).toBe('b-2');
    expect(labels.labelOf(undefined, 'x-1')).toBe('x-1');
  });

  it('is empty for an empty value', () => {
    expect(labels.labelOf('Boat', undefined)).toBe('');
    expect(labels.labelOf('Boat', null)).toBe('');
  });

  it('prefers an object the caller already holds', () => {
    expect(labels.labelOf('Boat', 'b-9', { id: 'b-9', sailNumber: 'HUN 77' } as never)).toBe('HUN 77');
  });

  it('follows the linked store as it loads', () => {
    expect(labels.labelOf('Boat', 'b-4')).toBe('b-4');
    boats.update((list) => [...list, { id: 'b-4', sailNumber: 'GER 1234' }]);
    expect(labels.labelOf('Boat', 'b-4')).toBe('GER 1234');
  });
});
