package com.ttcl.games.analisis;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mira las demos pendientes cada {@code ttcl.analisis.intervalo-min} minutos (la primera vez, dos minutos después de
 * arrancar, tras la primera sincronización). Sin trabajador de análisis configurado, nada (P12).
 */
@Component
public class AnalisisProgramado {

    private static final Logger log = LoggerFactory.getLogger(AnalisisProgramado.class);

    private final AnalisisDemos analisis;

    public AnalisisProgramado(AnalisisDemos analisis) {
        this.analisis = analisis;
    }

    @Scheduled(initialDelay = 2, fixedDelayString = "${ttcl.analisis.intervalo-min:15}", timeUnit = TimeUnit.MINUTES)
    public void analizar() {
        if (!analisis.activo()) {
            return;
        }
        try {
            var r = analisis.analizarPendientes();
            if (r.analizadas() + r.fallidas() > 0) {
                log.info("Demos: {} analizadas, {} que no se pueden analizar", r.analizadas(), r.fallidas());
            }
        } catch (RuntimeException e) {
            log.warn("No se pudieron analizar las demos pendientes: {}", e.getMessage());
        }
    }
}
