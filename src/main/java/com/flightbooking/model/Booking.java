package com.flightbooking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

public class Booking {

    public enum Status { CONFIRMED, CANCELLED }

    private final String bookingId;
    private final String flightNumber;
    private final String passengerName;
    private final String passengerEmail;
    private final int seats;
    private final BigDecimal totalPrice;
    private final LocalDateTime bookedAt;

    // AtomicReference makes the check-then-act in cancel() a single atomic CAS,
    // eliminating the race window that volatile leaves open.
    private final AtomicReference<Status> status;

    public Booking(String bookingId, String flightNumber, String passengerName,
                   String passengerEmail, int seats, BigDecimal totalPrice) {
        this.bookingId = bookingId;
        this.flightNumber = flightNumber;
        this.passengerName = passengerName;
        this.passengerEmail = passengerEmail;
        this.seats = seats;
        this.totalPrice = totalPrice;
        this.bookedAt = LocalDateTime.now();
        this.status = new AtomicReference<>(Status.CONFIRMED);
    }

    /**
     * Atomically transitions status from CONFIRMED → CANCELLED.
     *
     * Returns true exactly once across all concurrent callers.
     * Any subsequent call (from any thread) returns false, so releaseSeats()
     * in BookingService is guaranteed to execute at most once per booking.
     *
     * compareAndSet is a single hardware instruction — no window between
     * the read and the write for another thread to sneak through.
     */
    public boolean cancel() {
        return status.compareAndSet(Status.CONFIRMED, Status.CANCELLED);
    }

    public String getBookingId() { return bookingId; }
    public String getFlightNumber() { return flightNumber; }
    public String getPassengerName() { return passengerName; }
    public String getPassengerEmail() { return passengerEmail; }
    public int getSeats() { return seats; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public LocalDateTime getBookedAt() { return bookedAt; }
    public Status getStatus() { return status.get(); }
}
