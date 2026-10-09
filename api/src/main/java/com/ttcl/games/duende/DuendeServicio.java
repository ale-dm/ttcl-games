package com.ttcl.games.duende;

import com.ttcl.games.duende.DuendeModelos.Insight;
import com.ttcl.games.duende.DuendeModelos.ItemLote;
import com.ttcl.games.duende.DuendeModelos.Mensaje;
import com.ttcl.games.duende.DuendeModelos.PeticionChat;
import com.ttcl.games.duende.DuendeModelos.RespuestaChat;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.servicio.EquipoServicio;
import com.ttcl.games.servicio.Vistas.ConsejoBreve;
import com.ttcl.games.servicio.Vistas.ConsejosVista;
import com.ttcl.games.servicio.Vistas.JugadorVista;
import com.ttcl.games.servicio.Vistas.TarjetaJugador;
import com.ttcl.games.stats.Modelos.ResumenJuego;
import com.ttcl.games.stats.Periodo;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Une los datos de la API con el servicio Python del Duende. Si el Duende no responde, las páginas siguen
 * funcionando sin sus recomendaciones; solo el chat devuelve error.
 */
@Service
public class DuendeServicio {

    private static final Logger log = LoggerFactory.getLogger(DuendeServicio.class);
    private static final List<String> ORDEN_NIVEL = List.of("alto", "medio", "info", "bien");

    private final EquipoServicio equipo;
    private final DuendeCliente cliente;

    public DuendeServicio(EquipoServicio equipo, DuendeCliente cliente) {
        this.equipo = equipo;
        this.cliente = cliente;
    }

    /** "es" o "en". Cualquier otra cosa se trata como español. */
    public static String idioma(String lang) {
        return lang != null && lang.toLowerCase().startsWith("en") ? "en" : "es";
    }

    /** Tarjetas del equipo con lo primero que diría el Duende de cada uno. */
    public List<TarjetaJugador> tarjetas(Juego soloJuego, String lang) {
        List<JugadorVista> vistas = equipo.equipo();
        Map<String, Insight> primero = new HashMap<>();
        Map<String, Juego> juegoDelConsejo = new HashMap<>();
        try {
            for (ItemLote item : cliente.lote(equipo.peticionesEquipo(soloJuego, idioma(lang)))) {
                item.insights().stream()
                        .min(Comparator.comparingInt(i -> ORDEN_NIVEL.indexOf(i.nivel())))
                        .ifPresent(i -> {
                            Insight actual = primero.get(item.slug());
                            if (actual == null || ORDEN_NIVEL.indexOf(i.nivel()) < ORDEN_NIVEL.indexOf(actual.nivel())) {
                                primero.put(item.slug(), i);
                                juegoDelConsejo.put(item.slug(), item.juego());
                            }
                        });
            }
        } catch (DuendeNoDisponibleException e) {
            log.warn("Tarjetas sin consejo del Duende: {}", e.getMessage());
        }
        return vistas.stream()
                .map(v -> {
                    List<ResumenJuego> resumenes = v.resumenes().stream()
                            .filter(r -> soloJuego == null || r.juego() == soloJuego)
                            .toList();
                    Insight i = primero.get(v.slug());
                    ConsejoBreve consejo = i == null ? null : new ConsejoBreve(juegoDelConsejo.get(v.slug()), i.nivel(), i.titulo());
                    return new TarjetaJugador(v.slug(), v.nombre(), v.demo(), v.cuentas(), resumenes, consejo);
                })
                .filter(t -> soloJuego == null || !t.resumenes().isEmpty())
                .toList();
    }

    /**
     * Recomendaciones de un jugador en un juego con las partidas del periodo. Si el Duende no responde,
     * {@code disponible} es false.
     */
    public ConsejosVista consejos(String slug, Juego juego, String lang, Periodo periodo) {
        var peticion = equipo.peticionInsights(slug, juego, idioma(lang), periodo);
        try {
            return new ConsejosVista(true, cliente.insights(peticion));
        } catch (DuendeNoDisponibleException e) {
            log.warn("Consejos de {} sin Duende: {}", slug, e.getMessage());
            return new ConsejosVista(false, List.of());
        }
    }

    /**
     * Pregunta al Duende con el contexto del equipo; {@code foco} son los jugadores de los que va la charla y
     * {@code periodo}, el que se ve en la página (el Duende lo usa si la pregunta no dice otro).
     */
    public RespuestaChat chat(String lang, List<Mensaje> mensajes, List<String> foco, Juego juego, Periodo periodo) {
        return cliente.chat(new PeticionChat(
                idioma(lang), mensajes, equipo.slugsValidos(foco), equipo.contextoEquipo(), juego, periodo));
    }
}
