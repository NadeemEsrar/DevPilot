# Functional Requirements

This document lists what DevPilot must do. Each requirement has an ID that the [test cases](TEST_CASES.md) refer to.

**Status:** ✅ implemented · 🟡 partly implemented · ⬜ not implemented

## 1. Actors

| Actor | Description |
|---|---|
| User | A developer with a GitHub account who uses the desktop app |
| GitHub | OAuth Device Flow, REST API and repository archive download |
| Groq | Hosted LLM that writes the answers |
| RAG service | Local Python service that indexes repositories and retrieves code |

## 2. Authentication

| ID | Requirement | Status |
|---|---|---|
| FR-AUTH-01 | The app logs the user in with the GitHub OAuth Device Flow: it shows a short code and the GitHub verification address. | ✅ |
| FR-AUTH-02 | The app polls GitHub until the login is approved, cancelled or expired, and honours `slow_down` by lengthening the polling interval. | ✅ |
| FR-AUTH-03 | GitHub reports polling errors with HTTP 200, so the app reads the `error` field in the JSON. | ✅ |
| FR-AUTH-04 | The app checks that the granted scope includes `read:user` and `repo`; otherwise it asks the user to log in again. | ✅ |
| FR-AUTH-05 | A saved login is reused when the app starts, so the user skips the login screen. | ✅ |
| FR-AUTH-06 | Logging out removes the token from the local database but keeps the user's saved repositories. | ✅ |
| FR-AUTH-07 | If GitHub answers 401 at any point, the app logs the user out with a "session ended" message. | ✅ |

## 3. Repository dashboard

| ID | Requirement | Status |
|---|---|---|
| FR-REPO-01 | The dashboard lists the user's public and private repositories, with name, language, visibility and description. | ✅ |
| FR-REPO-02 | Saved repositories are shown first, so the list still appears when GitHub cannot be reached. | ✅ |
| FR-REPO-03 | **Refresh** fetches every page of repositories from GitHub and updates the local database in one transaction. | ✅ |
| FR-REPO-04 | If any page fails to download, nothing is changed or deleted locally. | ✅ |
| FR-REPO-05 | A repository that is no longer on GitHub is removed locally together with its chats. | ✅ |
| FR-REPO-06 | The user can filter the list by name or language. | ✅ |
| FR-REPO-07 | The user opens a repository's chat with **Open chat** or by double-clicking it. | ✅ |
| FR-REPO-08 | The user can choose a branch other than the default branch. | ⬜ |

## 4. Indexing (vector embeddings)

| ID | Requirement | Status |
|---|---|---|
| FR-IDX-01 | The user can create embeddings for the open repository with **Create vector embeddings**, and re-create them with **Re-create embeddings**. | ✅ |
| FR-IDX-02 | The service checks with GitHub that the user can read the repository on every request. | ✅ |
| FR-IDX-03 | Indexing uses the newest commit of the branch and records the commit SHA and creation time. | ✅ |
| FR-IDX-04 | The service downloads the repository archive, reads it in memory and keeps only text files. | ✅ |
| FR-IDX-05 | The service skips: dependency and build folders, lock files, key and credential files, `.env*` files, minified files, `.csv`, `.svg`, `.ipynb`, binary files, non-UTF-8 files and files over 200 KB. | ✅ |
| FR-IDX-06 | Files are cut into overlapping chunks of at most 900 characters (120 overlap), preferring code boundaries for known languages. Each chunk keeps its path and line range. | ✅ |
| FR-IDX-07 | Chunks are embedded locally with all-MiniLM-L6-v2 and stored in ChromaDB, one collection per repository and branch. | ✅ |
| FR-IDX-08 | New embeddings are built under a temporary name and swapped in only when complete, so a failed build keeps the old embeddings. | ✅ |
| FR-IDX-09 | Only one build per repository and branch can run at a time; a second request gets HTTP 409. | ✅ |
| FR-IDX-10 | A repository with no text files is rejected (422). One with more than 5,000 chunks or a 100 MB archive is rejected (413). | ✅ |
| FR-IDX-11 | The chat screen shows whether embeddings exist, their date and commit, and a "Creating embeddings..." indicator while building. | ✅ |
| FR-IDX-12 | The user can delete a repository's embeddings from the app. | ⬜ |
| FR-IDX-13 | The app shows indexing progress as a percentage. | ⬜ |

## 5. Asking questions

| ID | Requirement | Status |
|---|---|---|
| FR-ASK-01 | The user types a question of 1 to 2,000 characters and presses **Send** or Enter. | ✅ |
| FR-ASK-02 | The service finds the 8 chunks closest to the question and sends them, with the question, to Groq. | ✅ |
| FR-ASK-03 | The prompt tells the model to answer only from the excerpts, to say so when the answer is not there, to name the files it used, and to ignore instructions written inside the excerpts. | ✅ |
| FR-ASK-04 | The answer ends with a list of the searched excerpts as `path:start-end`. | ✅ |
| FR-ASK-05 | If no embeddings exist, the service replies 409 and the app asks the user to create them first. | ✅ |
| FR-ASK-06 | The app shows the answer with simple Markdown: paragraphs, headings, lists, bold, inline code and code blocks. | ✅ |
| FR-ASK-07 | A "Thinking..." indicator shows while the answer is being produced. | ✅ |
| FR-ASK-08 | Answers appear word by word as they are written (streaming). | ⬜ |
| FR-ASK-09 | Earlier messages of the conversation are sent to the model as context. | ⬜ |

## 6. Chat history

| ID | Requirement | Status |
|---|---|---|
| FR-CHAT-01 | Each question and answer is saved per user and repository. | ✅ |
| FR-CHAT-02 | Opening a repository's chat loads its saved conversation in the order it happened. | ✅ |
| FR-CHAT-03 | Deleting a user or repository deletes its chat history. | ✅ |
| FR-CHAT-04 | The user can clear a conversation from the chat screen. (The database method exists; there is no button.) | 🟡 |

## 7. Code browsing

| ID | Requirement | Status |
|---|---|---|
| FR-CODE-01 | The chat screen shows a file tree of the repository, taken from the commit that was embedded (or the default branch if there are none). | ✅ |
| FR-CODE-02 | Selecting a file shows its text in a read-only code viewer. Files over 500 KB are not shown. | ✅ |
| FR-CODE-03 | If GitHub shortens the file list (very large repository), the app tells the user. | ✅ |
| FR-CODE-04 | Syntax colouring and copy-to-clipboard in the code viewer. | ⬜ |

## 8. Errors

| ID | Requirement | Status |
|---|---|---|
| FR-ERR-01 | Every service failure carries a message that is safe to show, and the app displays it with a **Retry** action where it makes sense. | ✅ |
| FR-ERR-02 | HTTP errors from the RAG service follow the table in [api.md](api.md): 401, 404, 409, 413, 422, 429, 502, 503. | ✅ |
| FR-ERR-03 | A missing or rejected Groq key gives HTTP 503 with a message that tells the user to add `GROQ_API_KEY` to `.env`. | ✅ |
| FR-ERR-04 | Groq rate limiting gives HTTP 429. | ✅ |

## 9. Out of scope (for now)

- Writing to GitHub (DevPilot only reads)
- Several users on one RAG service
- Authentication of the RAG service itself (it must run on `127.0.0.1`)
- Packaging as an installer or one-click launcher
