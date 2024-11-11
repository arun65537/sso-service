ALTER TABLE users
    ADD COLUMN IF NOT EXISTS roles TEXT[];

UPDATE users u
SET roles = COALESCE(
    (
        SELECT ARRAY_AGG(r.name ORDER BY r.name)
        FROM user_roles ur
        JOIN roles r ON r.id = ur.role_id
        WHERE ur.user_id = u.id
    ),
    ARRAY['ROLE_USER']::TEXT[]
)
WHERE roles IS NULL OR CARDINALITY(roles) = 0;

ALTER TABLE users
    ALTER COLUMN roles SET NOT NULL;

DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
