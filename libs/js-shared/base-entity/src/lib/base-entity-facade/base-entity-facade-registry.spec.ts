import { Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { FlexboxDescriptor, FlexDirection } from '../base-entity/flexboxDescriptor';
import { BASE_ENTITY_FACADE_REGISTRY, EntityRegistryComponent } from './base-entity-facade-registry';

@Injectable()
class FacadeWithStringTitle {
  readonly descriptor: BaseEntityDescriptor;

  constructor() {
    const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
    nameAttr.description = 'The entity name';
    nameAttr.styleClass = 'name-style';
    nameAttr.labelClass = 'name-label';
    nameAttr.format = 'plain';
    nameAttr.isLinkToDetails = true;
    nameAttr.selectables = [{ key: 'k', value: 'v' }];
    nameAttr.hideInTable = false;
    nameAttr.isHeading = true;
    nameAttr.placeholder = 'enter name';
    nameAttr.lines = 1;
    nameAttr.required = true;
    nameAttr.linkedEntityType = 'OtherEntity';

    const groupedAttr = new BaseEntityAttrDescriptor('group', FormControlType.TEXT_BOX);
    const flexbox = new FlexboxDescriptor([groupedAttr], FlexDirection.ROW);

    this.descriptor = new BaseEntityDescriptor({
      store: {},
      attrDescriptors: [nameAttr, flexbox],
      entityName: 'EntityA',
      entityTitle: 'Entity A Title',
      isAbstract: false,
      parentEntity: 'ParentEntity',
    });
  }
}

@Injectable()
class FacadeWithFunctionTitle {
  readonly descriptor: BaseEntityDescriptor;

  constructor() {
    this.descriptor = new BaseEntityDescriptor({
      store: {},
      attrDescriptors: [],
      entityName: 'EntityB',
      entityTitle: () => 'Entity B Computed Title',
      isAbstract: true,
    });
  }
}

async function setupComponent(minified?: string) {
  const queryParamMap = of(convertToParamMap(minified === undefined ? {} : { minified }));

  await TestBed.configureTestingModule({
    imports: [EntityRegistryComponent],
    providers: [
      FacadeWithStringTitle,
      FacadeWithFunctionTitle,
      { provide: ActivatedRoute, useValue: { queryParamMap } },
      {
        provide: BASE_ENTITY_FACADE_REGISTRY,
        useValue: { entityA: FacadeWithStringTitle, entityB: FacadeWithFunctionTitle },
      },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(EntityRegistryComponent);
  fixture.detectChanges();

  const text = (fixture.nativeElement as HTMLElement).querySelector('pre')?.textContent ?? '';
  return { fixture, text };
}

describe('BASE_ENTITY_FACADE_REGISTRY', () => {
  it('defaults to an empty registry when nothing is provided', () => {
    TestBed.configureTestingModule({});
    expect(TestBed.inject(BASE_ENTITY_FACADE_REGISTRY)).toEqual({});
  });
});

describe('EntityRegistryComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders pretty-printed JSON when minified query param is absent', async () => {
    const { text } = await setupComponent();

    expect(text).toContain('\n');
    const parsed = JSON.parse(text);
    expect(parsed).toHaveLength(2);
    expect(parsed[0]).toMatchObject({
      entityName: 'EntityA',
      entityTitle: 'Entity A Title',
      isAbstract: false,
      parentEntityName: 'ParentEntity',
    });
    expect(parsed[1]).toMatchObject({
      entityName: 'EntityB',
      entityTitle: 'Entity B Computed Title',
      isAbstract: true,
    });
  });

  it('flattens nested FlexboxDescriptor children into the serialized attrDescriptors', async () => {
    const { text } = await setupComponent();
    const parsed = JSON.parse(text);
    const attrNames = parsed[0].attrDescriptors.map((a: { attrName: string }) => a.attrName);

    expect(attrNames).toEqual(['name', 'group']);
  });

  it('serializes BaseEntityAttrDescriptor fields onto each entry', async () => {
    const { text } = await setupComponent();
    const parsed = JSON.parse(text);
    const nameAttr = parsed[0].attrDescriptors[0];

    expect(nameAttr).toMatchObject({
      attrName: 'name',
      formControlType: FormControlType.TEXT_BOX,
      disabled: false,
      label: 'Name',
      description: 'The entity name',
      styleClass: 'name-style',
      labelClass: 'name-label',
      format: 'plain',
      isLinkToDetails: true,
      selectables: [{ key: 'k', value: 'v' }],
      visible: true,
      hideInTable: false,
      isHeading: true,
      placeholder: 'enter name',
      lines: 1,
      required: true,
      linkedEntityType: 'OtherEntity',
    });
  });

  it('renders minified JSON when minified=yes query param is present', async () => {
    const { text } = await setupComponent('yes');

    expect(text).not.toContain('\n');
    expect(() => JSON.parse(text)).not.toThrow();
  });

  it('renders pretty JSON when minified query param has any value other than "yes"', async () => {
    const { text } = await setupComponent('no');

    expect(text).toContain('\n');
  });
});
