import { TranslocoHttpLoader } from './transloco.loader';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

describe( 'TranslocoHttpLoader', () => {
  let loader: TranslocoHttpLoader;
  let httpTestingController: HttpTestingController;

  beforeEach( () => {
    TestBed.configureTestingModule( {
      imports: [ HttpClientTestingModule ],
      providers: [ TranslocoHttpLoader ]
    } );
    httpTestingController = TestBed.inject( HttpTestingController );
    loader = TestBed.inject( TranslocoHttpLoader );
  } );

  afterEach( () => {
    // Verify no outstanding requests
    httpTestingController.verify();
  } );

  it( 'should be created', () => {
    expect( loader ).toBeTruthy();
  } );

  it( 'should call HttpClient.get with the correct URL', () => {
    const mockTranslation = { key: 'value' }; // Mock translation response
    const lang = 'en'; // Language to load
    const expectedUrl = `assets/i18n/${lang}.json`;

    loader.getTranslation( lang ).subscribe( ( result ) => {
      expect( result ).toEqual( mockTranslation );
    } );

    const req = httpTestingController.expectOne( expectedUrl );
    expect( req.request.method ).toBe( 'GET' );
    req.flush( mockTranslation );
  } );
} );
