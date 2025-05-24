import { Component } from '@angular/core';
import { provideTranslocoScope } from '@jsverse/transloco';

@Component({
  selector: 'pp-my-profile',
  templateUrl: 'my-profile.component.html',
  styles: ``,
  imports: [],
  providers: [provideTranslocoScope('auth')],
})
export class MyProfileComponent {}
