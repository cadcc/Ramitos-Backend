-- Dev-only login accounts. Runs on every `docker compose up`; safe to re-run.
--
-- 1. Creates one fixed account per role (see table below).
-- 2. Gives every other account without credentials (e.g. the ones restored from
--    the anonymized dump) the username lower(name) and password 'dev123'.
--    Duplicate or taken usernames get '-<id>' appended.
--
-- Accounts that already have a password are never touched.
--
-- Hashes are plain bcrypt, so this only works while BCRYPT_PEPPER is empty.

\set ON_ERROR_STOP on
SET client_min_messages = warning;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;

WITH dev(username, name, role, password) AS (
    VALUES
        ('dev-admin', 'Dev Admin', 'admin'::account_role, 'dev123'),
        ('dev-mod',   'Dev Mod',   'mod'::account_role,   'dev123'),
        ('dev-stats', 'Dev Stats', 'stats'::account_role, 'dev123'),
        ('dev-none',  'Dev None',  'none'::account_role,  'dev123'),
        ('test',      'test',      'none'::account_role,  'mish123')
),
missing AS (
    SELECT * FROM dev
    WHERE NOT EXISTS (SELECT 1 FROM passwords p WHERE p.username = dev.username)
),
acc AS (
    INSERT INTO accounts (name, role)
    SELECT name, role FROM missing
    RETURNING id, name
)
INSERT INTO passwords (username, secret, account_id)
SELECT m.username, crypt(m.password, gen_salt('bf', 10)), acc.id
FROM missing m
JOIN acc ON acc.name = m.name;

WITH candidates AS (
    SELECT
        a.id,
        regexp_replace(lower(a.name), '\s+', '.', 'g') AS base
    FROM accounts a
    WHERE NOT EXISTS (SELECT 1 FROM passwords p WHERE p.account_id = a.id)
)
INSERT INTO passwords (username, secret, account_id)
SELECT
    CASE
        WHEN count(*) OVER (PARTITION BY c.base) > 1
          OR EXISTS (SELECT 1 FROM passwords p WHERE p.username = c.base)
        THEN c.base || '-' || c.id
        ELSE c.base
    END,
    crypt('dev123', gen_salt('bf', 10)),
    c.id
FROM candidates c
ON CONFLICT (username) DO NOTHING;

COMMIT;

SELECT a.id, p.username, a.name, a.role
FROM accounts a
JOIN passwords p ON p.account_id = a.id
ORDER BY a.id;
