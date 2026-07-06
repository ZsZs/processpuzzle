/**
 * Example: wiring a concrete metadata provider and using the editor in a
 * reactive form. Adapt getFields()/getField() to pull from your existing
 * entity schema / OpenAPI-generated model registry.
 */
import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RsqlQueryEditorComponent } from './rsql-query-editor.component';
import { RsqlFieldMetadataProvider, RsqlFieldMetadata } from './rsql-field-metadata.model';

class TaskFieldMetadataProvider extends RsqlFieldMetadataProvider {
  private readonly fields: RsqlFieldMetadata[] = [
    { name: 'status', type: 'enum', enumValues: ['OPEN', 'IN_PROGRESS', 'DONE'] },
    { name: 'priority', type: 'number' },
    { name: 'assignee', type: 'string' },
    { name: 'createdAt', type: 'datetime' },
    { name: 'archived', type: 'boolean' },
  ];

  getFields(): RsqlFieldMetadata[] {
    return this.fields;
  }

  getField(name: string): RsqlFieldMetadata | undefined {
    return this.fields.find((f) => f.name === name);
  }
}

@Component({
  selector: 'pp-task-search',
  standalone: true,
  imports: [ReactiveFormsModule, RsqlQueryEditorComponent],
  providers: [{ provide: RsqlFieldMetadataProvider, useClass: TaskFieldMetadataProvider }],
  template: `
    <pp-rsql-query-editor
      [formControl]="queryControl"
      (validityChange)="onValidityChange($event)"
    />
    @if (!isQueryValid) {
      <p class="error">Fix the highlighted issues before searching.</p>
    }
  `,
})
export class TaskSearchComponent {
  readonly queryControl = new FormControl('', { nonNullable: true });
  isQueryValid = true;

  onValidityChange(valid: boolean): void {
    this.isQueryValid = valid;
  }
}
