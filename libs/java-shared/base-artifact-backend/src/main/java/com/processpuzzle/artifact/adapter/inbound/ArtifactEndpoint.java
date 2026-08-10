package com.processpuzzle.artifact.adapter.inbound;

import com.processpuzzle.artifact.api.BaseArtifactApi;
import com.processpuzzle.artifact.model.*;
import com.processpuzzle.artifact.usecase.*;
import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.shared.model.ImportResult;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@LogClass
public class ArtifactEndpoint implements BaseArtifactApi {

    private final CreateArtifact createArtifact;
    private final UpdateArtifact updateArtifact;
    private final DeleteArtifact deleteArtifact;
    private final FindArtifact findArtifact;
    private final FindAllArtifacts findAllArtifacts;
    private final AppendArtifactBlock appendArtifactBlock;
    private final ReplaceArtifactBlock replaceArtifactBlock;
    private final DeleteArtifactBlock deleteArtifactBlock;
    private final ReorderArtifactBlocks reorderArtifactBlocks;
    private final ValidateArtifact validateArtifact;
    private final ImportArtifacts importArtifacts;
    private final ExportArtifact exportArtifact;
    private final ArtifactMapper mapper;

    public ArtifactEndpoint(CreateArtifact createArtifact,
                             UpdateArtifact updateArtifact,
                             DeleteArtifact deleteArtifact,
                             FindArtifact findArtifact,
                             FindAllArtifacts findAllArtifacts,
                             AppendArtifactBlock appendArtifactBlock,
                             ReplaceArtifactBlock replaceArtifactBlock,
                             DeleteArtifactBlock deleteArtifactBlock,
                             ReorderArtifactBlocks reorderArtifactBlocks,
                             ValidateArtifact validateArtifact,
                             ImportArtifacts importArtifacts,
                             ExportArtifact exportArtifact,
                             ArtifactMapper mapper) {
        this.createArtifact = createArtifact;
        this.updateArtifact = updateArtifact;
        this.deleteArtifact = deleteArtifact;
        this.findArtifact = findArtifact;
        this.findAllArtifacts = findAllArtifacts;
        this.appendArtifactBlock = appendArtifactBlock;
        this.replaceArtifactBlock = replaceArtifactBlock;
        this.deleteArtifactBlock = deleteArtifactBlock;
        this.reorderArtifactBlocks = reorderArtifactBlocks;
        this.validateArtifact = validateArtifact;
        this.importArtifacts = importArtifacts;
        this.exportArtifact = exportArtifact;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Artifact> createArtifact(String orgKey, ArtifactInput input) {
        var created = createArtifact.execute(orgKey, input);
        return new ResponseEntity<>(mapper.toModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<Artifact> updateArtifact(String orgKey, String artifactId, ArtifactInput input) {
        var updated = updateArtifact.execute(orgKey, artifactId, input);
        return ResponseEntity.ok(mapper.toModel(updated));
    }

    @Override
    public ResponseEntity<Void> deleteArtifact(String orgKey, String artifactId) {
        deleteArtifact.execute(orgKey, artifactId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Artifact> getArtifact(String orgKey, String artifactId) {
        return ResponseEntity.ok(mapper.toModel(findArtifact.execute(orgKey, artifactId)));
    }

    @Override
    public ResponseEntity<PageOfArtifactSummary> listArtifacts(
            String orgKey, String where, String order, Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toModel(findAllArtifacts.execute(orgKey, where, order, page, size)));
    }

    @Override
    public ResponseEntity<ArtifactBlock> appendArtifactBlock(
            String orgKey, String artifactId, ArtifactBlockInput input) {
        var created = appendArtifactBlock.execute(orgKey, artifactId, input);
        return new ResponseEntity<>(mapper.toModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ArtifactBlock> replaceArtifactBlock(
            String orgKey, String artifactId, String blockId, ArtifactBlockInput input) {
        var replaced = replaceArtifactBlock.execute(orgKey, artifactId, blockId, input);
        return ResponseEntity.ok(mapper.toModel(replaced));
    }

    @Override
    public ResponseEntity<Void> deleteArtifactBlock(String orgKey, String artifactId, String blockId) {
        deleteArtifactBlock.execute(orgKey, artifactId, blockId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<ArtifactBlock>> reorderArtifactBlocks(
            String orgKey, String artifactId, ReorderBlocksRequest request) {
        var reordered = reorderArtifactBlocks.execute(orgKey, artifactId, request.getBlockIds());
        return ResponseEntity.ok(reordered.stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<ValidationResult> validateArtifact(String orgKey, ArtifactInput input) {
        var outcome = validateArtifact.execute(input);
        return ResponseEntity.ok(mapper.toModel(outcome.valid(), outcome.problems()));
    }

    @Override
    public ResponseEntity<ImportResult> importArtifacts(String orgKey, MultipartFile file) {
        try {
            var outcome = importArtifacts.execute(orgKey, file.getInputStream());
            return ResponseEntity.ok(mapper.toModel(outcome));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<Resource> exportArtifact(String orgKey, String artifactId) {
        String yaml = exportArtifact.execute(orgKey, artifactId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + artifactId + ".yaml\"")
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .body(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
