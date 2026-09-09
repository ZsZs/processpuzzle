import { describe, expect, it } from 'vitest';
import { toReferenceIds } from './reference-ids';

describe('toReferenceIds', () => {
  it('passes a well-behaved id list through unchanged', () => {
    expect(toReferenceIds(['clerk', 'manager'])).toEqual(['clerk', 'manager']);
  });

  // The case this function exists for: `RelatedEntitiesListComponent` writes the *whole entity* into
  // its form control when the user picks one, while the contract wants a string.
  it('flattens a whole entity the RELATED_ENTITIES control just added', () => {
    expect(toReferenceIds([{ id: 'clerk', name: 'Order Clerk' }])).toEqual(['clerk']);
  });

  // Which is the shape a form in mid-edit actually holds: everything loaded from the server is still
  // an id, and only the pick just made is an entity.
  it('handles a list holding both at once', () => {
    expect(toReferenceIds(['clerk', { id: 'manager' }])).toEqual(['clerk', 'manager']);
  });

  it('answers an empty list for an absent value', () => {
    expect(toReferenceIds(undefined)).toEqual([]);
  });

  // A reference with nothing to point at is not a reference. Dropping it here rather than sending it
  // is what keeps a half-filled control from failing the backend's existence check.
  it('drops an entry that carries no usable id', () => {
    expect(toReferenceIds([{ name: 'nameless' }, { id: '' }, 'clerk'])).toEqual(['clerk']);
  });
});
