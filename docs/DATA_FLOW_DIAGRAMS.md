# Data Flow Diagrams

The diagrams use [Mermaid](https://mermaid.js.org/), which GitHub draws automatically. They follow the code in `DevPilot-JavaFx` and `DevPilot-RAGService`.

Notation: rectangles are external entities, rounded boxes are processes, cylinders are data stores, and arrows are labelled with the data that moves.

## Level 0: context diagram

DevPilot is one system. The user talks to it; it talks to GitHub and Groq.

```mermaid
flowchart LR
    U[User]
    GH[GitHub]
    GQ[Groq LLM]
    S(("DevPilot"))

    U -- "login approval, repository choice, questions" --> S
    S -- "answers, excerpts, file tree, code, status" --> U
    S -- "device code request, token polling, repository list, file list, archive download" --> GH
    GH -- "token, profile, repositories, files, zip archive" --> S
    S -- "prompt: question + code excerpts" --> GQ
    GQ -- "answer text" --> S
```

## Level 1: main processes

The two programs communicate only with HTTP and JSON.

```mermaid
flowchart TB
    U[User]
    GH[GitHub]
    GQ[Groq LLM]

    P1("1. Log in<br/>GitHubAuthService, LoginView")
    P2("2. Sync repositories<br/>RepoSyncService, DashboardView")
    P3("3. Browse files<br/>GitHubFileService, FileTreeView, CodeView")
    P4("4. Build index<br/>RAG service: fetch, chunk, embed, store")
    P5("5. Answer question<br/>RAG service: retrieve, generate")
    P6("6. Manage chat<br/>ChatView, ChatHistoryDAO")

    D1[("D1 SQLite devpilot.db<br/>users, repositories, chat_history")]
    D2[("D2 ChromaDB chroma_data<br/>one collection per repo + branch")]

    U -- "device code entered on github.com" --> GH
    P1 -- "client id, scope" --> GH
    GH -- "access token, scope" --> P1
    P1 -- "profile" --> GH
    P1 -- "user + token" --> D1
    P1 -- "login code, errors" --> U

    U -- "Refresh" --> P2
    P2 -- "token" --> GH
    GH -- "repositories (all pages)" --> P2
    P2 -- "insert / update / delete repositories" --> D1
    D1 -- "saved repositories" --> P2
    P2 -- "repository list" --> U

    U -- "select file" --> P3
    P3 -- "token, tree / blob request" --> GH
    GH -- "file list, file text" --> P3
    P3 -- "file tree, code" --> U

    U -- "Create / Re-create embeddings" --> P4
    P4 -- "token, repo, branch" --> GH
    GH -- "commit SHA, zip archive" --> P4
    P4 -- "chunk vectors + path, lines" --> D2
    P4 -- "index date + commit" --> U

    U -- "question" --> P5
    P5 -- "query vector" --> D2
    D2 -- "8 closest chunks" --> P5
    P5 -- "prompt" --> GQ
    GQ -- "answer" --> P5
    P5 -- "answer + searched excerpts" --> P6

    P6 -- "save question + answer" --> D1
    D1 -- "saved conversation" --> P6
    P6 -- "conversation" --> U
```

## Level 2: process 1, GitHub login (Device Flow)

```mermaid
sequenceDiagram
    actor U as User
    participant App as DevPilot app
    participant GH as GitHub
    participant DB as SQLite

    App->>GH: POST /login/device/code (client_id, scope)
    GH-->>App: device_code, user_code, verification_uri, interval
    App-->>U: show user_code and address
    U->>GH: enter user_code in the browser, approve
    loop every interval seconds until approved, denied or expired
        App->>GH: POST /login/oauth/access_token (device_code)
        GH-->>App: authorization_pending / slow_down / expired_token / access_denied / access_token
    end
    App->>GH: GET /user (Bearer token)
    GH-->>App: id, login, name, avatar_url
    App->>App: check scope contains read:user and repo
    App->>DB: save user + token + scope
    App-->>U: open dashboard
```

## Level 2: process 2, repository sync

```mermaid
flowchart LR
    A("Show saved repositories") --> B("Fetch /user<br/>(profile)")
    B --> C("Fetch /user/repos<br/>page 1..n, 100 per page")
    C -- "any page fails" --> X("Stop: nothing written<br/>show error / log out on 401")
    C -- "all pages fetched" --> D("One transaction:<br/>insert or update fetched,<br/>delete the rest")
    D --> E[("SQLite")]
    D --> F("Show updated list")
    A -.reads.-> E
```

## Level 2: process 4, build the index

```mermaid
flowchart TB
    U[User] -- "POST /index<br/>repo, branch + Bearer token" --> R1
    R1("4.1 Resolve<br/>GET /repos/{repo}: access check,<br/>repo id, default branch")
    R2("4.2 Lock<br/>one build per repo + branch<br/>else 409")
    R3("4.3 Latest commit<br/>GET commits/{branch}")
    R4("4.4 Download<br/>GET zipball/{sha}, max 100 MB")
    R5("4.5 Filter<br/>skip dirs, lock files, keys, .env,<br/>binary, non-UTF-8, over 200 KB")
    R6("4.6 Chunk<br/>900 chars, 120 overlap,<br/>keep path + line range")
    R7("4.7 Embed<br/>all-MiniLM-L6-v2 on CPU,<br/>batches of 64")
    R8("4.8 Swap<br/>build as building-id, rename,<br/>delete older collections")
    GH[GitHub]
    D2[("ChromaDB")]

    R1 <--> GH
    R1 --> R2 --> R3
    R3 <--> GH
    R3 --> R4
    R4 <--> GH
    R4 -- "zip bytes" --> R5
    R5 -- "{path: text}" --> R6
    R6 -- "no chunks: 422<br/>over 5000: 413" --> U
    R6 -- "chunks" --> R7
    R7 -- "vectors" --> R8
    R8 --> D2
    R8 -- "exists, created_at, commit" --> U
```

## Level 2: process 5, answer a question

```mermaid
flowchart TB
    U[User] -- "question" --> A("5.1 Send<br/>POST /ask with repo, branch, question")
    A --> B("5.2 Check Groq key<br/>else 503")
    B --> C("5.3 Resolve repo<br/>proves the user still has access")
    C --> D("5.4 Embed question<br/>same local model")
    D --> E("5.5 Search<br/>top 8 by cosine similarity")
    DB[("ChromaDB")]
    E <--> DB
    E -- "no index" --> X("409: create embeddings first")
    E -- "8 chunks" --> F("5.6 Build prompt<br/>system rules + excerpts + question")
    F --> G("5.7 Call Groq<br/>60 s timeout, 1 retry")
    GQ[Groq LLM]
    G <--> GQ
    G -- "401 / 429 / error" --> Y("503 / 429 / 502")
    G -- "answer text" --> H("5.8 Append sources<br/>'Searched excerpts' path:start-end")
    H -- "answer" --> V("5.9 Render Markdown<br/>AnswerView")
    V --> S[("SQLite chat_history")]
    V --> U
```

## Data dictionary

| Data | Where it lives | Fields |
|---|---|---|
| User | `users` table | `github_id`, `github_username`, `display_name`, `avatar_url`, `access_token`, `token_scope` |
| Repository | `repositories` table | `user_id`, `gh_repo_id`, `owner`, `name`, `full_name`, `is_private`, `default_branch`, `language`, `html_url`, `description` |
| Chat message | `chat_history` table | `user_id`, `repo_id`, `question`, `answer`, `timestamp` |
| Chunk | ChromaDB collection | text (path + code), vector (384 numbers), `path`, `start`, `end` |
| Index info | ChromaDB collection metadata | `commit`, `created_at` |
| Ask request | HTTP JSON | `repo`, `branch`, `question` |
| Index status | HTTP JSON | `exists`, `building`, `created_at`, `commit` |
