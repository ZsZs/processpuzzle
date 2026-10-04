import { TestBed } from '@angular/core/testing';
import { EntityDefinitionRegistry } from '@processpuzzle/base-entity';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RecognitionProfile } from './recognition-profile';
import { RecognitionProfileService } from './recognition-profile.service';
import { ProfiledEntityRegistry } from './profiled-entity.registry';

describe('ProfiledEntityRegistry', () => {
  const findAll = vi.fn();
  const byName = vi.fn();
  let registry: ProfiledEntityRegistry;

  beforeEach(() => {
    findAll.mockReset();
    byName.mockReset();
    TestBed.configureTestingModule({
      providers: [
        { provide: RecognitionProfileService, useValue: { findAll } },
        { provide: EntityDefinitionRegistry, useValue: { byName } },
      ],
    });
    registry = TestBed.inject(ProfiledEntityRegistry);
  });

  it("finds a profile by the descriptor's display name, through the definition's code", async () => {
    byName.mockImplementation(async (name: string) => ({ code: name === 'Boat' ? 'boat' : 'order' }));
    findAll.mockReturnValue(of({ content: [new RecognitionProfile({ entityName: 'boat' })] }));

    expect((await registry.profileFor('Boat'))?.entityName).toBe('boat');
    expect(await registry.profileFor('Order')).toBeUndefined();
    expect(findAll).toHaveBeenCalledTimes(1);
  });

  it('falls back to the snake-cased name for an entity with no definition', async () => {
    byName.mockResolvedValue(undefined);
    expect(await registry.keyOf('Special Order')).toBe('special-order');
  });

  it('answers "no profiles" when the AI backend is unreachable, and asks again next time', async () => {
    byName.mockResolvedValue({ code: 'boat' });
    findAll.mockReturnValueOnce(throwError(() => new Error('down'))).mockReturnValueOnce(of([new RecognitionProfile({ entityName: 'boat' })]));

    expect(await registry.profileFor('Boat')).toBeUndefined();
    expect(await registry.profileFor('Boat')).toBeDefined();
  });

  it.each([undefined, ''])('does not load definitions or profiles for an empty name (%s)', async (name) => {
    expect(await registry.profileFor(name)).toBeUndefined();
    expect(byName).not.toHaveBeenCalled();
    expect(findAll).not.toHaveBeenCalled();
  });

  it('falls back to the descriptor name when the definition lookup fails', async () => {
    byName.mockRejectedValue(new Error('unavailable'));
    expect(await registry.keyOf('Special Order')).toBe('special-order');
  });

  it.each([null, undefined, { content: null }, { content: [] }])('handles an empty response (%j)', async (response) => {
    byName.mockResolvedValue({ code: 'boat' });
    findAll.mockReturnValue(of(response));
    expect(await registry.profileFor('Boat')).toBeUndefined();
  });

  it('reads a single-record mock response and reloads after reset', async () => {
    const original = new RecognitionProfile({ entityName: 'boat', name: 'Original' });
    const updated = new RecognitionProfile({ entityName: 'boat', name: 'Updated' });
    byName.mockResolvedValue({ code: 'boat' });
    findAll.mockReturnValueOnce(of(original)).mockReturnValueOnce(of([updated]));

    expect(await registry.profileFor('Boat')).toBe(original);
    expect(await registry.profileFor('Boat')).toBe(original);
    expect(findAll).toHaveBeenCalledTimes(1);
    registry.reset();
    expect(await registry.profileFor('Boat')).toBe(updated);
    expect(findAll).toHaveBeenCalledTimes(2);
  });
});
