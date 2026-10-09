package com.ttcl.games.web;

import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Periodo;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Los parámetros {@code ?juego=cs2} y {@code /juegos/smite2} se leen por el código del juego, y
     * {@code ?periodo=7d} por el del periodo.
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Juego.class, Juego::desde);
        registry.addConverter(String.class, Periodo.class, Periodo::de);
    }
}
