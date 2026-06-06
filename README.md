# Flight Ticket Booking API

A Spring Boot 3 / Java 21 REST API for booking flight tickets, with in-memory storage and concurrency-safe overbooking prevention.

---

## Requirements

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.8+ |

---

## Run the service

```bash
# Clone / unzip the project, then:
cd flight-booking
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.  
Seven flights are pre-seeded automatically — the API is usable immediately.

---

## Run the tests

```bash
mvn test
```

Tests cover happy paths, every error path, and a 30-thread concurrency test that verifies overbooking is impossible.

---

## API overview

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/flights` | List all flights |
| `GET` | `/api/flights/{flightNumber}` | Get a single flight |
| `POST` | `/api/bookings` | Create a booking |
| `GET` | `/api/bookings/{bookingId}` | Get a single booking |
| `GET` | `/api/bookings` | List all bookings |
| `DELETE` | `/api/bookings/{bookingId}` | Cancel a booking |

---

## Endpoints & curl examples

### List all flights

```bash
curl -s http://localhost:8080/api/flights | jq
```

**200 OK**
```json
[
  {
    "flightNumber": "AI-101",
    "origin": "Delhi",
    "destination": "Mumbai",
    "departureTime": "2026-06-07T06:00:00",
    "arrivalTime": "2026-06-07T08:00:00",
    "totalSeats": 120,
    "availableSeats": 120,
    "pricePerSeat": 4500.00
  }
]
```

---

### Get a single flight

```bash
curl -s http://localhost:8080/api/flights/AI-101 | jq
```

**404 Not Found** (unknown flight number)
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Flight not found: GHOST-000",
  "timestamp": "2026-06-07T10:00:00"
}
```

---

### Create a booking

```bash
curl -s -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "flightNumber": "AI-101",
    "passengerName": "Priya Sharma",
    "passengerEmail": "priya@example.com",
    "seats": 2
  }' | jq
```

**201 Created**
```json
{
  "bookingId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "flightNumber": "AI-101",
  "passengerName": "Priya Sharma",
  "passengerEmail": "priya@example.com",
  "seats": 2,
  "totalPrice": 9000.00,
  "bookedAt": "2026-06-07T10:00:00",
  "status": "CONFIRMED"
}
```

**409 Conflict** (not enough seats)
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Cannot book 5 seat(s) on flight AI-999 — only 3 seat(s) available",
  "timestamp": "2026-06-07T10:00:00"
}
```

**400 Bad Request** (validation failure)
```json
{
  "status": 400,
  "error": "Validation failed",
  "fieldErrors": {
    "passengerEmail": "passengerEmail must be a valid email address",
    "seats": "seats must be at least 1"
  },
  "timestamp": "2026-06-07T10:00:00"
}
```

---

### Get a booking

```bash
curl -s http://localhost:8080/api/bookings/f47ac10b-58cc-4372-a567-0e02b2c3d479 | jq
```

---

### List all bookings

```bash
curl -s http://localhost:8080/api/bookings | jq
```

---

### Cancel a booking

```bash
curl -s -X DELETE \
  http://localhost:8080/api/bookings/f47ac10b-58cc-4372-a567-0e02b2c3d479 | jq
```

**200 OK** — returns the booking with `"status": "CANCELLED"` and restores the seats to the flight.

**409 Conflict** (already cancelled)
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Booking f47ac10b-... is already cancelled",
  "timestamp": "2026-06-07T10:00:00"
}
```

---

## Pre-seeded flights

| Flight | Route | Seats | Price/seat |
|--------|-------|-------|------------|
| AI-101 | Delhi → Mumbai | 120 | ₹4,500 |
| AI-202 | Mumbai → Bangalore | 90 | ₹3,800 |
| 6E-301 | Bangalore → Hyderabad | 150 | ₹2,200 |
| 6E-450 | Hyderabad → Chennai | 150 | ₹2,500 |
| SG-511 | Delhi → Kolkata | 100 | ₹5,200 |
| SG-620 | Chennai → Delhi | 110 | ₹4,900 |
| AI-999 | Delhi → Goa | 60 (3 left) | ₹6,800 |

`AI-999` starts with only 3 seats remaining — useful for testing overbooking rejection immediately.

---

## Design notes

### Overbooking prevention

`Flight.tryReserveSeats()` uses a **compare-and-set (CAS) loop** on an `AtomicInteger`:

```java
public boolean tryReserveSeats(int count) {
    while (true) {
        int current = availableSeats.get();
        if (current < count) return false;
        if (availableSeats.compareAndSet(current, current - count)) return true;
        // another thread raced us — retry with fresh value
    }
}
```

No `synchronized` block, no database transaction — just lock-free atomic hardware instructions. Under any number of concurrent requests, `availableSeats` can never go below zero.

### Error response shape

All errors return the same JSON structure so clients can handle them uniformly:

```json
{ "status": 4xx, "error": "...", "message": "...", "timestamp": "..." }
```

Validation errors include a `fieldErrors` map instead of a single `message`.
