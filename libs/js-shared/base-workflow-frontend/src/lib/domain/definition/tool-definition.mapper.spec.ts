import { describe, expect, it } from 'vitest';
import { AuthType, HttpMethod, ToolDefinition, ToolOperation } from './tool-definition';
import { ToolDefinitionMapper } from './tool-definition.mapper';
import { OTHER_TOOL_DEFINITION_DTO, TOOL_DEFINITION_DTO } from './test-tool-definition';

describe('ToolDefinitionMapper', () => {
  const mapper = new ToolDefinitionMapper();

  describe('the flattened auth block', () => {
    it('lifts the nested fields onto the entity, where a descriptor can address them', () => {
      const tool = mapper.fromDto(TOOL_DEFINITION_DTO);

      expect(tool.type).toBe(AuthType.BEARER_TOKEN);
      expect(tool.secretRef).toBe('AUTOMATED_CHECK_TOKEN');
    });

    it('re-nests them on save, leaving no stray top-level keys', () => {
      const dto = mapper.toDto(mapper.fromDto(TOOL_DEFINITION_DTO));

      expect(dto.auth).toEqual({ type: 'BEARER_TOKEN', secretRef: 'AUTOMATED_CHECK_TOKEN' });
      expect('type' in dto).toBe(false);
      expect('secretRef' in dto).toBe(false);
    });

    // The PUT is a full replacement, so anything this mapper does not know about is dropped for good
    // unless it is merged back. `auth` is kept on the entity for exactly that.
    it('preserves a field of auth no control here knows about', () => {
      const withFutureField = { ...TOOL_DEFINITION_DTO, auth: { ...TOOL_DEFINITION_DTO.auth, rotationDays: 30 } };

      const dto = mapper.toDto(mapper.fromDto(withFutureField));

      expect(dto.auth).toEqual({ type: 'BEARER_TOKEN', secretRef: 'AUTOMATED_CHECK_TOKEN', rotationDays: 30 });
    });

    it('handles a tool that arrives with no auth block at all', () => {
      const tool = mapper.fromDto(OTHER_TOOL_DEFINITION_DTO);

      expect(tool.type).toBeUndefined();
      expect(tool.secretRef).toBeUndefined();
      expect(mapper.toDto(tool).auth).toEqual({ type: undefined, secretRef: undefined });
    });
  });

  describe('expectedStatusCodes', () => {
    // `integer[]` on the wire, `string[]` in the model: TAGS is the only control that edits a flat list
    // and it emits strings.
    it('reads the numbers as chips', () => {
      const tool = mapper.fromDto(TOOL_DEFINITION_DTO);

      expect(tool.operations[0].expectedStatusCodes).toEqual(['200']);
      expect(tool.operations[1].expectedStatusCodes).toEqual(['200', '201']);
    });

    it('writes the chips back as numbers', () => {
      const dto = mapper.toDto(mapper.fromDto(TOOL_DEFINITION_DTO));

      expect(dto.operations?.[1].expectedStatusCodes).toEqual([200, 201]);
    });

    it('drops a chip that is not a whole number rather than sending NaN', () => {
      const tool = new ToolDefinition({ operations: [new ToolOperation({ id: 'op', expectedStatusCodes: ['200', 'two-oh-one', '2.5', ''] })] });

      expect(mapper.toDto(tool).operations?.[0].expectedStatusCodes).toEqual([200]);
    });

    // An empty array would tell the backend that *no* status code counts as success; an absent one lets
    // it apply its documented default of [200, 201, 204].
    it('omits the list when every chip was dropped', () => {
      const tool = new ToolDefinition({ operations: [new ToolOperation({ id: 'op', expectedStatusCodes: ['nope'] })] });

      expect(mapper.toDto(tool).operations?.[0].expectedStatusCodes).toBeUndefined();
    });

    it('omits the list when the operation declares none', () => {
      const tool = mapper.fromDto(OTHER_TOOL_DEFINITION_DTO);

      expect(tool.operations[0].expectedStatusCodes).toBeUndefined();
      expect(mapper.toDto(tool).operations?.[0].expectedStatusCodes).toBeUndefined();
    });
  });

  it('round-trips the operations without losing a field', () => {
    const dto = mapper.toDto(mapper.fromDto(TOOL_DEFINITION_DTO));

    expect(dto.operations).toHaveLength(2);
    expect(dto.operations?.[0]).toMatchObject({ id: 'inventory-check', method: HttpMethod.POST, path: '/v1/inventory/check' });
    expect(dto.operations?.[0].payloadTemplate).toBe('{ "orderId": "${entityId}" }');
  });

  it('emits exactly the contract’s fields and nothing else', () => {
    const dto = mapper.toDto(mapper.fromDto(TOOL_DEFINITION_DTO));

    expect(Object.keys(dto).sort()).toEqual(['auth', 'baseUrl', 'createdAt', 'description', 'id', 'name', 'operations', 'version']);
  });

  it('defaults the operations of a tool that declares none', () => {
    expect(mapper.fromDto({ id: 't1' }).operations).toEqual([]);
    expect(mapper.toDto(new ToolDefinition({ id: 't1' })).operations).toEqual([]);
  });
});
