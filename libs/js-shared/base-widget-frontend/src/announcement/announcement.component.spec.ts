import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AnnouncementComponent } from './announcement.component';

function render(id: string, extra: Partial<Record<'linkLabel' | 'link' | 'tone', string>> = {}) {
  const fixture = TestBed.createComponent(AnnouncementComponent);
  fixture.componentRef.setInput('announcementId', id);
  fixture.componentRef.setInput('message', 'Tutorials are available.');
  for (const [name, value] of Object.entries(extra)) fixture.componentRef.setInput(name, value);
  fixture.detectChanges();
  return fixture;
}

const bar = (fixture: { nativeElement: HTMLElement }) => fixture.nativeElement.querySelector('[data-testid="announcement"]');

describe('AnnouncementComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AnnouncementComponent],
      providers: [provideRouter([]), provideTranslocoTesting({ translations: {} })],
    });
  });

  afterEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('shows the message and the link when both are given', () => {
    const fixture = render('tutorial-entity', { linkLabel: 'Watch', link: '/design/tutorials/entity-designer' });

    expect(bar(fixture)?.textContent).toContain('Tutorials are available.');
    expect(bar(fixture)?.querySelector('a')?.textContent).toContain('Watch');
  });

  it('shows no link without a label', () => {
    const fixture = render('tutorial-entity', { link: '/design/tutorials/entity-designer' });

    expect(bar(fixture)?.querySelector('a')).toBeNull();
  });

  it('Hide removes the bar for this session only', () => {
    const fixture = render('maintenance');
    fixture.nativeElement.querySelector('[data-testid="announcement-hide"]').click();
    fixture.detectChanges();

    expect(bar(fixture)).toBeNull();
    expect(sessionStorage.getItem('pp.announcement.hidden.maintenance')).toBe('true');
    expect(localStorage.getItem('pp.announcement.dismissed.maintenance')).toBeNull();
  });

  it("Don't show again removes the bar and remembers it in this browser", () => {
    const fixture = render('maintenance');
    fixture.nativeElement.querySelector('[data-testid="announcement-dont-show-again"]').click();
    fixture.detectChanges();

    expect(bar(fixture)).toBeNull();
    expect(localStorage.getItem('pp.announcement.dismissed.maintenance')).toBe('true');
  });

  it('stays hidden for an announcement dismissed earlier', () => {
    localStorage.setItem('pp.announcement.dismissed.maintenance', 'true');

    expect(bar(render('maintenance'))).toBeNull();
  });

  it('shows a new announcement id even after another was dismissed', () => {
    localStorage.setItem('pp.announcement.dismissed.maintenance', 'true');

    expect(bar(render('maintenance-2'))).not.toBeNull();
  });
});
