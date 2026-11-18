package com.processpuzzle.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The model service an organization has subscribed to for its design assistant, with the organization's own API key.
 * The platform pays for no assistant use: an organization without a row has no assistant. The key is stored sealed
 * and is never returned by any read. Erased with its organization.
 */
@Entity
@Table(name = "ai_organization_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationAiSubscription {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiProvider provider;

    @Column(nullable = false, length = 200)
    private String model;

    /** Only for {@link AiProvider#OPENAI_COMPATIBLE}. */
    @Column(name = "base_url", length = 500)
    private String baseUrl;

    /** The API key, sealed by the assistant's cipher. */
    @Column(name = "sealed_key", nullable = false, length = 2000)
    private String sealedKey;

    @Column(name = "updated_by", nullable = false, length = 200)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OrganizationAiSubscription(String orgKey) {
        this.orgKey = orgKey;
    }

    public void update(AiProvider provider, String model, String baseUrl, String sealedKey, String updatedBy,
                       Instant now) {
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.sealedKey = sealedKey;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }
}
