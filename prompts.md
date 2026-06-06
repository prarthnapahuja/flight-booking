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

