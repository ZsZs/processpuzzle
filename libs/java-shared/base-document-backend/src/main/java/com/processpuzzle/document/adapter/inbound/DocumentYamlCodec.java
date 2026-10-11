package com.processpuzzle.document.adapter.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.document.usecase.DocumentCommand;
import com.processpuzzle.document.usecase.DocumentExport;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Decodes the entire input before a transaction-owning import is invoked. */
@Component
public class DocumentYamlCodec {
    private final DocumentMapper mapper;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public DocumentYamlCodec(DocumentMapper mapper) {
        this.mapper = mapper;
    }

    public List<DocumentCommand> read(InputStream input) throws IOException {
        DocumentYamlFile file = yaml.readValue(input, DocumentYamlFile.class);
        return file.documents() == null ? List.of() : file.documents().stream().map(mapper::toCommand).toList();
    }

    public String write(DocumentExport exported) {
        try {
            return yaml.writeValueAsString(new DocumentYamlFile(
                    List.of(mapper.toInput(exported.document(), exported.drafts()))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize document '" + exported.document().getId() + "'", e);
        }
    }
}
