# SSO Service

Central authentication service for OrderNest microservices.

## Features
- Registration, email verification, login, token refresh, logout
- RS256 JWT access tokens (15 minutes) + rotating refresh tokens (7 days)
- JWKS endpoint for downstream token verification
- Redis-backed login rate limiting and revoked access token blacklist
- PostgreSQL persistence with Flyway migrations
- RBAC with role claims in JWT
- Dedicated email notification service for verification and password reset links

## Notification integration
Set these environment variables:
- `NOTIFICATION_BASE_URL`
- `NOTIFICATION_EMAIL_PATH`
- `VERIFICATION_BASE_URL`, `VERIFICATION_PATH`
- `PASSWORD_RESET_BASE_URL`, `PASSWORD_RESET_PATH`

Example:
- `NOTIFICATION_BASE_URL=https://your-notification-service.onrender.com`
- `NOTIFICATION_EMAIL_PATH=/notifications/email`

## Secrets file (same pattern as notification-service)
This service can load secrets from:
- `./etc/secrets/config.properties`
- `/etc/secrets/config.properties` (Render secret file path)

## Run dependencies
```bash
docker compose up -d
```

## Run service
```bash
./gradlew bootRun
```

## Swagger
- OpenAPI JSON: `/v3/api-docs`
- Swagger UI: `/swagger-ui/index.html`

## Postman
Import:
- `postman/sso-service.postman_collection.json`

Note:
- Verification and reset tokens are delivered by email.
- Paste token values manually into Postman variables:
  - `verificationToken`
  - `resetToken`

## GitHub Actions (Render deploy)
This repo includes:
- `.github/workflows/render-deploy.yml`

Add this repository secret in GitHub:
- `RENDER_DEPLOY_HOOK_URL` = your Render deploy hook URL
