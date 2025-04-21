import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Translation, TranslocoLoader } from '@jsverse/transloco';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class TranslocoHttpLoader implements TranslocoLoader {
  constructor(private readonly http: HttpClient) {}

  getTranslation(lang: string): Observable<Translation> {
    const path = `/assets/i18n/${lang}.json`;
    console.log(`Loading translations from: ${path}`);
    return this.http.get<Translation>(path);
  }
}
