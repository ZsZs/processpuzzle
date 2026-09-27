import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ArtifactAttr, ObjectStoreService } from '@processpuzzle/base-entity';
import { Observable, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { APPLICATION_CONTEXT } from '../app-context/application-context';
import { testApplicationContext } from '../app-context/test-application-context';
import { AppLogoComponent } from './app-logo.component';

const LOGO: ArtifactAttr = { bucket: 'branding', objectId: 'logo-1', name: 'logo.png', mimeType: 'image/png' };

type UriLookup = (bucket: string, objectId: string) => Observable<{ uri: string }>;

describe('AppLogoComponent', () => {
  function render(props: { logo?: ArtifactAttr; link?: string } = {}, getObjectUriByID = vi.fn<UriLookup>(() => of({ uri: 'https://store/logo.png' }))) {
    TestBed.configureTestingModule({
      imports: [AppLogoComponent],
      providers: [
        provideRouter([]),
        { provide: APPLICATION_CONTEXT, useValue: testApplicationContext({ name: 'Demo', logoUrl: '/demo-logo.svg' }) },
        { provide: ObjectStoreService, useValue: { getObjectUriByID } },
      ],
    });
    const fixture = TestBed.createComponent(AppLogoComponent);
    for (const [name, value] of Object.entries(props)) fixture.componentRef.setInput(name, value);
    fixture.detectChanges();
    return { fixture, getObjectUriByID };
  }

  const image = (fixture: ReturnType<typeof render>['fixture']): HTMLImageElement | null => fixture.nativeElement.querySelector('img.pp-app-logo');

  it('shows the logo of the application by default, named after it', () => {
    const { fixture } = render();

    expect(image(fixture)?.getAttribute('src')).toBe('/demo-logo.svg');
    expect(image(fixture)?.getAttribute('alt')).toBe('Demo');
  });

  it('resolves an artifact logo through the object store', () => {
    const { fixture, getObjectUriByID } = render({ logo: LOGO });

    expect(getObjectUriByID).toHaveBeenCalledWith('branding', 'logo-1');
    expect(image(fixture)?.getAttribute('src')).toBe('https://store/logo.png');
  });

  it('renders nothing for an artifact whose URL cannot be resolved', () => {
    const { fixture } = render(
      { logo: LOGO },
      vi.fn<UriLookup>(() => throwError(() => new Error('gone'))),
    );

    expect(image(fixture)).toBeNull();
  });

  it('links home by default', () => {
    expect(render().fixture.nativeElement.querySelector('a.pp-app-logo__link')).not.toBeNull();
  });

  it('is only an image with an empty link', () => {
    expect(render({ link: '' }).fixture.nativeElement.querySelector('a.pp-app-logo__link')).toBeNull();
  });
});
