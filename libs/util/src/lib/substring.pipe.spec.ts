import { SubstringPipe } from './substring.pipe';

describe('SubstringPipe', () => {
  let pipe: SubstringPipe;

  beforeEach(() => {
    pipe = new SubstringPipe();
  });

  it('should create an instance', () => {
    expect(pipe).toBeTruthy();
  });

  it('should return a substring from the given start index', () => {
    const input = 'Hello World';
    const result = pipe.transform(input, 6);
    expect(result).toBe('World');
  });

  it('should return a substring from the given start index with a defined length', () => {
    const input = 'Hello Angular';
    const result = pipe.transform(input, 6, 10);
    expect(result).toBe('Angular'.substring(0, 4)); // Should return 'Angu'
  });

  it('should handle empty strings', () => {
    const input = '';
    const result = pipe.transform(input);
    expect(result).toBe('');
  });

  it('should handle invalid values gracefully', () => {
    const input: any = null;
    expect(() => pipe.transform(input)).toThrowError(TypeError);
  });

  it('should return the original string if start index is not provided', () => {
    const input = 'Hello World';
    const result = pipe.transform(input);
    expect(result).toBe('Hello World');
  });

  it('should handle negative start indices as default behavior for substring', () => {
    const input = 'Hello World';
    const result = pipe.transform(input, -5);
    expect(result).toBe(input.substring(-5)); // Default JS substring behavior
  });

  it('should handle start index larger than the string length', () => {
    const input = 'Hello';
    const result = pipe.transform(input, 10);
    expect(result).toBe(''); // Returns an empty string
  });
});
