package com.ttcl.games.stats;

import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Modelos.FilaParticipacion;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Qué partidas mira el chat cuando pregunta algo que no está en los resúmenes (P9): "¿cómo voy en Mirage este mes?",
 * "¿qué pasó en mis dos últimas derrotas?". Todo opcional; sin nada, todas.
 *
 * @param clave mapa (CS2) o dios (SMITE 2). Sin distinguir mayúsculas, tildes ni el "de_" de los mapas: "Mirage" vale
 *     por "de_mirage"
 * @param gano true solo victorias, false solo derrotas, null las dos
 * @param desde primer día que cuenta (en la zona del equipo), o null
 * @param hasta último día que cuenta (incluido), o null
 * @param ultimas de las que quedan, solo las {@code n} más recientes, o null
 */
public record FiltroPartidas(String clave, Boolean gano, LocalDate desde, LocalDate hasta, Integer ultimas) {

    public static final int MAX_ULTIMAS = 100;

    public FiltroPartidas {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("«desde» va después de «hasta»");
        }
        if (ultimas != null && (ultimas < 1 || ultimas > MAX_ULTIMAS)) {
            throw new IllegalArgumentException("«ultimas» tiene que ir de 1 a " + MAX_ULTIMAS);
        }
        clave = clave == null || clave.isBlank() ? null : clave.trim();
    }

    /** "victoria" o "derrota" (sin distinguir mayúsculas) a lo que guarda el filtro; null o vacío, las dos. */
    public static Boolean resultado(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return switch (texto.trim().toLowerCase(Locale.ROOT)) {
            case "victoria" -> true;
            case "derrota" -> false;
            default -> throw new IllegalArgumentException("Resultado desconocido: " + texto);
        };
    }

    /** Las partidas que pasan el filtro, la más reciente primero. Los días, en la zona del equipo. */
    public List<FilaParticipacion> aplicar(Juego juego, List<FilaParticipacion> filas, ZoneId zona) {
        String buscada = clave == null ? null : normalizar(clave);
        List<FilaParticipacion> dentro = Estadisticas.recientes(filas, Integer.MAX_VALUE).stream()
                .filter(f -> buscada == null || buscada.equals(normalizar(claveDe(juego, f))))
                .filter(f -> gano == null || gano.equals(f.gano()))
                .filter(f -> {
                    LocalDate dia = f.jugadaEn().atZone(zona).toLocalDate();
                    return (desde == null || !dia.isBefore(desde)) && (hasta == null || !dia.isAfter(hasta));
                })
                .toList();
        return ultimas == null ? dentro : dentro.stream().limit(ultimas).toList();
    }

    /** Mapa o dios de la partida, como en el desglose. */
    private static String claveDe(Juego juego, FilaParticipacion f) {
        Object valor = f.datos().get(juego.claveDesglose());
        return valor != null ? String.valueOf(valor) : f.modo();
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return s.replaceFirst("^de_", "").replaceAll("[^a-z0-9]", "");
    }
}
