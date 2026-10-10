package com.ttcl.games.servicio;

import com.ttcl.games.dominio.ConsejoDado;
import com.ttcl.games.dominio.Jugador;
import com.ttcl.games.dominio.Repositorios.ConsejoDadoRepo;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.duende.DuendeModelos.Insight;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Estadisticas;
import com.ttcl.games.stats.Modelos.ResumenJuego;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Memoria del Duende (P6): apunta los consejos que le da a cada uno, para ver después si han funcionado. Solo los de
 * mejorar ya y a vigilar, y no la misma recomendación dos veces en {@link #SIN_REPETIR}.
 */
@Service
public class MemoriaConsejos {

    static final Duration SIN_REPETIR = Duration.ofDays(7);
    private static final Set<String> NIVELES = Set.of("alto", "medio");
    /** Las del propio seguimiento ("ha funcionado", "sigue igual") no son consejos nuevos. */
    private static final String SEGUIMIENTO = "consejo_";

    private final JugadorRepo jugadores;
    private final ConsejoDadoRepo consejos;

    public MemoriaConsejos(JugadorRepo jugadores, ConsejoDadoRepo consejos) {
        this.jugadores = jugadores;
        this.consejos = consejos;
    }

    /**
     * Apunta los consejos nuevos de una tanda de recomendaciones, con el valor de su métrica en {@code resumen} (el de
     * todas las partidas). Devuelve cuántos ha apuntado.
     */
    @Transactional
    public int apuntar(String slug, Juego juego, ResumenJuego resumen, List<Insight> insights) {
        Jugador jugador = jugadores.findBySlug(slug).orElse(null);
        if (jugador == null) {
            return 0;
        }
        Instant ahora = Instant.now();
        int apuntados = 0;
        for (Insight i : insights) {
            if (!NIVELES.contains(i.nivel()) || i.id() == null || i.id().startsWith(SEGUIMIENTO)) {
                continue;
            }
            if (consejos.existsByJugadorAndJuegoAndInsightAndDadoEnAfter(jugador, juego, i.id(), ahora.minus(SIN_REPETIR))) {
                continue;
            }
            Double valor = i.metrica() == null ? null : Estadisticas.valor(resumen, i.metrica());
            consejos.save(new ConsejoDado(jugador, juego, i.id(), i.metrica(), valor, i.nivel(), ahora));
            apuntados++;
        }
        return apuntados;
    }
}
