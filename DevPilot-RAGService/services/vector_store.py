"""Stores chunk vectors in ChromaDB, one collection per repository and branch."""
import hashlib
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock
from uuid import uuid4

import chromadb
from chromadb.config import Settings

from services.chunker import Chunk
from services.embedder import embed
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

    def replace(self, ref: RepoRef, commit: str, chunks: list[Chunk]) -> IndexInfo:
        """Builds under a temporary name, then swaps, so a failure keeps the old index."""
        build_id = uuid4().hex
        temp = f"building-{build_id}"
        collection = self._client.create_collection(
            temp, embedding_function=None, configuration={"hnsw": {"space": "cosine"}})
        try:
            for i in range(0, len(chunks), BATCH):
                batch = chunks[i:i + BATCH]
                collection.add(
                    ids=[str(i + n) for n in range(len(batch))],
                    # The path is embedded too: file names are good search clues
                    embeddings=embed([f"{c.path}\n{c.text}" for c in batch]),
                    documents=[c.text for c in batch],
                    metadatas=[{"path": c.path, "start": c.start, "end": c.end} for c in batch])
        except BaseException:
            self._client.delete_collection(temp)
            raise
        info = IndexInfo(commit, datetime.now(timezone.utc).isoformat())
        name = _prefix(ref) + build_id
        with self._swap_lock:
            collection.modify(name=name, metadata={"commit": commit, "created_at": info.created_at})
            for other in self._collections():
                if other.name.startswith(_prefix(ref)) and other.name != name:
                    self._client.delete_collection(other.name)
        return info

    def search(self, ref: RepoRef, question: str, count: int) -> list[Chunk] | None:
        """Returns None if this repository has no embeddings."""
        vector = embed([question])
        with self._swap_lock:
            current = self._current(ref)
            if current is None:
                return None
            collection = self._client.get_collection(current.name, embedding_function=None)
            found = collection.query(query_embeddings=vector, n_results=count)
        return [Chunk(m["path"], m["start"], m["end"], text)
                for text, m in zip(found["documents"][0], found["metadatas"][0])]

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
