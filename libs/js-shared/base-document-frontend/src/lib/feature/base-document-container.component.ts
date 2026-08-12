import { Component, inject } from '@angular/core';
import { BaseEntityContainerComponent, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { createDocumentDescriptor } from '../domain/base-document.descriptors';
import { BaseDocumentStore } from '../domain/base-document.store';
import { DOCUMENT_CONTENT_TAB } from './document-content-tab';

/**
 * List and (title/description/ports) Properties come from BaseEntityContainerComponent, exactly as
 * BaseRuleContainerComponent uses it. This component's only job is to declare what the generic container
 * cannot know: that a document has a third screen, its content.
 *
 * The content editor sits behind its own tab rather than stacked under the Details form. Stacking was the
 * earlier shape only because BaseEntityTabsComponent had no way to show a third tab; it now does, through
 * `BaseEntityDescriptor.extraTabs`, and a tab is the better fit — the editor is a full-height writing
 * surface with its own scroll, and putting it below a form meant the content of a long document was always
 * one screen further down than the metadata nobody was editing. It also gives the content a URL of its own
 * (`document/<id>/content`), so a link can point at what somebody should read rather than at a form.
 *
 * Keeping the Properties save out of here stays deliberate: it belongs in BaseDocumentService.update,
 * because the generic form is rendered through a router-outlet and so has no binding back to this component.
 */
@Component({
  selector: 'pp-base-document-container',
  standalone: true,
  imports: [BaseEntityContainerComponent],
  template: ` <base-entity-container [entityDescriptor]="entityDescriptor"></base-entity-container> `,
})
export class BaseDocumentContainerComponent {
  protected readonly entityDescriptor: BaseEntityDescriptor;

  private readonly store = inject(BaseDocumentStore);

  constructor() {
    // BaseEntityTabsComponent takes its store from the descriptor it is handed, so the descriptor built
    // here has to carry it — DocumentFacade binds the store into its own descriptor instance, not this
    // one. Same two lines as BaseRuleContainerComponent and AppDefinitionContainerComponent.
    this.entityDescriptor = createDocumentDescriptor();
    this.entityDescriptor.store = this.store;
    // The same constant BASE_DOCUMENT_ROUTES mounts, so the link and the route cannot disagree.
    this.entityDescriptor.extraTabs = [DOCUMENT_CONTENT_TAB];
  }
}
