package com.processpuzzle.store.adapters.outbound;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.Map;

@Setter
@Getter
@Component
@PropertySource(value = "classpath:minio-config.yaml", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private Map<String, String> buckets;
    private Map<String, String> mimeTypes;

}
