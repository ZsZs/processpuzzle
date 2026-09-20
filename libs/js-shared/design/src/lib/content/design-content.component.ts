import { Component, inject } from '@angular/core';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { DESIGN_ROUTE_PREFIX } from '@processpuzzle/util';
import { CardsGridSpec, MatCardsGridComponent } from '@processpuzzle/widgets';

@Component({
  selector: 'pp-design-content',
  standalone: true,
  imports: [TranslocoDirective, MatCardsGridComponent],
  providers: [provideTranslocoScope({ scope: 'design' })],
  template: `
    <div>
      <ng-container *transloco="let t; prefix: 'design'">
        <mat-cards-grid [cards]="cards"></mat-cards-grid>
      </ng-container>
    </div>
  `,
})
export class DesignContentComponent {
  /**
   * `''` unless the hosting application mounts the designer under a path — see
   * {@link DESIGN_ROUTE_PREFIX}. The card links are absolute, so without it every card on the
   * Designer Home of a path-mounted shell navigates out of the tenant.
   */
  private readonly prefix = inject(DESIGN_ROUTE_PREFIX);

  readonly cards: CardsGridSpec[] = [
    {
      icon: 'checkbook',
      title: 'base-entity_card_title',
      subtitle: 'base-entity_card_subtitle',
      content: ['base-entity_card_content', 'base-entity_card_content_1', 'base-entity_card_content_2', 'base-entity_card_content_3'],
      actions: [{ link: `${this.prefix}/design/entities`, caption: 'base-entity_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
    {
      icon: 'article',
      title: 'base-document_card_title',
      subtitle: 'base-document_card_subtitle',
      content: ['base-document_card_content', 'base-document_card_content_1', 'base-document_card_content_2', 'base-document_card_content_3'],
      // Singular, matching the snake-cased entity name BASE_DOCUMENT_ROUTES mounts at — see the comment there.
      actions: [{ link: `${this.prefix}/design/document`, caption: 'base-document_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
    {
      icon: 'gavel',
      title: 'base-rule_card_title',
      subtitle: 'base-rule_card_subtitle',
      content: ['base-rule_card_content', 'base-rule_card_content_1', 'base-rule_card_content_2', 'base-rule_card_content_3'],
      actions: [{ link: `${this.prefix}/design/base-rule`, caption: 'base-rule_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
    {
      icon: 'flag_circle',
      title: 'base-state_card_title',
      subtitle: 'base-state_card_subtitle',
      content: ['base-state_card_content', 'base-state_card_content_1', 'base-state_card_content_2', 'base-state_card_content_3'],
      actions: [{ link: `${this.prefix}/design/states`, caption: 'base-state_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
    {
      icon: 'schema',
      title: 'base-workflow_card_title',
      subtitle: 'base-workflow_card_subtitle',
      content: ['base-workflow_card_content', 'base-workflow_card_content_1', 'base-workflow_card_content_2', 'base-workflow_card_content_3'],
      actions: [{ link: `${this.prefix}/design/workflows`, caption: 'base-workflow_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
    {
      icon: 'web',
      title: 'base-app_card_title',
      subtitle: 'base-app_card_subtitle',
      content: ['base-app_card_content', 'base-app_card_content_1', 'base-app_card_content_2', 'base-app_card_content_3'],
      // The section, not one of its tabs: `application` redirects to the App Definition tab on its own.
      actions: [{ link: `${this.prefix}/design/application`, caption: 'base-app_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
  ];
}
