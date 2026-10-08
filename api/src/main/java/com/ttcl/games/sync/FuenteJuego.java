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

    record CuentaResuelta(String externalId, String nombre) {}

    /**
     * @param externalPlayerId ID del jugador en la fuente; solo se guardan las de miembros del equipo
     * @param datos lo específico del juego: números o texto, nunca objetos anidados
     */
    record ParticipacionExterna(
            String externalPlayerId,
            Boolean gano,
            Integer kills,
            Integer muertes,
            Integer asistencias,
            Map<String, Object> datos) {}

    record PartidaExterna(
            String externalId,
            Juego juego,
            Instant jugadaEn,
            Integer duracionSeg,
            String modo,
            List<ParticipacionExterna> participaciones) {}
}
