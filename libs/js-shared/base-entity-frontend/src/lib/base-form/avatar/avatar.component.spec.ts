import { ANIMATION_MODULE_TYPE } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mock, type MockProxy } from 'vitest-mock-extended';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { ObjectStoreService } from '../../object-store/object-store.service';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { AvatarComponent } from './avatar.component';
import type { ArtifactAttr } from '../artifact/artifact-attr';

function makeAvatarArtifact(overrides: Partial<ArtifactAttr> = {}): ArtifactAttr {
  return { bucket: 'bucket-avatar', objectId: 'oid-avatar', name: 'avatar.png', mimeType: 'image/png', ...overrides };
}

function makeConfig(defaultAvatar?: string): BaseEntityAttrDescriptor {
  const config = new BaseEntityAttrDescriptor('avatar', FormControlType.AVATAR, 'Avatar');
  if (defaultAvatar) {
    config.defaultAvatar = defaultAvatar;
  }
  return config;
}

async function setupAvatarComponent(config: BaseEntityAttrDescriptor, entityArtifact: ArtifactAttr | null, dialog: MockProxy<MatDialog>, objectStore: MockProxy<ObjectStoreService>) {
  const entity = new TestEntity('1', 'name');
  Reflect.set(entity, 'avatar', entityArtifact);

  const harness = await setupFormControlTest(AvatarComponent, config, entity, [
    { provide: MatDialog, useValue: dialog },
    { provide: ObjectStoreService, useValue: objectStore },
  ]);

  const component = harness.component as AvatarComponent<TestEntity>;
  return { ...harness, component, entity };
}

describe('AvatarComponent', () => {
  let dialog: MockProxy<MatDialog>;
  let objectStore: MockProxy<ObjectStoreService>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ providers: [{ provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' }] }).compileComponents();
    dialog = mock<MatDialog>();
    objectStore = mock<ObjectStoreService>();
    objectStore.getThumbnailUriByID.mockReturnValue(of(null));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('artifact()', () => {
    it('returns the current form control value', async () => {
      const artifact = makeAvatarArtifact();
      const { component } = await setupAvatarComponent(makeConfig(), artifact, dialog, objectStore);

      expect(component.artifact()).toEqual(artifact);
    });

    it('returns null when the form control is empty', async () => {
      const { component } = await setupAvatarComponent(makeConfig(), null, dialog, objectStore);

      expect(component.artifact()).toBeNull();
    });
  });

  describe('defaultAvatar()', () => {
    it('defaults to person when not specified on descriptor', async () => {
      const { component } = await setupAvatarComponent(makeConfig(), null, dialog, objectStore);

      expect(component.defaultAvatar()).toBe('person');
    });

    it('returns descriptor configured icon name or image url', async () => {
      const { component } = await setupAvatarComponent(makeConfig('sailing'), null, dialog, objectStore);

      expect(component.defaultAvatar()).toBe('sailing');
    });
  });

  describe('template rendering', () => {
    it('renders predefined mat-icon when no avatar is uploaded', async () => {
      const { fixture } = await setupAvatarComponent(makeConfig('sailing'), null, dialog, objectStore);
      fixture.detectChanges();

      const placeholderIcon = fixture.nativeElement.querySelector('.avatar-placeholder mat-icon');
      expect(placeholderIcon).toBeTruthy();
      expect(placeholderIcon.textContent.trim()).toBe('sailing');
    });

    it('renders predefined image when defaultAvatar is an image path', async () => {
      const { fixture } = await setupAvatarComponent(makeConfig('assets/default-boat.png'), null, dialog, objectStore);
      fixture.detectChanges();

      const placeholderImg = fixture.nativeElement.querySelector('.avatar-placeholder img.avatar-thumbnail');
      expect(placeholderImg).toBeTruthy();
      expect(placeholderImg.getAttribute('src')).toBe('assets/default-boat.png');
    });

    it('renders 50x50 thumbnail when image is uploaded and thumbnail url resolves', async () => {
      const artifact = makeAvatarArtifact();
      objectStore.getThumbnailUriByID.mockReturnValue(of({ uri: 'https://cdn.example/thumb-50.png' }));

      const { fixture, component } = await setupAvatarComponent(makeConfig(), artifact, dialog, objectStore);
      fixture.detectChanges();

      expect(component.thumbnailUrlOf(artifact)).toBe('https://cdn.example/thumb-50.png');
      const img = fixture.nativeElement.querySelector('.avatar-display img, .avatar-item img');
      expect(img).toBeTruthy();
      expect(img.getAttribute('src')).toBe('https://cdn.example/thumb-50.png');
      expect(img.classList.contains('avatar-thumbnail')).toBe(true);
    });
  });

  describe('openArtifact()', () => {
    it('opens the resolved uri in a new window', async () => {
      const artifact = makeAvatarArtifact();
      objectStore.getObjectUriByID.mockReturnValue(of({ uri: 'https://cdn.example/avatar.png' }));
      const windowOpen = vi.spyOn(window, 'open').mockReturnValue(null);
      const preventDefault = vi.fn();
      const { component } = await setupAvatarComponent(makeConfig(), artifact, dialog, objectStore);

      component.openArtifact({ preventDefault } as unknown as Event, artifact);

      expect(preventDefault).toHaveBeenCalled();
      expect(objectStore.getObjectUriByID).toHaveBeenCalledWith('bucket-avatar', 'oid-avatar');
      expect(windowOpen).toHaveBeenCalledWith('https://cdn.example/avatar.png', '_blank', 'noopener,noreferrer');
    });
  });

  describe('onArtifactUploaded()', () => {
    it('writes the uploaded image onto the form control', async () => {
      const { component } = await setupAvatarComponent(makeConfig(), null, dialog, objectStore);
      const uploaded = makeAvatarArtifact({ objectId: 'new-avatar-id', name: 'photo.jpg', mimeType: 'image/jpeg' });

      component.onArtifactUploaded(uploaded);

      const control = component.formGroup.get('avatar');
      expect(control?.value).toEqual(uploaded);
      expect(control?.dirty).toBe(true);
      expect(control?.touched).toBe(true);
    });

    it('rejects non-image files', async () => {
      const { component } = await setupAvatarComponent(makeConfig(), null, dialog, objectStore);
      const uploaded = makeAvatarArtifact({ objectId: 'doc-id', name: 'doc.pdf', mimeType: 'application/pdf' });

      component.onArtifactUploaded(uploaded);

      const control = component.formGroup.get('avatar');
      expect(control?.value).toBeNull();
    });

    it('does nothing when the descriptor is disabled', async () => {
      const config = makeConfig();
      config.disabled = true;
      const { component } = await setupAvatarComponent(config, null, dialog, objectStore);
      const uploaded = makeAvatarArtifact();

      component.onArtifactUploaded(uploaded);

      const control = component.formGroup.get('avatar');
      expect(control?.value).toBeNull();
    });
  });

  describe('deleteArtifact()', () => {
    it('opens confirmation dialog and deletes artifact from store and clears form control', async () => {
      const artifact = makeAvatarArtifact();
      const afterClosedSubject = new Subject<boolean>();
      dialog.open.mockReturnValue({ afterClosed: () => afterClosedSubject.asObservable() } as never);
      objectStore.deleteObjectByID.mockReturnValue(of(undefined));

      const { component } = await setupAvatarComponent(makeConfig(), artifact, dialog, objectStore);

      component.deleteArtifact(artifact);

      expect(dialog.open).toHaveBeenCalled();
      afterClosedSubject.next(true);

      expect(objectStore.deleteObjectByID).toHaveBeenCalledWith('bucket-avatar', 'oid-avatar');
      const control = component.formGroup.get('avatar');
      expect(control?.value).toBeNull();
      expect(control?.dirty).toBe(true);
    });

    it('does not delete when dialog is cancelled', async () => {
      const artifact = makeAvatarArtifact();
      const afterClosedSubject = new Subject<boolean>();
      dialog.open.mockReturnValue({ afterClosed: () => afterClosedSubject.asObservable() } as never);

      const { component } = await setupAvatarComponent(makeConfig(), artifact, dialog, objectStore);

      component.deleteArtifact(artifact);
      afterClosedSubject.next(false);

      expect(objectStore.deleteObjectByID).not.toHaveBeenCalled();
      const control = component.formGroup.get('avatar');
      expect(control?.value).toEqual(artifact);
    });
  });
});
