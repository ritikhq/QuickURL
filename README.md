# QuickURL

QuickURL is a lightweight URL shortener built with **Spring Boot**, **Angular**, and **Redis**.

The project demonstrates several practical backend and system-design concepts, including URL shortening, HTTP redirects, Redis caching, rate limiting, click analytics, scheduled cleanup, REST APIs, and Docker-based local development.

The application is intentionally small and easy to inspect, making it useful for understanding how a URL-shortening service works end to end.

## What It Does

* Create a short URL from a long URL
* Optionally provide a custom alias
* Optionally configure an expiration time
* Redirect a short URL to the original URL
* Track click counts for shortened URLs
* Return basic statistics and analytics
* Apply per-client/IP rate limiting to the shorten API
* Cache URL lookups using Redis
* Periodically mark expired URLs as inactive
* Run the complete application using Docker Compose

## Architecture

The current application uses in-memory Java collections as the primary application store.

### Application storage

* `urlMappings` stores `shortCode -> UrlData`
* `clickAnalytics` stores `shortCode -> List<ClickEvent>`

### Redis

Redis is used for:

* URL caching
* Shared rate-limit state

### Components

* **Frontend:** Angular SPA
* **Backend:** Spring Boot REST API
* **Cache & rate limiting:** Redis
* **Container orchestration:** Docker Compose

> **Note:** The current version does not use a persistent database. URL mappings and click analytics are lost when the backend restarts.

## Project Structure

```text
QuickURL/
│
├── backend/
│   ├── src/main/java/com/ritik/quickurl/
│   │   ├── config/
│   │   ├── controllers/
│   │   ├── dto/
│   │   ├── models/
│   │   └── services/
│   │
│   ├── src/main/resources/
│   │   └── application.yaml
│   │
│   ├── Dockerfile
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
│
├── docker-compose.yml
└── README.md
```

## Services and Ports

| Service  |   Port | Purpose                    |
| -------- | -----: | -------------------------- |
| Frontend | `8082` | Angular web application    |
| Backend  | `8080` | Spring Boot REST API       |
| Redis    | `6379` | Cache and rate-limit state |

## Quick Start

### Prerequisites

Make sure you have:

* Docker Desktop
* Docker Compose

### Start the application

From the project root:

```powershell
docker compose up --build
```

Then open:

* Frontend: `http://localhost:8082`
* Backend health check: `http://localhost:8080/api/health`

### Stop the application

```powershell
docker compose down
```

### Force a full rebuild

```powershell
docker compose up -d --build --force-recreate
```

## API Endpoints

### Create a Short URL

```http
POST /api/shorten
Content-Type: application/json
```

Example request:

```json
{
  "originalUrl": "https://example.com/very/long/url",
  "customAlias": "optional-code",
  "expiresAt": "2026-12-31T23:59:59"
}
```

The response contains information such as:

* `shortUrl`
* `shortCode`
* `originalUrl`
* creation timestamp
* expiration timestamp

### Redirect to the Original URL

```http
GET /api/{shortCode}
```

For example:

```text
GET /api/abc123
```

The backend looks up the short code and responds with an HTTP `302 Found` redirect to the original URL.

### Get URL Statistics

```http
GET /api/stats/{shortCode}
```

Returns basic information such as:

* click count
* creator IP
* active status
* creation time
* expiration time

### Get Analytics

```http
GET /api/analytics/{shortCode}
```

Returns click analytics for the specified short URL.

### Delete a Short URL

```http
DELETE /api/{shortCode}
```

The URL is marked inactive.

### Health Check

```http
GET /api/health
```

Used to verify that the backend is running.

## End-to-End Request Flow

### 1. Creating a Short URL

1. The Angular frontend sends `POST /api/shorten`.
2. The backend controller receives the request.
3. The client's IP address is extracted.
4. The rate limiter checks whether the request is allowed.
5. The service uses the supplied custom alias or generates a random Base62 short code.
6. The `UrlData` object is stored in the in-memory map.
7. The original URL is cached in Redis.
8. The backend returns the generated short URL and related information.

### 2. Opening a Short URL

1. The browser requests `/api/{shortCode}`.
2. The backend checks Redis for the original URL.
3. If the value is not cached, the backend checks the in-memory `urlMappings`.
4. The backend checks whether the URL has expired.
5. If the URL is valid, a click event is recorded.
6. The backend responds with HTTP `302 Found`.
7. The browser follows the redirect to the original URL.

### 3. Loading Statistics

1. The frontend sends `GET /api/stats/{shortCode}`.
2. The backend retrieves the corresponding `UrlData`.
3. The service builds the statistics response.
4. The backend returns the click count and URL information.

### 4. Scheduled Cleanup

1. Spring scheduling is enabled.
2. `CleanupScheduler` runs at the configured interval.
3. Expired URLs are identified.
4. Expired URLs are marked inactive.
5. Related Redis cache entries are removed.

## Core Backend Components

### `QuickUrlApplication`

The main Spring Boot application class.

It starts the Spring application and enables the application's component scanning and configuration.

### `UrlShortenerController`

Responsible for the HTTP API endpoints:

* Creating short URLs
* Redirecting short URLs
* Getting statistics
* Getting analytics
* Deleting URLs
* Health checks

### `UrlShortenerService`

Contains the core URL-shortening business logic:

* Generating short codes
* Storing URL data
* Looking up URLs
* Recording clicks
* Building statistics and analytics responses
* Reading and writing the Redis cache
* Cleaning up expired URLs

### `RateLimitService`

Responsible for rate limiting:

* Checking whether a client/IP can create short URLs
* Reading rate-limit state from Redis
* Updating rate-limit counters
* Falling back to local memory if Redis is unavailable

### `CleanupScheduler`

Runs periodically and handles cleanup of expired URLs and their associated Redis cache entries.

### `RedisConfig`

Configures Redis-related infrastructure, including:

* `RedisTemplate<String, Object>`
* JSON serialization
* Java time serialization

`LocalDateTime` support is explicitly configured so objects such as `RateLimitData` can be serialized correctly.

## Models

### `UrlData`

Represents a shortened URL.

It contains information such as:

* Original URL
* Short code
* Creation timestamp
* Expiration timestamp
* Click count
* Creator IP
* Active status

### `ClickEvent`

Represents an individual click/redirect event.

It can contain:

* Timestamp
* IP address
* User agent
* Referrer
* Optional country/city information

### `RateLimitData`

Represents temporary rate-limit information for a client/IP.

It contains values such as:

* `requestCount`
* `windowStart`
* `lastRequest`

`requestCount` is **not** the number of clicks on a URL.

It represents the number of requests made by a client during the current rate-limit window.

## Redis Usage

Redis is used for two primary purposes.

### 1. URL Cache

Redis key format:

```text
url:{shortCode}
```

Example:

```text
url:abc123
```

The value contains the original URL.

The cache is used to:

* Speed up redirect lookups
* Reduce repeated access to the primary in-memory store
* Demonstrate the cache-aside pattern
* Automatically remove old cache entries using TTL

The cache TTL is controlled by:

```yaml
quickurl.cache.ttl-minutes
```

### 2. Rate-Limit State

Redis key format:

```text
ratelimit:{clientIp}
```

Example:

```text
ratelimit:192.168.1.10
```

The value contains serialized `RateLimitData`.

Redis allows rate-limit state to be shared between application instances instead of relying exclusively on local Java memory.

The rate-limit state is given a TTL so unused keys are eventually removed.

## Rate Limiting

Rate limiting is applied before creating a shortened URL.

Current behavior:

* Per client/IP
* Configurable requests-per-minute limit
* Configurable requests-per-hour limit
* Redis-backed when available
* Local in-memory fallback if Redis is unavailable

High-level flow:

1. Build a Redis key using the client's IP.
2. Read the existing `RateLimitData`.
3. Create or retrieve the client's rate-limit state.
4. Check whether the current request belongs to the active time window.
5. Reset the counter when the window expires.
6. Increment the request counter when the request is allowed.
7. Store the updated state in Redis.

### Important distinction

There are two different counters in the application:

**Rate-limit requests**

```text
Per client/IP
Temporary
Used to prevent excessive API requests
```

**URL clicks**

```text
Per short URL
Analytics data
Used to track redirects
```

They serve completely different purposes.

## Frontend

The Angular frontend provides a simple interface for:

* Creating shortened URLs
* Displaying generated short URLs
* Retrieving URL statistics
* Viewing basic analytics information

The UI also distinguishes between rate-limit requests and URL click counts.

## Configuration

Backend configuration is located at:

```text
backend/src/main/resources/application.yaml
```

### Application

```yaml
spring:
  application:
    name: quickurl
```

### QuickURL settings

```yaml
quickurl:
  base-url: http://localhost:8080
  short-code:
    length: 6
    max-attempts: 10
  rate-limit:
    requests-per-minute: 2
    requests-per-hour: 10
  cache:
    ttl-minutes: 30
  cleanup:
    interval-minutes: 1
    expired-urls-batch-size: 100
```

### Redis

Redis connection settings use:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

When running through Docker Compose, the backend uses the Redis service defined by the Compose configuration.

## Docker

Docker Compose starts three services:

```text
Frontend
   ↓
Backend
   ↓
Redis
```

The services communicate through the Docker Compose network.

The backend connects to Redis using:

```text
SPRING_DATA_REDIS_HOST
SPRING_DATA_REDIS_PORT
```

## Improvements Made

During development, several parts of the original implementation were improved or corrected:

* Updated Spring Boot Redis configuration properties
* Configured Redis serialization for `LocalDateTime`
* Made Redis cache operations fail-safe when Redis is unavailable
* Corrected generated short URLs to use `/api/{shortCode}`
* Clarified rate-limit requests versus URL click counts in the frontend
* Added explanatory comments around rate-limiting logic
* Renamed the project from TinyLink to QuickURL
* Renamed the Java package from `com.shahbytes.tinylink` to `com.ritik.quickurl`
* Renamed the main application class to `QuickUrlApplication`
* Updated Maven coordinates to `com.ritik:quickurl`

## Current Limitations

This project is intended as a learning and system-design project rather than a production-ready URL-shortening platform.

Current limitations include:

* URL data is stored in memory
* URL data is lost when the backend restarts
* Click analytics are stored in memory
* No persistent database
* Rate-limit updates are not fully atomic
* Hourly rate limiting is simplified
* No authentication
* No URL ownership model
* No audit/history system
* No advanced analytics
* No production observability or monitoring

## Possible Future Improvements

The system could be extended with:

* PostgreSQL or another persistent database
* Redis atomic counters or Lua scripts for robust rate limiting
* Persistent click analytics
* Authentication and user accounts
* URL ownership and management
* Custom domains
* QR code generation
* Advanced analytics
* Admin dashboard
* Prometheus/Grafana monitoring
* Distributed deployment
* Horizontal backend scaling
* Automated tests and integration tests

## Development

### Backend

From the project root:

```powershell
cd backend
.\mvnw.cmd test
```

Run the backend locally:

```powershell
.\mvnw.cmd spring-boot:run
```

Backend:

```text
http://localhost:8080
```

### Frontend

From the project root:

```powershell
cd frontend
npm install
npm start
```

Frontend development server:

```text
http://localhost:4200
```

## Tech Stack

* **Java 17**
* **Spring Boot**
* **Spring Web**
* **Spring Data Redis**
* **Redis**
* **Angular**
* **TypeScript**
* **Maven**
* **Docker**
* **Docker Compose**

## License

This project is intended for learning and educational purposes.
