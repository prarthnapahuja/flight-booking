package com.flightbooking.controller;

import com.flightbooking.dto.Dto;
import com.flightbooking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<Dto.BookingResponse> createBooking(
            @Valid @RequestBody Dto.CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.createBooking(request));
    }

    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Dto.BookingResponse> cancelBooking(@PathVariable String bookingId) {
        return ResponseEntity.ok(bookingService.cancelBooking(bookingId));
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<Dto.BookingResponse> getBooking(@PathVariable String bookingId) {
        return ResponseEntity.ok(bookingService.getBooking(bookingId));
    }

    @GetMapping
    public ResponseEntity<List<Dto.BookingResponse>> listBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }
}
