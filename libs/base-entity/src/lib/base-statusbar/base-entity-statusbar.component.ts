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
  baseEntityListOptions = input.required<BaseEntityDescriptor>();
  entityTitle: Signal<string> = computed<string>(() => eval(this.baseEntityListOptions().entityTitle));
  isVisible: Signal<boolean> = computed(() => this.store.currentEntity() != undefined || this.store.selectedEntities().length == 1);

  // region Angular lifecycle hooks
  ngOnInit(): void {
    this.store = this.baseEntityListOptions().store;
  }
  // endregion

  // event handling methods
  // endregion

  // protected, private helper methods
  // endregion
}
