CREATE TYPE account_role as ENUM ('none', 'stats', 'mod', 'admin');
CREATE TYPE semester as ENUM ('year', 'fall', 'spring', 'summer');

CREATE TABLE mufasa_cache_data(
    key VARCHAR(25) PRIMARY KEY,
    value JSONB NOT NULL,
    scheduled TIMESTAMP
);

CREATE TABLE accounts(
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    mufasa_id VARCHAR(10) UNIQUE,
    role account_role NOT NULL DEFAULT 'none',
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

CREATE TABLE dcc_sso(
    dcc_id VARCHAR(100) NOT NULL PRIMARY KEY,
    mufasa_id VARCHAR(10) NOT NULL UNIQUE,
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

CREATE TABLE course_offerings(
    id SERIAL PRIMARY KEY,
    course_code VARCHAR(20) NOT NULL REFERENCES courses(code),
    year SMALLINT NOT NULL,
    semester semester NOT NULL,
    section SMALLINT NOT NULL,
    UNIQUE (course_code, year, semester, section)
);

CREATE TABLE course_enrollments(
    student_mufasa_id VARCHAR(10) NOT NULL REFERENCES accounts(mufasa_id),
    course_offering_id INTEGER REFERENCES course_offerings(id),
    PRIMARY KEY (student_mufasa_id, course_offering_id)
);

CREATE TABLE course_enrollment_cache(
    student_mufasa_id VARCHAR(10) PRIMARY KEY REFERENCES accounts(mufasa_id),
    transient_data JSONB NOT NULL DEFAULT '[]',
    semester_synced_year SMALINT NOT NULL,
    semester_synced_type semester NOT NULL,
    last_update TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
