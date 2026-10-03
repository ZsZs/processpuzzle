"""Entry point: ``python -m vision_server`` serves the API on VISION_PORT (default 8000)."""

from __future__ import annotations

import logging
import os

import uvicorn

from vision_server.app import create_app
from vision_server.config import Settings
from vision_server.pipeline.vision import VisionPipeline


def main() -> None:
    logging.basicConfig(
        level=os.environ.get("VISION_LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(name)s %(message)s"
    )
    settings = Settings.from_env()
    app = create_app(settings, VisionPipeline(settings))
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("VISION_PORT", "8000")), log_level="info")


if __name__ == "__main__":
    main()
