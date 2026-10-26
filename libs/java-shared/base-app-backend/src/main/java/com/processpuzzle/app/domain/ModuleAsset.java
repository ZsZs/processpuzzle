package com.processpuzzle.app.domain;

import com.processpuzzle.core.definition.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One file a module brings along — a markdown page, an image — recorded by path, with the hash that names
 * its content in the object store. The bytes are not here: they live in the store under
 * {@code <orgKey>/<sha256>}, so two paths with the same content, and a draft and a released row of one path,
 * share one object.
 *
 * <p>A row of its own rather than a list inside {@code ModuleDefinition}: a module is replaced as a whole by
 * its PUT, which would silently drop assets the form never carried. Stored per {@link Channel} like the module,
 * so a release is a copy of rows and never of files.
 */
@Getter
@Entity
@Table(name = "app_module_assets")
@IdClass(ModuleAssetKey.class)
public class ModuleAsset {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "module_key", length = 100)
    private String moduleKey;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 16, nullable = false)
    private Channel channel;

    @Id
    @Column(name = "path", length = 200)
    private String path;

    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    @Column(name = "mime_type", length = 127, nullable = false)
    private String mimeType;

    @Column(name = "size", nullable = false)
    private long size;

    protected ModuleAsset() {
        // required by JPA
    }

    public ModuleAsset(String orgKey, String moduleKey, Channel channel, String path, String sha256,
                       String mimeType, long size) {
        this.orgKey = orgKey;
        this.moduleKey = moduleKey;
        this.channel = channel;
        this.path = path;
        replaceContent(sha256, mimeType, size);
    }

    /** What an upload to an existing path, or a release, changes: everything but the address. */
    public void replaceContent(String newSha256, String newMimeType, long newSize) {
        this.sha256 = newSha256;
        this.mimeType = newMimeType;
        this.size = newSize;
    }
}
