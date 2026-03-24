import { Component, computed, input, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbar } from '@angular/material/toolbar';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';

@Component({
  selector: 'base-entity-statusbar',
  standalone: true,
  imports: [CommonModule, MatToolbar],
  templateUrl: './base-entity-statusbar.component.html',
})
export class BaseEntityStatusbarComponent implements OnInit {
  store: any;
  entityDescriptor = input.required<BaseEntityDescriptor>();
  entityTitle: Signal<string> = computed<string>(() => this.evaluateEntityTitle(this.entityDescriptor().entityTitle));
  isVisible: Signal<boolean> = computed(() => this.store.currentEntity() != undefined || this.store.selectedEntities().length == 1);

  // region Angular lifecycle hooks
  ngOnInit(): void {
    this.store = this.entityDescriptor().store;
  }

  // endregion

  // event handling methods
  // endregion

  // protected, private helper methods
  private evaluateEntityTitle(title: string): string {
    const fn = new Function('ctx', 'return ' + title.replace(/this\./g, 'ctx.'));
    return fn(this);
  }

  // endregion
}
