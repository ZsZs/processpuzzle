# syntax=docker/dockerfile:1
#
# The vision server (apps/vision-server). Build context is the repository root:
#   docker build -f tools/docker/vision-server/Dockerfile -t processpuzzle-vision-server .
#   docker build -f tools/docker/vision-server/Dockerfile --target test -t vision-server-test . && docker run --rm vision-server-test
# Dockerfile.dockerignore beside this file keeps the context to the files it needs.
#
# CPU only: torch comes from the CPU wheel index, which is also what keeps CUDA's ~2 GB of libraries out.

FROM python:3.12-slim AS base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1 PIP_DISABLE_PIP_VERSION_CHECK=1
WORKDIR /app

# Unit and contract tests, against the service half only — no model library is installed.
FROM base AS test
COPY apps/vision-server/pyproject.toml ./
COPY apps/vision-server/src ./src
RUN pip install ".[test]"
COPY apps/vision-server/tests ./tests
COPY libs/java-shared/api-contracts/src/main/resources/vision-server-api.yaml ./contract/vision-server-api.yaml
ENV VISION_CONTRACT=/app/contract/vision-server-api.yaml
CMD ["python", "-m", "pytest", "-q"]

FROM base AS runtime
LABEL authors="Zsolt Zsuffa"
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl libglib2.0-0 libgomp1 \
    && rm -rf /var/lib/apt/lists/*
RUN pip install --index-url https://download.pytorch.org/whl/cpu torch torchvision
# Dependencies first, against a stub package, so that a code change does not reinstall them.
COPY apps/vision-server/pyproject.toml ./
RUN mkdir -p src/vision_server && touch src/vision_server/__init__.py && pip install ".[ml]" && rm -rf src build
ENV HF_HOME=/models/hf VISION_MODEL_DIR=/models/easyocr
# Weights are baked in, so a deployment needs no internet egress and a cold start no download. The build
# arg lets CI skip this layer when it only proves that the image builds.
ARG PRELOAD_MODELS=true
COPY tools/docker/vision-server/preload_models.py /tmp/preload_models.py
RUN if [ "$PRELOAD_MODELS" = "true" ]; then python /tmp/preload_models.py; fi
RUN useradd --system --uid 10001 vision && mkdir -p /models && chown -R vision /models
COPY apps/vision-server/src ./src
RUN pip install --no-deps . && rm -rf src build
ENV HF_HUB_OFFLINE=1
USER vision
EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --retries=8 CMD curl -fs http://localhost:8000/v1/health || exit 1
ENTRYPOINT ["python", "-m", "vision_server"]
