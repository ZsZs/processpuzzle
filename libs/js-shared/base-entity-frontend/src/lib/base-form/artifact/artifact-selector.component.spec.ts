import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mock, type MockProxy } from 'vitest-mock-extended';
import { ObjectStoreService, type UploadObjectResponse } from '../../object-store/object-store.service';
import { ArtifactSelectorComponent } from './artifact-selector.component';
import { CameraCaptureDialog } from './camera-capture.dialog';
import { CameraSupportService } from './camera-support.service';

interface InitialState {
  isSelectorVisible?: boolean;
  isUploading?: boolean;
  cameraAvailable?: boolean;
}

function createFile(name: string, type: string): File {
  return new File(['payload'], name, { type });
}

function fileSelectEvent(file: File | null): Event {
  const files = { length: file ? 1 : 0, item: (i: number) => (i === 0 ? file : null) };
  return { target: { files } } as unknown as Event;
}

function emptyFileEvent(): Event {
  return { target: { files: { item: () => null } } } as unknown as Event;
}

let snackBar: { open: ReturnType<typeof vi.fn> };
let dialog: { open: ReturnType<typeof vi.fn> };
let capturedFile: Subject<File | undefined>;

async function setupSelector(objectStore: MockProxy<ObjectStoreService>, initialState?: InitialState) {
  await TestBed.configureTestingModule({
    imports: [ArtifactSelectorComponent],
    providers: [
      { provide: ObjectStoreService, useValue: objectStore },
      { provide: MatSnackBar, useValue: snackBar },
      { provide: MatDialog, useValue: dialog },
      { provide: CameraSupportService, useValue: { available: signal(initialState?.cameraAvailable ?? false).asReadonly() } },
      { provide: TranslocoService, useValue: { translate: vi.fn((key: string) => key) } },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(ArtifactSelectorComponent);
  const component = fixture.componentInstance;
  if (initialState?.isSelectorVisible !== undefined) component.isSelectorVisible.set(initialState.isSelectorVisible);
  if (initialState?.isUploading !== undefined) component.isUploading.set(initialState.isUploading);
  fixture.detectChanges();
  return { fixture, component };
}

describe('ArtifactSelectorComponent', () => {
  let objectStore: MockProxy<ObjectStoreService>;

  beforeEach(() => {
    TestBed.resetTestingModule();
    objectStore = mock<ObjectStoreService>();
    snackBar = { open: vi.fn() };
    capturedFile = new Subject<File | undefined>();
    dialog = { open: vi.fn(() => ({ afterClosed: () => capturedFile.asObservable() })) };
  });

  it('renders the upload trigger while the selector is hidden', async () => {
    const { fixture } = await setupSelector(objectStore);
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('button[type="button"]')?.textContent?.trim()).toBe('Upload file');
    expect(host.querySelector('input[type="file"]')).toBeNull();
  });

  it('flips isSelectorVisible to true on showSelector()', async () => {
    const { component } = await setupSelector(objectStore);

    component.showSelector();

    expect(component.isSelectorVisible()).toBe(true);
  });

  it('renders the file/name/mime inputs and upload/cancel buttons when the selector is visible', async () => {
    const { fixture } = await setupSelector(objectStore, { isSelectorVisible: true });
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('input[type="file"]')).not.toBeNull();
    expect(host.querySelectorAll('input[type="text"]')).toHaveLength(2);
    const buttonLabels = Array.from(host.querySelectorAll('button')).map((btn) => btn.textContent?.trim());
    expect(buttonLabels).toEqual(expect.arrayContaining(['Upload', 'Cancel']));
  });

  it('renders the uploading indicator when isUploading is set', async () => {
    const { fixture } = await setupSelector(objectStore, { isUploading: true });

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Uploading...');
  });

  it('does nothing when onFileSelected fires without a file', async () => {
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.onFileSelected(emptyFileEvent());

    expect(component.artifactName).toBe('');
    expect(component.mimeType).toBe('');
    expect(component.canUpload()).toBe(false);
    expect(objectStore.uploadObject).not.toHaveBeenCalled();
  });

  it('populates artifactName and mimeType on file selection without uploading', async () => {
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.onFileSelected(fileSelectEvent(createFile('local.txt', 'text/plain')));

    expect(component.artifactName).toBe('local.txt');
    expect(component.mimeType).toBe('text/plain');
    expect(component.canUpload()).toBe(true);
    expect(objectStore.uploadObject).not.toHaveBeenCalled();
  });

  it('uploads via uploadSelectedFile() and emits the artifact using the server response', async () => {
    const response: UploadObjectResponse = { objectID: 'oid-1', fileName: 'server-name.txt', mimeType: 'text/x-custom', bucketName: 'bucket-a' };
    objectStore.uploadObject.mockReturnValue(of(response));
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });
    const emit = vi.fn();
    component.artifactUploaded.subscribe(emit);

    const file = createFile('local.txt', 'text/plain');
    component.onFileSelected(fileSelectEvent(file));
    component.artifactName = 'edited-name.txt';
    component.mimeType = 'text/edited';
    component.uploadSelectedFile();

    expect(objectStore.uploadObject).toHaveBeenCalledWith(file, 'edited-name.txt', 'text/edited');
    expect(emit).toHaveBeenCalledWith({
      bucket: 'bucket-a',
      objectId: 'oid-1',
      name: 'server-name.txt',
      mimeType: 'text/x-custom',
    });
    expect(component.isSelectorVisible()).toBe(false);
    expect(component.isUploading()).toBe(false);
  });

  it('falls back to local artifactName and mimeType when the response omits them', async () => {
    objectStore.uploadObject.mockReturnValue(of({ objectID: 'oid-2', fileName: '', mimeType: '' } as UploadObjectResponse));
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });
    const emit = vi.fn();
    component.artifactUploaded.subscribe(emit);

    component.onFileSelected(fileSelectEvent(createFile('local.csv', 'text/plain')));
    component.uploadSelectedFile();

    expect(emit).toHaveBeenCalledWith({
      bucket: '',
      objectId: 'oid-2',
      name: 'local.csv',
      mimeType: 'text/plain',
    });
  });

  it.each([
    { scenario: 'derives the mime type from the file extension', fileName: 'report.csv', expected: 'text/csv' },
    { scenario: 'falls back to application/octet-stream for unknown extensions', fileName: 'binary.dat', expected: 'application/octet-stream' },
    { scenario: 'falls back to application/octet-stream when the file has no extension', fileName: 'Makefile', expected: 'application/octet-stream' },
  ])('$scenario when the browser leaves file.type empty', async ({ fileName, expected }) => {
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.onFileSelected(fileSelectEvent(createFile(fileName, '')));

    expect(component.mimeType).toBe(expected);
  });

  it('does not upload when uploadSelectedFile() is called without a selected file', async () => {
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.uploadSelectedFile();

    expect(objectStore.uploadObject).not.toHaveBeenCalled();
  });

  it('keeps isUploading true while the upload is pending', async () => {
    const inflight = new Subject<UploadObjectResponse>();
    objectStore.uploadObject.mockReturnValue(inflight.asObservable());
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.onFileSelected(fileSelectEvent(createFile('first.txt', 'text/plain')));
    component.uploadSelectedFile();

    expect(component.isUploading()).toBe(true);
    expect(component.canUpload()).toBe(false);

    inflight.next({ objectID: 'oid', fileName: 'first.txt', mimeType: 'text/plain', bucketName: 'b' });
    inflight.complete();

    expect(component.isUploading()).toBe(false);
  });

  it('reports the failure and keeps the selection for a retry when the upload fails', async () => {
    objectStore.uploadObject.mockReturnValue(throwError(() => new Error('boom')));
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });
    const emit = vi.fn();
    component.artifactUploaded.subscribe(emit);

    const file = createFile('a.txt', 'text/plain');
    component.onFileSelected(fileSelectEvent(file));
    component.uploadSelectedFile();

    expect(snackBar.open).toHaveBeenCalledOnce();
    expect(component.isUploading()).toBe(false);
    // Left open with the selection intact — a silent close would be indistinguishable from a cancel.
    expect(component.isSelectorVisible()).toBe(true);
    expect(component.artifactName).toBe('a.txt');
    expect(component.mimeType).toBe('text/plain');
    expect(component.canUpload()).toBe(true);
    expect(emit).not.toHaveBeenCalled();

    objectStore.uploadObject.mockReturnValue(of({ objectID: 'oid-retry', fileName: 'a.txt', mimeType: 'text/plain', bucketName: 'bucket-a' }));
    component.uploadSelectedFile();

    expect(objectStore.uploadObject).toHaveBeenLastCalledWith(file, 'a.txt', 'text/plain');
    expect(emit).toHaveBeenCalledWith({ bucket: 'bucket-a', objectId: 'oid-retry', name: 'a.txt', mimeType: 'text/plain' });
    expect(component.isSelectorVisible()).toBe(false);
  });

  it('resets to display mode when cancel() is called', async () => {
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.onFileSelected(fileSelectEvent(createFile('a.txt', 'text/plain')));
    component.cancel();

    expect(component.isSelectorVisible()).toBe(false);
    expect(component.artifactName).toBe('');
    expect(component.mimeType).toBe('');
    expect(objectStore.uploadObject).not.toHaveBeenCalled();
  });

  it('offers no camera option on a device without a camera', async () => {
    const { fixture } = await setupSelector(objectStore);

    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="artifact-camera-capture"]')).toBeNull();
  });

  it('offers the camera option next to the upload trigger when a camera is available', async () => {
    const { fixture } = await setupSelector(objectStore, { cameraAvailable: true });
    const cameraButton = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="artifact-camera-capture"]');

    expect(cameraButton?.textContent?.trim()).toBe('base_entity.artifact.camera_capture');
  });

  it('feeds a capture into the upload step, with its name and MIME type filled in', async () => {
    const { fixture, component } = await setupSelector(objectStore, { cameraAvailable: true });
    ((fixture.nativeElement as HTMLElement).querySelector('[data-testid="artifact-camera-capture"]') as HTMLButtonElement).click();
    expect(dialog.open).toHaveBeenCalledWith(CameraCaptureDialog, expect.anything());

    capturedFile.next(createFile('photo-1.jpg', 'image/jpeg'));

    expect(component.isSelectorVisible()).toBe(true);
    expect(component.artifactName).toBe('photo-1.jpg');
    expect(component.mimeType).toBe('image/jpeg');
    expect(component.canUpload()).toBe(true);
    expect(objectStore.uploadObject).not.toHaveBeenCalled();
  });

  it('stays in display mode when the camera dialog is cancelled', async () => {
    const { component } = await setupSelector(objectStore, { cameraAvailable: true });
    component.openCamera();

    capturedFile.next(undefined);

    expect(component.isSelectorVisible()).toBe(false);
    expect(component.canUpload()).toBe(false);
  });

  it('derives the MIME type of a video file the browser left untyped', async () => {
    const { component } = await setupSelector(objectStore, { isSelectorVisible: true });

    component.onFileSelected(fileSelectEvent(createFile('clip.webm', '')));

    expect(component.mimeType).toBe('video/webm');
  });
});
