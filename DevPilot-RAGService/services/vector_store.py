"""Stores chunk vectors in ChromaDB, one collection per repository, branch and commit."""
import hashlib
from pathlib import Path

import chromadb
from chromadb.config import Settings

from services.chunker import Chunk
from services.embedder import embed
from services.github_fetcher import RepoVersion

DATA_DIR = Path(__file__).resolve().parent.parent / "chroma_data"
BATCH = 64


class VectorStore:
    def __init__(self, path: Path = DATA_DIR):
        self._client = chromadb.PersistentClient(
            path=str(path), settings=Settings(anonymized_telemetry=False))

    def has(self, version: RepoVersion) -> bool:
        return _name(version) in self._names()

    def index(self, version: RepoVersion, chunks: list[Chunk]) -> None:
        """Builds under a temporary name, so a half-built index is never used."""
        temp = f"building-{version.repo_id}"
        if temp in self._names():
            self._client.delete_collection(temp)
        collection = self._client.create_collection(
            temp, embedding_function=None, configuration={"hnsw": {"space": "cosine"}})
        for i in range(0, len(chunks), BATCH):
            batch = chunks[i:i + BATCH]
            collection.add(
                ids=[str(i + n) for n in range(len(batch))],
                # The path is embedded too: file names are good search clues
                embeddings=embed([f"{c.path}\n{c.text}" for c in batch]),
                documents=[c.text for c in batch],
                metadatas=[{"path": c.path, "start": c.start, "end": c.end} for c in batch])
        collection.modify(name=_name(version))
        for name in self._names():  # older commits of this branch
            if name.startswith(_prefix(version)) and name != _name(version):
                self._client.delete_collection(name)

    def search(self, version: RepoVersion, question: str, count: int) -> list[Chunk]:
        collection = self._client.get_collection(_name(version), embedding_function=None)
        found = collection.query(query_embeddings=embed([question]), n_results=count)
        return [Chunk(m["path"], m["start"], m["end"], text)
                for text, m in zip(found["documents"][0], found["metadatas"][0])]

    def _names(self) -> list[str]:
        names, page = [], 500
        while True:
            batch = self._client.list_collections(limit=page, offset=len(names))
            names += [c.name for c in batch]
            if len(batch) < page:
                return names


def _prefix(v: RepoVersion) -> str:
    return f"repo-{v.repo_id}-{hashlib.sha256(v.branch.encode()).hexdigest()[:12]}-"


def _name(v: RepoVersion) -> str:
    return _prefix(v) + v.sha
