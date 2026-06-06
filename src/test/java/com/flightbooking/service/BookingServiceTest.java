package com.flightbooking.service;

import com.flightbooking.dto.Dto;
import com.flightbooking.exception.BookingAlreadyCancelledException;
import com.flightbooking.exception.BookingNotFoundException;
import com.flightbooking.exception.FlightNotFoundException;
import com.flightbooking.exception.InsufficientSeatsException;
import com.flightbooking.model.Flight;
import com.flightbooking.repository.BookingRepository;
import com.flightbooking.repository.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class BookingServiceTest {

    private FlightRepository flightRepository;
    private BookingRepository bookingRepository;
    private BookingService bookingService;

    private static final String FLIGHT_NUMBER = "TEST-001";
    private static final int TOTAL_SEATS = 10;

    @BeforeEach
    void setUp() {
        flightRepository = new FlightRepository();
        bookingRepository = new BookingRepository();
        bookingService = new BookingService(flightRepository, bookingRepository);

        flightRepository.save(new Flight(
                FLIGHT_NUMBER, "A", "B",
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2),
                TOTAL_SEATS,
                new BigDecimal("1000.00")
        ));
    }

    // ── Happy-path tests ──────────────────────────────────────────────────────

    @Test
    void createBooking_succeeds_and_deductsSeats() {
        var request = new Dto.CreateBookingRequest(FLIGHT_NUMBER, "Alice Smith", "alice@example.com", 3);

        Dto.BookingResponse response = bookingService.createBooking(request);

        assertThat(response.bookingId()).isNotBlank();
        assertThat(response.flightNumber()).isEqualTo(FLIGHT_NUMBER);
        assertThat(response.seats()).isEqualTo(3);
        assertThat(response.totalPrice()).isEqualByComparingTo("3000.00");
        assertThat(response.status()).isEqualTo("CONFIRMED");

        // Flight should have 7 seats remaining
        assertThat(flightRepository.findByFlightNumber(FLIGHT_NUMBER).get().getAvailableSeats())
                .isEqualTo(TOTAL_SEATS - 3);
    }

    @Test
    void cancelBooking_restoresSeats() {
        var createRequest = new Dto.CreateBookingRequest(FLIGHT_NUMBER, "Bob Jones", "bob@example.com", 2);
        Dto.BookingResponse created = bookingService.createBooking(createRequest);

        Dto.BookingResponse cancelled = bookingService.cancelBooking(created.bookingId());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(flightRepository.findByFlightNumber(FLIGHT_NUMBER).get().getAvailableSeats())
                .isEqualTo(TOTAL_SEATS); // seats restored
    }

    @Test
    void getBooking_returnsCorrectBooking() {
        var request = new Dto.CreateBookingRequest(FLIGHT_NUMBER, "Carol White", "carol@example.com", 1);
        Dto.BookingResponse created = bookingService.createBooking(request);

        Dto.BookingResponse fetched = bookingService.getBooking(created.bookingId());

        assertThat(fetched.bookingId()).isEqualTo(created.bookingId());
        assertThat(fetched.passengerName()).isEqualTo("Carol White");
    }

    // ── Error-path tests ──────────────────────────────────────────────────────

    @Test
    void createBooking_unknownFlight_throwsFlightNotFoundException() {
        var request = new Dto.CreateBookingRequest("GHOST-000", "Dave", "dave@example.com", 1);

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(FlightNotFoundException.class)
                .hasMessageContaining("GHOST-000");
    }

    @Test
    void createBooking_notEnoughSeats_throwsInsufficientSeatsException() {
        var request = new Dto.CreateBookingRequest(FLIGHT_NUMBER, "Eve", "eve@example.com", TOTAL_SEATS + 1);

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(InsufficientSeatsException.class);
    }

    @Test
    void cancelBooking_unknownId_throwsBookingNotFoundException() {
        assertThatThrownBy(() -> bookingService.cancelBooking("no-such-id"))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining("no-such-id");
    }

    @Test
    void cancelBooking_alreadyCancelled_throwsBookingAlreadyCancelledException() {
        var request = new Dto.CreateBookingRequest(FLIGHT_NUMBER, "Frank", "frank@example.com", 1);
        Dto.BookingResponse created = bookingService.createBooking(request);
        bookingService.cancelBooking(created.bookingId());

        assertThatThrownBy(() -> bookingService.cancelBooking(created.bookingId()))
                .isInstanceOf(BookingAlreadyCancelledException.class);
    }

    @Test
    void getBooking_unknownId_throwsBookingNotFoundException() {
        assertThatThrownBy(() -> bookingService.getBooking("missing"))
                .isInstanceOf(BookingNotFoundException.class);
    }

    // ── Concurrency test ──────────────────────────────────────────────────────

    @Test
    void concurrentBookings_neverExceedAvailableSeats() throws InterruptedException {
        int threads = 30;                       // 30 threads each trying to book 1 seat
        int seatsPerRequest = 1;               // total demand = 30, but only 10 seats
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    var req = new Dto.CreateBookingRequest(
                            FLIGHT_NUMBER,
                            "Passenger-" + idx,
                            "p" + idx + "@example.com",
                            seatsPerRequest
                    );
                    bookingService.createBooking(req);
                    successCount.incrementAndGet();
                } catch (InsufficientSeatsException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    synchronized (errors) { errors.add(e.getMessage()); }
                }
            });
        }

        ready.await();
        go.countDown();
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(errors).isEmpty();
        assertThat(successCount.get()).isEqualTo(TOTAL_SEATS);       // exactly 10 succeeded
        assertThat(failureCount.get()).isEqualTo(threads - TOTAL_SEATS); // 20 were rejected

        int remainingSeats = flightRepository.findByFlightNumber(FLIGHT_NUMBER)
                .get().getAvailableSeats();
        assertThat(remainingSeats).isZero();
    }
}
