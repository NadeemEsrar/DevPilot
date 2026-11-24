"""The pipeline: fetch -> chunk -> embed -> store -> retrieve -> generate."""
from threading import Lock

from services import chunker, github_fetcher, llm_client
from services.errors import ServiceError
from services.vector_store import VectorStore

RESULTS = 8
MAX_CHUNKS = 5000

_store: VectorStore | None = None
_lock = Lock()  # one index or search at a time, so an index can't vanish mid-search


def ask(repo: str, branch: str | None, question: str, token: str | None) -> str:
    global _store
    llm_client.require_key()  # fail before doing slow work
    version = github_fetcher.resolve(repo, branch, token)
    with _lock:
        if _store is None:
            _store = VectorStore()
        if not _store.has(version):
            chunks = chunker.chunk_files(github_fetcher.download(version, token))
            if not chunks:
                raise ServiceError(422, "This repository has no text files to index.")
            if len(chunks) > MAX_CHUNKS:
                raise ServiceError(413, "This repository is too large to index.")
            _store.index(version, chunks)
        found = _store.search(version, question, RESULTS)
    text = llm_client.answer(version.full_name, question, found)
    sources = dict.fromkeys(f"{c.path}:{c.start}-{c.end}" for c in found)
    return f"{text}\n\nSearched excerpts:\n" + "\n".join(sources)
