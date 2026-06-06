package com.flightbooking.service;

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
        assertThat(flight.getAvailableSeats()).isEqualTo(2); // unchanged
    }

    @Test
    void tryReserveSeats_failsAtExactZero() {
        Flight flight = buildFlight(3);
        flight.tryReserveSeats(3);
        assertThat(flight.tryReserveSeats(1)).isFalse();
    }

    @Test
    void releaseSeats_restoresAvailability() {
        Flight flight = buildFlight(5);
        flight.tryReserveSeats(5);
        assertThat(flight.getAvailableSeats()).isZero();
        flight.releaseSeats(3);
        assertThat(flight.getAvailableSeats()).isEqualTo(3);
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
}
