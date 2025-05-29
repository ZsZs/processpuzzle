import { Component, inject } from '@angular/core';
import { LayoutService } from '@processpuzzle/util';
import { NgClass } from '@angular/common';
import { TranslocoDirective } from '@jsverse/transloco';
import { CardsGridSpec, MatCardsGridComponent } from '@processpuzzle/widgets/mat-cards-grid';

@Component({
  selector: 'app-content',
  templateUrl: './content.component.html',
  imports: [NgClass, TranslocoDirective, MatCardsGridComponent],
  styleUrls: ['./content.component.scss', './mat-card-grid.css'],
})
export class ContentComponent {
  readonly layoutService = inject(LayoutService);

  readonly cards: CardsGridSpec[] = [
    {
      title: 'Utils - @processpuzzle/util',
      subtitle: 'utils_card_subtitle',
      content: ['utils_card_content', 'utils_card_content_1', 'utils_card_content_2', 'utils_card_content_3'],
      actions: [{ link: '/util', caption: 'utils_card_button' }],
      translocoPrefix: 'home',
    },
    {
      title: 'Test Utils - @processpuzzle/test-util',
      subtitle: 'test-utils_card_subtitle',
      content: ['test-utils_card_content', 'test-utils_card_content_1', 'test-utils_card_content_2'],
      actions: [{ link: '/test-util', caption: 'test-utils_card_button' }],
      translocoPrefix: 'home',
    },
    {
      title: 'Widgets - @processpuzzle/widgets',
      subtitle: 'widgets_card_subtitle',
      content: ['widgets_card_content', 'widgets_card_content_1', 'widgets_card_content_2', 'widgets_card_content_3'],
      actions: [{ link: '/widgets', caption: 'widgets_card_button' }],
      translocoPrefix: 'home',
    },
    {
      title: 'Authentication - @processpuzzle/auth',
      subtitle: 'auth_card_subtitle',
      content: ['auth_card_content', 'auth_card_content_1', 'auth_card_content_2', 'auth_card_content_3', 'auth_card_content_4', 'auth_card_content_5', 'auth_card_content_6', 'auth_card_content_7'],
      actions: [{ link: '/auth', caption: 'auth_card_button' }],
      translocoPrefix: 'home',
    },
    {
      title: 'Base Entity - @processpuzzle/base-entity',
      subtitle: 'base-entity_card_subtitle',
      content: ['base-entity_card_content', 'base-entity_card_content_1', 'base-entity_card_content_2', 'base-entity_card_content_3'],
      actions: [{ link: '/base-entity', caption: 'base-entity_card_button' }],
      translocoPrefix: 'home',
    },
    {
      title: 'Continuous Delivery',
      subtitle: 'ci-cd_card_subtitle',
      content: ['ci-cd_card_content', 'ci-cd_card_content_1', 'ci-cd_card_content_2', 'ci-cd_card_content_3'],
      actions: [{ link: '/ci-cd', caption: 'ci-cd_card_button' }],
      translocoPrefix: 'home',
    },
  ];
}
