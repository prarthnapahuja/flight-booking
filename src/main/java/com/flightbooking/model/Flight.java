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
     * Returns true if reservation succeeded, false if not enough seats are available.
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
            // Another thread modified availableSeats — retry
        }
    }

    public void releaseSeats(int count) {
        availableSeats.addAndGet(count);
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
