import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CardsGridSpec } from './cards-spec';
import { MatCardsGridComponent } from './mat-cards-grid.component';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Component } from '@angular/core';
import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '@processpuzzle/test-util';

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
    await TestBed.configureTestingModule({
      imports: [TestHostComponent, NoopAnimationsModule, getTranslocoTestingModule(compilations)],
      providers: [provideRouter([])],
    }).compileComponents();

    hostFixture = TestBed.createComponent(TestHostComponent);
    hostComponent = hostFixture.componentInstance;
    hostFixture.detectChanges();
  });

  it('should create', () => {
    expect(hostComponent).toBeTruthy();
  });

  it('should render two cards', () => {
    const cards = hostFixture.nativeElement.querySelectorAll('mat-card');
    expect(cards.length).toBe(2);
  });

  it('should render card titles correctly', () => {
    const cardTitles = hostFixture.nativeElement.querySelectorAll('mat-card-title');
    expect(cardTitles.length).toBe(2);
    expect(cardTitles[0].textContent).toContain('Test Card 1');
    expect(cardTitles[1].textContent).toContain('Test Card 2');
  });

  it('should render translated subtitles correctly', () => {
    const cardSubtitles = hostFixture.nativeElement.querySelectorAll('mat-card-subtitle');
    expect(cardSubtitles.length).toBe(2);
    expect(cardSubtitles[0].textContent).toContain('Test Subtitle 1');
    expect(cardSubtitles[1].textContent).toContain('Test Subtitle 2');
  });

  it('should render translated content correctly', () => {
    const cardContents = hostFixture.nativeElement.querySelectorAll('mat-card-content');
    expect(cardContents.length).toBe(2);
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
    expect(cardActions.length).toBe(2);
  });

  it('should render translated buttons correctly', () => {
    const buttons = hostFixture.nativeElement.querySelectorAll('button');
    expect(buttons.length).toBe(2);
    expect(buttons[0].textContent).toContain('Test Button 1');
    expect(buttons[0].getAttribute('ng-reflect-router-link')).toBe('/test1');
    expect(buttons[1].textContent).toContain('Test Button 2');
    expect(buttons[1].getAttribute('ng-reflect-router-link')).toBe('/test2');
  });
});
