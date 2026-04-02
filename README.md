# SSO Service

Reusable authentication service for any web or mobile application.

Default local URL: `http://localhost:8090`
Live URL: `https://sso-service-lvow.onrender.com`
API Gateway URL: `https://ordernest-api-gateway.onrender.com`

## What it does
- Register and verify users
- Login, refresh token, logout
- Password reset flow
- Current user profile (`GET /api/auth/me` via API Gateway)
- Exposes JWKS for JWT verification

## Register payload
`POST /api/auth/register`
```json
{
  "email": "user@example.com",
  "password": "Password@123",
  "firstName": "Arun",
  "lastName": "Singh"
}
```

## Quick start
1. Start dependencies:
```bash
docker compose up -d
```
2. Run service:
```bash
./gradlew bootRun
```

## Required config
This service can load secrets from:
- `./etc/secrets/config.properties`
- `/etc/secrets/config.properties`

Common notification-related keys:
- `NOTIFICATION_BASE_URL`
- `NOTIFICATION_EMAIL_PATH`
- `VERIFICATION_BASE_URL`
- `VERIFICATION_PATH`
- `PASSWORD_RESET_BASE_URL`
- `PASSWORD_RESET_PATH`
- `APP_NAME` (optional, used in email/content templates)

## API + Swagger
- API base URL: `https://ordernest-api-gateway.onrender.com`
- Swagger UI: `http://localhost:8090/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8090/v3/api-docs`
- Health: `http://localhost:8090/actuator/health`
- Live Swagger UI: `https://sso-service-lvow.onrender.com/swagger-ui/index.html`
- Live OpenAPI JSON: `https://sso-service-lvow.onrender.com/v3/api-docs`
- Live Health: `https://sso-service-lvow.onrender.com/actuator/health`

## Postman
Import:
- `postman/sso-service.postman_collection.json`

Notes:
- Verification and reset tokens come from email links.
- Paste tokens into Postman variables `verificationToken` and `resetToken`.

## Mermaid design
```mermaid
flowchart LR
    U["User / Client App"] --> G["API Gateway"]
    G --> S["SSO Service"]
    S --> P["PostgreSQL"]
    S --> R["Redis"]
    S --> N["Notification Service"]
    N --> E["Email Provider (Resend)"]
    S --> J["JWKS Endpoint"]
```
