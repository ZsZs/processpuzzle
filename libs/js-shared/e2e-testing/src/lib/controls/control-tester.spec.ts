import { describe, expect, it } from 'vitest';
import type { BaseEntityAttrDescriptor, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import {
  ArtifactControlTester,
  ControlTester,
  RelationshipControlTester,
  artifactTestersFor,
  controlTestersFor,
  createControlTester,
  identificationAttrFromTesters,
  linkedFixtureAttrKey,
  parentReferenceAttrName,
  relationshipTestersFor,
} from './control-tester';

/**
 * Descriptors reach the suites as JSON off the application's registry endpoint, never as instances of the
 * `base-entity` classes — evaluating that Angular library in a Node process needs the JIT compiler. So the
 * fixtures here are the plain objects the suites actually receive, and the casts are the same ones the library
 * makes internally.
 */
type AttrFixture = { attrName: string; formControlType: string } & Record<string, unknown>;

function attr(fixture: AttrFixture): BaseEntityAttrDescriptor {
  return fixture as unknown as BaseEntityAttrDescriptor;
}

function descriptor(entityName: string, attrs: BaseEntityAttrDescriptor[]): BaseEntityDescriptor {
  return { entityName, attrDescriptors: attrs } as unknown as BaseEntityDescriptor;
}

describe('createControlTester', () => {
  it.each([
    ['TEXT_BOX', true],
    ['TEXTAREA', true],
    ['CHECKBOX', true],
    ['DATE', true],
    ['DROPDOWN', true],
    ['TAGS', true],
    ['FOREIGN_KEY', true],
    ['LOOKUP', true],
  ])('treats %s as a form value', (formControlType, isInput) => {
    expect(createControlTester(attr({ attrName: 'a', formControlType })).isInput).toBe(isInput);
  });

  it.each(['RELATED_ENTITIES', 'COMPONENTS', 'EMBEDDED_COMPONENTS'])('keeps %s out of form filling — a relationship is not a scalar', (formControlType) => {
    const tester = createControlTester(attr({ attrName: 'children', formControlType }));

    expect(tester).toBeInstanceOf(RelationshipControlTester);
    expect(tester.isInput).toBe(false);
  });

  it.each([
    ['RELATED_ENTITIES', 'Delete related entity reference'],
    ['COMPONENTS', 'Delete component'],
    ['EMBEDDED_COMPONENTS', 'Delete embedded component'],
  ])('labels the row delete button of %s as the generated control does', (formControlType, label) => {
    const tester = createControlTester(attr({ attrName: 'children', formControlType })) as RelationshipControlTester;

    expect(tester.rowDeleteAriaLabel).toBe(label);
  });

  it('confirms a destructive row delete, but detaching a reference needs no dialog', () => {
    const association = createControlTester(attr({ attrName: 'refs', formControlType: 'RELATED_ENTITIES' })) as RelationshipControlTester;
    const containment = createControlTester(attr({ attrName: 'children', formControlType: 'COMPONENTS' })) as RelationshipControlTester;

    expect(association.confirmsDelete).toBe(false);
    expect(containment.confirmsDelete).toBe(true);
  });

  it.each(['RELATED_ENTITIES', 'COMPONENTS', 'EMBEDDED_COMPONENTS'])('addresses the row list of %s and titles its add button after the linked entity', (formControlType) => {
    const tester = createControlTester(attr({ attrName: 'children', formControlType, linkedEntityType: 'Child Entity' })) as RelationshipControlTester;

    expect(tester.innerLocator()).toBe('ul');
    expect(tester.addButtonName()).toBe('Add Child Entity');
  });

  it('titles the add button without a name when the descriptor reports no linked entity', () => {
    const tester = createControlTester(attr({ attrName: 'children', formControlType: 'COMPONENTS' })) as RelationshipControlTester;

    expect(tester.addButtonName()).toBe('Add ');
  });

  it('asserts no value on a relationship: the rows are the fieldset page object’s subject', async () => {
    const tester = createControlTester(attr({ attrName: 'children', formControlType: 'COMPONENTS' })) as RelationshipControlTester;

    await expect(tester.assertValue()).resolves.toBeUndefined();
  });

  it.each(['FLEX_BOX', 'LABEL'])('keeps %s out of form filling — it holds no value at all', (formControlType) => {
    expect(createControlTester(attr({ attrName: 'layout', formControlType })).isInput).toBe(false);
  });

  it('gives a control the suites do not drive no inner locator and nothing to assert', async () => {
    const tester = createControlTester(attr({ attrName: 'layout', formControlType: 'FLEX_BOX' }));

    expect(tester.innerLocator()).toBe('');
    // Reached through the base signature, which is how the suites call it — the override ignores both arguments,
    // which is exactly the behaviour being pinned down.
    await expect(tester.assertValue({} as never, '')).resolves.toBeUndefined();
  });

  it('falls back to treating an unrecognised control type as an input', () => {
    // The generous default is deliberate: a control type added to base-entity should show up as a suite failing
    // to fill it, rather than as a suite silently skipping it.
    expect(createControlTester(attr({ attrName: 'novel', formControlType: 'SOMETHING_NEW' })).isInput).toBe(true);
  });
});

describe('DROPDOWN option values', () => {
  const dropdown = (fixture: Record<string, unknown>) => createControlTester(attr({ attrName: 'status', formControlType: 'DROPDOWN', ...fixture }));
  const noContext = {} as never;

  it('fills with the first option and updates to the second', () => {
    const tester = dropdown({ selectables: [{ value: 'OPEN' }, { value: 'CLOSED' }] });

    expect(tester.createValue(noContext)).toBe('OPEN');
    expect(tester.updateValue(noContext, { status: 'OPEN' })).toBe('CLOSED');
  });

  it.each([
    [1, '1'],
    [true, 'true'],
    [10n, '10'],
  ])('carries a %s option value as the string the form data holds', (value, expected) => {
    expect(dropdown({ selectables: [{ value }] }).createValue(noContext)).toBe(expected);
  });

  it.each([['an object', {}] as const, ['a function', () => 'x'] as const, ['null', null] as const, ['undefined', undefined] as const])(
    'reports %s option value as absent rather than stringifying it into text no option carries',
    (_label, value) => {
      // Falling back is what a dropdown with too few options does, and it fails on the form rather than on a
      // value like "[object Object]" that no mat-option could ever match.
      const tester = dropdown({ selectables: [{ value }] });

      expect(tester.createValue(noContext)).toBe('');
      expect(tester.updateValue(noContext, { status: 'OPEN' })).toBe('OPEN');
    },
  );

  it('resolves selectables declared as a factory', () => {
    expect(dropdown({ selectables: () => [{ value: 'OPEN' }] }).createValue(noContext)).toBe('OPEN');
  });

  it('prefers the descriptor’s getSelectables over the raw property', () => {
    const tester = dropdown({ selectables: [{ value: 'RAW' }], getSelectables: () => [{ value: 'RESOLVED' }] });

    expect(tester.createValue(noContext)).toBe('RESOLVED');
  });

  it('has no value to offer when the descriptor declares no selectables', () => {
    const tester = dropdown({});

    expect(tester.createValue(noContext)).toBe('');
    expect(tester.updateValue(noContext, {})).toBe('');
  });
});

describe('ArtifactControlTester', () => {
  const artifactAttr = attr({ attrName: 'artifact', formControlType: 'ARTIFACT' });

  it('is what an ARTIFACT attribute resolves to', () => {
    expect(createControlTester(artifactAttr)).toBeInstanceOf(ArtifactControlTester);
  });

  it('stays out of form filling, which keeps the object store off the CRUD suite’s critical path', () => {
    expect(createControlTester(artifactAttr).isInput).toBe(false);
  });

  it('addresses the single-row list inside the fieldset', () => {
    expect(new ArtifactControlTester(artifactAttr).innerLocator()).toBe('ul');
  });

  it('names the buttons and the row delete control as ArtifactComponent authors them', () => {
    const tester = new ArtifactControlTester(artifactAttr);

    expect(tester.revealSelectorButtonName).toBe('Upload file');
    expect(tester.uploadButtonName).toBe('Upload');
    expect(tester.rowDeleteAriaLabel).toBe('Delete artifact reference');
  });

  it('asserts no value: what the control holds is a reference, not something readable off the form', async () => {
    await expect(new ArtifactControlTester(artifactAttr).assertValue()).resolves.toBeUndefined();
  });

  describe('showsThumbnailFor', () => {
    it('expects a thumbnail for a raster image', () => {
      expect(new ArtifactControlTester(artifactAttr).showsThumbnailFor('image/png')).toBe(true);
    });

    it('expects a MIME icon for anything else', () => {
      expect(new ArtifactControlTester(artifactAttr).showsThumbnailFor('text/plain')).toBe(false);
    });

    it('honours an explicit showThumbnail: false even for an image', () => {
      const suppressed = attr({ attrName: 'artifact', formControlType: 'ARTIFACT', showThumbnail: false });

      expect(new ArtifactControlTester(suppressed).showsThumbnailFor('image/png')).toBe(false);
    });

    it('treats showThumbnail as opt-out: absent means the thumbnail is expected', () => {
      const explicit = attr({ attrName: 'artifact', formControlType: 'ARTIFACT', showThumbnail: true });

      expect(new ArtifactControlTester(explicit).showsThumbnailFor('image/jpeg')).toBe(true);
    });
  });
});

describe('tester selection over a descriptor', () => {
  const entity = descriptor('Test Entity', [
    attr({ attrName: 'name', formControlType: 'TEXT_BOX', isLinkToDetails: true }),
    attr({ attrName: 'hidden', formControlType: 'TEXT_BOX', visible: false }),
    attr({ attrName: 'readOnly', formControlType: 'TEXT_BOX', disabled: true }),
    attr({ attrName: 'children', formControlType: 'COMPONENTS' }),
    attr({ attrName: 'artifact', formControlType: 'ARTIFACT' }),
    attr({ attrName: 'layout', formControlType: 'FLEX_BOX' }),
  ]);

  it('returns only the scalar inputs, skipping the invisible and the disabled', () => {
    expect(controlTestersFor(entity).map((tester) => tester.attr.attrName)).toEqual(['name']);
  });

  it('returns the relationship attributes separately', () => {
    expect(relationshipTestersFor(entity).map((tester) => tester.attr.attrName)).toEqual(['children']);
  });

  it('returns the artifact attributes separately', () => {
    expect(artifactTestersFor(entity).map((tester) => tester.attr.attrName)).toEqual(['artifact']);
  });

  it('keeps the three sets disjoint, so no attribute is driven by two suites', () => {
    const named = (testers: ControlTester[]) => testers.map((tester) => tester.attr.attrName);
    const inputs = named(controlTestersFor(entity));
    const relationships = named(relationshipTestersFor(entity));
    const artifacts = named(artifactTestersFor(entity));

    expect(inputs.filter((name) => relationships.includes(name) || artifacts.includes(name))).toEqual([]);
    expect(relationships.filter((name) => artifacts.includes(name))).toEqual([]);
  });

  it('skips an invisible artifact attribute — there is nothing on the form to upload into', () => {
    const withHidden = descriptor('Test Entity', [attr({ attrName: 'artifact', formControlType: 'ARTIFACT', visible: false })]);

    expect(artifactTestersFor(withHidden)).toEqual([]);
  });

  it('finds the attribute that links to the detail form', () => {
    expect(identificationAttrFromTesters(entity)?.attrName).toBe('name');
  });

  it('reports no identification attribute when none is marked', () => {
    const anonymous = descriptor('Anonymous', [attr({ attrName: 'note', formControlType: 'TEXT_BOX' })]);

    expect(identificationAttrFromTesters(anonymous)).toBeUndefined();
  });
});

describe('parentReferenceAttrName', () => {
  const child = descriptor('Child', [
    attr({ attrName: 'name', formControlType: 'TEXT_BOX' }),
    attr({ attrName: 'ownerId', formControlType: 'FOREIGN_KEY', linkedEntityType: 'Owner' }),
    attr({ attrName: 'otherId', formControlType: 'FOREIGN_KEY', linkedEntityType: 'Other' }),
  ]);

  it('names the foreign key pointing back at the given owner', () => {
    expect(parentReferenceAttrName(child, 'Owner')).toBe('ownerId');
  });

  it('is undefined when the child holds no key to that owner', () => {
    expect(parentReferenceAttrName(child, 'Unrelated')).toBeUndefined();
  });
});

describe('linkedFixtureAttrKey', () => {
  it('qualifies the attribute with its entity, so two entities can share an attribute name', () => {
    expect(linkedFixtureAttrKey('Test Entity', 'ownerId')).toBe('Test Entity.ownerId');
  });
});
