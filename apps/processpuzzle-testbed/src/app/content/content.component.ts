import { Component } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { CardsGridSpec, MatCardsGridComponent } from '@processpuzzle/widgets/mat-cards-grid';

@Component({
  selector: 'app-content',
  templateUrl: './content.component.html',
  imports: [TranslocoDirective, MatCardsGridComponent],
  styleUrls: ['./content.component.scss'],
})
export class ContentComponent {
  readonly cards: CardsGridSpec[] = [
    {
      title: 'utils_card_title',
      subtitle: 'utils_card_subtitle',
      content: ['utils_card_content', 'utils_card_content_1', 'utils_card_content_2', 'utils_card_content_3'],
      //      actions: [{ link: '/util', caption: 'utils_card_button', buttonType: 'filled' }],
      actions: [{ link: '/util', caption: 'utils_card_button', colour: 'primary' }],
      translocoPrefix: 'home',
    },
    {
      title: 'test-utils_card_title',
      subtitle: 'test-utils_card_subtitle',
      content: ['test-utils_card_content', 'test-utils_card_content_1', 'test-utils_card_content_2'],
      //      actions: [{ link: '/test-util', caption: 'test-utils_card_button', buttonType: 'filled' }],
      actions: [{ link: '/test-util', caption: 'test-utils_card_button', colour: 'primary' }],
      translocoPrefix: 'home',
    },
    {
      title: 'widgets_card_title',
      subtitle: 'widgets_card_subtitle',
      content: ['widgets_card_content', 'widgets_card_content_1', 'widgets_card_content_2', 'widgets_card_content_3'],
      //      actions: [{ link: '/widgets', caption: 'widgets_card_button', buttonType: 'filled' }],
      actions: [{ link: '/widgets', caption: 'widgets_card_button', colour: 'primary' }],
      translocoPrefix: 'home',
    },
    {
      title: 'auth_card_title',
      subtitle: 'auth_card_subtitle',
      content: ['auth_card_content', 'auth_card_content_1', 'auth_card_content_2', 'auth_card_content_3', 'auth_card_content_4', 'auth_card_content_5', 'auth_card_content_6', 'auth_card_content_7'],
      //      actions: [{ link: '/auth', caption: 'auth_card_button', buttonType: 'filled' }],
      actions: [{ link: '/auth', caption: 'auth_card_button', colour: 'primary' }],
      translocoPrefix: 'home',
    },
    {
      title: 'base-entity_card_title',
      subtitle: 'base-entity_card_subtitle',
      content: ['base-entity_card_content', 'base-entity_card_content_1', 'base-entity_card_content_2', 'base-entity_card_content_3'],
      //      actions: [{ link: '/base-entity', caption: 'base-entity_card_button', buttonType: 'filled' }],
      actions: [{ link: '/base-entity', caption: 'base-entity_card_button', colour: 'primary' }],
      translocoPrefix: 'home',
    },
    {
      title: 'ci-cd_card_title',
      subtitle: 'ci-cd_card_subtitle',
      content: ['ci-cd_card_content', 'ci-cd_card_content_1', 'ci-cd_card_content_2', 'ci-cd_card_content_3'],
      //      actions: [{ link: '/ci-cd', caption: 'ci-cd_card_button', buttonType: 'filled' }],
      actions: [{ link: '/ci-cd', caption: 'ci-cd_card_button', colour: 'primary' }],
      translocoPrefix: 'home',
    },
  ];
}
