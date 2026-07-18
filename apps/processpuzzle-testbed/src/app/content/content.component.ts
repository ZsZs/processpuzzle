import { Component, inject } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { DesignRouteService } from '@processpuzzle/design';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { CardsGridSpec, MatCardsGridComponent } from '@processpuzzle/widgets';

@Component({
  selector: 'app-content',
  templateUrl: './content.component.html',
  imports: [TranslocoDirective, MatCardsGridComponent],
  styleUrls: ['./content.component.scss'],
})
export class ContentComponent {
  readonly isDesignRoute = inject(DesignRouteService).isDesignRoute;

  readonly cards: CardsGridSpec[] = [
    {
      icon: 'service_toolbox',
      title: 'utils_card_title',
      subtitle: 'utils_card_subtitle',
      content: ['utils_card_content', 'utils_card_content_1', 'utils_card_content_2', 'utils_card_content_3'],
      actions: [{ link: '/util', caption: 'utils_card_button', colour: 'primary' }],
      menuItems: [{ icon: 'open_in_new', label: 'utils_card_button', link: '/util' }],
      translocoPrefix: 'home',
    },
    {
      icon: 'check_circle',
      title: 'test-utils_card_title',
      subtitle: 'test-utils_card_subtitle',
      content: ['test-utils_card_content', 'test-utils_card_content_1', 'test-utils_card_content_2'],
      actions: [{ link: '/test-util', caption: 'test-utils_card_button', colour: 'primary' }],
      menuItems: [{ icon: 'open_in_new', label: 'test-utils_card_button', link: '/test-util' }],
      translocoPrefix: 'home',
    },
    {
      icon: 'web_asset',
      title: 'widgets_card_title',
      subtitle: 'widgets_card_subtitle',
      content: ['widgets_card_content', 'widgets_card_content_1', 'widgets_card_content_2', 'widgets_card_content_3'],
      actions: [{ link: '/widgets', caption: 'widgets_card_button', colour: 'primary' }],
      menuItems: [{ icon: 'open_in_new', label: 'widgets_card_button', link: '/widgets' }],
      translocoPrefix: 'home',
    },
    {
      icon: 'person_add',
      title: 'auth_card_title',
      subtitle: 'auth_card_subtitle',
      content: ['auth_card_content', 'auth_card_content_1', 'auth_card_content_2', 'auth_card_content_3', 'auth_card_content_4', 'auth_card_content_5', 'auth_card_content_6', 'auth_card_content_7'],
      actions: [{ link: '/auth-lib', caption: 'auth_card_button', colour: 'primary' }],
      menuItems: [{ icon: 'open_in_new', label: 'auth_card_button', link: '/auth-lib' }],
      translocoPrefix: 'home',
    },
    {
      icon: 'checkbook',
      title: 'base-entity_card_title',
      subtitle: 'base-entity_card_subtitle',
      content: ['base-entity_card_content', 'base-entity_card_content_1', 'base-entity_card_content_2', 'base-entity_card_content_3'],
      actions: [{ link: '/base-entity', caption: 'base-entity_card_button', colour: 'primary' }],
      menuItems: [{ icon: 'open_in_new', label: 'base-entity_card_button', link: '/base-entity' }],
      translocoPrefix: 'home',
    },
    {
      icon: 'gavel',
      title: 'base-rule_card_title',
      subtitle: 'base-rule_card_subtitle',
      content: ['base-rule_card_content', 'base-rule_card_content_1', 'base-rule_card_content_2', 'base-rule_card_content_3'],
      actions: [{ link: '/base-rule', caption: 'base-rule_card_button', colour: 'primary' }],
      menuItems: [{ icon: 'open_in_new', label: 'base-rule_card_button', link: '/base-rule' }],
      translocoPrefix: 'home',
    },
    {
      icon: 'repartition',
      title: 'ci-cd_card_title',
      subtitle: 'ci-cd_card_subtitle',
      content: ['ci-cd_card_content', 'ci-cd_card_content_1', 'ci-cd_card_content_2', 'ci-cd_card_content_3'],
      actions: [{ link: '/ci-cd', caption: 'ci-cd_card_button', colour: 'primary' }],
      menuItems: [{ icon: 'open_in_new', label: 'ci-cd_card_button', link: '/ci-cd' }],
      translocoPrefix: 'home',
    },
  ];
}
