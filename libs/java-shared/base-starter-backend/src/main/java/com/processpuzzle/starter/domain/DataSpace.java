package com.processpuzzle.starter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The common of a business starter: the organization, kept by the platform, whose released definitions and records
 * every organization that installed the starter reads. One per starter, and never removed.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "starter_data_space")
public class DataSpace {

    @Id
    @Column(name = "common_key", nullable = false, updatable = false)
    private String commonKey;

    @Column(name = "starter_id", nullable = false, updatable = false, unique = true)
    private String starterId;

    public DataSpace(String commonKey, String starterId) {
        this.commonKey = commonKey;
        this.starterId = starterId;
    }
}
