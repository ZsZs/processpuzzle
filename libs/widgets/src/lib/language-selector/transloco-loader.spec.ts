import { TranslocoHttpLoader } from './transloco.loader';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';

describe('TranslocoHttpLoader', () => {
  let loader: TranslocoHttpLoader;
  let httpClientMock: jest.Mocked<HttpClient>;

  beforeEach(() => {
    httpClientMock = {
      get: jest.fn(),
    } as unknown as jest.Mocked<HttpClient>;

    loader = new TranslocoHttpLoader(httpClientMock);
  });

  it('should be created', () => {
    expect(loader).toBeTruthy();
  });

  it('should call HttpClient.get with the correct URL', (done) => {
    const mockTranslation = { key: 'value' }; // Mock translation response
    const lang = 'en'; // Language to load
    const expectedUrl = `/assets/i18n/${lang}.json`;

    httpClientMock.get.mockReturnValue(of(mockTranslation));

    loader.getTranslation(lang).subscribe((result) => {
      expect(httpClientMock.get).toHaveBeenCalledTimes(1);
      expect(httpClientMock.get).toHaveBeenCalledWith(expectedUrl);

      expect(result).toEqual(mockTranslation);
      done();
    });
  });
});
