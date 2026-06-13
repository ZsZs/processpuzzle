import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ObjectStoreService } from './object-store.service';

describe('ObjectStoreService', () => {
  const objectStoreRoot = 'http://localhost:4200/services/object-store/api/v1';
  let service: ObjectStoreService;
  let controller: HttpTestingController;

  function setup(runtimeConfiguration: unknown) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: runtimeConfiguration }, ObjectStoreService],
    });
    service = TestBed.inject(ObjectStoreService);
    controller = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => {
    setup({ BASE_CONFIGURATION: { OBJECT_STORE_SERVICE_ROOT: objectStoreRoot } });
  });

  describe('baseUrl resolution', () => {
    it('uses OBJECT_STORE_SERVICE_ROOT when available', () => {
      service.deleteObjectByID('bucket', 'oid').subscribe();
      controller.expectOne(`${objectStoreRoot}/objects/bucket/oid`);
      controller.verify();
    });

    it('falls back to BACKEND_SERVICE_ROOT when OBJECT_STORE_SERVICE_ROOT is missing', () => {
      TestBed.resetTestingModule();
      const backendRoot = 'http://localhost:4200/services/backend/api/v1';
      setup({ BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: backendRoot } });

      service.deleteObjectByID('bucket', 'oid').subscribe();
      controller.expectOne(`${backendRoot}/objects/bucket/oid`);
      controller.verify();
    });

    it('falls back to an empty base url when neither root is configured', () => {
      TestBed.resetTestingModule();
      setup({ BASE_CONFIGURATION: {} });

      service.deleteObjectByID('bucket', 'oid').subscribe();
      controller.expectOne('/objects/bucket/oid');
      controller.verify();
    });
  });

  describe('uploadObject()', () => {
    it('posts a multipart form with file, name and mimeType and merges the Location header into bucketName', () => {
      const file = new Blob(['hello'], { type: 'text/plain' });
      const responseBody = { objectID: 'oid-1', fileName: 'greeting.txt', mimeType: 'text/plain' };
      let actual: { objectID: string; fileName: string; mimeType: string; bucketName?: string } | undefined;

      service.uploadObject(file, 'greeting.txt', 'text/plain').subscribe((response) => (actual = response));

      const request = controller.expectOne(`${objectStoreRoot}/objects`);
      expect(request.request.method).toBe('POST');
      const body = request.request.body as FormData;
      expect(body).toBeInstanceOf(FormData);
      expect(body.get('name')).toBe('greeting.txt');
      expect(body.get('mimeType')).toBe('text/plain');
      expect(body.get('file')).toBeInstanceOf(File);

      request.flush(responseBody, { headers: { Location: 'bucket-a' } });
      controller.verify();

      expect(actual).toEqual({ ...responseBody, bucketName: 'bucket-a' });
    });

    it('leaves bucketName undefined when the Location header is missing', () => {
      const file = new Blob(['x']);
      const responseBody = { objectID: 'oid-2', fileName: 'x.bin', mimeType: 'application/octet-stream' };
      let actual: { bucketName?: string } | undefined;

      service.uploadObject(file, 'x.bin', 'application/octet-stream').subscribe((response) => (actual = response));

      const request = controller.expectOne(`${objectStoreRoot}/objects`);
      request.flush(responseBody);
      controller.verify();

      expect(actual?.bucketName).toBeUndefined();
    });

    it('throws when the response body is missing', () => {
      const file = new Blob(['x']);
      let actualError: unknown;

      service.uploadObject(file, 'x.bin', 'application/octet-stream').subscribe({
        next: () => expect.fail('next handler must not be called'),
        error: (error) => (actualError = error),
      });

      const request = controller.expectOne(`${objectStoreRoot}/objects`);
      request.flush(null);
      controller.verify();

      expect((actualError as Error).message).toBe('Object store upload response body is missing');
    });
  });

  describe('getObjectByID()', () => {
    it('returns the blob, fileName and bucketName extracted from headers', () => {
      const blob = new Blob(['payload']);
      let actual: { data: Blob; fileName?: string; bucketName?: string } | undefined;

      service.getObjectByID('bucket-x', 'oid-3').subscribe((response) => (actual = response));

      const request = controller.expectOne(`${objectStoreRoot}/objects/bucket-x/oid-3`);
      expect(request.request.method).toBe('GET');
      expect(request.request.responseType).toBe('blob');
      request.flush(blob, { headers: { 'X-Object-Name': 'file.txt', 'X-Object-Bucket': 'bucket-x' } });
      controller.verify();

      expect(actual?.data).toBe(blob);
      expect(actual?.fileName).toBe('file.txt');
      expect(actual?.bucketName).toBe('bucket-x');
    });

    it('returns an empty blob and undefined metadata when body and headers are missing', () => {
      let actual: { data: Blob; fileName?: string; bucketName?: string } | undefined;

      service.getObjectByID('bucket-y', 'oid-4').subscribe((response) => (actual = response));

      const request = controller.expectOne(`${objectStoreRoot}/objects/bucket-y/oid-4`);
      request.flush(null);
      controller.verify();

      expect(actual?.data).toBeInstanceOf(Blob);
      expect(actual?.data.size).toBe(0);
      expect(actual?.fileName).toBeUndefined();
      expect(actual?.bucketName).toBeUndefined();
    });

    it('URL-encodes bucketName and objectID', () => {
      service.getObjectByID('bucket/with slash', 'oid with space').subscribe();
      controller.expectOne(`${objectStoreRoot}/objects/bucket%2Fwith%20slash/oid%20with%20space`);
      controller.verify();
    });
  });

  describe('deleteObjectByID()', () => {
    it('issues DELETE against the encoded object path', () => {
      service.deleteObjectByID('bucket', 'oid-5').subscribe();
      const request = controller.expectOne(`${objectStoreRoot}/objects/bucket/oid-5`);
      expect(request.request.method).toBe('DELETE');
      request.flush(null);
      controller.verify();
    });
  });

  describe('getObjectUriByID()', () => {
    it('returns the uri payload from the /uri endpoint', () => {
      let actual: { uri: string } | undefined;

      service.getObjectUriByID('bucket', 'oid-6').subscribe((response) => (actual = response));

      const request = controller.expectOne(`${objectStoreRoot}/objects/bucket/oid-6/uri`);
      expect(request.request.method).toBe('GET');
      request.flush({ uri: 'https://cdn.example/oid-6' });
      controller.verify();

      expect(actual).toEqual({ uri: 'https://cdn.example/oid-6' });
    });
  });
});
