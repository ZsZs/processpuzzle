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
      icon: 'gavel',
      title: 'base-rule_card_title',
      subtitle: 'base-rule_card_subtitle',
      content: ['base-rule_card_content', 'base-rule_card_content_1', 'base-rule_card_content_2', 'base-rule_card_content_3'],
      actions: [{ link: '/design/rules', caption: 'base-rule_card_button', colour: 'primary' }],
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
      icon: 'desktop_windows',
      title: 'base-desktop_card_title',
      subtitle: 'base-desktop_card_subtitle',
      content: ['base-desktop_card_content', 'base-desktop_card_content_1', 'base-desktop_card_content_2', 'base-desktop_card_content_3'],
      actions: [{ link: '/design/desktop', caption: 'base-desktop_card_button', colour: 'primary' }],
      translocoPrefix: 'design',
    },
  ];
}
