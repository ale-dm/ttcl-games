package com.ttcl.games.config;

import java.nio.file.Path;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración propia del proyecto (prefijo {@code ttcl} en application.yml).
 *
 * @param zonaHoraria zona del equipo, para saber a qué hora del día se jugó cada partida (por defecto, Europe/Madrid)
 */
@ConfigurationProperties(prefix = "ttcl")
public record TtclProperties(
        boolean demo, String equipoJson, String zonaHoraria, Duende duende, Faceit faceit, Smite2 smite2, Sync sync,
        Discord discord, Analisis analisis) {

    public ZoneId zona() {
        return zonaHoraria == null || zonaHoraria.isBlank() ? ZoneId.of("Europe/Madrid") : ZoneId.of(zonaHoraria);
    }

    /** Servicio Python del Duende. */
    public record Duende(String url, int timeoutMs) {}

    /** CS2 vía FACEIT Data API (gratis). Sin clave, CS2 no se sincroniza. */
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

    /**
     * Trabajador de análisis de demos de CS2 (P12). Sin URL, no se analiza nada.
     *
     * @param timeoutMs cuánto se espera a que analice una demo (descargarla y leerla puede llevar minutos)
     * @param intervaloMin cada cuántos minutos se miran las demos pendientes
     * @param lote cuántas demos se miran cada vez
     * @param carpeta carpeta con demos dejadas a mano (la misma que lee el trabajador)
     */
    public record Analisis(String url, int timeoutMs, int intervaloMin, int lote, String carpeta) {
        public boolean configurado() {
            return url != null && !url.isBlank();
        }

        public Path rutaCarpeta() {
            return Path.of(carpeta == null || carpeta.isBlank() ? "../demos" : carpeta).toAbsolutePath().normalize();
        }
    }

    /**
     * Webhook de un canal de Discord (P11). Sin URL, no se publica nada.
     *
     * @param lang idioma de lo que se publica ("es" o "en")
     * @param resumenSemanal cuándo se publica el resumen de la semana (cron de Spring, en la zona del equipo)
     */
    public record Discord(String webhookUrl, String lang, String resumenSemanal) {
        public boolean configurado() {
            return webhookUrl != null && !webhookUrl.isBlank();
        }
    }
}
