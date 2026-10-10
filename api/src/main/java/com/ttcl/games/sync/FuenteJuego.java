package com.ttcl.games.sync;

import com.ttcl.games.juego.Juego;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Contrato común de las fuentes de datos. Cada juego tiene un adaptador que traduce su API a estas formas; así el
 * sincronizador y las estadísticas no saben nada de FACEIT ni de Hi-Rez.
 */
public interface FuenteJuego {

    Juego juego();

    /** Busca la cuenta por nick. Vacío si no existe. */
    Optional<CuentaResuelta> resolverCuenta(String nick);

    /**
     * Partidas recientes de la cuenta (la más nueva primero), sin las que ya están en {@code conocidas}: así no se
     * piden otra vez los detalles de partidas guardadas.
     */
    List<PartidaExterna> partidasRecientes(String externalId, int limite, Set<String> conocidas);

    /** Nivel y ELO actuales de la cuenta (P8). Vacío si la fuente no los da (SMITE 2). */
    default Optional<NivelCuenta> nivel(String externalId) {
        return Optional.empty();
    }

    record CuentaResuelta(String externalId, String nombre) {}

    /**
     * @param elo null si la fuente da el nivel pero no el ELO
     * @param steamId steamid de la cuenta (P12: con él se le encuentra en las demos), o null si no lo da
     */
    record NivelCuenta(int nivel, Integer elo, String steamId) {
        public NivelCuenta(int nivel, Integer elo) {
            this(nivel, elo, null);
        }
    }

    /**
     * @param externalPlayerId ID del jugador en la fuente; los del equipo se guardan como participaciones
     * @param nivel su nivel en esa partida (FACEIT), o null si no se sabe: los que no son del equipo y tienen nivel
     *     se guardan, sin identificarlos, como muestras de lo normal en su nivel
     * @param datos lo específico del juego: números o texto, nunca objetos anidados
     */
    record ParticipacionExterna(
            String externalPlayerId,
            Integer nivel,
            Boolean gano,
            Integer kills,
            Integer muertes,
            Integer asistencias,
            Map<String, Object> datos) {}

    /** @param demoUrl dónde está su demo (P12: FACEIT la da en los detalles de la partida), o null */
    record PartidaExterna(
            String externalId,
            Juego juego,
            Instant jugadaEn,
            Integer duracionSeg,
            String modo,
            List<ParticipacionExterna> participaciones,
            String demoUrl) {

        public PartidaExterna(
                String externalId, Juego juego, Instant jugadaEn, Integer duracionSeg, String modo,
                List<ParticipacionExterna> participaciones) {
            this(externalId, juego, jugadaEn, duracionSeg, modo, participaciones, null);
        }
    }
}
