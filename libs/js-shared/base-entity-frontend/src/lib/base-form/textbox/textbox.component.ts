import { Component } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { type Selectable } from '../../base-entity/base-entity-attr.descriptor';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';

/**
 * A free-text field. Given `selectables`, it offers them as suggestions while staying free: unlike a
 * `DROPDOWN`, a value outside the list is still a value — for a field whose usual values are known but not
 * exhaustive, such as an event parameter named after an attribute of its subject.
 */
@Component({
  selector: 'base-textbox',
  standalone: true,
  templateUrl: './textbox.component.html',
  imports: [MatFormField, MatInput, MatLabel, MatAutocompleteModule, FormsModule, ReactiveFormsModule, EntityLabelPipe],
  styles: [
    `
      :host {
        display: block;
      }
      mat-form-field {
        width: 100%;
      }
    `,
  ],
})
export class TextboxComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  /** The suggestions matching what is typed so far; read from the template, so `selectables` may be a function. */
  suggestions(): Selectable[] {
    const typed = String(this.formGroup?.get(this.config().attrName)?.value ?? '').toLocaleLowerCase();
    return (this.config().getSelectables() ?? []).filter((option) => String(option.value).toLocaleLowerCase().includes(typed));
  }
}
