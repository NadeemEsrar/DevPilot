# Non-Functional Requirements

Quality targets for DevPilot. Each has an ID, a measurable target where possible, and how it is met or checked today.

**Status:** ✅ met · 🟡 partly met · ⬜ not met or not measured

## 1. Performance

| ID | Requirement | How it is met / checked | Status |
|---|---|---|---|
| NFR-PERF-01 | The UI never freezes. Network and database work runs off the JavaFX thread. | `ui/Background` wraps a JavaFX `Task` in a daemon thread; callbacks run on the JavaFX thread. | ✅ |
| NFR-PERF-02 | The RAG service does not block while handling a slow request. | Endpoints are plain `def`, so FastAPI runs them in worker threads. | ✅ |
| NFR-PERF-03 | Search over a repository's embeddings is fast. Target: retrieval under 1 second for a repository of up to 5,000 chunks, not counting the Groq call. | ChromaDB cosine search over 8 results. Not yet measured. | ⬜ |
| NFR-PERF-04 | Indexing a typical small repository (under 500 files) finishes in a few minutes on a laptop CPU. | Local ONNX model, batches of 64 chunks. Not yet measured. | ⬜ |
| NFR-PERF-05 | The app's request timeout for indexing and asking is long enough for large repositories. | 10-minute client timeout; 60-second Groq timeout. | ✅ |

## 2. Scalability limits

| ID | Requirement | Status |
|---|---|---|
| NFR-SCAL-01 | A repository archive is limited to 100 MB, a file to 200 KB and an index to 5,000 chunks, and the user gets a clear error beyond that. | ✅ |
| NFR-SCAL-02 | The design is for one user on one computer. | ✅ |
| NFR-SCAL-03 | Indexing runs inside one HTTP request, so a very large repository ties up that request for a long time. A background job with progress would remove this limit. | ⬜ |

## 3. Security

| ID | Requirement | How it is met / checked | Status |
|---|---|---|---|
| NFR-SEC-01 | The service must confirm that the user can read a repository before indexing or answering. | `github_fetcher.resolve` calls GitHub with the user's token on every request. | ✅ |
| NFR-SEC-02 | The GitHub token is never stored by the RAG service or written to logs. | It is read from the `Authorization` header per request. `Token.toString()` in the app hides the value. | ✅ |
| NFR-SEC-03 | Files such as `.env`, private keys and credential files are not indexed. | `SKIP_NAMES`, `SKIP_SUFFIXES` and the `.env*` rule in `github_fetcher.py`. Secrets written inside ordinary code files can still be indexed. | 🟡 |
| NFR-SEC-04 | Repository content is untrusted. The archive is read in memory and nothing from it is written to disk or run; the prompt tells the model to ignore instructions inside excerpts. | `extract()` and the system prompt in `llm_client.py`. | ✅ |
| NFR-SEC-05 | SQL is built with parameters so user input cannot change a query. | DAO classes use prepared statements; `treatsSqlInInputAsText` tests this. | ✅ |
| NFR-SEC-06 | The local database stores the GitHub token encrypted. | The token is stored as plain text in `devpilot.db`. Logging out removes it. | ⬜ |
| NFR-SEC-07 | The requested GitHub scope is the smallest that works. | `repo` is needed for private repositories but also allows writing; DevPilot only reads. | 🟡 |
| NFR-SEC-08 | The RAG service accepts connections only from the same computer. | Documented: start it with `--host 127.0.0.1`. The service has no login of its own. | 🟡 |
| NFR-SEC-09 | The Groq API key is kept out of source control. | `.env` is git-ignored. | ✅ |
| NFR-SEC-10 | Question text and matching code excerpts, including from private repositories, are sent to Groq. Users are told this. | Stated in the README "Privacy and security" section. | ✅ |

## 4. Reliability

| ID | Requirement | How it is met / checked | Status |
|---|---|---|---|
| NFR-REL-01 | A failed index build must not damage the existing index. | Build under `building-<id>`, then rename and delete older collections. | ✅ |
| NFR-REL-02 | Temporary collections left by a crash are removed at startup. | `VectorStore.__init__`. | ✅ |
| NFR-REL-03 | A failed repository sync must not change the local database. | Fetch first, write in one transaction; `failedSyncChangesNothing` tests this. | ✅ |
| NFR-REL-04 | The app works offline for what is stored locally (repository list, chat history). | Saved repositories load before the network call. | ✅ |
| NFR-REL-05 | Foreign keys are enforced so no orphan chats remain. | `PRAGMA foreign_keys = ON` on every connection. | ✅ |
| NFR-REL-06 | Network calls have timeouts so the app cannot wait forever. | 30 s for GitHub from the service; 60 s for Groq; 10 min from the app to the service. | ✅ |

## 5. Usability

| ID | Requirement | Status |
|---|---|---|
| NFR-USE-01 | Every error message says what went wrong and what to do next, in plain language. | ✅ |
| NFR-USE-02 | The user sees a progress indicator for every wait (login, sync, files, indexing, answering). | ✅ |
| NFR-USE-03 | The window has a minimum size of 900 × 560 and the layout is usable at that size. | ✅ |
| NFR-USE-04 | Enter sends a question; double-click opens a repository. | ✅ |
| NFR-USE-05 | Fonts are bundled so the look is the same on every machine. | ✅ |
| NFR-USE-06 | Accessibility (keyboard-only use, screen readers, contrast) has been reviewed. | ⬜ |

## 6. Compatibility and portability

| ID | Requirement | Status |
|---|---|---|
| NFR-COMP-01 | Java 21 and JavaFX 21 for the app; Python 3.13 for the service. | ✅ |
| NFR-COMP-02 | Runs on macOS. | ✅ |
| NFR-COMP-03 | Runs on Windows and Linux. Not tested. | ⬜ |

## 7. Maintainability

| ID | Requirement | How it is met / checked | Status |
|---|---|---|---|
| NFR-MAINT-01 | Code is layered: `ui` has no SQL, `dao` holds all SQL, `service` talks to the network. | [architecture.md](architecture.md) | ✅ |
| NFR-MAINT-02 | Dependencies have pinned versions. | `pom.xml` and `requirements.txt` | ✅ |
| NFR-MAINT-03 | The JSON contract between the two parts is documented and kept in sync. | [api.md](api.md); `RagClientService` records mirror `models/schemas.py`. | ✅ |
| NFR-MAINT-04 | Automated tests cover the database classes. Target: also the RAG service's pure functions and the Java service clients. | 27 JUnit tests exist; Python and service tests are planned (see [TEST_CASES.md](TEST_CASES.md)). | 🟡 |
| NFR-MAINT-05 | Settings (GitHub client ID, scope, RAG address, database) are in `application.properties`, not in code. | ✅ |

## 8. Privacy

| ID | Requirement | Status |
|---|---|---|
| NFR-PRIV-01 | Embeddings are created on the user's computer; the code is not sent to an embedding provider. | ✅ |
| NFR-PRIV-02 | Indexed code stays in `DevPilot-RAGService/chroma_data/`, which the user can delete to clear it. | ✅ |
| NFR-PRIV-03 | Logging out removes the token from the computer. | ✅ |

## 9. Quality of answers

| ID | Requirement | Status |
|---|---|---|
| NFR-QUAL-01 | Answers cite the files they rely on and list the searched excerpts, so the user can check them. | ✅ |
| NFR-QUAL-02 | The model says when the excerpts do not contain the answer instead of guessing. | ✅ (prompt rule; quality not yet measured) |
| NFR-QUAL-03 | Retrieval quality is measured on a set of sample questions for at least one real repository. | ⬜ |
