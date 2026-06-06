package com.flightbooking.exception;

public class InsufficientSeatsException extends RuntimeException {
    public InsufficientSeatsException(String flightNumber, int requested, int available) {
        super(String.format(
                "Cannot book %d seat(s) on flight %s — only %d seat(s) available",
                requested, flightNumber, available
        ));
    }
}
