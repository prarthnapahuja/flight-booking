package com.flightbooking.repository;

import com.flightbooking.model.Booking;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class BookingRepository {

    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();

    public void save(Booking booking) {
        bookings.put(booking.getBookingId(), booking);
    }

    public Optional<Booking> findById(String bookingId) {
        return Optional.ofNullable(bookings.get(bookingId));
    }

    public List<Booking> findAll() {
        return List.copyOf(bookings.values());
    }

    /** Clears all bookings — intended for test isolation only. */
    public void clear() {
        bookings.clear();
    }
}
