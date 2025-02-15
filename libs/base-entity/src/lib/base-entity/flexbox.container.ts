import { AbstractAttrDescriptor, FormControlType } from './abstact-attr.descriptor';

export enum FlexDirection {
  CONTAINER = 0,
  COLUMN = 1,
  ROW = 2,
}

export class FlexboxContainer extends AbstractAttrDescriptor {
  readonly attrDescriptors: AbstractAttrDescriptor[];
  readonly direction: FlexDirection;

  constructor(attrDescriptors: AbstractAttrDescriptor[], direction: FlexDirection) {
    super('', FormControlType.FLEX_BOX);
    this.attrDescriptors = attrDescriptors;
    this.direction = direction;
  }
}
