import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { TranslocoTestingModule } from '@jsverse/transloco';
import en from '../../assets/i18n/base_app/en.json';
import { APP_CONCEPTS, AppConceptsGuideComponent, appConceptAnchor } from './app-concepts-guide.component';

describe('AppConceptsGuideComponent', () => {
  async function setup() {
    await TestBed.configureTestingModule({
      // Nested under the scope name, so the guide's full `base_app.concepts.*` keys resolve as in the app.
      imports: [AppConceptsGuideComponent, TranslocoTestingModule.forRoot({ langs: { en: { base_app: en } }, translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }, preloadLangs: true })],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppConceptsGuideComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders one anchored section per concept, in assembly order', async () => {
    const element = await setup();

    const ids = Array.from(element.querySelectorAll('section')).map((section) => section.id);
    expect(ids).toEqual(APP_CONCEPTS.map(appConceptAnchor));
  });

  it('has a translated title and body for every concept', () => {
    for (const concept of APP_CONCEPTS) {
      expect(en.concepts[concept].title, concept).toBeTruthy();
      expect(en.concepts[concept].body, concept).toBeTruthy();
    }
  });

  it('keeps the inline markup of the bodies', async () => {
    const element = await setup();

    expect(element.querySelector(`#${appConceptAnchor('routes')} code`)?.textContent).toBe('claims');
  });
});
