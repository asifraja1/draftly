-- email_embeddings is now stored in Chroma (no longer in Postgres)

-- Generic writing style profile per (user_id, relation)
CREATE TABLE IF NOT EXISTS writing_styles (
    id                 SERIAL PRIMARY KEY,
    user_id            VARCHAR(255) NOT NULL,
    relation           VARCHAR(255) NOT NULL,
    tone               VARCHAR(100),
    formality          VARCHAR(50),
    avg_sentence_len   INTEGER,
    style_description  TEXT,
    common_phrases     JSONB DEFAULT '[]',
    style_examples     JSONB DEFAULT '[]',
    sample_count       INTEGER DEFAULT 0,
    updated_at         TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, relation)
);

-- Tracks paused graphs waiting for user input
CREATE TABLE IF NOT EXISTS user_input_requests (
    id             SERIAL PRIMARY KEY,
    thread_id      VARCHAR(255) NOT NULL,
    user_id        VARCHAR(255) NOT NULL,
    question       TEXT NOT NULL,           -- what the agent is asking the user
    options        JSONB DEFAULT '[]',      -- e.g. ["Accept", "Decline", "Ask for more info"]
    status         VARCHAR(50) DEFAULT 'pending',  -- pending | answered | expired
    user_answer    TEXT,                    -- filled when user responds
    graph_config   JSONB NOT NULL,          -- thread_id + checkpoint info to resume graph
    created_at     TIMESTAMPTZ DEFAULT NOW(),
    answered_at    TIMESTAMPTZ
);

-- Output messages published to the output topic
CREATE TABLE IF NOT EXISTS output_messages (
    id             SERIAL PRIMARY KEY,
    user_id        VARCHAR(255),
    thread_id      VARCHAR(255),
    message_type   VARCHAR(50),    -- 'draft' | 'summary'
    content        TEXT,
    original_email TEXT,
    similarity     FLOAT,
    metadata       JSONB DEFAULT '{}',
    created_at     TIMESTAMPTZ DEFAULT NOW()
);
