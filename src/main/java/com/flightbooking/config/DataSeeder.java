package com.flightbooking.config;

import com.flightbooking.model.Flight;
import com.flightbooking.repository.FlightRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Configuration
public class DataSeeder {

    @Bean
    ApplicationRunner seedFlights(FlightRepository repo) {
        return args -> {
            LocalDateTime base = LocalDateTime.now().plusDays(1).withHour(6).withMinute(0).withSecond(0).withNano(0);

            repo.save(new Flight("AI-101", "Delhi", "Mumbai",
                    base, base.plusHours(2), 120, new BigDecimal("4500.00")));

            repo.save(new Flight("AI-202", "Mumbai", "Bangalore",
                    base.plusHours(4), base.plusHours(5).plusMinutes(30), 90, new BigDecimal("3800.00")));

            repo.save(new Flight("6E-301", "Bangalore", "Hyderabad",
                    base.plusHours(8), base.plusHours(9), 150, new BigDecimal("2200.00")));

            repo.save(new Flight("6E-450", "Hyderabad", "Chennai",
                    base.plusHours(10), base.plusHours(11).plusMinutes(15), 150, new BigDecimal("2500.00")));

            repo.save(new Flight("SG-511", "Delhi", "Kolkata",
                    base.plusHours(7), base.plusHours(9).plusMinutes(30), 100, new BigDecimal("5200.00")));

            repo.save(new Flight("SG-620", "Chennai", "Delhi",
                    base.plusHours(14), base.plusHours(16).plusMinutes(45), 110, new BigDecimal("4900.00")));

            // One nearly-full flight to demonstrate overbooking protection
            Flight nearlyFull = new Flight("AI-999", "Delhi", "Goa",
                    base.plusHours(16), base.plusHours(18).plusMinutes(30), 60, new BigDecimal("6800.00"));
            // Simulate 57 seats already taken
            for (int i = 0; i < 57; i++) nearlyFull.tryReserveSeats(1);
            repo.save(nearlyFull);
        };
    }
}
