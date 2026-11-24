"""Checks access and downloads a repository's text files from GitHub."""
import io
import zipfile
from dataclasses import dataclass
from pathlib import PurePosixPath
from urllib.parse import quote

import httpx

from services.errors import ServiceError

API = "https://api.github.com"
MAX_ZIP_BYTES = 100 * 1024 * 1024
MAX_FILE_BYTES = 200 * 1024
SKIP_DIRS = {".git", "node_modules", "target", "build", "dist", "vendor", "venv", ".venv",
             "__pycache__", ".idea", ".gradle", ".next", "bin", "obj"}
SKIP_NAMES = {"package-lock.json", "yarn.lock", "pnpm-lock.yaml", "poetry.lock",
              "id_rsa", "id_ed25519", "credentials.json", ".npmrc", ".netrc"}
SKIP_SUFFIXES = {".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".csv", ".svg", ".ipynb"}


@dataclass(frozen=True)
class RepoRef:
    repo_id: int
    full_name: str
    branch: str


def resolve(repo: str, branch: str | None, token: str | None) -> RepoRef:
    """Runs on every request: proves the user can read the repository."""
    with _client(token) as client:
        info = _get(client, f"/repos/{repo}").json()
    return RepoRef(info["id"], info["full_name"], branch or info["default_branch"])


def latest_commit(ref: RepoRef, token: str | None) -> str:
    with _client(token) as client:
        return _get(client, f"/repositories/{ref.repo_id}/commits/{quote(ref.branch, safe='')}").json()["sha"]


def download(ref: RepoRef, sha: str, token: str | None) -> dict[str, str]:
    """Returns {path: text} for the files worth indexing."""
    data = bytearray()
    try:
        # httpx drops the Authorization header when GitHub redirects to another host
        with _client(token) as client, client.stream(
                "GET", f"/repositories/{ref.repo_id}/zipball/{sha}") as response:
            _check(response)
            for part in response.iter_bytes():
                data += part
                if len(data) > MAX_ZIP_BYTES:
                    raise ServiceError(413, "This repository is too large to index.")
    except httpx.HTTPError as e:
        raise ServiceError(502, "Could not download the repository from GitHub.") from e
    return extract(bytes(data))


def extract(zip_bytes: bytes) -> dict[str, str]:
    """Reads files in memory; nothing from the archive is written to disk or run."""
    files = {}
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as archive:
        for entry in archive.infolist():
            path = entry.filename.partition("/")[2]  # drop GitHub's top-level folder
            if entry.is_dir() or not _wanted(path) or entry.file_size > MAX_FILE_BYTES:
                continue
            raw = archive.read(entry)
            if b"\0" in raw:
                continue  # binary
            try:
                text = raw.decode("utf-8")
            except UnicodeDecodeError:
                continue
            if text.strip():
                files[path] = text
    return files


def _wanted(path: str) -> bool:
    p = PurePosixPath(path)
    name = p.name.lower()
    return not (SKIP_DIRS & set(p.parts[:-1]) or name in SKIP_NAMES
                or p.suffix.lower() in SKIP_SUFFIXES
                or name.startswith(".env") or ".min." in name)


def _client(token: str | None) -> httpx.Client:
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "python-httpx"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return httpx.Client(base_url=API, headers=headers, timeout=30, follow_redirects=True)


def _get(client: httpx.Client, url: str) -> httpx.Response:
    try:
        response = client.get(url)
    except httpx.HTTPError as e:
        raise ServiceError(502, "Could not reach GitHub.") from e
    _check(response)
    return response


def _check(response: httpx.Response) -> None:
    status = response.status_code
    if status == 200:
        return
    if status == 401:
        raise ServiceError(401, "GitHub rejected your login. Please log in again.")
    if status in (404, 422):
        raise ServiceError(404, "Repository or branch not found, or you no longer have access.")
    if status == 409:
        raise ServiceError(422, "This repository is empty.")
    if status in (403, 429):
        raise ServiceError(502, "GitHub refused the request. You may have hit its rate limit.")
    raise ServiceError(502, f"GitHub returned an error (HTTP {status}).")
