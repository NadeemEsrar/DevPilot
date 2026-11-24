"""DevPilot RAG service. Phase 7: /ask returns a fixed answer."""
from fastapi import FastAPI

from models.schemas import AskRequest, AskResponse

app = FastAPI(title="DevPilot RAG Service")


@app.post("/ask")
def ask(request: AskRequest) -> AskResponse:
    return AskResponse(
        answer=f"Stub answer about {request.repo}. You asked: {request.question}"
    )
