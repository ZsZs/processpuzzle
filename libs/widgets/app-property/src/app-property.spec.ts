import { ApplicationProperty } from './app-property';

// Mock the uuid generation to produce a predictable result for testing
jest.mock('uuid', () => ({
  v4: jest.fn(() => 'mocked-uuid'),
}));

describe('ApplicationProperty', () => {
  it('should create an ApplicationProperty with default values', () => {
    const property = new ApplicationProperty();

    // Verify the default id is generated correctly
    expect(property.id).toBe('mocked-uuid');

    // Verify default name and value are empty strings
    expect(property.name).toBe('');
    expect(property.value).toBe('');
  });

  it('should create an ApplicationProperty with given name and value', () => {
    const property = new ApplicationProperty(undefined, 'likes', '42');

    // Verify the id is generated correctly
    expect(property.id).toBe('mocked-uuid');

    // Verify the name and value match what was provided
    expect(property.name).toBe('likes');
    expect(property.value).toBe('42');
  });

  it('should return the correct name through the getter', () => {
    const property = new ApplicationProperty(undefined, 'theme', 'dark');

    // Verify the getter for the name
    expect(property.name).toBe('theme');
  });

  it('should return the correct initial value through the getter', () => {
    const property = new ApplicationProperty(undefined, 'theme', 'dark');

    // Verify the getter for the initial value
    expect(property.value).toBe('dark');
  });

  it('should allow the value to be updated using the setter', () => {
    const property = new ApplicationProperty(undefined, 'theme', 'light');

    // Update the value using the setter
    property.value = 'dark';

    // Verify the updated value through the getter
    expect(property.value).toBe('dark');
  });

  it('should use the provided id when given', () => {
    const customId = 'custom-id-123';
    const property = new ApplicationProperty(customId, 'customProp', 'customValue');

    // Verify the custom id is used instead of the generated one
    expect(property.id).toBe(customId);

    // Verify the name and value are also set correctly
    expect(property.name).toBe('customProp');
    expect(property.value).toBe('customValue');
  });
});
