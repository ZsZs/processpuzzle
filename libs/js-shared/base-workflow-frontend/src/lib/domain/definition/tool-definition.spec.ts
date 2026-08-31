import { describe, expect, it } from 'vitest';
import { AuthType, HttpMethod, ToolDefinition, ToolOperation } from './tool-definition';

describe('ToolDefinition', () => {
  it('defaults the embedded operation list', () => {
    expect(new ToolDefinition().operations).toEqual([]);
  });

  // The two flattened fields and the object they came from live side by side: the form edits the
  // former, the mapper merges onto the latter.
  it('carries the flattened auth fields beside the object they came from', () => {
    const tool = new ToolDefinition({ type: AuthType.BASIC, secretRef: 'VAULT_PATH', auth: { type: AuthType.BASIC, secretRef: 'VAULT_PATH' } });

    expect(tool.type).toBe(AuthType.BASIC);
    expect(tool.secretRef).toBe('VAULT_PATH');
    expect(tool.auth).toEqual({ type: 'BASIC', secretRef: 'VAULT_PATH' });
  });

  it('leaves the server-assigned fields undefined until a backend fills them', () => {
    const tool = new ToolDefinition({ id: 't1' });

    expect(tool.version).toBeUndefined();
    expect(tool.createdAt).toBeUndefined();
  });

  it('mirrors the contract enums', () => {
    expect(Object.keys(AuthType)).toEqual(['NONE', 'BEARER_TOKEN', 'BASIC', 'API_KEY']);
    expect(Object.keys(HttpMethod)).toEqual(['GET', 'POST', 'PUT', 'PATCH', 'DELETE']);
  });
});

describe('ToolOperation', () => {
  it('mints a blank row an Add can open a form on', () => {
    const operation = new ToolOperation();

    expect(operation.id).toBe('');
    expect(operation.path).toBe('');
    expect(operation.method).toBeUndefined();
  });

  // A plain scalar list, not an embedded one: absent stays absent, so the backend applies its default
  // rather than being told that no status code is a success.
  it('leaves the status codes undefined rather than empty', () => {
    expect(new ToolOperation().expectedStatusCodes).toBeUndefined();
  });
});
