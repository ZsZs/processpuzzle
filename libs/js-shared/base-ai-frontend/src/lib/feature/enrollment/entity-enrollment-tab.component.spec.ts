import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { NEVER, of, throwError } from 'rxjs';
import { afterEach, assert, beforeEach, describe, expect, it, vi } from 'vitest';
import en from '../../../assets/i18n/base_ai/en.json';
import { Enrollment, EnrollmentPhoto } from '../../domain/enrollment/enrollment';
import { EnrollmentService } from '../../domain/enrollment/enrollment.service';
import { RecognitionProfile } from '../../domain/profile/recognition-profile';
import { ProfiledEntityRegistry } from '../../domain/profile/profiled-entity.registry';
import { ENROLLMENT_MAX_POLLS, ENROLLMENT_POLL_MS, EntityEnrollmentTabComponent } from './entity-enrollment-tab.component';

const PROFILE = new RecognitionProfile({ entityName: 'boat', detectorClass: 'boat', galleryAttributeKey: 'photos', identifierAttributeKey: 'sailNumber' });

function enrollment(status: Enrollment['status'], photos: Enrollment['photos']): Enrollment {
  return { entityName: 'boat', objectId: 'o-1', status, identifierText: 'CAN 603', photos };
}

function photo(photoId: string, status: EnrollmentPhoto['status'], extra: Partial<EnrollmentPhoto> = {}): EnrollmentPhoto {
  return { photoId, photoRef: `ref-${photoId}`, status, identifierMismatch: false, addedAt: '', ...extra };
}

describe('EntityEnrollmentTabComponent', () => {
  const find = vi.fn<EnrollmentService['find']>();
  const profileFor = vi.fn();
  const synchronize = vi.fn<EnrollmentService['synchronize']>();
  const deleteAll = vi.fn<EnrollmentService['deleteAll']>();
  const descriptor = signal<BaseEntityDescriptor | undefined>(undefined);
  let fixture: ComponentFixture<EntityEnrollmentTabComponent>;

  async function render(withOutlet = true): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [
        EntityEnrollmentTabComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: { base_ai: en } },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [
        { provide: ProfiledEntityRegistry, useValue: { profileFor } },
        { provide: EnrollmentService, useValue: { find, synchronize, deleteAll } },
        ...(withOutlet ? [{ provide: ROUTER_OUTLET_DATA, useValue: descriptor }] : []),
      ],
    });
    fixture = TestBed.createComponent(EntityEnrollmentTabComponent);
    fixture.componentRef.setInput('entityId', 'o-1');
    fixture.detectChanges();
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).not.toContain(en.entity_enrollment.loading);
    });
    return fixture.nativeElement as HTMLElement;
  }

  function button(element: HTMLElement, testId: string): HTMLButtonElement | null {
    return element.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`);
  }

  function click(element: HTMLElement, selector: string): void {
    const target = element.querySelector<HTMLButtonElement>(selector);
    assert(target, `Button ${selector} should be rendered`);
    target.click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    find.mockReset();
    profileFor.mockReset();
    synchronize.mockReset().mockReturnValue(of(enrollment('READY', [])));
    deleteAll.mockReset().mockReturnValue(of(undefined));
    profileFor.mockResolvedValue(PROFILE);
    find.mockReturnValue(of(enrollment('NOT_ENROLLED', [])));
    descriptor.set(new BaseEntityDescriptor({ entityName: 'Boat', attrDescriptors: [] }));
  });

  afterEach(() => {
    fixture?.destroy();
    vi.useRealTimers();
  });

  it('says so when the entity type has no recognition profile', async () => {
    profileFor.mockResolvedValue(undefined);
    const element = await render();
    expect(element.textContent).toContain(en.entity_enrollment.noProfile);
    expect(find).not.toHaveBeenCalled();
    expect(button(element, 'synchronize')).toBeNull();
  });

  it('names the detector class and the photos attribute in its introduction', async () => {
    const element = await render();
    expect(element.textContent).toContain("This boat's photos are taken from its 'photos' attribute");
  });

  it('shows each photo with what was read on it, and flags a mismatch', async () => {
    find.mockReturnValue(
      of(
        enrollment('READY', [
          photo('p-1', 'ENROLLED', { observedIdentifierText: 'CAN603' }),
          photo('p-2', 'ENROLLED', { observedIdentifierText: 'USA 7', identifierMismatch: true }),
        ]),
      ),
    );
    const element = await render();

    expect(element.querySelector('[data-testid="registered-identifier"]')?.textContent).toContain('CAN 603');
    expect(element.querySelectorAll('[data-testid="enrollment-photo"]')).toHaveLength(2);
    expect(Array.from(element.querySelectorAll('[data-testid="observed-identifier"]')).map((e) => e.textContent)).toEqual(['CAN603', 'USA 7']);
    expect(element.querySelectorAll('[role="alert"]')).toHaveLength(1);
  });

  it('has no per-photo controls and no upload: the photos are edited on the subject', async () => {
    find.mockReturnValue(of(enrollment('READY', [photo('p-1', 'ENROLLED')])));
    const element = await render();
    expect(element.querySelectorAll('[data-testid="enrollment-photo"] button')).toHaveLength(0);
    expect(element.querySelector('input[type="file"]')).toBeNull();
  });

  it('re-reads the gallery while photos are being processed', async () => {
    vi.useFakeTimers();
    find.mockReturnValueOnce(of(enrollment('PROCESSING', [photo('p-1', 'PENDING')])));
    find.mockReturnValue(of(enrollment('READY', [photo('p-1', 'ENROLLED')])));
    await render();

    await vi.advanceTimersByTimeAsync(ENROLLMENT_POLL_MS);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="enrollment-status"]')?.getAttribute('data-status')).toBe('READY');
    expect(find).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(ENROLLMENT_POLL_MS * 2);
    expect(find).toHaveBeenCalledTimes(2);
  });

  it('shows an empty gallery when there is no enrollment, with nothing to rebuild', async () => {
    find.mockReturnValue(of(undefined));
    const element = await render();
    expect(element.textContent).toContain(en.entity_enrollment.empty);
    expect(element.querySelector('[data-testid="registered-identifier"]')?.textContent).toBe(en.entity_enrollment.noIdentifier);
    expect(element.querySelector('[data-testid="enrollment-status"]')?.getAttribute('data-status')).toBe('NOT_ENROLLED');
    expect(button(element, 'synchronize')).not.toBeNull();
    expect(button(element, 'rebuild')).toBeNull();
  });

  it('shows an empty gallery when the read fails', async () => {
    find.mockReturnValue(throwError(() => new Error('down')));
    const element = await render();
    expect(element.textContent).toContain(en.entity_enrollment.empty);
  });

  it('works without router outlet data and does not load an enrollment', async () => {
    profileFor.mockResolvedValue(undefined);
    await render(false);
    expect(profileFor).toHaveBeenCalledWith(undefined);
    expect(find).not.toHaveBeenCalled();
  });

  it('reloads for a different subject and cancels the previous polling timer', async () => {
    vi.useFakeTimers();
    find.mockReturnValueOnce(of(enrollment('PROCESSING', []))).mockReturnValue(of(enrollment('READY', [])));
    await render();
    fixture.componentRef.setInput('entityId', 'o-2');
    fixture.detectChanges();
    await vi.waitFor(() => expect(find).toHaveBeenLastCalledWith('boat', 'o-2'));
    await vi.advanceTimersByTimeAsync(ENROLLMENT_POLL_MS * 2);
    expect(find).toHaveBeenCalledTimes(2);
  });

  it('cancels polling when the tab is destroyed', async () => {
    vi.useFakeTimers();
    find.mockReturnValue(of(enrollment('PROCESSING', [])));
    await render();
    fixture.destroy();
    await vi.advanceTimersByTimeAsync(ENROLLMENT_POLL_MS * 2);
    expect(find).toHaveBeenCalledTimes(1);
  });

  it('synchronizes the subject and shows the answered gallery', async () => {
    synchronize.mockReturnValue(of(enrollment('READY', [photo('p-1', 'ENROLLED'), photo('p-2', 'ENROLLED')])));
    const element = await render();
    click(element, '[data-testid="synchronize"]');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(element.querySelectorAll('[data-testid="enrollment-photo"]')).toHaveLength(2);
    });
    expect(synchronize).toHaveBeenCalledWith('boat', 'o-1');
    expect(deleteAll).not.toHaveBeenCalled();
    expect(find).toHaveBeenCalledTimes(1);
  });

  it('disables the actions while synchronizing', async () => {
    find.mockReturnValue(of(enrollment('READY', [photo('p-1', 'ENROLLED')])));
    synchronize.mockReturnValue(NEVER);
    const element = await render();
    click(element, '[data-testid="synchronize"]');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(button(element, 'synchronize')?.disabled).toBe(true);
    });
    expect(button(element, 'rebuild')?.disabled).toBe(true);
  });

  it('stops polling at the limit and restarts after synchronizing', async () => {
    vi.useFakeTimers();
    find.mockReturnValue(of(enrollment('PROCESSING', [])));
    synchronize.mockReturnValue(of(enrollment('PROCESSING', [])));
    const element = await render();
    await vi.advanceTimersByTimeAsync(ENROLLMENT_POLL_MS * (ENROLLMENT_MAX_POLLS + 1));
    expect(find).toHaveBeenCalledTimes(ENROLLMENT_MAX_POLLS + 1);

    click(element, '[data-testid="synchronize"]');
    await vi.advanceTimersByTimeAsync(ENROLLMENT_POLL_MS);
    expect(find).toHaveBeenCalledTimes(ENROLLMENT_MAX_POLLS + 2);
  });

  it('requires confirmation to rebuild and allows cancelling', async () => {
    find.mockReturnValue(of(enrollment('READY', [photo('p-1', 'ENROLLED')])));
    synchronize.mockReturnValue(of(enrollment('PROCESSING', [photo('p-1', 'PENDING')])));
    const element = await render();

    click(element, '[data-testid="rebuild"]');
    expect(button(element, 'rebuild')).toBeNull();
    click(element, '[data-testid="confirm-rebuild"] + button');
    expect(button(element, 'confirm-rebuild')).toBeNull();
    expect(deleteAll).not.toHaveBeenCalled();

    click(element, '[data-testid="rebuild"]');
    click(element, '[data-testid="confirm-rebuild"]');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(element.querySelector('[data-testid="enrollment-status"]')?.getAttribute('data-status')).toBe('PROCESSING');
    });
    expect(deleteAll).toHaveBeenCalledWith('boat', 'o-1');
    expect(synchronize).toHaveBeenCalledWith('boat', 'o-1');
    expect(deleteAll.mock.invocationCallOrder[0]).toBeLessThan(synchronize.mock.invocationCallOrder[0]);
    expect(button(element, 'confirm-rebuild')).toBeNull();
  });

  it('does not synchronize when discarding the gallery fails', async () => {
    find.mockReturnValue(of(enrollment('READY', [photo('p-1', 'ENROLLED')])));
    deleteAll.mockReturnValue(throwError(() => ({ error: { errorText: 'Deletion failed' } })));
    const element = await render();
    click(element, '[data-testid="rebuild"]');
    click(element, '[data-testid="confirm-rebuild"]');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(element.querySelector('.pp-enrollment__error')?.textContent).toBe('Deletion failed');
    });
    expect(synchronize).not.toHaveBeenCalled();
    expect(element.querySelectorAll('[data-testid="enrollment-photo"]')).toHaveLength(1);
  });

  it.each([
    [{ error: { errorText: 'Backend error' } }, 'Backend error'],
    [new Error('Network failed'), 'Network failed'],
    ['Rejected', 'Rejected'],
  ])('displays a synchronization failure and clears it on retry (%j)', async (error, message) => {
    synchronize.mockReturnValueOnce(throwError(() => error)).mockReturnValueOnce(of(enrollment('READY', [])));
    const element = await render();
    click(element, '[data-testid="synchronize"]');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(element.querySelector('[role="alert"]')?.textContent).toBe(message);
    });
    expect(button(element, 'synchronize')?.disabled).toBe(false);

    click(element, '[data-testid="synchronize"]');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(element.querySelector('[role="alert"]')).toBeNull();
    });
    expect(synchronize).toHaveBeenCalledTimes(2);
  });

  it('renders crop URLs, missing readings and failure reasons', async () => {
    find.mockReturnValue(
      of(
        enrollment('PROCESSING', [
          photo('p-1', 'ENROLLED', { photoUrl: 'original.jpg', cropUrl: 'crop.jpg' }),
          photo('p-2', 'FAILED', { photoUrl: 'failed.jpg', failureReason: 'Unreadable photo' }),
          photo('p-3', 'PENDING', { photoUrl: 'pending.jpg' }),
        ]),
      ),
    );
    const element = await render();
    expect(Array.from(element.querySelectorAll('img')).map((image) => image.getAttribute('src'))).toEqual(['crop.jpg', 'failed.jpg', 'pending.jpg']);
    expect(element.textContent).toContain(en.entity_enrollment.noReading);
    expect(element.textContent).toContain('Unreadable photo');
  });

  it('does not request an identifier reading for appearance-only profiles', async () => {
    profileFor.mockResolvedValue(new RecognitionProfile({ entityName: 'boat', detectorClass: 'boat', galleryAttributeKey: 'photos' }));
    find.mockReturnValue(of(enrollment('READY', [photo('p-1', 'ENROLLED')])));
    const element = await render();
    expect(element.textContent).not.toContain(en.entity_enrollment.noReading);
  });
});
