package com.flightbooking.service;

import com.flightbooking.model.Booking;
import com.flightbooking.model.Flight;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FlightConcurrencyTest {

    private Flight buildFlight(int seats) {
        return new Flight("F-TEST", "X", "Y",
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1),
                seats, BigDecimal.TEN);
    }

    private Booking buildBooking(String flightNumber, int seats) {
        return new Booking(
                java.util.UUID.randomUUID().toString(),
                flightNumber, "Test Passenger", "test@example.com",
                seats, BigDecimal.TEN.multiply(BigDecimal.valueOf(seats))
        );
    }

    // ── tryReserveSeats ───────────────────────────────────────────────────────

    @Test
    void tryReserveSeats_succeeds_whenEnoughSeatsAvailable() {
        Flight flight = buildFlight(5);
        assertThat(flight.tryReserveSeats(3)).isTrue();
        assertThat(flight.getAvailableSeats()).isEqualTo(2);
    }

    @Test
    void tryReserveSeats_fails_whenNotEnoughSeats() {
        Flight flight = buildFlight(2);
        assertThat(flight.tryReserveSeats(3)).isFalse();
        assertThat(flight.getAvailableSeats()).isEqualTo(2);
    }

    @Test
    void tryReserveSeats_failsAtExactZero() {
        Flight flight = buildFlight(3);
        flight.tryReserveSeats(3);
        assertThat(flight.tryReserveSeats(1)).isFalse();
    }

    @Test
    void highConcurrency_neverOverbooksSeats() throws InterruptedException {
        int capacity = 20;
        int threads = 100;
        Flight flight = buildFlight(capacity);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger booked = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                start.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                if (flight.tryReserveSeats(1)) booked.incrementAndGet();
                done.countDown();
            });
        }

        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(booked.get()).isEqualTo(capacity);
        assertThat(flight.getAvailableSeats()).isZero();
    }

    // ── releaseSeats ──────────────────────────────────────────────────────────

    @Test
    void releaseSeats_restoresAvailability() {
        Flight flight = buildFlight(5);
        flight.tryReserveSeats(5);
        assertThat(flight.getAvailableSeats()).isZero();
        flight.releaseSeats(3);
        assertThat(flight.getAvailableSeats()).isEqualTo(3);
    }

    @Test
    void releaseSeats_neverExceedsTotalSeats_defensiveCap() {
        // Even if releaseSeats is somehow called twice for the same booking,
        // availableSeats must never go above totalSeats.
        Flight flight = buildFlight(5);
        flight.tryReserveSeats(3);                 // 2 available
        flight.releaseSeats(3);                    // back to 5
        flight.releaseSeats(3);                    // spurious second call
        assertThat(flight.getAvailableSeats()).isEqualTo(5); // capped at totalSeats
    }

    @Test
    void concurrentReleases_neverExceedTotalSeats() throws InterruptedException {
        int totalSeats = 10;
        int threads = 50;
        Flight flight = buildFlight(totalSeats);
        flight.tryReserveSeats(totalSeats); // fill completely

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);

        // 50 threads all try to release 1 seat concurrently
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                start.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                flight.releaseSeats(1);
                done.countDown();
            });
        }

        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Must never exceed capacity regardless of how many releases happened
        assertThat(flight.getAvailableSeats()).isLessThanOrEqualTo(totalSeats);
        assertThat(flight.getAvailableSeats()).isEqualTo(totalSeats); // cap applied
    }

    // ── Booking.cancel() ──────────────────────────────────────────────────────

    @Test
    void cancel_firstCall_returnsTrue() {
        Booking booking = buildBooking("F-TEST", 2);
        assertThat(booking.cancel()).isTrue();
        assertThat(booking.getStatus()).isEqualTo(Booking.Status.CANCELLED);
    }

    @Test
    void cancel_secondCall_returnsFalse() {
        Booking booking = buildBooking("F-TEST", 2);
        booking.cancel();
        assertThat(booking.cancel()).isFalse();
    }

    @Test
    void cancel_concurrentCalls_exactlyOneSucceeds() throws InterruptedException {
        // This is the core race the volatile fix was meant to solve.
        // 50 threads all call cancel() simultaneously on the same booking.
        // Exactly 1 must return true; the rest must return false.
        int threads = 50;
        Booking booking = buildBooking("F-TEST", 2);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                start.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                if (booking.cancel()) successCount.incrementAndGet();
                done.countDown();
            });
        }

        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // If volatile was still used, successCount could be > 1,
        // causing seats to be released multiple times.
        assertThat(successCount.get())
                .as("Exactly one cancel() call should succeed across all threads")
                .isEqualTo(1);

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.CANCELLED);
    }

    @Test
    void cancel_concurrentCalls_seatsReleasedExactlyOnce() throws InterruptedException {
        // End-to-end: 50 threads race to cancel the same 3-seat booking.
        // The flight should gain back exactly 3 seats — not 6, not 150.
        int threads = 50;
        int bookedSeats = 3;
        int totalSeats = 10;

        Flight flight = buildFlight(totalSeats);
        flight.tryReserveSeats(bookedSeats); // 7 remaining
        Booking booking = buildBooking(flight.getFlightNumber(), bookedSeats);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                start.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                // Mirrors exactly what BookingService.cancelBooking() does
                if (booking.cancel()) {
                    flight.releaseSeats(booking.getSeats());
                }
                done.countDown();
            });
        }

        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 7 original + 3 released = 10. Must not be 7 + 3 + 3 + 3... = overflow
        assertThat(flight.getAvailableSeats())
                .as("Seats should be restored exactly once")
                .isEqualTo(totalSeats - bookedSeats + bookedSeats) // = totalSeats
                .isEqualTo(10);
    }
}
