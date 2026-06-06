package com.flightbooking.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class Dto {

    private Dto() {}

    // ── Request DTOs ──────────────────────────────────────────────────────────

    public record CreateBookingRequest(
            @NotBlank(message = "flightNumber is required")
            String flightNumber,

            @NotBlank(message = "passengerName is required")
            @Size(min = 2, max = 100, message = "passengerName must be 2–100 characters")
            String passengerName,

            @NotBlank(message = "passengerEmail is required")
            @Email(message = "passengerEmail must be a valid email address")
            String passengerEmail,

            @NotNull(message = "seats is required")
            @Min(value = 1, message = "seats must be at least 1")
            @Max(value = 9, message = "seats cannot exceed 9 per booking")
            Integer seats
    ) {}

    // ── Response DTOs ─────────────────────────────────────────────────────────

    public record FlightResponse(
            String flightNumber,
            String origin,
            String destination,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            int totalSeats,
            int availableSeats,
            BigDecimal pricePerSeat
    ) {}

    public record BookingResponse(
            String bookingId,
            String flightNumber,
            String passengerName,
            String passengerEmail,
            int seats,
            BigDecimal totalPrice,
            LocalDateTime bookedAt,
            String status
    ) {}

    public record ErrorResponse(
            int status,
            String error,
            String message,
            LocalDateTime timestamp
    ) {}

    public record ValidationErrorResponse(
            int status,
            String error,
            java.util.Map<String, String> fieldErrors,
            LocalDateTime timestamp
    ) {}
}
