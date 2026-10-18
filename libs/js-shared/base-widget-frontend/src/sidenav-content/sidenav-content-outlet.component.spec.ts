import { ComponentPortal } from '@angular/cdk/portal';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it } from 'vitest';
import widgetsEn from '../assets/i18n/widgets/en.json';
import { SidenavContentOutletComponent } from './sidenav-content-outlet.component';
import { SidenavContentService } from './sidenav-content.service';

@Component({ selector: 'pp-test-book-toc', template: '<p data-testid="book-toc">Table of contents</p>' })
class BookTocComponent {}

@Component({
  imports: [SidenavContentOutletComponent],
  template: `<pp-sidenav-content-outlet><nav data-testid="host-menu">Menu</nav></pp-sidenav-content-outlet>`,
})
class ShellComponent {}

describe('SidenavContentOutletComponent', () => {
  let fixture: ComponentFixture<ShellComponent>;
  let service: SidenavContentService;

  const byTestId = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ShellComponent,
        TranslocoTestingModule.forRoot({ langs: { en: {}, 'widgets/en': widgetsEn }, translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }, preloadLangs: true }),
      ],
    }).compileComponents();
    service = TestBed.inject(SidenavContentService);
    fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders only the host menu while nothing is attached', () => {
    expect(service.hasOutlet()).toBe(true);
    expect(byTestId('host-menu')).not.toBeNull();
    expect(byTestId('sidenav-content-toggle')).toBeNull();
  });

  it('replaces the menu with the attached content and offers the way back', async () => {
    service.attach(new ComponentPortal(BookTocComponent), 'Handbook');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(byTestId('book-toc')).not.toBeNull();
    expect(byTestId('host-menu')).toBeNull();
    expect(byTestId('sidenav-content-title')?.textContent).toContain('Handbook');
    expect(byTestId('sidenav-content-toggle')?.getAttribute('aria-label')).toBe('Show the menu');

    byTestId('sidenav-content-toggle')?.click();
    fixture.detectChanges();

    expect(byTestId('host-menu')).not.toBeNull();
    expect(byTestId('book-toc')).toBeNull();
    expect(byTestId('sidenav-content-toggle')?.getAttribute('aria-label')).toBe('Show the contents');
  });

  it('falls back to the menu when the content is detached', () => {
    const detach = service.attach(new ComponentPortal(BookTocComponent), 'Handbook');
    fixture.detectChanges();

    detach();
    fixture.detectChanges();

    expect(byTestId('host-menu')).not.toBeNull();
    expect(byTestId('sidenav-content-toggle')).toBeNull();
  });
});
