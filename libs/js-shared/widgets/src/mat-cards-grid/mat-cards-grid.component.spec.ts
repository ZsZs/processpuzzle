import { ComponentFixture } from '@angular/core/testing';
import { CardsGridSpec } from './cards-spec';
import { MatCardsGridComponent } from './mat-cards-grid.component';
import { Component } from '@angular/core';
import { provideRouter } from '@angular/router';
import { setUpTranslocoTestBed } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';

// Create a test host component to test the component with inputs
@Component({
  template: ` <mat-cards-grid [cards]="cards"></mat-cards-grid>`,
  standalone: true,
  imports: [MatCardsGridComponent],
})
class TestHostComponent {
  cards: CardsGridSpec[] = [
    {
      title: 'Test Card 1',
      subtitle: 'subtitle',
      content: ['content', 'content_1', 'content_2'],
      actions: [{ link: '/test1', caption: 'button' }],
      translocoPrefix: 'test1',
    },
    {
      title: 'Test Card 2',
      subtitle: 'subtitle',
      content: ['content', 'content_1', 'content_2', 'content_3'],
      actions: [{ link: '/test2', caption: 'button' }],
      translocoPrefix: 'test2',
    },
  ];
}

describe('MatCardsGridComponent', () => {
  let hostComponent: TestHostComponent;
  let hostFixture: ComponentFixture<TestHostComponent>;
  const compilations = {
    en: {
      test1: {
        subtitle: 'Test Subtitle 1',
        content: 'Test Content 1',
        content_1: 'Test Content Item 1.1',
        content_2: 'Test Content Item 1.2',
        button: 'Test Button 1',
      },
      test2: {
        subtitle: 'Test Subtitle 2',
        content: 'Test Content 2',
        content_1: 'Test Content Item 2.1',
        content_2: 'Test Content Item 2.2',
        content_3: 'Test Content Item 2.3',
        button: 'Test Button 2',
      },
    },
  };

  beforeEach(async () => {
    const testVars = await setUpTranslocoTestBed(TestHostComponent, { translations: compilations }, { providers: [provideRouter([])] });
    hostFixture = testVars.fixture;
    hostComponent = testVars.component;
  });

  it('should create', () => {
    expect(hostComponent).toBeTruthy();
  });

  it('should render two cards', () => {
    const cards = hostFixture.nativeElement.querySelectorAll('mat-card');
    expect(cards).toHaveLength(2);
  });

  it('should render card titles correctly', () => {
    const cardTitles = hostFixture.nativeElement.querySelectorAll('mat-card-title');
    expect(cardTitles).toHaveLength(2);
    expect(cardTitles[0].textContent).toContain('Test Card 1');
    expect(cardTitles[1].textContent).toContain('Test Card 2');
  });

  it('should render translated subtitles correctly', () => {
    const cardSubtitles = hostFixture.nativeElement.querySelectorAll('mat-card-subtitle');
    expect(cardSubtitles).toHaveLength(2);
    expect(cardSubtitles[0].textContent).toContain('Test Subtitle 1');
    expect(cardSubtitles[1].textContent).toContain('Test Subtitle 2');
  });

  it('should render translated content correctly', () => {
    const cardContents = hostFixture.nativeElement.querySelectorAll('mat-card-content');
    expect(cardContents).toHaveLength(2);
    expect(cardContents[0].textContent).toContain('Test Content 1');
    expect(cardContents[0].textContent).toContain('Test Content Item 1.1');
    expect(cardContents[0].textContent).toContain('Test Content Item 1.2');
    expect(cardContents[1].textContent).toContain('Test Content 2');
    expect(cardContents[1].textContent).toContain('Test Content Item 2.1');
    expect(cardContents[1].textContent).toContain('Test Content Item 2.2');
    expect(cardContents[1].textContent).toContain('Test Content Item 2.3');
  });

  it('should render card actions', () => {
    const cardActions = hostFixture.nativeElement.querySelectorAll('mat-card-actions');
    expect(cardActions).toHaveLength(2);
  });

  it('should render translated buttons correctly', () => {
    const buttons = hostFixture.nativeElement.querySelectorAll('button');
    expect(buttons).toHaveLength(2);
    expect(buttons[0].textContent).toContain('Test Button 1');
    //    expect(buttons[0].getAttribute('ng-reflect-router-link')).toBe('/test1');
    expect(buttons[1].textContent).toContain('Test Button 2');
    //    expect(buttons[1].getAttribute('ng-reflect-router-link')).toBe('/test2');
  });

});

// Separate host so the icon card is present on first render, exercising the @if (card.icon) branch.
@Component({
  template: ` <mat-cards-grid [cards]="cards"></mat-cards-grid>`,
  standalone: true,
  imports: [MatCardsGridComponent],
})
class IconHostComponent {
  cards: CardsGridSpec[] = [{ icon: 'settings', title: 'Iconic Card', subtitle: 'subtitle', content: ['content'], actions: [], translocoPrefix: 'test1' }];
}

describe('MatCardsGridComponent with an icon card', () => {
  it('should render the card icon button when a card provides an icon', async () => {
    const { fixture } = await setUpTranslocoTestBed(
      IconHostComponent,
      { translations: { en: { test1: { subtitle: 'Test Subtitle 1', content: 'Test Content 1' } } } },
      { providers: [provideRouter([])] },
    );

    const icon = fixture.nativeElement.querySelector('.icon-large') as HTMLElement;
    expect(icon).toBeTruthy();
    expect(icon.textContent).toContain('settings');
  });
});

// Host with a card that declares menu items, exercising the @if (card.menuItems) branch.
@Component({
  template: ` <mat-cards-grid [cards]="cards"></mat-cards-grid>`,
  standalone: true,
  imports: [MatCardsGridComponent],
})
class MenuHostComponent {
  cards: CardsGridSpec[] = [
    {
      title: 'Menu Card',
      subtitle: 'subtitle',
      content: ['content'],
      actions: [],
      menuItems: [
        { icon: 'open_in_new', label: 'menu_item_1', link: '/test1' },
        { label: 'menu_item_2', link: '/test2' },
      ],
      translocoPrefix: 'test1',
    },
  ];
}

describe('MatCardsGridComponent with a menu card', () => {
  it('should render a more_vert trigger and the translated menu items', async () => {
    const { fixture } = await setUpTranslocoTestBed(
      MenuHostComponent,
      { translations: { en: { test1: { subtitle: 'Test Subtitle 1', content: 'Test Content 1', menu_item_1: 'First Item', menu_item_2: 'Second Item' } } } },
      { providers: [provideRouter([])] },
    );

    const trigger = fixture.nativeElement.querySelector('button[aria-label="Card menu"]') as HTMLButtonElement;
    expect(trigger).toBeTruthy();
    expect(trigger.textContent).toContain('more_vert');

    trigger.click();
    fixture.detectChanges();

    const menuItems = document.querySelectorAll('.mat-mdc-menu-item');
    expect(menuItems).toHaveLength(2);
    expect(menuItems[0].textContent).toContain('First Item');
    expect(menuItems[1].textContent).toContain('Second Item');
  });
});
