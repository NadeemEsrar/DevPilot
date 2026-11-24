"""Cuts files into overlapping pieces that remember where they came from."""
from dataclasses import dataclass

# The embedding model reads only about 256 tokens, so longer chunks would be partly ignored
MAX_CHARS = 900
OVERLAP_LINES = 3


@dataclass(frozen=True)
class Chunk:
    path: str
    start: int  # first line, counting from 1
    end: int    # last line
    text: str


def chunk_files(files: dict[str, str]) -> list[Chunk]:
    return [c for path, text in sorted(files.items()) for c in chunk_text(path, text)]


def chunk_text(path: str, text: str) -> list[Chunk]:
    # Very long lines are split so that no chunk exceeds the limit
    pieces = [(number, line[i:i + MAX_CHARS])
              for number, line in enumerate(text.split("\n"), start=1)
              for i in range(0, max(len(line), 1), MAX_CHARS)]
    chunks = []
    start = 0
    while start < len(pieces):
        end, size = start, 0
        while end < len(pieces) and (end == start or size + len(pieces[end][1]) + 1 <= MAX_CHARS):
            size += len(pieces[end][1]) + 1
            end += 1
        body = "\n".join(line for _, line in pieces[start:end])
        if body.strip():
            chunks.append(Chunk(path, pieces[start][0], pieces[end - 1][0], body))
        if end == len(pieces):
            break
        start = max(end - OVERLAP_LINES, start + 1)
    return chunks
