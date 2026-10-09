# kubsei-gateway-java

Single entry point (Spring Cloud Gateway). Validates the JWT, applies CORS and routes to the services.

## Adding an application

Only `application.yml`: declare the service and its filters (routes). Evaluated in list order.

```yaml
security:
  gateway:
    service:
      billing:
        uri: ${BILLING_URI:http://localhost:8083}
    filter:
      - id: invoice-pdf
        origin: /invoices/*/pdf          # "*" = one segment, trailing "/**" = any rest
        destination: /api/invoices/*/pdf # "*" (or ${inmask}) = the origin's segment
        service: billing
        methods: GET                     # optional
        permit-all: false                # optional, true = no token required
        timeout: 30m                     # optional, overrides httpclient.response-timeout
```

## Run

```bash
export JWT_SECRET=...            # same in users, editor and gateway
mvn spring-boot:run              # http://localhost:3001
```

| Variable | Default |
|---|---|
| `USERS_URI` | `http://localhost:8081` |
| `EDITOR_URI` | `http://localhost:8082` |
| `JWT_SECRET` | required |
| `JWT_ISSUER` | `kubsei` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://127.0.0.1:3000` |
| `TRUSTED_PROXIES` | loopback (regex; set your load balancer in production) |
| `SERVER_PORT` | `3001` |
