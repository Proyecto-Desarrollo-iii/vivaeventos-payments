package co.empresa.vivaeventos.payments.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita el procesamiento de la anotación {@code @Observed} mediante AOP.
 * El {@link ObservedAspect} crea un span/observación alrededor de cada método
 * anotado, midiendo su duración y enlazándolo a la traza distribuida (Zipkin).
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
