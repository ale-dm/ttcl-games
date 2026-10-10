package com.ttcl.games.servicio;

import static com.ttcl.games.dominio.Valoracion.CONSEJO;
import static com.ttcl.games.dominio.Valoracion.RESPUESTA;

import com.ttcl.games.dominio.Jugador;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.dominio.Repositorios.ValoracionRepo;
import com.ttcl.games.dominio.Valoracion;
import com.ttcl.games.duende.DuendeServicio;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.servicio.Vistas.GrupoValoraciones;
import com.ttcl.games.servicio.Vistas.ResumenValoraciones;
import com.ttcl.games.servicio.Vistas.ValoracionVista;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valoraciones de la web (P7): un 👍 o un 👎 a cada recomendación del Duende y a cada respuesta del chat. Cada navegador
 * vota con un id al azar: un voto por cosa valorada, que se puede cambiar o quitar. Sin usuarios (llegarán con P5),
 * nada impide votar desde otro navegador: sirve para saber qué revisar, no para contar votos al detalle.
 */
@Service
public class Valoraciones {

    /** Id al azar del navegador que vota (la web lo crea y lo guarda en localStorage). */
    public static final String VOTANTE = "[A-Za-z0-9-]{8,40}";
    static final int MAX_TEXTO = 8000;
    /** Cuántas valoraciones negativas se devuelven para leer. */
    static final int NEGATIVAS = 20;
    private static final String REGLAS = "reglas";
    /** El que más 👎 tiene por encima de sus 👍 primero; a igualdad, el que más 👎 tiene. */
    private static final Comparator<GrupoValoraciones> PEOR_PRIMERO = Comparator
            .comparingInt((GrupoValoraciones g) -> g.positivos() - g.negativos())
            .thenComparing(GrupoValoraciones::negativos, Comparator.reverseOrder())
            .thenComparing(GrupoValoraciones::origen)
            .thenComparing(GrupoValoraciones::clave, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * Voto a una recomendación del panel de un jugador.
     *
     * @param voto 1 (me sirve), -1 (no me sirve) o 0 (quita el voto)
     * @param insight id de la recomendación (debil_adr, tilt_sesion...)
     * @param texto lo que se vio: título, texto y consejo
     */
    public record VotoConsejo(
            @NotNull @Pattern(regexp = VOTANTE) String votante,
            @NotNull @Min(-1) @Max(1) Integer voto,
            @NotBlank String jugador,
            @NotNull Juego juego,
            @NotBlank @Size(max = 60) String insight,
            @Pattern(regexp = "alto|medio|bien|info") String nivel,
            String lang,
            @NotBlank @Size(max = 20000) String texto) {}

    /**
     * Voto a una respuesta del chat.
     *
     * @param voto 1 (me sirve), -1 (no me sirve) o 0 (quita el voto)
     * @param foco de quién iba la conversación (se guarda el primero)
     * @param origen "reglas" o "gemini", como lo dijo el Duende
     * @param intencion de qué iba la pregunta según las reglas (la manda el Duende con la respuesta)
     */
    public record VotoRespuesta(
            @NotNull @Pattern(regexp = VOTANTE) String votante,
            @NotNull @Min(-1) @Max(1) Integer voto,
            String lang,
            @Size(max = 2) List<String> foco,
            Juego juego,
            @NotBlank @Size(max = 2000) String pregunta,
            @NotBlank @Size(max = 20000) String respuesta,
            @NotNull @Pattern(regexp = "gemini|reglas") String origen,
            @Size(max = 80) String modelo,
            @Pattern(regexp = "[a-z_]{1,30}") String intencion) {}

    private final ValoracionRepo valoraciones;
    private final JugadorRepo jugadores;

    public Valoraciones(ValoracionRepo valoraciones, JugadorRepo jugadores) {
        this.valoraciones = valoraciones;
        this.jugadores = jugadores;
    }

    @Transactional
    public void votarConsejo(VotoConsejo v) {
        Jugador jugador = jugadores.findBySlug(v.jugador()).orElseThrow(
                () -> new NoEncontradoException("No hay ningún jugador con el slug \"" + v.jugador() + "\"."));
        guardar(
                valoraciones.findFirstByVotanteAndTipoAndClaveAndJugadorAndJuego(
                        v.votante(), CONSEJO, v.insight(), jugador, v.juego()),
                v.voto(),
                () -> new Valoracion(
                        CONSEJO, v.insight(), v.voto(), REGLAS, null, null, v.nivel(), jugador, v.juego(),
                        DuendeServicio.idioma(v.lang()), null, recortar(v.texto()), v.votante(), Instant.now()));
    }

    @Transactional
    public void votarRespuesta(VotoRespuesta v) {
        String clave = hash(v.pregunta() + "\n" + v.respuesta());
        Jugador jugador = v.foco() == null
                ? null
                : v.foco().stream()
                        .filter(Objects::nonNull)
                        .map(jugadores::findBySlug)
                        .flatMap(Optional::stream)
                        .findFirst()
                        .orElse(null);
        guardar(
                valoraciones.findFirstByVotanteAndTipoAndClave(v.votante(), RESPUESTA, clave),
                v.voto(),
                () -> new Valoracion(
                        RESPUESTA, clave, v.voto(), v.origen(), v.modelo(), v.intencion(), null, jugador, v.juego(),
                        DuendeServicio.idioma(v.lang()), v.pregunta(), recortar(v.respuesta()), v.votante(),
                        Instant.now()));
    }

    /** El voto nuevo sustituye al que ya hubiera dado ese navegador; con 0 solo se quita. */
    private void guardar(Optional<Valoracion> anterior, int voto, Supplier<Valoracion> nueva) {
        anterior.ifPresent(valoraciones::delete);
        if (voto != 0) {
            valoraciones.save(nueva.get());
        }
    }

    @Transactional(readOnly = true)
    public ResumenValoraciones resumen() {
        List<Valoracion> todas = valoraciones.findAllRecientes();
        int positivos = (int) todas.stream().filter(v -> v.getVoto() > 0).count();
        List<ValoracionVista> negativas = todas.stream()
                .filter(v -> v.getVoto() < 0)
                .limit(NEGATIVAS)
                .map(Valoraciones::vista)
                .toList();
        return new ResumenValoraciones(
                positivos,
                todas.size() - positivos,
                grupos(todas, CONSEJO, Valoracion::getClave),
                grupos(todas, RESPUESTA, Valoracion::getIntencion),
                negativas);
    }

    /** Votos de un tipo agrupados por origen y {@code clave}, los peor valorados primero. */
    private static List<GrupoValoraciones> grupos(
            List<Valoracion> todas, String tipo, Function<Valoracion, String> clave) {
        Map<List<String>, int[]> votos = new LinkedHashMap<>();
        for (Valoracion v : todas) {
            if (v.getTipo().equals(tipo)) {
                // Arrays.asList admite null (una respuesta sin intención).
                int[] cuenta = votos.computeIfAbsent(Arrays.asList(v.getOrigen(), clave.apply(v)), k -> new int[2]);
                cuenta[v.getVoto() > 0 ? 0 : 1]++;
            }
        }
        return votos.entrySet().stream()
                .map(e -> new GrupoValoraciones(e.getKey().get(0), e.getKey().get(1), e.getValue()[0], e.getValue()[1]))
                .sorted(PEOR_PRIMERO)
                .toList();
    }

    private static ValoracionVista vista(Valoracion v) {
        return new ValoracionVista(
                v.getTipo(), v.getClave(), v.getOrigen(), v.getModelo(), v.getIntencion(), v.getNivel(),
                v.getJugador() == null ? null : v.getJugador().getNombre(), v.getJuego(), v.getLang(), v.getPregunta(),
                v.getTexto(), v.getVotadaEn());
    }

    /** 32 caracteres hexadecimales: la misma respuesta a la misma pregunta tiene siempre la misma clave. */
    static String hash(String texto) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lo que cabe en la columna: las respuestas largas se guardan cortadas. */
    private static String recortar(String texto) {
        return texto.length() <= MAX_TEXTO ? texto : texto.substring(0, MAX_TEXTO - 1) + "…";
    }
}
