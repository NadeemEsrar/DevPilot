# RAG service API

Base address: `http://127.0.0.1:8000`. While it runs, `/docs` shows an interactive version.

Every request needs the user's GitHub token, which is used to check access and is not stored:

```text
Authorization: Bearer <GitHub token>
```

`branch` is optional; without it, the repository's default branch is used.

## GET /index

Query: `repo=owner/name`, `branch`. Reports whether embeddings exist.

```json
{"exists": true, "building": false, "created_at": "2026-11-25T10:30:00+00:00", "commit": "3049ca0..."}
```

## POST /index

Creates or re-creates embeddings from the newest commit. Replies when finished, in the same format.

```json
{"repo": "owner/name", "branch": "main"}
```

## POST /ask

The question can have up to 2,000 characters.

```json
{"repo": "owner/name", "branch": "main", "question": "How does login work?"}
```

```json
{"answer": "..."}
```

## Errors

Errors have a `detail` field with a message. For validation errors it is a list.

| Status | Meaning |
|---|---|
| 401 | The Authorization value isn't a Bearer token, or GitHub rejected it |
| 404 | Repository or branch not found, or no access |
| 409 | Embeddings are being created, or don't exist yet |
| 413 | Repository too large |
| 422 | Invalid request, empty repository, or nothing to index |
| 429 | Groq's rate limit was reached |
| 502 | GitHub or Groq failed |
| 503 | Groq key missing or rejected, or the embedding model can't be downloaded |
