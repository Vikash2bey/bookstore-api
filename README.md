# Bookstore API

A Java 21 / Spring Boot REST API for a book catalog, authenticated carts, orders,
stock reservations and simulated card payments. It uses Spring Data JPA, H2,
JWT authentication, MapStruct and Bean Validation.

## Run locally

Requirements: Java 21 and Maven 3.8+, or the included Maven wrapper.

```powershell
.\mvnw.cmd spring-boot:run
```

The default base URL is `http://localhost:8181`. The in-memory database is reset
when the application stops. The H2 console is at `http://localhost:8181/h2-console`
with JDBC URL `jdbc:h2:mem:bookstoredb`, username `sa`, and an empty password.
Sample books are added when the database is empty.

## API documentation

With the application running, open
[`/swagger-ui/index.html`](http://localhost:8181/swagger-ui/index.html) for the
interactive API documentation or [`/v3/api-docs`](http://localhost:8181/v3/api-docs)
for the OpenAPI JSON. In Swagger UI, use **Authorize** with the token returned by
registration or login. Cart, order and payment endpoints require a bearer token.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/register` | Register and return a token |
| POST | `/api/auth/login` | Authenticate and return a token |
| GET | `/api/books` | List active books |
| GET | `/api/cart` | Get or create the current user's cart |
| POST | `/api/cart/items` | Add a book and quantity |
| PUT | `/api/cart/items/{bookId}` | Set a cart item's quantity |
| DELETE | `/api/cart/items/{bookId}` | Remove a cart item |
| POST | `/api/orders` or `/api/orders/checkout` | Create an order from the cart |
| GET | `/api/orders` | List the current user's orders |
| GET | `/api/orders/{orderId}` | Get an order owned by the current user |
| POST | `/api/orders/{orderId}/pay` | Pay an order; optional `paymentMethod`, default `CARD` |

Register with JSON `{"username":"Alice","email":"alice@example.com","password":"password123"}`.
Login uses `{"email":"alice@example.com","password":"password123"}`. Both return
`token`, `tokenType`, `userId`, `email` and `name`. Add the token to protected
requests as `Authorization: Bearer <token>`. For example:

```powershell
curl.exe -X POST http://localhost:8181/api/cart/items `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d '{\"bookId\":1,\"quantity\":2}'
```

The add-item body requires a positive `bookId` and a quantity of at least one.
The update-item body is `{"quantity":2}`. Order creation optionally accepts an
`Idempotency-Key` header, up to 255 characters. A previously saved order for the
same user and key is returned on a sequential retry; concurrent submissions of
the same key are **not** guaranteed to be deduplicated.

Application and request-validation errors use JSON with `timestamp`, `status`,
`code` and `message`. Typical statuses include 400 for invalid input, 404 for a
missing resource, 409 for a duplicate registration, and 422 for insufficient
stock. Authentication and authorization errors are handled by Spring Security.

## Stock reservations and payment

Checkout deducts stock under database row locks, clears the cart and creates a
`PENDING_PAYMENT` order. Its response includes `expiresAt`; timestamps are local
server time. The order stores each item's unit price at checkout and calculates
its total from those prices, so later catalog price changes do not alter the order.
Stock is reserved for 15 minutes by default. A scheduled cleanup
checks every minute, changes overdue orders to `EXPIRED`, and restores stock.
Payment also checks the deadline immediately. Payment started before expiry may
finish after it; expiration waits for that payment transaction. A failed payment
marks the order `FAILED` and restores stock. An expired or failed order cannot
be paid, and retrying an already paid order does not charge it again.

Payment currently uses a simulated card gateway, not a real payment provider.
For orders created before expiry timestamps were introduced, the deadline is
the creation time plus the configured reservation duration. All application
instances must use synchronized clocks and the same local time zone.

| Environment variable | Default | Description |
|---|---|---|
| `ORDER_RESERVATION_DURATION` | `PT15M` | Positive ISO-8601 duration, e.g. `PT30M` |
| `ORDER_EXPIRATION_CHECK_INTERVAL` | `PT1M` | ISO-8601 cleanup interval, e.g. `PT30S` |
| `JWT_SECRET_KEY` | Development-only value | JWT signing secret; set a strong secret outside local development |

Other settings are in `src/main/resources/application.yml`. H2, the H2 console,
the simulated gateway and the development JWT secret are intended for local
development, not production.

## Tests

```powershell
.\mvnw.cmd test
```

Unit tests mock repositories and payment collaborators. Integration tests use
H2 and MockMvc for API validation, database transactions, reservations, payment
and concurrency scenarios.
