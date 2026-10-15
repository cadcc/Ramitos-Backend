-- migrate:up
ALTER TABLE courses ADD COLUMN IF NOT EXISTS total_reviews INTEGER;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS comment_reviews INTEGER;

UPDATE courses SET
    total_reviews = (SELECT COUNT(*) FROM reviews WHERE reviews.course_code = code),
    comment_reviews = (SELECT COUNT(*) FROM reviews WHERE reviews.course_code = code AND comments IS NOT NULL);

ALTER TABLE courses ALTER COLUMN total_reviews SET NOT NULL;
ALTER TABLE courses ALTER COLUMN comment_reviews SET NOT NULL;

-- migrate:down
ALTER TABLE courses DROP COLUMN IF EXISTS comment_reviews;
ALTER TABLE courses DROP COLUMN IF EXISTS total_reviews;

