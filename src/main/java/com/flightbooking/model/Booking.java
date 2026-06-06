package com.flightbooking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Booking {

    public enum Status { CONFIRMED, CANCELLED }

    private final String bookingId;
    private final String flightNumber;
    private final String passengerName;
    private final String passengerEmail;
    private final int seats;
    private final BigDecimal totalPrice;
    private final LocalDateTime bookedAt;
    private volatile Status status;

    public Booking(String bookingId, String flightNumber, String passengerName,
                   String passengerEmail, int seats, BigDecimal totalPrice) {
        this.bookingId = bookingId;
        this.flightNumber = flightNumber;
        this.passengerName = passengerName;
        this.passengerEmail = passengerEmail;
        this.seats = seats;
        this.totalPrice = totalPrice;
        this.bookedAt = LocalDateTime.now();
        this.status = Status.CONFIRMED;
    }

    public boolean cancel() {
        if (status == Status.CONFIRMED) {
            status = Status.CANCELLED;
            return true;
        }
        return false;
    }

    public String getBookingId() { return bookingId; }
    public String getFlightNumber() { return flightNumber; }
    public String getPassengerName() { return passengerName; }
    public String getPassengerEmail() { return passengerEmail; }
    public int getSeats() { return seats; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public LocalDateTime getBookedAt() { return bookedAt; }
    public Status getStatus() { return status; }
}
