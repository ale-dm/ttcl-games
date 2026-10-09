package com.ttcl.games.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración propia del proyecto (prefijo {@code ttcl} en application.yml).
 *
 * @param zonaHoraria zona del equipo, para saber a qué hora del día se jugó cada partida (por defecto, Europe/Madrid)
 */
@ConfigurationProperties(prefix = "ttcl")
public record TtclProperties(
        boolean demo, String equipoJson, String zonaHoraria, Duende duende, Faceit faceit, Smite2 smite2, Sync sync) {

    public ZoneId zona() {
        return zonaHoraria == null || zonaHoraria.isBlank() ? ZoneId.of("Europe/Madrid") : ZoneId.of(zonaHoraria);
    }

    /** Servicio Python del Duende. */
    public record Duende(String url, int timeoutMs) {}

    /** CS2 vía FACEIT Data API. Sin clave, CS2 no se sincroniza. */
    public record Faceit(String apiKey, String base) {
        public boolean configurada() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /** SMITE 2 vía API de Hi-Rez. Sin las tres cosas, SMITE 2 no se sincroniza. */
    public record Smite2(String base, String devId, String authKey) {
        public boolean configurada() {
            return base != null && !base.isBlank() && devId != null && !devId.isBlank() && authKey != null
                    && !authKey.isBlank();
        }
    }

    /** Cada cuántos minutos se sincroniza y cuántas partidas recientes se miran por cuenta. */
    public record Sync(int intervaloMin, int limite) {}
}
