"""Cuts files into overlapping pieces that remember where they came from."""
from pathlib import PurePosixPath

from langchain_core.documents import Document
from langchain_text_splitters import Language, RecursiveCharacterTextSplitter

# The embedding model reads only about 256 tokens, so longer chunks would be partly ignored
MAX_CHARS = 900
OVERLAP_CHARS = 120

# Lets LangChain prefer natural boundaries such as classes and functions
LANGUAGES = {
    ".java": Language.JAVA, ".py": Language.PYTHON, ".js": Language.JS, ".jsx": Language.JS,
    ".ts": Language.TS, ".tsx": Language.TS, ".go": Language.GO, ".rs": Language.RUST,
    ".cpp": Language.CPP, ".c": Language.C, ".cs": Language.CSHARP, ".kt": Language.KOTLIN,
    ".php": Language.PHP, ".rb": Language.RUBY, ".html": Language.HTML, ".md": Language.MARKDOWN,
}


def chunk_files(files: dict[str, str]) -> list[Document]:
    return [c for path, text in sorted(files.items()) for c in chunk_text(path, text)]


def chunk_text(path: str, text: str) -> list[Document]:
    options = {"chunk_size": MAX_CHARS, "chunk_overlap": OVERLAP_CHARS, "add_start_index": True}
    language = LANGUAGES.get(PurePosixPath(path).suffix.lower())
    splitter = (RecursiveCharacterTextSplitter.from_language(language, **options)
                if language else RecursiveCharacterTextSplitter(**options))
    chunks = []
    for piece in splitter.create_documents([text]):
        start = text.count("\n", 0, piece.metadata["start_index"]) + 1
        chunks.append(Document(
            # The path is included because file names are good search clues
            page_content=f"{path}\n{piece.page_content}",
            metadata={"path": path, "start": start,
                      "end": start + piece.page_content.count("\n")}))
    return chunks
