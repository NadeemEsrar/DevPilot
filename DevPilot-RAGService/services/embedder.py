"""Turns text into vectors with a small model that runs on this computer."""
from functools import lru_cache

import httpx
from chromadb.utils.embedding_functions import ONNXMiniLM_L6_V2

from services.errors import ServiceError


@lru_cache(maxsize=1)
def _model() -> ONNXMiniLM_L6_V2:
    # CPU only: predictable, and fast enough for a model this small
    return ONNXMiniLM_L6_V2(preferred_providers=["CPUExecutionProvider"])


def embed(texts: list[str]) -> list:
    try:
        return _model()(texts)  # downloads the model the first time
    except httpx.HTTPError as e:
        raise ServiceError(503, "Could not download the embedding model. Check your internet and try again.") from e
