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
 * One file an application brings along — a card image, a logo — recorded by path, with the hash that names its
 * content in the object store: {@link ModuleAsset} one level up. The bytes are not here; they live in the same
 * store under {@code <orgKey>/<sha256>}, shared with the module assets of the organization.
 *
 * <p>A row of its own rather than a part of the app's graph, so that saving the graph never drops a file the
 * form did not carry. The app row holds its draft and published graphs side by side, but a file needs a record
 * per {@link Channel}: an upload writes the draft rows, and publishing the app copies them to the released ones.
 */
@Getter
@Entity
@Table(name = "app_assets")
@IdClass(AppAssetKey.class)
public class AppAsset {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "app_id", length = 100)
    private String appId;

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

    protected AppAsset() {
        // required by JPA
    }

    public AppAsset(String orgKey, String appId, Channel channel, String path, String sha256, String mimeType, long size) {
        this.orgKey = orgKey;
        this.appId = appId;
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
