-- ── Tables ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    user_id      VARCHAR(36)  PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL,
    display_name VARCHAR(128),
    bio          TEXT         DEFAULT '',
    avatar_base64 TEXT        DEFAULT '',   -- full data-URI, e.g. "data:image/png;base64,..."
    status       VARCHAR(16)  DEFAULT 'offline',
    created_at   TIMESTAMPTZ  DEFAULT NOW(),
    last_seen    TIMESTAMPTZ  DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS rooms (
    room_id    VARCHAR(128) PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    type       VARCHAR(16)  DEFAULT 'text',  -- text | voice | dm
    created_at TIMESTAMPTZ  DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS messages (
    message_id  VARCHAR(36)  PRIMARY KEY,
    room_id     VARCHAR(128) NOT NULL REFERENCES rooms(room_id) ON DELETE CASCADE,
    sender_id   VARCHAR(36),
    sender_name VARCHAR(128),
    content     TEXT         NOT NULL,
    type        VARCHAR(32)  DEFAULT 'TEXT', -- TEXT | JOIN | LEAVE | SYSTEM
    sent_at     TIMESTAMPTZ  DEFAULT NOW()
);

-- ── Indexes ─────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_msg_room_sent ON messages(room_id, sent_at);
CREATE INDEX IF NOT EXISTS idx_msg_sender    ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_user_status   ON users(status);

-- ── Seed default channels ────────────────────────────
INSERT INTO rooms (room_id, name, type) VALUES
    ('general',     'general',     'text'),
    ('java-dev',    'java-dev',    'text'),
    ('webrtc-help', 'webrtc-help', 'text'),
    ('random',      'random',      'text')
ON CONFLICT (room_id) DO NOTHING;