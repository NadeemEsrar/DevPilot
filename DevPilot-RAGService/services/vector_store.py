"""Stores chunk vectors in ChromaDB, one collection per repository and branch."""
import hashlib
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock
from uuid import uuid4

import chromadb
from chromadb.config import Settings
from langchain_chroma import Chroma
from langchain_core.documents import Document

from services.embedder import LocalEmbeddings
from services.github_fetcher import RepoRef

DATA_DIR = Path(__file__).resolve().parent.parent / "chroma_data"
BATCH = 64


@dataclass(frozen=True)
class IndexInfo:
    commit: str
    created_at: str  # ISO 8601, UTC


class VectorStore:
    def __init__(self, path: Path = DATA_DIR):
        self._client = chromadb.PersistentClient(
            path=str(path), settings=Settings(anonymized_telemetry=False))
        self._embeddings = LocalEmbeddings()
        self._swap_lock = Lock()  # an index can't be deleted while it is being searched
        for collection in self._collections():  # left over from an interrupted build
            if collection.name.startswith("building-"):
                self._client.delete_collection(collection.name)

    def info(self, ref: RepoRef) -> IndexInfo | None:
        with self._swap_lock:
            current = self._current(ref)
        if current is None:
            return None
        return IndexInfo(current.metadata["commit"], current.metadata["created_at"])

    def replace(self, ref: RepoRef, commit: str, chunks: list[Document]) -> IndexInfo:
        """Builds under a temporary name, then swaps, so a failure keeps the old index."""
        build_id = uuid4().hex
        temp = f"building-{build_id}"
        try:
            store = Chroma(client=self._client, collection_name=temp, embedding_function=self._embeddings,
                           collection_configuration={"hnsw": {"space": "cosine"}})
            for i in range(0, len(chunks), BATCH):
                store.add_documents(chunks[i:i + BATCH])
        except BaseException:
            if any(c.name == temp for c in self._collections()):
                self._client.delete_collection(temp)
            raise
        info = IndexInfo(commit, datetime.now(timezone.utc).isoformat())
        name = _prefix(ref) + build_id
        with self._swap_lock:
            self._client.get_collection(temp, embedding_function=None).modify(
                name=name, metadata={"commit": commit, "created_at": info.created_at})
            for other in self._collections():
                if other.name.startswith(_prefix(ref)) and other.name != name:
                    self._client.delete_collection(other.name)
        return info

    def search(self, ref: RepoRef, question: str, count: int) -> list[Document] | None:
        """Returns None if this repository has no embeddings."""
        vector = self._embeddings.embed_query(question)
        with self._swap_lock:
            current = self._current(ref)
            if current is None:
                return None
            store = Chroma(client=self._client, collection_name=current.name,
                           embedding_function=self._embeddings, create_collection_if_not_exists=False)
            return store.similarity_search_by_vector(vector, k=count)

    def _current(self, ref: RepoRef):
        """The newest index; ones without a date come from an older DevPilot version."""
        dated = [c for c in self._collections()
                 if c.name.startswith(_prefix(ref)) and (c.metadata or {}).get("created_at")]
        return max(dated, key=lambda c: c.metadata["created_at"], default=None)

    def _collections(self) -> list:
        result, page = [], 500
        while True:
            batch = self._client.list_collections(limit=page, offset=len(result))
            result += batch
            if len(batch) < page:
                return result


def _prefix(ref: RepoRef) -> str:
    return f"repo-{ref.repo_id}-{hashlib.sha256(ref.branch.encode()).hexdigest()[:12]}-"
