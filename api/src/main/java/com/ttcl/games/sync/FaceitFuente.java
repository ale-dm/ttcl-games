package com.ttcl.games.sync;

import static com.ttcl.games.sync.Json.entero;
import static com.ttcl.games.sync.Json.lista;
import static com.ttcl.games.sync.Json.mapa;
import static com.ttcl.games.sync.Json.numero;
import static com.ttcl.games.sync.Json.sumaEnteros;
import static com.ttcl.games.sync.Json.texto;

import com.ttcl.games.juego.Juego;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * CS2 con la FACEIT Data API (https://developers.faceit.com/docs/tools/data-api). Requiere API key. Solo sirve para
 * jugadores con cuenta de FACEIT: las estadísticas de partidas de CS2 no se exponen por la API de Steam.
 *
 * <p>Los nombres de campo ("Kills", "Headshots %", "Entry Count"...) son los de la respuesta de FACEIT. Están escritos
 * a partir de la documentación y hay que validarlos con una partida real (ver README). Los del nivel (P8:
 * {@code games.cs2.skill_level} y {@code faceit_elo} del jugador, {@code game_skill_level} de cada uno en el roster
 * de la partida) están comprobados con el swagger de la Data API v4, aún no con una respuesta real.
 */
public class FaceitFuente implements FuenteJuego {

    private final RestClient http;

    public FaceitFuente(String apiKey, String base) {
        this.http = RestClient.builder()
                .baseUrl(base.replaceAll("/$", ""))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(Json.factory(Duration.ofSeconds(20)))
                .build();
    }

    @Override
    public Juego juego() {
        return Juego.CS2;
    }

    @Override
    public Optional<CuentaResuelta> resolverCuenta(String nick) {
        try {
            Object j = http.get().uri("/players?nickname={nick}&game=cs2", nick).retrieve().body(Object.class);
            String id = texto(mapa(j).get("player_id"));
            return id == null ? Optional.empty() : Optional.of(new CuentaResuelta(id, texto(mapa(j).get("nickname"))));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<NivelCuenta> nivel(String externalId) {
        return mapearNivel(http.get().uri("/players/{id}", externalId).retrieve().body(Object.class));
    }

    /**
     * Nivel y ELO de CS2 del perfil de un jugador ({@code games.cs2}), y su steamid ({@code games.cs2.game_player_id}
     * o, si no, {@code steam_id_64}; P12). Pública para los tests.
     */
    public static Optional<NivelCuenta> mapearNivel(Object jugador) {
        Map<String, Object> cs2 = mapa(mapa(jugador, "games"), "cs2");
        Integer nivel = entero(cs2.get("skill_level"));
        if (nivel == null || nivel < 1) {
            return Optional.empty();
        }
        String steamId = texto(cs2.get("game_player_id"));
        if (steamId == null || !steamId.matches("\\d{17}")) {
            steamId = texto(mapa(jugador).get("steam_id_64"));
        }
        return Optional.of(new NivelCuenta(nivel, entero(cs2.get("faceit_elo")),
                steamId != null && steamId.matches("\\d{17}") ? steamId : null));
    }

    @Override
    public List<PartidaExterna> partidasRecientes(String externalId, int limite, Set<String> conocidas) {
        Object historial = http.get()
                .uri("/players/{id}/history?game=cs2&offset=0&limit={limite}", externalId, limite)
                .retrieve()
                .body(Object.class);
        List<PartidaExterna> partidas = new ArrayList<>();
        // Secuencial a propósito: FACEIT limita las peticiones por minuto y aquí no hay prisa.
        for (Object item : lista(historial, "items")) {
            String matchId = texto(mapa(item).get("match_id"));
            if (matchId == null || conocidas.contains(matchId)) {
                continue;
            }
            Object stats = http.get().uri("/matches/{id}/stats", matchId).retrieve().body(Object.class);
            Object detalles = detallesDe(matchId);
            Mapeo m = conNiveles(mapearEstadisticas(stats), nivelesDelRoster(detalles));
            Double empezada = numero(mapa(item).get("started_at"));
            Double terminada = numero(mapa(item).get("finished_at"));
            partidas.add(new PartidaExterna(
                    matchId,
                    Juego.CS2,
                    empezada == null ? Instant.now() : Instant.ofEpochSecond(empezada.longValue()),
                    empezada != null && terminada != null ? (int) (terminada - empezada) : null,
                    m.mapa(),
                    m.participaciones(),
                    demoDe(detalles)));
        }
        return partidas;
    }

    /**
     * Detalles de la partida: el nivel de cada jugador (P8) y la URL de la demo (P12). Si no se pueden pedir, nada: la
     * partida se guarda igual, solo que sin muestras de su nivel y sin URL de la demo.
     */
    private Object detallesDe(String matchId) {
        try {
            return http.get().uri("/matches/{id}", matchId).retrieve().body(Object.class);
        } catch (RestClientException e) {
            return null;
        }
    }

    /**
     * La URL de la demo en los detalles de una partida ({@code demo_url}, una lista; la primera), o null. Es la del
     * almacén de FACEIT: para descargarla hace falta pedir una firmada a su API de descargas. Pública para los tests.
     */
    public static String demoDe(Object detalles) {
        Object valor = mapa(detalles).get("demo_url");
        String url = valor instanceof List<?> l ? l.stream().map(Json::texto).filter(t -> t != null).findFirst().orElse(null)
                : texto(valor);
        return url != null && url.startsWith("http") ? url : null;
    }

    /** Nivel de cada jugador (por player_id) en los detalles de una partida. Pública para los tests. */
    public static Map<String, Integer> nivelesDelRoster(Object detalles) {
        Map<String, Integer> niveles = new HashMap<>();
        for (Object faccion : mapa(detalles, "teams").values()) {
            for (Object jugador : lista(faccion, "roster")) {
                String id = texto(mapa(jugador).get("player_id"));
                Integer nivel = entero(mapa(jugador).get("game_skill_level"));
                if (id != null && nivel != null && nivel >= 1) {
                    niveles.put(id, nivel);
                }
            }
        }
        return niveles;
    }

    /** Las mismas participaciones con el nivel de cada uno en esa partida. Pública para los tests. */
    public static Mapeo conNiveles(Mapeo m, Map<String, Integer> niveles) {
        return new Mapeo(m.mapa(), m.participaciones().stream()
                .map(p -> new ParticipacionExterna(p.externalPlayerId(), niveles.get(p.externalPlayerId()), p.gano(),
                        p.kills(), p.muertes(), p.asistencias(), p.datos()))
                .toList());
    }

    public record Mapeo(String mapa, List<ParticipacionExterna> participaciones) {}

    /** Traduce las estadísticas de una partida de FACEIT a participaciones. Pública para los tests. */
    public static Mapeo mapearEstadisticas(Object estadisticas) {
        Object ronda = lista(estadisticas, "rounds").stream().findFirst().orElse(null);
        Map<String, Object> rondaStats = mapa(ronda, "round_stats");
        String mapa = texto(rondaStats.get("Map"));
        Integer rondas = entero(rondaStats.get("Rounds"));
        String marcador = texto(rondaStats.get("Score"));

        List<ParticipacionExterna> participaciones = new ArrayList<>();
        for (Object equipo : lista(ronda, "teams")) {
            for (Object jugador : lista(equipo, "players")) {
                Map<String, Object> s = mapa(jugador, "player_stats");
                Map<String, Object> datos = new LinkedHashMap<>();
                datos.put("mapa", mapa);
                datos.put("marcador", marcador);
                datos.put("rondas", rondas);
                datos.put("adr", numero(s.get("ADR")));
                datos.put("hs_pct", numero(s.get("Headshots %")));
                datos.put("kr", numero(s.get("K/R Ratio")));
                datos.put("mvps", entero(s.get("MVPs")));
                datos.put("multikills", sumaEnteros(s, "Triple Kills", "Quadro Kills", "Penta Kills"));
                datos.put("entry_intentos", entero(s.get("Entry Count")));
                datos.put("entry_ganados", entero(s.get("Entry Wins")));
                datos.put("clutch_intentos", sumaEnteros(s, "1v1Count", "1v2Count"));
                datos.put("clutch_ganados", sumaEnteros(s, "1v1Wins", "1v2Wins"));
                datos.put("dano_utilidad", entero(s.get("Utility Damage")));
                datos.values().removeIf(v -> v == null);

                String resultado = texto(s.get("Result"));
                participaciones.add(new ParticipacionExterna(
                        texto(mapa(jugador).get("player_id")),
                        null,
                        resultado == null ? null : resultado.equals("1"),
                        entero(s.get("Kills")),
                        entero(s.get("Deaths")),
                        entero(s.get("Assists")),
                        datos));
            }
        }
        return new Mapeo(mapa, participaciones);
    }
}
