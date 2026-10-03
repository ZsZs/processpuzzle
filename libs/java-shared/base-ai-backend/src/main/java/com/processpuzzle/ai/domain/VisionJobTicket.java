package com.processpuzzle.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * This side's record of one job on the vision server: which job, for whom, and the hash of the
 * per-job callback token. The vision server keeps nothing durable, so this ticket is what survives
 * a restart of either side — the poller resubmits a SUBMITTING ticket and chases an overdue SUBMITTED
 * one.
 *
 * <p>Only the token's SHA-256 is stored. The token itself exists in the submission and in the
 * vision server's memory; a database read does not let anyone forge a notification.
 */
@Entity
@Table(name = "ai_vision_job_tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisionJobTicket {

    public enum Kind { ENROLLMENT, EMBEDDING, RECOGNITION }

    public enum Status {
        /** Recorded, not yet accepted by the vision server — the first submission failed or is under way. */
        SUBMITTING,
        SUBMITTED,
        COMPLETED,
        FAILED
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "org_key", nullable = false, length = 63)
    private String orgKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "callback_token_hash", nullable = false, length = 64)
    private String callbackTokenHash;

    @Column(name = "submit_attempts", nullable = false)
    private int submitAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Last transition, which is what the poller measures overdue against. */
    @Column(name = "touched_at", nullable = false)
    private Instant touchedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    /**
     * An open ticket, not yet submitted. Its callback token is issued by {@link #newCallbackToken()}
     * at each submission; until then the stored hash is of a token nobody holds.
     */
    public static VisionJobTicket open(String orgKey, Kind kind, Instant now) {
        VisionJobTicket ticket = new VisionJobTicket();
        ticket.jobId = UUID.randomUUID();
        ticket.orgKey = orgKey;
        ticket.kind = kind;
        ticket.status = Status.SUBMITTING;
        ticket.callbackTokenHash = hash(randomToken());
        ticket.createdAt = now;
        ticket.touchedAt = now;
        return ticket;
    }

    /**
     * A fresh callback token, returned once to go into the submission; only its hash is kept. A
     * resubmission needs a new one anyway — the vision server lost the old one with the job.
     */
    public String newCallbackToken() {
        String token = randomToken();
        this.callbackTokenHash = hash(token);
        return token;
    }

    public boolean accepts(String token) {
        return token != null && MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.US_ASCII),
                callbackTokenHash.getBytes(StandardCharsets.US_ASCII));
    }

    public boolean isOpen() {
        return status == Status.SUBMITTING || status == Status.SUBMITTED;
    }

    public void submitting(Instant now) {
        this.status = Status.SUBMITTING;
        this.submitAttempts++;
        this.touchedAt = now;
    }

    public void submitted(Instant now) {
        this.status = Status.SUBMITTED;
        this.submittedAt = now;
        this.touchedAt = now;
    }

    public void completed(Instant now) {
        this.status = Status.COMPLETED;
        this.finishedAt = now;
        this.touchedAt = now;
    }

    public void failed(String reason, Instant now) {
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.finishedAt = now;
        this.touchedAt = now;
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String randomToken() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }
}
