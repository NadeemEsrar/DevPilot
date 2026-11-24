"""DevPilot RAG service: answers questions about GitHub repositories."""
from typing import Annotated

from dotenv import load_dotenv
from fastapi import FastAPI, Header, Request
from fastapi.responses import JSONResponse

from models.schemas import AskRequest, AskResponse
from services import rag
from services.errors import ServiceError

load_dotenv()
app = FastAPI(title="DevPilot RAG Service")


@app.exception_handler(ServiceError)
def service_error(_: Request, error: ServiceError) -> JSONResponse:
    return JSONResponse(status_code=error.status, content={"detail": error.message})


# A plain "def" endpoint runs in a worker thread, so slow work doesn't block the server
@app.post("/ask")
def ask(request: AskRequest, authorization: Annotated[str, Header()]) -> AskResponse:
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise ServiceError(401, "Please log in to GitHub.")
    return AskResponse(answer=rag.ask(request.repo, request.branch, request.question, token.strip()))
