package com.flightbooking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

public class Flight {

    private final String flightNumber;
    private final String origin;
    private final String destination;
    private final LocalDateTime departureTime;
    private final LocalDateTime arrivalTime;
    private final int totalSeats;
    private final AtomicInteger availableSeats;
    private final BigDecimal pricePerSeat;

    public Flight(String flightNumber, String origin, String destination,
                  LocalDateTime departureTime, LocalDateTime arrivalTime,
                  int totalSeats, BigDecimal pricePerSeat) {
        this.flightNumber = flightNumber;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.totalSeats = totalSeats;
        this.availableSeats = new AtomicInteger(totalSeats);
        this.pricePerSeat = pricePerSeat;
    }

    /**
     * Thread-safe seat reservation using a CAS loop.
     *
     * Pattern: read → guard-check → compareAndSet → retry on contention.
     * If another thread changes availableSeats between our read and our CAS,
     * compareAndSet returns false and we loop with a fresh read.
     * This guarantees availableSeats never goes below zero.
     */
    public boolean tryReserveSeats(int count) {
        while (true) {
            int current = availableSeats.get();
            if (current < count) {
                return false;
            }
            if (availableSeats.compareAndSet(current, current - count)) {
                return true;
            }
            // Lost the CAS race — another thread modified availableSeats; retry
        }
    }

    /**
     * Returns reserved seats to the pool after a cancellation.
     *
     * addAndGet is a single atomic operation so concurrent cancellations
     * cannot lose an update. The cap at totalSeats is a defensive guard —
     * Booking.cancel() already ensures releaseSeats() is only reachable once
     * per booking via its own CAS, but belt-and-suspenders here costs nothing.
     */
    public void releaseSeats(int count) {
        availableSeats.updateAndGet(current ->
                Math.min(current + count, totalSeats)
        );
    }

    public String getFlightNumber() { return flightNumber; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public LocalDateTime getDepartureTime() { return departureTime; }
    public LocalDateTime getArrivalTime() { return arrivalTime; }
    public int getTotalSeats() { return totalSeats; }
    public int getAvailableSeats() { return availableSeats.get(); }
    public BigDecimal getPricePerSeat() { return pricePerSeat; }
}
