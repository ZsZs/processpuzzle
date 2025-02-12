import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AppConfigService {
  init() {
    console.log('Initializing Application...');
  }
}
