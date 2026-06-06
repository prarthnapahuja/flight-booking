package com.flightbooking.controller;

import com.flightbooking.dto.Dto;
import com.flightbooking.service.FlightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flights")
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }

    @GetMapping
    public ResponseEntity<List<Dto.FlightResponse>> listFlights() {
        return ResponseEntity.ok(flightService.getAllFlights());
    }

    @GetMapping("/{flightNumber}")
    public ResponseEntity<Dto.FlightResponse> getFlight(@PathVariable String flightNumber) {
        return ResponseEntity.ok(flightService.getFlight(flightNumber));
    }
}
