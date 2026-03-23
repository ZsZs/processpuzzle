import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { TestEntityStore } from './test-entity.store';
import { testEntityDescriptors } from './test-entity.descriptors';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'test-entity-container',
  standalone: true,
  imports: [CommonModule, BaseEntityContainerComponent, MarkdownComponent],
  template: `
    <markdown clipboard mermaid ngPreserveWhitespaces>
      \`\`\`typescript const selectables = Object.keys(TestEnum) .filter((key: any) => parseInt(key) >= 0) .map((key: string) => (&#123; key: key, value: TestEnum[key as keyof typeof TestEnum]
      &#123;)); const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true); const descriptionAttr = new BaseEntityAttrDescriptor('description',
      FormControlType.TEXTAREA, 'Description'); const booleanAttr = new BaseEntityAttrDescriptor('boolean', FormControlType.CHECKBOX, 'Boolean'); const numberAttr = new
      BaseEntityAttrDescriptor('number', FormControlType.TEXT_BOX, 'Number', undefined, false, &#123; inputType: 'number' &#123;); const dateAttr = new BaseEntityAttrDescriptor('date',
      FormControlType.DATE, 'Date', undefined, false, &#123; inputType: 'date' &#123;); const enumAttr = new BaseEntityAttrDescriptor('enumValue', FormControlType.DROPDOWN, 'Enum', selectables); const
      column_1 = new FlexboxContainer([nameAttr, descriptionAttr, booleanAttr], FlexDirection.COLUMN); const column_2 = new FlexboxContainer([numberAttr, dateAttr, enumAttr], FlexDirection.COLUMN);
      const flexBoxContainer = new FlexboxContainer([column_1, column_2], FlexDirection.CONTAINER); flexBoxContainer.style = &#123; 'column-gap': '20px' &#123;; export const testEntityDescriptors:
      AbstractAttrDescriptor[] = [flexBoxContainer]; \`\`\`
    </markdown>
    <base-entity-container [baseEntityDescriptor]="baseEntityDescriptor"></base-entity-container>
  `,
  styles: ``,
})
export class TestEntityContainerComponent implements OnDestroy {
  private containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  private store = inject(TestEntityStore);
  baseEntityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.baseEntityDescriptor = {
      entityName: 'Test Entity',
      store: this.store,
      attrDescriptors: testEntityDescriptors,
      entityTitle: "this.store.currentEntity() ? this.store.currentEntity().name : ''",
    };
  }

  // region Angular lifecycle hooks
  ngOnDestroy(): void {
    if (this.containerComponentRef) {
      this.containerComponentRef.destroy();
    }
  }

  // endregion

  // protected, private helper methods
  // endregion
}
