package com.processpuzzle.ai.assistant;

import java.util.List;

/** Asks a model service which models an API key may use. */
@FunctionalInterface
public interface ModelLister {

    /**
     * @param baseUrl the service's OpenAI-compatible root, e.g. {@code https://api.mistral.ai/v1}
     * @return the model names, sorted and without duplicates
     * @throws ModelListException when the service cannot be asked, or refuses the key
     */
    List<String> list(String baseUrl, String apiKey);

    /** The service could not list its models; the message is fit to show the administrator. */
    class ModelListException extends RuntimeException {

        public ModelListException(String message) {
            super(message);
        }

        public ModelListException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
