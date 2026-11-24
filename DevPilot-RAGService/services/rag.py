"""Indexing: fetch -> chunk -> embed -> store.  Asking: retrieve -> generate."""
from threading import Lock

from services import chunker, github_fetcher, llm_client
from services.errors import ServiceError
from services.vector_store import IndexInfo, VectorStore

RESULTS = 8
MAX_CHUNKS = 5000

_store: VectorStore | None = None
_store_lock = Lock()
_building: set[tuple[int, str]] = set()
_building_lock = Lock()


def status(repo: str, branch: str | None, token: str | None) -> tuple[IndexInfo | None, bool]:
    """Returns the current embeddings (if any) and whether new ones are being built."""
    ref = github_fetcher.resolve(repo, branch, token)
    with _building_lock:
        building = (ref.repo_id, ref.branch) in _building
    return _get_store().info(ref), building


def build(repo: str, branch: str | None, token: str | None) -> IndexInfo:
    """Always uses the newest commit. Existing embeddings stay in use until this succeeds."""
    ref = github_fetcher.resolve(repo, branch, token)
    key = (ref.repo_id, ref.branch)
    with _building_lock:
        if key in _building:
            raise ServiceError(409, "Embeddings are already being created for this repository.")
        _building.add(key)
    try:
        sha = github_fetcher.latest_commit(ref, token)
        chunks = chunker.chunk_files(github_fetcher.download(ref, sha, token))
        if not chunks:
            raise ServiceError(422, "This repository has no text files to index.")
        if len(chunks) > MAX_CHUNKS:
            raise ServiceError(413, "This repository is too large to index.")
        return _get_store().replace(ref, sha, chunks)
    finally:
        with _building_lock:
            _building.discard(key)


def ask(repo: str, branch: str | None, question: str, token: str | None) -> str:
    llm_client.require_key()
    ref = github_fetcher.resolve(repo, branch, token)  # also proves the user still has access
    found = _get_store().search(ref, question, RESULTS)
    if found is None:
        raise ServiceError(409, "Create the vector embeddings for this repository first.")
    text = llm_client.answer(ref.full_name, question, found)
    sources = dict.fromkeys(f"{c.path}:{c.start}-{c.end}" for c in found)
    return f"{text}\n\nSearched excerpts:\n" + "\n".join(sources)


def _get_store() -> VectorStore:
    global _store
    with _store_lock:
        if _store is None:
            _store = VectorStore()
        return _store
