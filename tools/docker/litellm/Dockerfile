# syntax=docker/dockerfile:1
#
# The LLM gateway: upstream LiteLLM plus the model aliases of config.yaml, so the configuration travels
# with the image like every other shared service here. Build context is the repository root:
#   docker build -f tools/docker/litellm/Dockerfile -t processpuzzle-litellm .
FROM ghcr.io/berriai/litellm:main-stable

COPY tools/docker/litellm/config.yaml /app/config.yaml

CMD ["--config", "/app/config.yaml", "--port", "4000"]
