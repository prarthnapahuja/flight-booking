Prompt 1-

Create a complete Spring Boot 3 Java 21 Maven project for a flight ticket booking REST API.
Requirements:
- In-memory storage only, no database
- Pre-seed some sample flights on startup so the API is immediately usable
- Prevent overbooking — multiple concurrent requests should not be able to book more seats than available
- Endpoints to create a booking (client already knows the flight number, no search needed) and cancel a booking
- Endpoints to list flights and get a single flight
- Use appropriate HTTP status codes
- Input validation with meaningful error messages
- A global exception handler returning consistent error response structure
- No authentication or authorization needed
  Include unit tests for the booking logic and a README with how to run the service, all endpoints, and example curl requests.


Prompt 2-

Can you add a swagger endpoint

Prompt 3-

Add integration tests using @SpringBootTest and MockMvc covering: create booking, overbooking rejection, cancellation, cancellation releasing seats for rebooking, double cancellation, validation errors, unknown flight/booking

Prompt 4-

Review the following areas of the flight booking Spring Boot project for thread safety issues and fix any problems found:

1. The cancel() method on the Booking model — check if the status check and update are atomic
2. The releaseSeats() method on the Flight model — check if seat release is thread-safe under concurrent cancellations
3. The reserveSeats() method on the Flight model — verify the CAS loop implementation is correct
4. The in-memory repositories — check if they use ConcurrentHashMap or regular HashMap, and fix if needed

