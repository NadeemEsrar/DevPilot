"""The API contract shared with the JavaFX app."""
from pydantic import BaseModel, ConfigDict, Field


class AskRequest(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    repo: str = Field(pattern=r"^[\w.-]+/[\w.-]+$", examples=["owner/name"])
    branch: str | None = None  # None = the repository's default branch
    question: str = Field(min_length=1, max_length=2000)


class AskResponse(BaseModel):
    answer: str
