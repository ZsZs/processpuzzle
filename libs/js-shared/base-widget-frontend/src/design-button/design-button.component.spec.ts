import { describe, expect, it } from 'vitest';
import { Component, Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DefaultUrlSerializer, provideRouter, Router, UrlSerializer, UrlTree } from '@angular/router';
import { DesignButtonComponent } from './design-button.component';

@Component({ template: '' })
class BlankComponent {}

/** Decorates every URL the way `LocaleUrlSerializer` does, without depending on transloco. */
@Injectable()
class PrefixingUrlSerializer extends DefaultUrlSerializer {
  override parse(url: string): UrlTree {
    return super.parse(url.replace(/^\/en(?=\/|$)/, '') || '/');
  }

  override serialize(tree: UrlTree): string {
    return `/en${super.serialize(tree)}`;
  }
}

describe('DesignButtonComponent', () => {
  async function renderAt(url: string) {
    TestBed.configureTestingModule({
      imports: [DesignButtonComponent],
      providers: [
        provideRouter([
          { path: 'home', component: BlankComponent },
          { path: 'design', component: BlankComponent },
        ]),
        { provide: UrlSerializer, useClass: PrefixingUrlSerializer },
      ],
    });
    await TestBed.inject(Router).navigateByUrl(url);
    const fixture = TestBed.createComponent(DesignButtonComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('offers the way into the designer outside it', async () => {
    const element = await renderAt('/en/home');

    expect(element.querySelector('mat-icon')?.textContent?.trim()).toBe('design_services');
    expect(element.querySelector('a, button')?.getAttribute('aria-label')).toBe('Design Button');
  });

  it('offers the way home inside the designer when the URL carries a language prefix', async () => {
    const element = await renderAt('/en/design');

    expect(element.querySelector('mat-icon')?.textContent?.trim()).toBe('home');
    expect(element.querySelector('button')?.getAttribute('aria-label')).toBe('Home Button');
  });
});
