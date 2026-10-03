"""Downloads the default models into the image at build time (HF_HOME, VISION_MODEL_DIR)."""

import os

import easyocr
from transformers import AutoImageProcessor, AutoModel, AutoModelForObjectDetection

DETECTOR = os.environ.get("VISION_DETECTOR_MODEL", "PekingU/rtdetr_r18vd_coco_o365")
EMBEDDING = os.environ.get("VISION_EMBEDDING_MODEL", "facebook/dinov2-small")

AutoImageProcessor.from_pretrained(DETECTOR)
AutoModelForObjectDetection.from_pretrained(DETECTOR)
AutoImageProcessor.from_pretrained(EMBEDDING)
AutoModel.from_pretrained(EMBEDDING)
MODEL_DIR = os.environ["VISION_MODEL_DIR"]
easyocr.Reader(
    os.environ.get("VISION_OCR_LANGUAGES", "en").split(","),
    gpu=False,
    model_storage_directory=MODEL_DIR,
    user_network_directory=os.path.join(MODEL_DIR, "user_network"),
)
print("models preloaded")
