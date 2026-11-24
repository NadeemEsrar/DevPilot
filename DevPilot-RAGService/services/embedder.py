"""Turns text into vectors with a small model that runs on this computer."""
import httpx
from chromadb.utils.embedding_functions import ONNXMiniLM_L6_V2
from langchain_core.embeddings import Embeddings

from services.errors import ServiceError


class LocalEmbeddings(Embeddings):
    """Adapts ChromaDB's bundled model to LangChain's Embeddings interface."""

    def __init__(self):
        # CPU only: predictable, and fast enough for a model this small
        self._model = ONNXMiniLM_L6_V2(preferred_providers=["CPUExecutionProvider"])

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        try:
            return [vector.tolist() for vector in self._model(texts)]  # downloads the model the first time
        except httpx.HTTPError as e:
            raise ServiceError(503, "Could not download the embedding model. Check your internet and try again.") from e

    def embed_query(self, text: str) -> list[float]:
        return self.embed_documents([text])[0]
