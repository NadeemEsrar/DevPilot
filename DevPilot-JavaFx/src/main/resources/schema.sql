-- Executed on startup. IF NOT EXISTS makes it safe to run every time.
CREATE TABLE IF NOT EXISTS users (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    github_id       INTEGER NOT NULL UNIQUE,
    github_username TEXT    NOT NULL,
    display_name    TEXT,
    avatar_url      TEXT,
    access_token    TEXT,
    token_scope     TEXT
);

CREATE TABLE IF NOT EXISTS repositories (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id        INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gh_repo_id     INTEGER NOT NULL,
    owner          TEXT    NOT NULL,
    name           TEXT    NOT NULL,
    full_name      TEXT    NOT NULL,
    is_private     INTEGER NOT NULL DEFAULT 0,
    default_branch TEXT,
    language       TEXT,
    html_url       TEXT,
    description    TEXT,
    UNIQUE (user_id, gh_repo_id)
);

CREATE TABLE IF NOT EXISTS chat_history (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    repo_id   INTEGER NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    question  TEXT    NOT NULL,
    answer    TEXT,
    timestamp TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
