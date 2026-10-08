package com.ttcl.games;

import com.ttcl.games.config.CarpetaTemporal;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
// Los repositorios están anidados en dominio.Repositorios; Spring Data no los busca ahí si no se le pide.
@EnableJpaRepositories(considerNestedRepositories = true)
public class TtclApplication {

    public static void main(String[] args) {
        CarpetaTemporal.asegurarRutaAscii();
        SpringApplication.run(TtclApplication.class, args);
    }
}
