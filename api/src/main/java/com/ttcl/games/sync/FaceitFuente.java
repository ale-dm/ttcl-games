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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * CS2 con la FACEIT Data API (https://developers.faceit.com/docs/tools/data-api). Requiere API key. Solo sirve para
 * jugadores con cuenta de FACEIT: las estadísticas de partidas de CS2 no se exponen por la API de Steam.
 *
 * <p>Los nombres de campo ("Kills", "Headshots %", "Entry Count"...) son los de la respuesta de FACEIT. Están escritos
 * a partir de la documentación y hay que validarlos con una partida real (ver README).
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
            Mapeo m = mapearEstadisticas(stats);
            Double empezada = numero(mapa(item).get("started_at"));
            Double terminada = numero(mapa(item).get("finished_at"));
            partidas.add(new PartidaExterna(
                    matchId,
                    Juego.CS2,
                    empezada == null ? Instant.now() : Instant.ofEpochSecond(empezada.longValue()),
                    empezada != null && terminada != null ? (int) (terminada - empezada) : null,
                    m.mapa(),
                    m.participaciones()));
        }
        return partidas;
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
