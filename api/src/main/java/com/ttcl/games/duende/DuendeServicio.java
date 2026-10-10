package com.ttcl.games.duende;

import com.ttcl.games.duende.DuendeModelos.Insight;
import com.ttcl.games.duende.DuendeModelos.ItemInforme;
import com.ttcl.games.duende.DuendeModelos.ItemLote;
import com.ttcl.games.duende.DuendeModelos.Mensaje;
import com.ttcl.games.duende.DuendeModelos.PeticionChat;
import com.ttcl.games.duende.DuendeModelos.RespuestaChat;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.servicio.EquipoServicio;
import com.ttcl.games.servicio.EquipoServicio.LoteNovedades;
import com.ttcl.games.servicio.EquipoServicio.NovedadConHechos;
import com.ttcl.games.servicio.MemoriaConsejos;
import com.ttcl.games.servicio.Vistas.ConsejoBreve;
import com.ttcl.games.servicio.Vistas.ConsejosVista;
import com.ttcl.games.servicio.Vistas.JugadorVista;
import com.ttcl.games.servicio.Vistas.Novedad;
import com.ttcl.games.servicio.Vistas.Novedades;
import com.ttcl.games.servicio.Vistas.PaginaPartidas;
import com.ttcl.games.servicio.Vistas.PartidaVista;
import com.ttcl.games.servicio.Vistas.ResumenSemanal;
import com.ttcl.games.servicio.Vistas.TarjetaJugador;
import com.ttcl.games.stats.Modelos.Hecho;
import com.ttcl.games.stats.Modelos.ResumenJuego;
import com.ttcl.games.stats.Modelos.SemanaJuego;
import com.ttcl.games.stats.Periodo;
import java.time.Duration;
import java.time.Instant;
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
    private final MemoriaConsejos memoria;

    public DuendeServicio(EquipoServicio equipo, DuendeCliente cliente, MemoriaConsejos memoria) {
        this.equipo = equipo;
        this.cliente = cliente;
        this.memoria = memoria;
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
     * {@code disponible} es false. Con todas las partidas, los consejos nuevos se apuntan en la memoria del Duende
     * (con un periodo no: el valor de la métrica sería el de esos días y el seguimiento no cuadraría).
     */
    public ConsejosVista consejos(String slug, Juego juego, String lang, Periodo periodo) {
        var peticion = equipo.peticionInsights(slug, juego, idioma(lang), periodo);
        List<Insight> insights;
        try {
            insights = cliente.insights(peticion);
        } catch (DuendeNoDisponibleException e) {
            log.warn("Consejos de {} sin Duende: {}", slug, e.getMessage());
            return new ConsejosVista(false, List.of());
        }
        if (periodo == Periodo.TODO) {
            try {
                memoria.apuntar(slug, juego, peticion.resumen(), insights);
            } catch (RuntimeException e) {
                // Si no se puede apuntar, los consejos se enseñan igual.
                log.warn("No se pudieron apuntar los consejos de {}: {}", slug, e.getMessage());
            }
        }
        return new ConsejosVista(true, insights);
    }

    /** Historial de partidas con lo que dice el Duende de cada una (P10). Si el Duende no responde, sin comentarios. */
    public PaginaPartidas partidas(String slug, Juego juego, int limite, int offset, Periodo periodo, String lang) {
        PaginaPartidas pagina = equipo.partidas(slug, juego, limite, offset, periodo);
        Map<Long, List<Hecho>> hechos = equipo.hechos(slug, pagina.items().stream().map(PartidaVista::partidaId).toList());
        Map<String, String> textos = comentarios(lang, pagina.items().stream()
                .map(p -> new ItemInforme(
                        String.valueOf(p.partidaId()), p.juego(), hechos.getOrDefault(p.partidaId(), List.of())))
                .toList());
        return new PaginaPartidas(
                pagina.items().stream().map(p -> p.conComentario(textos.get(String.valueOf(p.partidaId())))).toList(),
                pagina.total());
    }

    /**
     * Lo guardado desde {@code desde} (por defecto, el último día), con lo que dice el Duende de cada partida (P11).
     * Para la siguiente vez, pedir desde el {@code hasta} de la respuesta.
     */
    public Novedades novedades(Instant desde, String lang) {
        Instant ahora = Instant.now();
        LoteNovedades lote = equipo.novedades(desde == null ? ahora.minus(Duration.ofDays(1)) : desde, ahora);
        Map<String, String> textos = comentarios(lang, lote.items().stream()
                .map(n -> new ItemInforme(id(n.novedad()), n.novedad().partida().juego(), n.hechos()))
                .toList());
        return new Novedades(lote.desde(), lote.hasta(), lote.items().stream()
                .map(NovedadConHechos::novedad)
                .map(n -> new Novedad(n.jugador(), n.partida().conComentario(textos.get(id(n)))))
                .toList());
    }

    /** Los últimos 7 días de cada juego, con el mejor y el peor, y lo que dice el Duende (null si no responde). */
    public ResumenSemanal semana(String lang) {
        Instant ahora = Instant.now();
        List<SemanaJuego> juegos = equipo.semana(ahora);
        String texto = null;
        if (!juegos.isEmpty()) {
            try {
                texto = cliente.semana(idioma(lang), juegos);
            } catch (DuendeNoDisponibleException e) {
                log.warn("Resumen semanal sin Duende: {}", e.getMessage());
            }
        }
        return new ResumenSemanal(ahora.minus(Duration.ofDays(7)), ahora, juegos, texto);
    }

    /** Una partida con dos del equipo son dos informes: el id lleva la partida y el jugador. */
    private static String id(Novedad n) {
        return n.partida().partidaId() + "-" + n.jugador().slug();
    }

    /** Lo que dice el Duende de cada informe con algo que contar, por id. Si no responde, nada. */
    private Map<String, String> comentarios(String lang, List<ItemInforme> items) {
        List<ItemInforme> conAlgo = items.stream().filter(i -> !i.hechos().isEmpty()).toList();
        if (conAlgo.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, String> textos = new HashMap<>();
            cliente.informes(idioma(lang), conAlgo).stream()
                    .filter(t -> t.id() != null && t.texto() != null)
                    .forEach(t -> textos.put(t.id(), t.texto()));
            return textos;
        } catch (DuendeNoDisponibleException e) {
            log.warn("Partidas sin comentario del Duende: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Pregunta al Duende con el contexto del equipo; {@code foco} son los jugadores de los que va la charla y
     * {@code periodo}, el que se ve en la página (el Duende lo usa si la pregunta no dice otro).
     */
    public RespuestaChat chat(String lang, List<Mensaje> mensajes, List<String> foco, Juego juego, Periodo periodo) {
        return cliente.chat(new PeticionChat(
                idioma(lang), mensajes, equipo.slugsValidos(foco), equipo.contextoEquipo(), juego, periodo,
                equipo.hoy()));
    }
}
