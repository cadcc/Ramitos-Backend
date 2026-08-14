CREATE TYPE account_role as ENUM ('none', 'stats', 'mod', 'admin');

CREATE TABLE accounts(
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    mufasa_id VARCHAR(10),
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
    stats JSONB NOT NULL,
    tag_stats JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reviews(
    id SERIAL PRIMARY KEY,
    account_id INTEGER REFERENCES accounts(id),
    course_code VARCHAR(20) NOT NULL REFERENCES courses(code),
    comments TEXT,
    docencia SMALLINT,
    vibes SMALLINT,
    relevancia SMALLINT,
    carga SMALLINT,
    dificultad SMALLINT,
    tags JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX unique_reviews_account_course ON reviews(account_id, course_code);
