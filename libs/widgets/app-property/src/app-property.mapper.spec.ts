import { ApplicationPropertyMapper } from './app-property.mapper';
import { ApplicationProperty } from './app-property';

describe('ApplicationPropertyMapper', () => {
  let mapper: ApplicationPropertyMapper;

  beforeEach(() => {
    mapper = new ApplicationPropertyMapper();
  });

  describe('fromDto', () => {
    it('should create an ApplicationProperty from a DTO with all properties', () => {
      // Arrange
      const dto = {
        id: '123',
        name: 'testProperty',
        value: 'testValue'
      };

      // Act
      const result = mapper.fromDto(dto);

      // Assert
      expect(result).toBeInstanceOf(ApplicationProperty);
      expect(result.id).toBe('123');
      expect(result.name).toBe('testProperty');
      expect(result.value).toBe('testValue');
    });

    it('should handle a DTO with missing properties', () => {
      // Arrange
      const dto = {
        id: '123'
      };

      // Act
      const result = mapper.fromDto(dto);

      // Assert
      expect(result).toBeInstanceOf(ApplicationProperty);
      expect(result.id).toBe('123');
      expect(result.name).toBe('');
      expect(result.value).toBe('');
    });
  });

  describe('toDto', () => {
    it('should convert an ApplicationProperty to a DTO with all properties', () => {
      // Arrange
      const entity = new ApplicationProperty('123', 'testProperty', 'testValue');

      // Act
      const result = mapper.toDto(entity);

      // Assert
      expect(result).toEqual({
        id: '123',
        name: 'testProperty',
        value: 'testValue'
      });
    });

    it('should handle an ApplicationProperty with default values', () => {
      // Arrange
      const entity = new ApplicationProperty('123');

      // Act
      const result = mapper.toDto(entity);

      // Assert
      expect(result).toEqual({
        id: '123',
        name: '',
        value: ''
      });
    });
  });
});
