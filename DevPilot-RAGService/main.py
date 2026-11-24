"""DevPilot RAG service: answers questions about GitHub repositories."""
from typing import Annotated

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, Header, Query, Request
from fastapi.responses import JSONResponse

from models.schemas import AskRequest, AskResponse, IndexStatus, RepoRequest
from services import rag
from services.errors import ServiceError

load_dotenv()
app = FastAPI(title="DevPilot RAG Service")


@app.exception_handler(ServiceError)
def service_error(_: Request, error: ServiceError) -> JSONResponse:
    return JSONResponse(status_code=error.status, content={"detail": error.message})


def github_token(authorization: Annotated[str, Header()]) -> str:
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise ServiceError(401, "Please log in to GitHub.")
    return token.strip()


Token = Annotated[str, Depends(github_token)]


# Plain "def" endpoints run in worker threads, so slow work doesn't block the server
@app.get("/index")
def index_status(request: Annotated[RepoRequest, Query()], token: Token) -> IndexStatus:
    info, building = rag.status(request.repo, request.branch, token)
    if info is None:
        return IndexStatus(exists=False, building=building)
    return IndexStatus(exists=True, building=building, created_at=info.created_at, commit=info.commit)


@app.post("/index")
def create_index(request: RepoRequest, token: Token) -> IndexStatus:
    info = rag.build(request.repo, request.branch, token)
    return IndexStatus(exists=True, created_at=info.created_at, commit=info.commit)


@app.post("/ask")
def ask(request: AskRequest, token: Token) -> AskResponse:
    return AskResponse(answer=rag.ask(request.repo, request.branch, request.question, token))
