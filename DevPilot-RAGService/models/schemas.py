"""The API contract shared with the JavaFX app."""
from pydantic import BaseModel, ConfigDict, Field


class RepoRequest(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    repo: str = Field(pattern=r"^[A-Za-z0-9_-]+/[A-Za-z0-9_.-]*[A-Za-z0-9_-][A-Za-z0-9_.-]*$", examples=["owner/name"])
    branch: str | None = None  # None = the repository's default branch


class AskRequest(RepoRequest):
    question: str = Field(min_length=1, max_length=2000)


class IndexStatus(BaseModel):
    exists: bool
    building: bool = False
    created_at: str | None = None
    commit: str | None = None


class AskResponse(BaseModel):
    answer: str
