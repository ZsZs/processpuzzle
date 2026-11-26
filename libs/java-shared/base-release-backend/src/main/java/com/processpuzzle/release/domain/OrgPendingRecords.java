package com.processpuzzle.release.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One records file of a starter, queued by its install or upgrade for the organization's next release, which loads it
 * once the definitions it needs are released (see {@code PendingRecordQueue}). Pending while {@code releaseNumber} is
 * null; afterwards part of that release's record, and never changed again.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "org_pending_records")
public class OrgPendingRecords {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_key", nullable = false, updatable = false)
    private String orgKey;

    /** The order the files are loaded in: the order they were queued. */
    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Column(name = "file_name", nullable = false, updatable = false, length = 512)
    private String fileName;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "content", nullable = false, updatable = false)
    private String content;

    /** The release that loaded the file; null while it is pending. */
    @Column(name = "release_number")
    private Long releaseNumber;

    /** How many records the release created from the file; the others were present already. */
    @Column(name = "created_records")
    private Integer createdRecords;

    public OrgPendingRecords(String orgKey, int sequenceNo, String fileName, byte[] yaml) {
        this.orgKey = orgKey;
        this.sequenceNo = sequenceNo;
        this.fileName = fileName;
        this.content = new String(yaml, StandardCharsets.UTF_8);
    }

    public byte[] yaml() {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    /** Records that release {@code number} loaded the file, creating {@code created} records. */
    public void loadedIn(long number, int created) {
        this.releaseNumber = number;
        this.createdRecords = created;
    }
}
