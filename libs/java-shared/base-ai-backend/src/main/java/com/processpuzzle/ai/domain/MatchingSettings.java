package com.processpuzzle.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Fusion and acceptance settings of a {@link RecognitionProfile}. The defaults are the design
 * document's starting point (§3.3) and the contract's schema defaults, and are meant to be calibrated
 * on labeled clips.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MatchingSettings {

    public static final double DEFAULT_IDENTIFIER_WEIGHT = 0.6;
    public static final double DEFAULT_ACCEPT_SCORE = 0.75;
    public static final double DEFAULT_ACCEPT_MARGIN = 0.1;
    public static final double DEFAULT_SAMPLE_FPS = 3;

    @Column(name = "identifier_weight", nullable = false)
    private double identifierWeight = DEFAULT_IDENTIFIER_WEIGHT;

    @Column(name = "accept_score", nullable = false)
    private double acceptScore = DEFAULT_ACCEPT_SCORE;

    @Column(name = "accept_margin", nullable = false)
    private double acceptMargin = DEFAULT_ACCEPT_MARGIN;

    @Column(name = "sample_fps", nullable = false)
    private double sampleFps = DEFAULT_SAMPLE_FPS;

    public static MatchingSettings defaults() {
        return new MatchingSettings();
    }
}
