ALTER TABLE users
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(80),
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(80);

UPDATE users
SET first_name = COALESCE(NULLIF(first_name, ''), 'User'),
    last_name = COALESCE(NULLIF(last_name, ''), 'Unknown')
WHERE first_name IS NULL OR first_name = '' OR last_name IS NULL OR last_name = '';

ALTER TABLE users
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name SET NOT NULL;
