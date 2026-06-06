package com.flightbooking.service;

import com.flightbooking.dto.Dto;
import com.flightbooking.exception.FlightNotFoundException;
import com.flightbooking.model.Flight;
import com.flightbooking.repository.FlightRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FlightService {

    private final FlightRepository flightRepository;

    public FlightService(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    public List<Dto.FlightResponse> getAllFlights() {
        return flightRepository.findAll().stream()
                .map(this::toFlightResponse)
                .toList();
    }

    public Dto.FlightResponse getFlight(String flightNumber) {
        Flight flight = flightRepository.findByFlightNumber(flightNumber)
                .orElseThrow(() -> new FlightNotFoundException(flightNumber));
        return toFlightResponse(flight);
    }

    private Dto.FlightResponse toFlightResponse(Flight flight) {
        return new Dto.FlightResponse(
                flight.getFlightNumber(),
                flight.getOrigin(),
                flight.getDestination(),
                flight.getDepartureTime(),
                flight.getArrivalTime(),
                flight.getTotalSeats(),
                flight.getAvailableSeats(),
                flight.getPricePerSeat()
        );
    }
}
