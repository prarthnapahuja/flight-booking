package com.flightbooking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightbooking.model.Flight;
import com.flightbooking.repository.BookingRepository;
import com.flightbooking.repository.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BookingIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired FlightRepository flightRepository;
    @Autowired BookingRepository bookingRepository;

    @BeforeEach
    void resetState() {
        bookingRepository.clear();
        flightRepository.clear();

        LocalDateTime base = LocalDateTime.now().plusDays(1)
                .withHour(8).withMinute(0).withSecond(0).withNano(0);

        // 9 seats — fits within the @Max(9) per-booking validation limit,
        // so a single request can fill the flight completely
        flightRepository.save(new Flight(
                "TEST-001", "Delhi", "Mumbai",
                base, base.plusHours(2),
                9, new BigDecimal("1000.00")
        ));

        // Nearly-full — 2 seats remaining
        Flight tightFlight = new Flight(
                "TEST-002", "Mumbai", "Goa",
                base.plusHours(4), base.plusHours(5),
                5, new BigDecimal("2000.00")
        );
        tightFlight.tryReserveSeats(3);
        flightRepository.save(tightFlight);
    }

    // ── Flight endpoints ──────────────────────────────────────────────────────

    @Test
    void listFlights_returns200_withSeededFlights() throws Exception {
        mockMvc.perform(get("/api/flights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].flightNumber", containsInAnyOrder("TEST-001", "TEST-002")));
    }

    @Test
    void getFlight_knownFlightNumber_returns200() throws Exception {
        mockMvc.perform(get("/api/flights/TEST-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("TEST-001"))
                .andExpect(jsonPath("$.origin").value("Delhi"))
                .andExpect(jsonPath("$.destination").value("Mumbai"))
                .andExpect(jsonPath("$.totalSeats").value(9))
                .andExpect(jsonPath("$.availableSeats").value(9))
                .andExpect(jsonPath("$.pricePerSeat").value(1000.00));
    }

    @Test
    void getFlight_unknownFlightNumber_returns404() throws Exception {
        mockMvc.perform(get("/api/flights/GHOST-000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("GHOST-000")));
    }

    // ── Create booking ────────────────────────────────────────────────────────

    @Test
    void createBooking_validRequest_returns201AndDeductsSeats() throws Exception {
        String body = """
                {
                  "flightNumber": "TEST-001",
                  "passengerName": "Prarthna Pahuja",
                  "passengerEmail": "prarthna@example.com",
                  "seats": 3
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").isNotEmpty())
                .andExpect(jsonPath("$.flightNumber").value("TEST-001"))
                .andExpect(jsonPath("$.passengerName").value("Prarthna Pahuja"))
                .andExpect(jsonPath("$.seats").value(3))
                .andExpect(jsonPath("$.totalPrice").value(3000.00))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Verify seats were actually deducted from the flight
        mockMvc.perform(get("/api/flights/TEST-001"))
                .andExpect(jsonPath("$.availableSeats").value(6));
    }

    @Test
    void createBooking_unknownFlight_returns404() throws Exception {
        String body = """
                {
                  "flightNumber": "NO-SUCH",
                  "passengerName": "Alice",
                  "passengerEmail": "alice@example.com",
                  "seats": 1
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("NO-SUCH")));
    }

    // ── Overbooking ───────────────────────────────────────────────────────────

    @Test
    void createBooking_requestMoreSeatsThanAvailable_returns409() throws Exception {
        // TEST-002 has only 2 seats remaining
        String body = """
                {
                  "flightNumber": "TEST-002",
                  "passengerName": "Bob",
                  "passengerEmail": "bob@example.com",
                  "seats": 3
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("TEST-002")))
                .andExpect(jsonPath("$.message").value(containsString("2")));
    }

    @Test
    void createBooking_exactlyAvailableSeats_succeeds() throws Exception {
        // TEST-002 has exactly 2 seats — booking both should work
        String body = """
                {
                  "flightNumber": "TEST-002",
                  "passengerName": "Carol",
                  "passengerEmail": "carol@example.com",
                  "seats": 2
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/flights/TEST-002"))
                .andExpect(jsonPath("$.availableSeats").value(0));
    }

    @Test
    void createBooking_afterFlightFullyBooked_returns409() throws Exception {
        // Fill TEST-001 completely (9 seats = one request at the @Max limit)
        createBooking("TEST-001", "Dave", "dave@example.com", 9);

        // Any further request should be rejected
        String body = """
                {
                  "flightNumber": "TEST-001",
                  "passengerName": "Eve",
                  "passengerEmail": "eve@example.com",
                  "seats": 1
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ── Cancellation ──────────────────────────────────────────────────────────

    @Test
    void cancelBooking_confirmedBooking_returns200WithCancelledStatus() throws Exception {
        String bookingId = createBooking("TEST-001", "Frank", "frank@example.com", 2);

        mockMvc.perform(delete("/api/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelBooking_releasesSeatsForRebooking() throws Exception {
        // Fill all 9 seats
        String firstBookingId = createBooking("TEST-001", "Grace", "grace@example.com", 9);

        mockMvc.perform(get("/api/flights/TEST-001"))
                .andExpect(jsonPath("$.availableSeats").value(0));

        // Cancel — seats should be released
        mockMvc.perform(delete("/api/bookings/" + firstBookingId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/flights/TEST-001"))
                .andExpect(jsonPath("$.availableSeats").value(9));

        // Another passenger can now book
        String body = """
                {
                  "flightNumber": "TEST-001",
                  "passengerName": "Heidi",
                  "passengerEmail": "heidi@example.com",
                  "seats": 5
                }
                """;
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void cancelBooking_doubleCancellation_returns409() throws Exception {
        String bookingId = createBooking("TEST-001", "Ivan", "ivan@example.com", 1);

        mockMvc.perform(delete("/api/bookings/" + bookingId))
                .andExpect(status().isOk());

        // Second cancel should be rejected
        mockMvc.perform(delete("/api/bookings/" + bookingId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already cancelled")));
    }

    @Test
    void cancelBooking_unknownBookingId_returns404() throws Exception {
        mockMvc.perform(delete("/api/bookings/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("does-not-exist")));
    }

    // ── Get booking ───────────────────────────────────────────────────────────

    @Test
    void getBooking_knownId_returns200() throws Exception {
        String bookingId = createBooking("TEST-001", "Judy", "judy@example.com", 1);

        mockMvc.perform(get("/api/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.passengerName").value("Judy"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void getBooking_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/bookings/no-such-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── Validation errors ─────────────────────────────────────────────────────

    @Test
    void createBooking_missingFlightNumber_returns400WithFieldError() throws Exception {
        String body = """
                {
                  "passengerName": "Karl",
                  "passengerEmail": "karl@example.com",
                  "seats": 1
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.flightNumber").value("flightNumber is required"));
    }

    @Test
    void createBooking_invalidEmail_returns400WithFieldError() throws Exception {
        String body = """
                {
                  "flightNumber": "TEST-001",
                  "passengerName": "Laura",
                  "passengerEmail": "not-an-email",
                  "seats": 1
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.passengerEmail")
                        .value("passengerEmail must be a valid email address"));
    }

    @Test
    void createBooking_zeroSeats_returns400WithFieldError() throws Exception {
        String body = """
                {
                  "flightNumber": "TEST-001",
                  "passengerName": "Mike",
                  "passengerEmail": "mike@example.com",
                  "seats": 0
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.seats").value("seats must be at least 1"));
    }

    @Test
    void createBooking_tooManySeats_returns400WithFieldError() throws Exception {
        // @Max(9) — 10 should fail validation before hitting business logic
        String body = """
                {
                  "flightNumber": "TEST-001",
                  "passengerName": "Nina",
                  "passengerEmail": "nina@example.com",
                  "seats": 10
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.seats").value("seats cannot exceed 9 per booking"));
    }

    @Test
    void createBooking_nameTooShort_returns400WithFieldError() throws Exception {
        String body = """
                {
                  "flightNumber": "TEST-001",
                  "passengerName": "X",
                  "passengerEmail": "x@example.com",
                  "seats": 1
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.passengerName").exists());
    }

    @Test
    void createBooking_multipleValidationErrors_allFieldErrorsPresent() throws Exception {
        // Missing flightNumber, short name, bad email, zero seats
        String body = """
                {
                  "passengerName": "X",
                  "passengerEmail": "bad",
                  "seats": 0
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.flightNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.passengerEmail").exists())
                .andExpect(jsonPath("$.fieldErrors.seats").exists())
                .andExpect(jsonPath("$.fieldErrors.passengerName").exists());
    }

    @Test
    void createBooking_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    // ── List bookings ─────────────────────────────────────────────────────────

    @Test
    void listBookings_returnsAllBookings() throws Exception {
        createBooking("TEST-001", "Oscar", "oscar@example.com", 1);
        createBooking("TEST-001", "Peggy", "peggy@example.com", 2);

        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String createBooking(String flightNumber, String name, String email, int seats) throws Exception {
        String body = String.format("""
                {
                  "flightNumber": "%s",
                  "passengerName": "%s",
                  "passengerEmail": "%s",
                  "seats": %d
                }
                """, flightNumber, name, email, seats);

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("bookingId").asText();
    }
}
