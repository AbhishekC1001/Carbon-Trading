# Emerald — Carbon Credit Exchange

A real-time carbon credit trading platform that ingests emissions data, manages credit balances, and matches buy/sell orders continuously.

> **ACP CW3 — Advanced Cloud Platforms**  
> University of Edinburgh  
> Student ID: s2891348

---

## What this project is

A proof-of-concept SaaS platform for carbon credit trading, inspired by ETRM/CTRM systems like ION Aspect. Companies report emissions in real time; the system tracks how much of their regulatory cap has been used; they buy and sell carbon credits through an order-matching engine; everything is backed by a REST API and visible on a live dashboard.

---

## Tech Stack

| Technology | Role |
|---|---|
| **Java 21 + Spring Boot 3.4** | REST API and application logic |
| **Apache Kafka** | Streams emissions data continuously from producers to the processing service |
| **Redis** | Caches live state — company balances, the order book, market price, recent alerts |
| **RabbitMQ** | Asynchronously delivers trade and cap-warning notifications |
| **PostgreSQL** | Permanent storage — companies, trade ledger, emissions history |
| **HTML/CSS/JS Dashboard** | Optional live multi-view dashboard polling REST endpoints |

---

## Prerequisites

- Java 21 or higher
- Docker & Docker Compose
- (Optional) IntelliJ IDEA or any Java IDE
- (Maven not required — the Maven Wrapper `mvnw` is included)

---

## How to Build & Run

### 1. Start the infrastructure

From the project root:

```bash
docker compose up -d
```

This starts Kafka, RabbitMQ, Redis, and Postgres. Wait roughly 20 seconds for everything to be ready.

Verify services are up:

```bash
docker compose ps
```

### 2. Build the application

Using the Maven wrapper (no Maven installation needed):

```bash
# Windows
.\mvnw.cmd clean package -DskipTests

# Mac / Linux
./mvnw clean package -DskipTests
```

### 3. Run the application

Either from your IDE (run the `CarbonTradingApplication` class), or from the command line:

```bash
java -jar target\cw3-0.0.1-SNAPSHOT.jar
```

You should see log lines confirming connections to Redis, Kafka, RabbitMQ and the consumer starting.

### 4. Tear down

When you're done:

```bash
docker compose down -v
```

(The `-v` flag also removes the Postgres volume, giving you a clean slate.)

---

## REST API

Base URL: `http://localhost:8080/api/v1/carbon`

Every feature of the system is exposed through a REST endpoint. The dashboard (described later) is simply a visual client for these endpoints — anything the dashboard does, you can do with `curl` or Postman.

### Company Management

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/company` | Register a new company with a cap |
| `GET` | `/companies` | List all companies with live balance data |
| `GET` | `/balance/{companyId}` | Single company's balance + usage |
| `GET` | `/balances` | All balances (raw Redis state) |

### Trading

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/order` | Place a buy or sell order |
| `GET` | `/orderbook` | Currently open orders |
| `GET` | `/trades` | Last 20 matched trades |
| `GET` | `/trades/{companyId}` | All trades for one company |
| `GET` | `/price` | Current market price |

### Alerts

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/alerts` | Recent alerts (cached in Redis) |

### Simulator

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/simulate/start` | Start emissions stream |
| `POST` | `/simulate/stop` | Stop emissions stream |
| `GET` | `/simulate/status` | Is the simulator running? |

### Example Workflow (using curl)

**1. Register companies:**
```bash
curl -X POST http://localhost:8080/api/v1/carbon/company \
  -H "Content-Type: application/json" \
  -d '{"companyId": "SHELL", "name": "Shell plc", "emissionsCap": 10000}'

curl -X POST http://localhost:8080/api/v1/carbon/company \
  -H "Content-Type: application/json" \
  -d '{"companyId": "BP", "name": "BP plc", "emissionsCap": 8000}'
```

**2. Start the emissions simulator:**
```bash
curl -X POST http://localhost:8080/api/v1/carbon/simulate/start
```

**3. Place a sell order:**
```bash
curl -X POST http://localhost:8080/api/v1/carbon/order \
  -H "Content-Type: application/json" \
  -d '{"companyId": "SHELL", "type": "SELL", "credits": 500, "price": 25.00}'
```

**4. Place a matching buy order — the trade executes immediately:**
```bash
curl -X POST http://localhost:8080/api/v1/carbon/order \
  -H "Content-Type: application/json" \
  -d '{"companyId": "BP", "type": "BUY", "credits": 300, "price": 25.00}'
```

**5. Check the trade history:**
```bash
curl http://localhost:8080/api/v1/carbon/trades
```

**6. Check live balances:**
```bash
curl http://localhost:8080/api/v1/carbon/companies
```

---

## Web Dashboard (optional)

A single-page dashboard is included for visualising the REST API in real time. Open **http://localhost:8080/dashboard.html** in your browser after starting the app.

The dashboard has four views accessible from the sidebar:

- **Overview** — Live market price, company emissions progress bars, recent alerts
- **Trading Desk** — Order entry form + live bid/ask order book
- **Trade History** — All executed trades in chronological order
- **Alert Stream** — Complete feed of all warnings and notifications

It also has two action buttons in the sidebar to avoid needing Postman:

- **Register Company** — Opens a modal to add a new company
- **Start Simulator** — Toggles the emissions simulation on/off

The dashboard polls the REST endpoints every 2 seconds. Every action it performs is just a REST call documented above — nothing the dashboard does is exclusive to it.

---

## Project Structure

```
carbon-trading/
├── pom.xml
├── docker-compose.yml
├── Dockerfile
├── README.md
├── cw3_explanation.pdf
└── src/
    └── main/
        ├── java/uk/ac/ed/acp/cw3/
        │   ├── CarbonTradingApplication.java
        │   ├── config/
        │   │   └── AppConfig.java               # Kafka, Redis, RabbitMQ beans
        │   ├── controller/
        │   │   └── CarbonController.java        # All REST endpoints
        │   ├── model/                           # JPA entities + repositories
        │   │   ├── Company.java
        │   │   ├── CompanyRepository.java
        │   │   ├── Trade.java
        │   │   ├── TradeRepository.java
        │   │   ├── EmissionsReading.java
        │   │   └── EmissionsReadingRepository.java
        │   └── service/
        │       ├── RedisService.java            # Live state management
        │       ├── AlertService.java            # Publishes to RabbitMQ
        │       ├── EmissionsService.java        # Kafka consumer
        │       ├── SimulatorService.java        # Kafka producer (fake data)
        │       └── TradingService.java          # Order matching engine
        └── resources/
            ├── application.yml
            └── static/
                └── dashboard.html               # Multi-view SPA dashboard
```

---

## Architecture

```
 Emissions Simulator
        |
        v
  Kafka [emissions topic]
        |
        v
 EmissionsService (consumer thread)
        |
        +--> Redis              (running totals, credit balances)
        +--> PostgreSQL         (emissions history for audit)
        +--> AlertService --> RabbitMQ (cap warnings & exceeded alerts)

 REST API (buy/sell orders, company registration)
        |
        v
 TradingService (order matching engine)
        |
        +--> Redis              (order book, market price)
        +--> PostgreSQL         (permanent trade ledger)
        +--> AlertService --> RabbitMQ (trade match notifications)

 Dashboard (HTML / JS)
        |
        v
 Polls REST endpoints every 2 seconds
```

---

## Key Design Decisions

**Why Redis and Postgres?**  
Redis holds hot, constantly-changing state — a company's running emissions total updates several times per second. Writing every increment to Postgres would be wasteful. Postgres holds the durable, audit-worthy data: company registrations, matched trades, and individual emissions readings for historical reporting.

**Why Kafka for emissions but RabbitMQ for alerts?**  
Emissions data is a continuous stream that potentially many consumers might want to read (the balance service, an analytics service, a regulatory reporting service). Kafka's pub-sub with independent consumer offsets is the right fit. Alerts are discrete events routed to specific consumers; RabbitMQ's queue-based model is simpler.

**Why no authentication?**  
This is a proof-of-concept. Companies are identified by a `companyId` string passed in each request. In production this would be replaced with OAuth/JWT.

**Why polling in the dashboard instead of WebSockets?**  
Simpler to build and simpler to reason about. A 2-second poll on lightweight REST endpoints is sufficient for human-speed trading visualisation.

---

## AI Usage

Assistance with architectural brainstorming, debugging, and iterating on the UI design was provided by Claude (Anthropic). All design decisions, code structure, and implementation were reviewed and understood before inclusion. See `cw3_explanation.pdf` for details.
