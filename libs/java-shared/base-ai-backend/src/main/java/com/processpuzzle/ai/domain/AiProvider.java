package com.processpuzzle.ai.domain;

/** The kinds of model service an organization can bring its own key for. */
public enum AiProvider {
    /** Mistral's hosted API; the address is fixed. */
    MISTRAL,
    /** Any service that speaks the OpenAI chat protocol (OpenAI, Scaleway, a gateway); the address is the organization's. */
    OPENAI_COMPATIBLE
}
