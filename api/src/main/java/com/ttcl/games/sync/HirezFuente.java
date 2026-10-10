package com.ttcl.games.sync;

import static com.ttcl.games.sync.Json.entero;
import static com.ttcl.games.sync.Json.lista;
import static com.ttcl.games.sync.Json.mapa;
import static com.ttcl.games.sync.Json.numero;
import static com.ttcl.games.sync.Json.texto;

import com.ttcl.games.juego.Juego;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.client.RestClient;

/**
 * SMITE 2 con la API de Hi-Rez. Requiere dev ID y auth key, que se piden a Hi-Rez.
 *
 * <p>⚠️ SIN VERIFICAR CONTRA LA API REAL. La firma (MD5 de devId + método + authKey + timestamp), la sesión de 15
 * minutos y el formato de fecha son los de la API pública clásica de Hi-Rez. Hay que comprobar con credenciales
 * reales la URL base de SMITE 2 (SMITE2_API_BASE), los nombres de método y los campos de getmatchhistory.
 */
public class HirezFuente implements FuenteJuego {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FECHA_HIREZ = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US);

    private final RestClient http;
    private final String devId;
    private final String authKey;
    private String sesion;
    private Instant sesionCaduca = Instant.EPOCH;

    public HirezFuente(String base, String devId, String authKey) {
        this.http = RestClient.builder()
                .baseUrl(base.replaceAll("/$", ""))
                .requestFactory(Json.factory(Duration.ofSeconds(20)))
                .build();
        this.devId = devId;
        this.authKey = authKey;
    }

    @Override
    public Juego juego() {
        return Juego.SMITE2;
    }

    /** Fecha en UTC con el formato que espera Hi-Rez: yyyyMMddHHmmss. */
    public static String timestamp(Instant instante) {
        return TIMESTAMP.format(instante);
    }

    /** Firma de una llamada: MD5(devId + método + authKey + timestamp), en hexadecimal minúscula. */
    public static String firma(String devId, String metodo, String authKey, String timestamp) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5")
                    .digest((devId + metodo + authKey + timestamp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 no disponible", e);
        }
    }

    /** Sesión de la API (caduca a los 15 min; se reutiliza con margen). */
    private synchronized String sesion() {
        if (sesion != null && Instant.now().isBefore(sesionCaduca)) {
            return sesion;
        }
        String ts = timestamp(Instant.now());
        Object r = http.get()
                .uri("/createsessionjson/{dev}/{firma}/{ts}", devId, firma(devId, "createsession", authKey, ts), ts)
                .retrieve()
                .body(Object.class);
        sesion = texto(mapa(r).get("session_id"));
        if (sesion == null) {
            throw new IllegalStateException("Hi-Rez no devolvió sesión: " + texto(mapa(r).get("ret_msg")));
        }
        sesionCaduca = Instant.now().plus(Duration.ofMinutes(14));
        return sesion;
    }

    private Object llamar(String metodo, String parametro) {
        String ts = timestamp(Instant.now());
        return http.get()
                .uri("/" + metodo + "json/{dev}/{firma}/{sesion}/{ts}/{p}",
                        devId, firma(devId, metodo, authKey, ts), sesion(), ts, parametro)
                .retrieve()
                .body(Object.class);
    }

    @Override
    public Optional<CuentaResuelta> resolverCuenta(String nick) {
        return lista(llamar("searchplayers", nick)).stream()
                .map(Json::mapa)
                .filter(j -> nick.equalsIgnoreCase(texto(j.get("Name"))))
                .findFirst()
                .map(j -> new CuentaResuelta(texto(j.get("player_id")), texto(j.get("Name"))));
    }

    @Override
    public List<PartidaExterna> partidasRecientes(String externalId, int limite, Set<String> conocidas) {
        return lista(llamar("getmatchhistory", externalId)).stream()
                .map(Json::mapa)
                .filter(f -> texto(f.get("Match")) != null && !conocidas.contains(texto(f.get("Match"))))
                .limit(limite)
                .map(f -> mapearFila(f, externalId))
                .toList();
    }

    /** Traduce una fila del historial a una partida con la participación de ese jugador. Pública para los tests. */
    public static PartidaExterna mapearFila(Map<String, Object> fila, String playerId) {
        Double minutos = numero(fila.get("Minutes"));
        Double dano = numero(fila.get("Damage"));
        Double oro = numero(fila.get("Gold"));
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("dios", texto(fila.get("God")));
        datos.put("dano", dano == null ? null : dano.intValue());
        datos.put("mitigado", entero(fila.get("Damage_Mitigated")));
        datos.put("curacion", entero(fila.get("Healing")));
        datos.put("oro", oro == null ? null : oro.intValue());
        datos.put("minutos", minutos);
        datos.values().removeIf(v -> v == null);

        String estado = texto(fila.get("Win_Status"));
        return new PartidaExterna(
                texto(fila.get("Match")),
                Juego.SMITE2,
                fecha(texto(fila.get("Entry_Datetime"))),
                minutos == null ? null : (int) Math.round(minutos * 60),
                texto(fila.get("Queue")),
                List.of(new ParticipacionExterna(
                        playerId,
                        null,
                        estado == null ? null : estado.equalsIgnoreCase("Winner"),
                        entero(fila.get("Kills")),
                        entero(fila.get("Deaths")),
                        entero(fila.get("Assists")),
                        datos)));
    }

    /** Hi-Rez da fechas como "10/8/2026 7:05:12 PM" (UTC). Se acepta también ISO por si SMITE 2 lo cambia. */
    static Instant fecha(String texto) {
        if (texto == null) {
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(texto, FECHA_HIREZ).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(texto);
            } catch (DateTimeParseException e2) {
                return Instant.now();
            }
        }
    }
}
