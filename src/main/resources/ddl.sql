CREATE TYPE account_role as ENUM ('none', 'stats', 'mod', 'admin');

CREATE TABLE accounts(
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    role account_role,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE passwords(
    username VARCHAR(100) NOT NULL PRIMARY KEY,
    secret VARCHAR(255) NOT NULL,
    account_id INTEGER NOT NULL REFERENCES accounts(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ucampus_sso(
    ucampus_id VARCHAR(100) NOT NULL PRIMARY KEY,
    account_id SERIAL NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE courses(
    code VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    difficulty FLOAT,
    difficulty_count INTEGER NOT NULL DEFAULT 0,
    difficulty_sum BIGINT NOT NULL DEFAULT 0,
    load FLOAT,
    load_count INTEGER NOT NULL DEFAULT 0,
    load_sum BIGINT NOT NULL DEFAULT 0,
    utility FLOAT,
    utility_count INTEGER NOT NULL DEFAULT 0,
    utility_sum BIGINT NOT NULL DEFAULT 0,
    interest FLOAT,
    interest_count INTEGER NOT NULL DEFAULT 0,
    interest_sum BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE reviews(
    id SERIAL PRIMARY KEY,
    account_id INTEGER REFERENCES accounts(id),
    course_code VARCHAR(20) NOT NULL REFERENCES courses(code),
    comments TEXT NOT NULL,
    difficulty FLOAT,
    load FLOAT,
    utility FLOAT,
    interest FLOAT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
