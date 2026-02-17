# 🍽️ Restaurant POS Order API

A **Point of Sale (POS)** system for restaurants, built with a **microservices** architecture using **Spring Boot 3**, **MongoDB**, **RabbitMQ**, and **Docker**.

---

## 📋 Table of Contents

- [Architecture](#-architecture)
- [Services](#-services)
  - [Menu Service](#menu-service)
  - [Order Service](#order-service)
- [Resilience — Circuit Breaker & Retry](#-resilience--circuit-breaker--retry)
- [Monitoring](#-monitoring)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Running with Docker Compose](#running-with-docker-compose)
  - [Running Locally](#running-locally)
- [API Endpoints](#-api-endpoints)
  - [Menu Service (port 8081)](#menu-service-port-8081)
  - [Order Service (port 8082)](#order-service-port-8082)
- [Testing the API with requests.http](#-testing-the-api-with-requestshttp)

---

## 🏗️ Architecture

The project follows a **microservices** architecture, where each business domain is isolated in an independent service.

### Microservices Principles Applied

This system implements key microservices architectural patterns:

**1. Single Responsibility Principle**
- **Menu Service**: Exclusively manages the restaurant menu catalog (items, prices, descriptions)
- **Order Service**: Handles all order-related operations (creation, status updates, history)
- Each service owns its bounded context and can evolve independently

**2. Database Per Service**
- Each microservice has its own MongoDB collections
- **Menu Service** → `menu_items` collection
- **Order Service** → `orders` collection

**3. Independent Deployment & Scaling**
- Services can be deployed, updated, and scaled independently
- Different release cycles without affecting other services
- Technology stack can vary per service if needed
- Containerized with Docker for consistent environments

**4. Resilient Communication**
- **Synchronous**: REST APIs with Circuit Breaker and Retry patterns
- **Asynchronous**: Event-driven messaging via RabbitMQ
- Resilient behavior when dependencies are unavailable
- Fault isolation prevents cascading failures

### Inter-Service Communication

| Type | From | To | Description |
|------|------|----|-------------|
| **Synchronous (REST)** | Order Service | Menu Service | Get menu item data when creating an order |
| **Asynchronous (RabbitMQ)** | Order Service (Publisher) | `order-status-notification` queue | Publishes order status change notifications |
| **Asynchronous (RabbitMQ)** | `order-status-notification` queue | Order Service (Consumer) | Consumes notifications and sends to customer notification system |

---

## 📦 Services

### Menu Service

Responsible for **managing the restaurant menu** (CRUD operations on menu items).

**Features:**
- Create menu item
- Update menu item
- Delete menu item
- List all items (with pagination)
- Get item by ID

**Port:** `8081`

### Order Service

Responsible for **managing customer orders**, including creation, status updates, and order history.

**Features:**
- Create order (with item validation via Menu Service)
- Update order status (CREATED → PREPARING → READY → DELIVERED / CANCELLED)
- List order history (with pagination)
- Get order by ID
- Asynchronous status change notification via RabbitMQ

**Port:** `8082`

### Standardized Error Responses

Both services implement a **Global Exception Handler** (`@RestControllerAdvice`) that ensures all API errors follow a consistent JSON structure:

```json
{
  "timestamp": "2026-02-17T12:00:00.000",
  "status": 400,
  "error": "Validation Failed",
  "message": "..."
}
```
---

## 🛡️ Resilience — Circuit Breaker & Retry

One of the key pillars of this architecture is **resilience in inter-service communication**. When the **Order Service** needs to query the **Menu Service** to validate order items, the HTTP call can fail for various reasons (network issues, service downtime, high latency). To handle this, we implemented two resilience patterns with **Resilience4j**:

### Retry

The **Retry** pattern automatically re-executes a failed call, giving the service a chance to recover from transient failures.

**Configuration:**

```yaml
resilience4j:
  retry:
    retryAspectOrder: 2  # Retry executes BEFORE the Circuit Breaker
    instances:
      menuService:
        max-attempts: 3              # Up to 3 attempts
        wait-duration: 1s            # Waits 1 second between attempts
        retry-exceptions:
          - java.lang.Exception      # Retries on any exception
        ignore-exceptions:
          - com.allo.restaurant.order.exception.MenuItemNotFoundException  # Does NOT retry when item doesn't exist
```

**How it works:**
1. The call to Menu Service fails
2. Resilience4j waits **1 second** and retries
3. Repeats up to **3 total attempts**
4. If the error is `MenuItemNotFoundException` (item doesn't exist in the menu), it **does not retry** — since it's a business error, not an infrastructure issue

### Circuit Breaker

The **Circuit Breaker** pattern protects the system against cascading failures. When too many calls fail consecutively, the circuit "opens" and **temporarily blocks new calls**, returning a fallback response immediately.

**Configuration:**

```yaml
resilience4j:
  circuitbreaker:
    circuitBreakerAspectOrder: 1  # Circuit Breaker executes AFTER Retry
    instances:
      menuService:
        register-health-indicator: true
        sliding-window-size: 10                                    # Window of 10 calls
        minimum-number-of-calls: 5                                 # Minimum 5 calls to evaluate
        failure-rate-threshold: 50                                 # Opens circuit if 50% fail
        wait-duration-in-open-state: 30s                           # Stays open for 30 seconds
        permitted-number-of-calls-in-half-open-state: 3            # Allows 3 calls in half-open state
        sliding-window-type: COUNT_BASED                           # Count-based sliding window
        automatic-transition-from-open-to-half-open-enabled: true  # Auto-transition to half-open
```

**Circuit Breaker States:**

The Circuit Breaker operates in three distinct states:

1. **CLOSED** (Normal Operation)
   - All requests pass through to the Menu Service
   - The circuit monitors success and failure rates
   - If the failure rate reaches 50% (with minimum 5 calls), transitions to OPEN

2. **OPEN** (Failure Mode)
   - Circuit blocks all requests immediately
   - Returns fallback response without attempting the call
   - Prevents cascading failures and gives the Menu Service time to recover
   - After 30 seconds, automatically transitions to HALF-OPEN

3. **HALF-OPEN** (Recovery Testing)
   - Allows 3 test requests to pass through
   - If all succeed → transitions back to CLOSED (service recovered)
   - If any fails → transitions back to OPEN (service still unavailable)

**State Transitions:**
- CLOSED → OPEN: When failure rate ≥ 50% (minimum 5 calls)
- OPEN → HALF-OPEN: After 30 seconds wait
- HALF-OPEN → CLOSED: When test calls succeed
- HALF-OPEN → OPEN: When test calls fail

## 📊 Monitoring

### Health Check

The Order Service exposes health check endpoints via Spring Actuator, including the Circuit Breaker state:

```bash
curl http://localhost:8082/actuator/health
```

**Example response with Circuit Breaker:**
```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "menuService": {
          "status": "UP",
          "details": {
            "failureRate": "-1.0%",
            "failureRateThreshold": "50.0%",
            "slowCallRate": "-1.0%",
            "slowCallRateThreshold": "100.0%",
            "bufferedCalls": 1,
            "slowCalls": 0,
            "slowFailedCalls": 0,
            "failedCalls": 0,
            "notPermittedCalls": 0,
            "state": "CLOSED"
          }
        }
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 1081101176832,
        "free": 1014540894208,
        "threshold": 10485760,
        "path": "/app/.",
        "exists": true
      }
    },
    "mongo": {
      "status": "UP",
      "details": {
        "maxWireVersion": 21
      }
    },
    "ping": {
      "status": "UP"
    },
    "rabbit": {
      "status": "UP",
      "details": {
        "version": "3.13.7"
      }
    },
    "ssl": {
      "status": "UP",
      "details": {
        "validChains": [],
        "invalidChains": []
      }
    }
  }
}

```
## 🚀 Getting Started

### Prerequisites

- **Docker** and **Docker Compose** installed
- (Optional) **Java 21** and **Maven 3.9+** — only for local execution without Docker

### Running with Docker Compose

**1. Clone the repository:**
```bash
git clone <repository-url>
cd restaurant-pos-order-api
```

**2. Start all services:**
```bash
docker-compose up --build
```

This will:
- Start **MongoDB** on port `27017`
- Start **RabbitMQ** on ports `5672`
- Build and start **Menu Service** on port `8081`
- Build and start **Order Service** on port `8082`

**3. Run in detached mode (background):**
```bash
docker-compose up --build -d
```

**4. View logs:**
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f order-service
docker-compose logs -f menu-service
```

**5. Stop services:**
```bash
docker-compose down
```

### Running Locally

To run services outside Docker, you need MongoDB and RabbitMQ running locally (or via Docker):

**1. Start only the infrastructure:**
```bash
docker-compose up mongodb rabbitmq -d
```

**2. Run Menu Service:**
```bash
cd menu-service
./mvnw spring-boot:run 
```

**3. Run Order Service (in another terminal):**
```bash
cd order-service
./mvnw spring-boot:run
```

---

## 📡 API Endpoints

### Menu Service (port 8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/menu-items` | Create menu item |
| `GET` | `/menu-items` | List items (query params: `limit`, `offset`) |
| `GET` | `/menu-items/{id}` | Get item by ID |
| `PUT` | `/menu-items/{id}` | Update item |
| `DELETE` | `/menu-items/{id}` | Delete item |

### Order Service (port 8082)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/orders` | Create order |
| `GET` | `/orders` | List orders (query params: `limit`, `offset`) |
| `GET` | `/orders/{orderId}` | Get order by ID |
| `PATCH` | `/orders/{orderId}/status` | Update order status |

---

## 🧪 Testing the API with `requests.http`

The project includes a [`requests.http`](requests.http) file at the root of the repository with all the requests and Curl ready to test the API.

### How to use

1. **Open the file** `requests.http` in your editor
2. **Make sure the services are running** (via Docker Compose or locally)
3. **Click "Send Request"** above each request to execute it

> ⚠️ The variables `{{ menuItemId }}` and `{{ orderId }}` must be filled in manually with the IDs returned in the creation responses.

---
