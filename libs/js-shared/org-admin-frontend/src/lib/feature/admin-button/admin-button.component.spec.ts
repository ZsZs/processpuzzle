import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { ORG_ADMIN_ORG_KEY } from '../../domain/organization-user.service';
import { AdminButtonComponent } from './admin-button.component';

describe('AdminButtonComponent', () => {
  function render(orgKey?: string): HTMLElement {
    TestBed.configureTestingModule({
      imports: [AdminButtonComponent],
      providers: [provideRouter([]), ...(orgKey ? [{ provide: ORG_ADMIN_ORG_KEY, useValue: orgKey }] : [])],
    });
    const fixture = TestBed.createComponent(AdminButtonComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('links to the administration of the organization', () => {
    const link = render('acme').querySelector('a');

    expect(link?.getAttribute('href')).toBe('/acme/admin');
    expect(link?.getAttribute('aria-label')).toBe('Administration Button');
  });

  it('draws nothing where the application has no organization', () => {
    expect(render().querySelector('a')).toBeNull();
  });
});
