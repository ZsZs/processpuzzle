import { Component, computed, DOCUMENT, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardActions, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatChip, MatChipSet } from '@angular/material/chips';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';

/**
 * The signed-in user's own profile, read-only. Editing happens at the identity provider, which owns the
 * account; the page links there when the provider offers an account page (Keycloak's account console).
 */
@Component({
  selector: 'pp-my-profile',
  templateUrl: 'my-profile.component.html',
  styleUrls: ['my-profile.component.css'],
  imports: [MatCard, MatCardHeader, MatCardTitle, MatCardContent, MatCardActions, MatFormField, MatLabel, MatInput, MatChipSet, MatChip, MatButton, MatIcon, TranslocoDirective],
  providers: [provideTranslocoScope('auth')],
})
export class MyProfileComponent {
  private readonly authService = inject(AUTHENTICATION_SERVICE);
  private readonly document = inject(DOCUMENT);
  readonly user = computed(() => this.authService.currentUser());
  readonly accountManagementUrl = computed(() => (this.user() ? this.authService.getAccountManagementUrl(this.document.location.href) : undefined));
}
