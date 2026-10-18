package com.processpuzzle.calculation.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;

class JsonColumnMappingAutoConfigurationTest {

    @Test
    void readsNestedObjectsAsJavaMaps() throws Exception {
        Object value = JsonColumnMappingAutoConfiguration.objectMapper().readValue("{\"a\":{\"b\":1}}", Map.class).get("a");

        assertThat(value).isInstanceOf(java.util.LinkedHashMap.class);
        assertThat(JsonColumnMappingAutoConfiguration.objectMapper().getRegisteredModuleIds())
                .noneMatch(id -> id.toString().contains("scala"));
    }

    @Test
    void keepsAFormatMapperTheApplicationChose() {
        Map<String, Object> properties = new HashMap<>(Map.of(AvailableSettings.JSON_FORMAT_MAPPER, "jackson3"));
        new JsonColumnMappingAutoConfiguration().scalaFreeJsonFormatMapper().customize(properties);
        assertThat(properties).containsEntry(AvailableSettings.JSON_FORMAT_MAPPER, "jackson3");

        Map<String, Object> defaults = new HashMap<>();
        new JsonColumnMappingAutoConfiguration().scalaFreeJsonFormatMapper().customize(defaults);
        assertThat(defaults.get(AvailableSettings.JSON_FORMAT_MAPPER))
                .isInstanceOf(org.hibernate.type.format.jackson.JacksonJsonFormatMapper.class);
    }
}
