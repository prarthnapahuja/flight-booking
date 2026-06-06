package com.flightbooking.service;

import com.flightbooking.dto.Dto;
import com.flightbooking.exception.BookingAlreadyCancelledException;
import com.flightbooking.exception.BookingNotFoundException;
import com.flightbooking.exception.FlightNotFoundException;
import com.flightbooking.exception.InsufficientSeatsException;
import com.flightbooking.model.Booking;
import com.flightbooking.model.Flight;
import com.flightbooking.repository.BookingRepository;
import com.flightbooking.repository.FlightRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private final FlightRepository flightRepository;
    private final BookingRepository bookingRepository;

    public BookingService(FlightRepository flightRepository, BookingRepository bookingRepository) {
        this.flightRepository = flightRepository;
        this.bookingRepository = bookingRepository;
    }

    public Dto.BookingResponse createBooking(Dto.CreateBookingRequest request) {
        Flight flight = flightRepository.findByFlightNumber(request.flightNumber())
                .orElseThrow(() -> new FlightNotFoundException(request.flightNumber()));

        // Atomic CAS-loop reservation — no overbooking possible under concurrent load
        boolean reserved = flight.tryReserveSeats(request.seats());
        if (!reserved) {
            throw new InsufficientSeatsException(
                    flight.getFlightNumber(), request.seats(), flight.getAvailableSeats()
            );
        }

        BigDecimal totalPrice = flight.getPricePerSeat()
                .multiply(BigDecimal.valueOf(request.seats()));

        Booking booking = new Booking(
                UUID.randomUUID().toString(),
                flight.getFlightNumber(),
                request.passengerName(),
                request.passengerEmail(),
                request.seats(),
                totalPrice
        );
        bookingRepository.save(booking);

        return toBookingResponse(booking);
    }

    public Dto.BookingResponse cancelBooking(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        boolean cancelled = booking.cancel();
        if (!cancelled) {
            throw new BookingAlreadyCancelledException(bookingId);
        }

        // Release seats back to the flight
        flightRepository.findByFlightNumber(booking.getFlightNumber())
                .ifPresent(f -> f.releaseSeats(booking.getSeats()));

        return toBookingResponse(booking);
    }

    public Dto.BookingResponse getBooking(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        return toBookingResponse(booking);
    }

    public List<Dto.BookingResponse> getAllBookings() {
        return bookingRepository.findAll().stream()
                .map(this::toBookingResponse)
                .toList();
    }

    private Dto.BookingResponse toBookingResponse(Booking booking) {
        return new Dto.BookingResponse(
                booking.getBookingId(),
                booking.getFlightNumber(),
                booking.getPassengerName(),
                booking.getPassengerEmail(),
                booking.getSeats(),
                booking.getTotalPrice(),
                booking.getBookedAt(),
                booking.getStatus().name()
        );
    }
}
