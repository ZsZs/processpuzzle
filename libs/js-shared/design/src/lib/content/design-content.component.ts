import { Component } from '@angular/core';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
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
  readonly cards: CardsGridSpec[] = [
    {
      icon: 'checkbook',
      title: 'base-entity_card_title',
      subtitle: 'base-entity_card_subtitle',
      content: ['base-entity_card_content', 'base-entity_card_content_1', 'base-entity_card_content_2', 'base-entity_card_content_3'],
      actions: [{ link: '/design/entities', caption: 'base-entity_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
    {
      icon: 'article',
      title: 'base-document_card_title',
      subtitle: 'base-document_card_subtitle',
      content: ['base-document_card_content', 'base-document_card_content_1', 'base-document_card_content_2', 'base-document_card_content_3'],
      actions: [{ link: '/design/documents', caption: 'base-document_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
    {
      icon: 'gavel',
      title: 'base-rule_card_title',
      subtitle: 'base-rule_card_subtitle',
      content: ['base-rule_card_content', 'base-rule_card_content_1', 'base-rule_card_content_2', 'base-rule_card_content_3'],
      actions: [{ link: '/design/base-rule', caption: 'base-rule_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
    {
      icon: 'flag_circle',
      title: 'base-state_card_title',
      subtitle: 'base-state_card_subtitle',
      content: ['base-state_card_content', 'base-state_card_content_1', 'base-state_card_content_2', 'base-state_card_content_3'],
      actions: [{ link: '/design/states', caption: 'base-state_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
    {
      icon: 'schema',
      title: 'base-workflow_card_title',
      subtitle: 'base-workflow_card_subtitle',
      content: ['base-workflow_card_content', 'base-workflow_card_content_1', 'base-workflow_card_content_2', 'base-workflow_card_content_3'],
      actions: [{ link: '/design/workflows', caption: 'base-workflow_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
    {
      icon: 'web',
      title: 'base-app_card_title',
      subtitle: 'base-app_card_subtitle',
      content: ['base-app_card_content', 'base-app_card_content_1', 'base-app_card_content_2', 'base-app_card_content_3'],
      actions: [{ link: '/design/app-definition', caption: 'base-app_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
  ];
}
