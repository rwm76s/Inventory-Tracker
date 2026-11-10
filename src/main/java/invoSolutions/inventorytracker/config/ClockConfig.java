package invoSolutions.inventorytracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {
    // Injected Clock (not Instant.now()) so tests can pin "now" and exercise backdating deterministically.
    @Bean
    public Clock clock() { return Clock.systemUTC(); }
}
