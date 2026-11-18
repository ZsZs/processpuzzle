import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { beforeEach, describe, expect, it } from 'vitest';
import { ORG_ADMIN_SERVICE_ROOT, ORG_KEY, provideOrgAdminTesting, required } from '../domain/test-organization-user';
import { AiSubscriptionComponent } from './ai-subscription.component';

describe('AiSubscriptionComponent', () => {
  const url = `${ORG_ADMIN_SERVICE_ROOT}/organizations/${ORG_KEY}/ai-subscription`;
  let fixture: ComponentFixture<AiSubscriptionComponent>;
  let controller: HttpTestingController;

  const render = (): HTMLElement => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [AiSubscriptionComponent],
      providers: [...provideOrgAdminTesting(), provideTransloco({ config: { availableLangs: ['en'], defaultLang: 'en' } })],
    });
    fixture = TestBed.createComponent(AiSubscriptionComponent);
    controller = TestBed.inject(HttpTestingController);
  });

  it('treats a 404 as no subscription and needs a key to save the first one', () => {
    render();
    controller.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });
    const host = render();

    const model = required<HTMLInputElement>(host, '#ai-model');
    model.value = 'mistral-large-latest';
    model.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(required<HTMLButtonElement>(host, '#save-ai-subscription').disabled).toBe(true);

    const key = required<HTMLInputElement>(host, '#ai-api-key');
    key.value = 'sk-secret';
    key.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(required<HTMLButtonElement>(host, '#save-ai-subscription').disabled).toBe(false);
  });

  it('sends the key once and clears the field, since the backend never returns it', () => {
    render();
    controller.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });
    const component = fixture.componentInstance;
    component.form.patchValue({ model: 'mistral-large-latest', apiKey: ' sk-secret ' });

    component.onSave();

    const request = controller.expectOne(url);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ provider: 'MISTRAL', model: 'mistral-large-latest', baseUrl: undefined, apiKey: 'sk-secret' });
    request.flush({ provider: 'MISTRAL', model: 'mistral-large-latest', keySet: true, updatedAt: '2026-11-18T10:00:00Z', updatedBy: 'Ada' });
    expect(component.form.controls.apiKey.value).toBe('');
    expect(component.current()?.keySet).toBe(true);
  });

  it('keeps the stored key when the key field is left blank', () => {
    render();
    controller.expectOne(url).flush({ provider: 'OPENAI_COMPATIBLE', model: 'gpt', baseUrl: 'https://api.example.com/v1', keySet: true, updatedAt: '2026-11-18T10:00:00Z', updatedBy: 'Ada' });
    const component = fixture.componentInstance;
    component.form.patchValue({ model: 'gpt-2' });

    component.onSave();

    const request = controller.expectOne(url);
    expect(request.request.body.apiKey).toBeUndefined();
    expect(request.request.body.baseUrl).toBe('https://api.example.com/v1');
    request.flush({ provider: 'OPENAI_COMPATIBLE', model: 'gpt-2', baseUrl: 'https://api.example.com/v1', keySet: true, updatedAt: '2026-11-18T10:00:00Z', updatedBy: 'Ada' });
  });

  it('shows what the backend refused', () => {
    render();
    controller.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });
    const component = fixture.componentInstance;
    component.form.patchValue({ provider: 'OPENAI_COMPATIBLE', model: 'm', baseUrl: 'http://x', apiKey: 'k' });

    component.onSave();
    controller.expectOne(url).flush({ message: 'The base URL must be an https address without credentials' }, { status: 400, statusText: 'Bad Request' });

    expect(component.error()).toContain('https');
    expect(component.busy()).toBe(false);
  });
});
