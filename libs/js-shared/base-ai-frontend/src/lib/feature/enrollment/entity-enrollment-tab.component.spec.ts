import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { afterEach, assert, beforeEach, describe, expect, it, vi } from 'vitest';
import en from '../../../assets/i18n/base_ai/en.json';
import { Enrollment } from '../../domain/enrollment/enrollment';
import { EnrollmentService } from '../../domain/enrollment/enrollment.service';
import { RecognitionProfile } from '../../domain/profile/recognition-profile';
import { ProfiledEntityRegistry } from '../../domain/profile/profiled-entity.registry';
import { ENROLLMENT_MAX_POLLS, ENROLLMENT_POLL_MS, EntityEnrollmentTabComponent } from './entity-enrollment-tab.component';

const PROFILE = new RecognitionProfile({ entityName: 'boat', detectorClass: 'boat', identifierAttributeKey: 'sailNumber' });

function enrollment(status: Enrollment['status'], photos: Enrollment['photos']): Enrollment {
  return { entityName: 'boat', objectId: 'o-1', status, identifierText: 'CAN 603', photos };
}

describe('EntityEnrollmentTabComponent', () => {
  const find = vi.fn();
  const profileFor = vi.fn();
  const addPhotos = vi.fn<EnrollmentService['addPhotos']>();
  const deletePhoto = vi.fn<EnrollmentService['deletePhoto']>();
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
        { provide: EnrollmentService, useValue: { find, addPhotos, deletePhoto, deleteAll } },
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

  beforeEach(() => {
    find.mockReset();
    profileFor.mockReset();
    addPhotos.mockReset();
    deletePhoto.mockReset().mockReturnValue(of(undefined));
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
  });

  it('shows each photo with what was read on it, and flags a mismatch', async () => {
    profileFor.mockResolvedValue(PROFILE);
    find.mockReturnValue(
      of(
        enrollment('READY', [
          { photoId: 'p-1', status: 'ENROLLED', observedIdentifierText: 'CAN603', identifierMismatch: false, addedAt: '' },
          { photoId: 'p-2', status: 'ENROLLED', observedIdentifierText: 'USA 7', identifierMismatch: true, addedAt: '' },
        ]),
      ),
    );
    const element = await render();

    expect(element.querySelector('[data-testid="registered-identifier"]')?.textContent).toContain('CAN 603');
    expect(element.querySelectorAll('[data-testid="enrollment-photo"]')).toHaveLength(2);
    expect(Array.from(element.querySelectorAll('[data-testid="observed-identifier"]')).map((e) => e.textContent)).toEqual(['CAN603', 'USA 7']);
    expect(element.querySelectorAll('[role="alert"]')).toHaveLength(1);
  });

  it('re-reads the gallery while photos are being processed', async () => {
    vi.useFakeTimers();
    profileFor.mockResolvedValue(PROFILE);
    find.mockReturnValueOnce(of(enrollment('PROCESSING', [{ photoId: 'p-1', status: 'PENDING', identifierMismatch: false, addedAt: '' }])));
    find.mockReturnValue(of(enrollment('READY', [{ photoId: 'p-1', status: 'ENROLLED', identifierMismatch: false, addedAt: '' }])));
    await render();

    await vi.advanceTimersByTimeAsync(ENROLLMENT_POLL_MS);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="enrollment-status"]')?.getAttribute('data-status')).toBe('READY');
    expect(find).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(ENROLLMENT_POLL_MS * 2);
    expect(find).toHaveBeenCalledTimes(2);
  });

  function pickFiles(element: HTMLElement, files: File[] | null): HTMLInputElement {
    const picker = element.querySelector<HTMLInputElement>('[data-testid="photo-picker"]');
    assert(picker, 'Photo picker should be rendered');
    Object.defineProperty(picker, 'files', { configurable: true, value: files });
    picker.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    return picker;
  }

  function click(element: HTMLElement, selector: string): void {
    const button = element.querySelector<HTMLButtonElement>(selector);
    assert(button, `Button ${selector} should be rendered`);
    button.click();
    fixture.detectChanges();
  }

  it('shows an empty gallery when there is no enrollment', async () => {
    find.mockReturnValue(of(undefined));
    const element = await render();
    expect(element.textContent).toContain(en.entity_enrollment.empty);
    expect(element.querySelector('[data-testid="registered-identifier"]')?.textContent).toBe(en.entity_enrollment.noIdentifier);
    expect(element.querySelector('[data-testid="enrollment-status"]')?.getAttribute('data-status')).toBe('NOT_ENROLLED');
    expect(element.querySelector('[data-testid="delete-all"]')).toBeNull();
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

  it('stops polling at the limit and restarts after adding a photo', async () => {
    vi.useFakeTimers();
    find.mockReturnValue(of(enrollment('PROCESSING', [])));
    const element = await render();
    await vi.advanceTimersByTimeAsync(ENROLLMENT_POLL_MS * (ENROLLMENT_MAX_POLLS + 1));
    expect(find).toHaveBeenCalledTimes(ENROLLMENT_MAX_POLLS + 1);

    addPhotos.mockResolvedValue(enrollment('PROCESSING', []));
    pickFiles(element, [new File(['jpeg'], 'boat.jpg', { type: 'image/jpeg' })]);
    await vi.advanceTimersByTimeAsync(ENROLLMENT_POLL_MS);
    expect(find).toHaveBeenCalledTimes(ENROLLMENT_MAX_POLLS + 2);
  });

  it('cancels polling when the tab is destroyed', async () => {
    vi.useFakeTimers();
    find.mockReturnValue(of(enrollment('PROCESSING', [])));
    await render();
    fixture.destroy();
    await vi.advanceTimersByTimeAsync(ENROLLMENT_POLL_MS * 2);
    expect(find).toHaveBeenCalledTimes(1);
  });

  it('renders crop URLs, missing readings, failure reasons and pending-photo controls', async () => {
    find.mockReturnValue(
      of(
        enrollment('PROCESSING', [
          { photoId: 'p-1', status: 'ENROLLED', photoUrl: 'original.jpg', cropUrl: 'crop.jpg', identifierMismatch: false, addedAt: '' },
          { photoId: 'p-2', status: 'FAILED', photoUrl: 'failed.jpg', failureReason: 'Unreadable photo', identifierMismatch: false, addedAt: '' },
          { photoId: 'p-3', status: 'PENDING', photoUrl: 'pending.jpg', identifierMismatch: false, addedAt: '' },
        ]),
      ),
    );
    const element = await render();
    expect(Array.from(element.querySelectorAll('img')).map((image) => image.getAttribute('src'))).toEqual(['crop.jpg', 'failed.jpg', 'pending.jpg']);
    expect(element.textContent).toContain(en.entity_enrollment.noReading);
    expect(element.textContent).toContain('Unreadable photo');
    expect(Array.from(element.querySelectorAll<HTMLButtonElement>('[data-testid="enrollment-photo"] button')).map((button) => button.disabled)).toEqual([false, false, true]);
  });

  it('does not request an identifier reading for appearance-only profiles', async () => {
    profileFor.mockResolvedValue(new RecognitionProfile({ entityName: 'boat', detectorClass: 'boat' }));
    find.mockReturnValue(of(enrollment('READY', [{ photoId: 'p-1', status: 'ENROLLED', identifierMismatch: false, addedAt: '' }])));
    const element = await render();
    expect(element.textContent).not.toContain(en.entity_enrollment.noReading);
  });

  it.each([{ files: null }, { files: [] }])('ignores a cancelled or empty file selection ($files)', async ({ files }) => {
    const element = await render();
    pickFiles(element, files);
    expect(addPhotos).not.toHaveBeenCalled();
  });

  it('opens the picker and displays upload progress until the gallery is updated', async () => {
    let finish!: (value: Enrollment) => void;
    addPhotos.mockImplementation(
      (_entity, _id, _files, onProgress) =>
        new Promise((resolve) => {
          finish = resolve;
          onProgress?.(1);
        }),
    );
    const element = await render();
    const picker = element.querySelector<HTMLInputElement>('[data-testid="photo-picker"]');
    assert(picker, 'Photo picker should be rendered');
    const open = vi.spyOn(picker, 'click');
    click(element, '[data-testid="add-photos"]');
    expect(open).toHaveBeenCalledOnce();
    const files = [new File(['jpeg'], 'one.jpg', { type: 'image/jpeg' }), new File(['png'], 'two.png', { type: 'image/png' })];
    pickFiles(element, files);

    expect(addPhotos).toHaveBeenCalledWith('boat', 'o-1', files, expect.any(Function));
    expect(picker.value).toBe('');
    expect(element.querySelector<HTMLButtonElement>('[data-testid="add-photos"]')?.disabled).toBe(true);
    expect(element.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('50');

    finish(enrollment('READY', [{ photoId: 'p-new', status: 'ENROLLED', identifierMismatch: false, addedAt: '' }]));
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(element.querySelectorAll('[data-testid="enrollment-photo"]')).toHaveLength(1);
      expect(element.querySelector('[role="progressbar"]')).toBeNull();
    });
    expect(element.querySelector<HTMLButtonElement>('[data-testid="add-photos"]')?.disabled).toBe(false);
  });

  it.each([
    [{ error: { errorText: 'Backend upload error' } }, 'Backend upload error'],
    [new Error('Network failed'), 'Network failed'],
    ['Upload rejected', 'Upload rejected'],
  ])('displays an upload failure and clears it on retry (%j)', async (error, message) => {
    addPhotos.mockRejectedValueOnce(error).mockResolvedValueOnce(enrollment('READY', []));
    const element = await render();
    const files = [new File(['jpeg'], 'boat.jpg', { type: 'image/jpeg' })];
    pickFiles(element, files);
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(element.querySelector('[role="alert"]')?.textContent).toBe(message);
    });
    expect(element.querySelector('[role="progressbar"]')).toBeNull();
    pickFiles(element, files);
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(element.querySelector('[role="alert"]')).toBeNull();
      expect(element.querySelector('[role="progressbar"]')).toBeNull();
    });
  });

  it('removes a photo and refreshes the gallery', async () => {
    find.mockReturnValueOnce(of(enrollment('READY', [{ photoId: 'p-1', status: 'ENROLLED', identifierMismatch: false, addedAt: '' }]))).mockReturnValue(of(enrollment('NOT_ENROLLED', [])));
    const element = await render();
    click(element, '[data-testid="enrollment-photo"] button');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(element.querySelectorAll('[data-testid="enrollment-photo"]')).toHaveLength(0);
    });
    expect(deletePhoto).toHaveBeenCalledWith('boat', 'o-1', 'p-1');
    expect(find).toHaveBeenCalledTimes(2);
  });

  it('requires confirmation to delete the gallery and allows cancelling', async () => {
    find.mockReturnValueOnce(of(enrollment('READY', [{ photoId: 'p-1', status: 'ENROLLED', identifierMismatch: false, addedAt: '' }]))).mockReturnValue(of(enrollment('NOT_ENROLLED', [])));
    const element = await render();
    click(element, '[data-testid="delete-all"]');
    expect(deleteAll).not.toHaveBeenCalled();
    click(element, '[data-testid="confirm-delete-all"] + button');
    expect(element.querySelector('[data-testid="confirm-delete-all"]')).toBeNull();
    expect(deleteAll).not.toHaveBeenCalled();
    click(element, '[data-testid="delete-all"]');
    click(element, '[data-testid="confirm-delete-all"]');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(element.textContent).toContain(en.entity_enrollment.empty);
    });
    expect(deleteAll).toHaveBeenCalledWith('boat', 'o-1');
    expect(find).toHaveBeenCalledTimes(2);
  });

  it.each(['photo', 'gallery'])('displays failed %s deletions without removing photos', async (target) => {
    find.mockReturnValue(of(enrollment('READY', [{ photoId: 'p-1', status: 'ENROLLED', identifierMismatch: false, addedAt: '' }])));
    deletePhoto.mockReturnValue(throwError(() => new Error('Deletion failed')));
    deleteAll.mockReturnValue(throwError(() => ({ error: { errorText: 'Deletion failed' } })));
    const element = await render();
    if (target === 'photo') {
      click(element, '[data-testid="enrollment-photo"] button');
    } else {
      click(element, '[data-testid="delete-all"]');
      click(element, '[data-testid="confirm-delete-all"]');
    }
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(element.querySelector('[role="alert"]')?.textContent).toBe('Deletion failed');
      expect(find).toHaveBeenCalledTimes(2);
    });
    expect(element.querySelectorAll('[data-testid="enrollment-photo"]')).toHaveLength(1);
  });
});
