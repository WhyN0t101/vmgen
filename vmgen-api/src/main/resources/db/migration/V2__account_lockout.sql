-- Account lockout support (V2)
-- Tracks per-user lockout state and per-build flag attempt counters.

-- Lockout timestamp: set when a lockout is triggered (flag-based).
-- Login brute-force is mitigated at the rate-limiter level (IP-scoped), not here.
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP;

-- Flag-based lockout: per-build failed flag submission counter.
-- When a build reaches the per-build attempt limit (5), it is "exhausted".
-- When 3 builds are exhausted, the account is locked for 1 hour.
ALTER TABLE vm_requests ADD COLUMN failed_flag_attempts INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_users_locked_until ON users(locked_until) WHERE locked_until IS NOT NULL;
