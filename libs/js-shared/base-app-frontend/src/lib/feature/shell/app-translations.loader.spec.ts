import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { CardsGridSpec, MatCardsGridComponent } from '@processpuzzle/widgets';
import { firstValueFrom, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppTranslationService } from '../../domain/app-translation.service';
import { AppTranslationsLoader } from './app-translations.loader';

// What a starter's home page is: a cards grid whose texts are keys under a prefix, none of them shipped by the platform.
@Component({
  template: `<pp-cards-grid [cards]="cards"></pp-cards-grid>`,
  standalone: true,
  imports: [MatCardsGridComponent],
})
class HomeHostComponent {
  cards: CardsGridSpec[] = [{ title: 'plan.title', subtitle: '', content: ['plan.text'], actions: [], translocoPrefix: 'home.cards' }];
}

describe('AppTranslationsLoader', () => {
  const service = { bundle: vi.fn() };
  const stored: Record<string, Record<string, string>> = {
    en: { 'home.cards.plan.title': 'Plan Race', 'home.cards.plan.text': 'Regattas and courses' },
    de: { 'home.cards.plan.title': 'Rennen planen', 'home.cards.plan.text': 'Regatten und Kurse' },
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.resetAllMocks();
    service.bundle.mockImplementation((_appId: string, locale: string) => of(stored[locale] ?? {}));
  });

  /** The application as the runtime builds it: the texts are merged when the routes are built, before a page renders. */
  function configure(shipped: Record<string, Record<string, string>> = { en: {}, de: {} }) {
    TestBed.configureTestingModule({
      imports: [
        HomeHostComponent,
        // As in production, a rendered widget follows the language the user switches to.
        TranslocoTestingModule.forRoot({
          langs: shipped,
          translocoConfig: { availableLangs: ['en', 'de'], defaultLang: 'en', reRenderOnLangChange: true },
          preloadLangs: true,
        }),
      ],
      providers: [provideRouter([]), { provide: AppTranslationService, useValue: service }],
    });
    return TestBed.inject(AppTranslationsLoader);
  }

  function render() {
    const fixture = TestBed.createComponent(HomeHostComponent);
    fixture.detectChanges();
    return fixture;
  }

  const title = (fixture: { nativeElement: HTMLElement }) => fixture.nativeElement.querySelector('mat-card-title')?.textContent?.trim();
  const text = (fixture: { nativeElement: HTMLElement }) => fixture.nativeElement.querySelector('mat-card-content')?.textContent?.trim();

  it('shows the keys themselves while nothing is merged', () => {
    configure();

    expect(title(render())).toBe('home.cards.plan.title');
  });

  it('merges the stored texts of the language in use, so the widgets resolve them under their prefix', async () => {
    const loader = configure();

    await loader.activate('app', 'released');
    const fixture = render();

    expect(service.bundle).toHaveBeenCalledWith('app', 'en', 'released');
    expect(title(fixture)).toBe('Plan Race');
    expect(text(fixture)).toContain('Regattas and courses');
  });

  it('reads the draft in the Preview', async () => {
    await configure().activate('app', 'draft');

    expect(service.bundle).toHaveBeenCalledWith('app', 'en', 'draft');
  });

  it('shows the texts of another language once the user switches to it', async () => {
    const loader = configure();
    await loader.activate('app', 'released');
    const fixture = render();
    expect(title(fixture)).toBe('Plan Race');
    // The other languages are merged in the background; the switch finds them there.
    const transloco = TestBed.inject(TranslocoService);
    await vi.waitFor(() => expect(transloco.getTranslation('de')['home.cards.plan.title']).toBe('Rennen planen'));

    transloco.setActiveLang('de');
    await firstValueFrom(transloco.load('de'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(title(fixture)).toBe('Rennen planen');
    expect(text(fixture)).toContain('Regatten und Kurse');
  });

  it('merges over the shipped translations of a language and keeps them', async () => {
    const loader = configure({ en: { 'shipped.key': 'Shipped', 'home.cards.plan.title': 'Shipped title' }, de: {} });

    await loader.activate('app', 'released');

    const transloco = TestBed.inject(TranslocoService);
    expect(transloco.translate('shipped.key')).toBe('Shipped');
    expect(transloco.translate('home.cards.plan.title')).toBe('Plan Race');
    expect(title(render())).toBe('Plan Race');
  });

  it('leaves the keys as text when the stored texts cannot be read', async () => {
    service.bundle.mockReturnValue(throwError(() => new Error('403')));
    const loader = configure();

    await expect(loader.activate('app', 'released')).resolves.toBeUndefined();

    expect(title(render())).toBe('home.cards.plan.title');
  });

  it('adds nothing for a language with nothing stored', async () => {
    const loader = configure({ en: { 'shipped.key': 'Shipped' }, de: {} });
    const setTranslation = vi.spyOn(TestBed.inject(TranslocoService), 'setTranslation');
    service.bundle.mockReturnValue(of({}));

    await loader.activate('app', 'released');

    expect(setTranslation).not.toHaveBeenCalled();
  });
});
