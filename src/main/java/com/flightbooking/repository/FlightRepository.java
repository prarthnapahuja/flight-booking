package com.flightbooking.repository;

import com.flightbooking.model.Flight;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class FlightRepository {

    private final Map<String, Flight> flights = new ConcurrentHashMap<>();

    public void save(Flight flight) {
        flights.put(flight.getFlightNumber(), flight);
    }

    public Optional<Flight> findByFlightNumber(String flightNumber) {
        return Optional.ofNullable(flights.get(flightNumber));
    }

    public List<Flight> findAll() {
        return List.copyOf(flights.values());
    }
}
