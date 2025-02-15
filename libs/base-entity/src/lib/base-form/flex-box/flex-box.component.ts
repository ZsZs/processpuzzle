import { Component, computed, ViewChild } from '@angular/core';
import { BaseEntity } from '../../base-entity/base-entity';
import { BaseFormControlComponent } from '../base-form-control.component';
import { FlexBoxHostDirective } from './flex-box-host.directive';
import { FlexboxContainer, FlexDirection } from '../../base-entity/flexbox.container';
import { NgClass, NgStyle } from '@angular/common';

@Component({
  selector: 'flex-box',
  imports: [FlexBoxHostDirective, NgClass, NgStyle],
  templateUrl: './flex-box.component.html',
  styleUrls: ['./flex-box.component.scss'],
})
export class FlexBoxComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  @ViewChild(FlexBoxHostDirective, { static: true, read: FlexBoxHostDirective }) flexBoxHost!: FlexBoxHostDirective;
  flexBoxClass = computed<string>(() => this.calculateFlexStyle((this.config() as unknown as FlexboxContainer).direction));

  // region protected, private helper methods
  private calculateFlexStyle(direction: FlexDirection): string {
    switch (direction) {
      case FlexDirection.CONTAINER:
        return 'flex-box-container';
      case FlexDirection.COLUMN:
        return 'flex-box-container-columns';
      case FlexDirection.ROW:
        return 'flex-box-container-rows';
      default:
        throw new Error('Unknown flex direction');
    }
  }

  // endregion
}
