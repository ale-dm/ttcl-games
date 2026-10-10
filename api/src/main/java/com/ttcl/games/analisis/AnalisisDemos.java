package com.ttcl.games.analisis;

import com.ttcl.games.analisis.AnalisisCliente.AnalisisNoDisponibleException;
import com.ttcl.games.analisis.AnalisisCliente.DemoNoValidaException;
import com.ttcl.games.analisis.AnalisisModelos.JugadorAnalizado;
import com.ttcl.games.analisis.AnalisisModelos.JugadorBuscado;
import com.ttcl.games.analisis.AnalisisModelos.MuerteAnonima;
import com.ttcl.games.analisis.AnalisisModelos.PeticionAnalisis;
import com.ttcl.games.analisis.AnalisisModelos.RespuestaAnalisis;
import com.ttcl.games.analisis.AnalisisModelos.RondaJugador;
import com.ttcl.games.config.TtclProperties;
import com.ttcl.games.dominio.Cuenta;
import com.ttcl.games.dominio.Demo;
import com.ttcl.games.dominio.Jugador;
import com.ttcl.games.dominio.MuerteMapa;
import com.ttcl.games.dominio.Participacion;
import com.ttcl.games.dominio.Partida;
import com.ttcl.games.dominio.Repositorios.CuentaRepo;
import com.ttcl.games.dominio.Repositorios.DemoRepo;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.dominio.Repositorios.MuerteMapaRepo;
import com.ttcl.games.dominio.Repositorios.ParticipacionRepo;
import com.ttcl.games.dominio.Repositorios.RondaRepo;
import com.ttcl.games.dominio.Ronda;
import com.ttcl.games.juego.Juego;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Analiza las demos pendientes (P12): consigue la demo (un fichero de la carpeta de demos o, con el token de la API de
 * descargas de FACEIT, una URL firmada), se la pasa al trabajador de análisis con los del equipo que estaban y guarda
 * lo que hizo cada uno en cada ronda, y dónde murió la gente (sin decir quién). Se miran por turnos, unas pocas cada
 * vez: la que hace más que no se revisa, primero.
 */
@Service
public class AnalisisDemos {

    private static final Logger log = LoggerFactory.getLogger(AnalisisDemos.class);
    /** Intentos fallidos por culpa del trabajador o de la red antes de dar una demo por perdida. */
    public static final int MAX_INTENTOS = 3;
    /** Extensiones de las demos de la carpeta: sin comprimir o comprimidas como las da FACEIT. */
    private static final List<String> EXTENSIONES = List.of(".dem", ".gz", ".zst", ".bz2");
    static final String SIN_ACCESO = "Sin acceso a la demo: hace falta el token de la API de descargas de FACEIT "
            + "(FACEIT_DOWNLOADS_TOKEN) o dejarla en la carpeta de demos con el id de la partida en el nombre.";

    private final DemoRepo demos;
    private final RondaRepo rondas;
    private final MuerteMapaRepo muertes;
    private final ParticipacionRepo participaciones;
    private final CuentaRepo cuentas;
    private final JugadorRepo jugadores;
    private final AnalisisCliente cliente;
    private final DescargasFaceit descargas;
    private final TtclProperties props;
    private final TransactionTemplate tx;

    public AnalisisDemos(
            DemoRepo demos,
            RondaRepo rondas,
            MuerteMapaRepo muertes,
            ParticipacionRepo participaciones,
            CuentaRepo cuentas,
            JugadorRepo jugadores,
            AnalisisCliente cliente,
            DescargasFaceit descargas,
            TtclProperties props,
            TransactionTemplate tx) {
        this.demos = demos;
        this.rondas = rondas;
        this.muertes = muertes;
        this.participaciones = participaciones;
        this.cuentas = cuentas;
        this.jugadores = jugadores;
        this.cliente = cliente;
        this.descargas = descargas;
        this.props = props;
        this.tx = tx;
    }

    /** Cuántas se han analizado, cuántas han fallado del todo y cuántas siguen pendientes de esta vez. */
    public record Resultado(int analizadas, int fallidas, int pendientes) {}

    public boolean activo() {
        return cliente.configurado();
    }

    /** Mira hasta {@code ttcl.analisis.lote} demos pendientes. Sin trabajador configurado, ninguna. */
    public Resultado analizarPendientes() {
        if (!activo()) {
            return new Resultado(0, 0, 0);
        }
        int lote = Math.max(1, props.analisis().lote());
        int analizadas = 0;
        int fallidas = 0;
        int pendientes = 0;
        for (Demo d : demos.findPorRevisar(Demo.PENDIENTE, PageRequest.of(0, lote))) {
            switch (analizar(d)) {
                case Demo.ANALIZADA -> analizadas++;
                case Demo.FALLIDA -> fallidas++;
                default -> pendientes++;
            }
        }
        return new Resultado(analizadas, fallidas, pendientes);
    }

    /** La demo, de la carpeta o descargada: una de las dos cosas. */
    record Origen(String url, String archivo) {}

    /** Analiza una demo pendiente y devuelve cómo queda: analizada, fallida o aún pendiente. */
    String analizar(Demo d) {
        Partida partida = d.getPartida();
        Optional<Origen> origen = origen(d);
        if (origen.isEmpty()) {
            d.aplazada(SIN_ACCESO, false);
            demos.save(d);
            return d.getEstado();
        }
        try {
            RespuestaAnalisis r = cliente.analizar(
                    new PeticionAnalisis(origen.get().url(), origen.get().archivo(), buscados(partida)));
            String estado = tx.execute(t -> guardar(d, partida, r));
            log.info("Demo de la partida {}: {}", partida.getExternalId(), estado);
            return estado;
        } catch (DemoNoValidaException e) {
            d.fallida(e.getMessage());
            log.info("Demo de la partida {} no válida: {}", partida.getExternalId(), e.getMessage());
        } catch (AnalisisNoDisponibleException e) {
            d.aplazada(e.getMessage(), true);
            if (d.getIntentos() >= MAX_INTENTOS) {
                d.fallida(e.getMessage());
            }
            log.warn("Demo de la partida {} sin analizar: {}", partida.getExternalId(), e.getMessage());
        }
        demos.save(d);
        return d.getEstado();
    }

    /** Primero la carpeta (no gasta nada); si no está, una URL firmada de FACEIT si hay token y se sabe su URL. */
    Optional<Origen> origen(Demo d) {
        Optional<String> archivo = enCarpeta(d.getPartida().getExternalId());
        if (archivo.isPresent()) {
            return Optional.of(new Origen(null, archivo.get()));
        }
        return descargas.urlFirmada(d.getUrl()).map(url -> new Origen(url, null));
    }

    /** Un fichero de la carpeta de demos cuyo nombre empiece por el id de la partida ("1-cb03...-1-1.dem.zst"). */
    Optional<String> enCarpeta(String externalId) {
        Path carpeta = props.analisis().rutaCarpeta();
        if (externalId == null || !Files.isDirectory(carpeta)) {
            return Optional.empty();
        }
        String prefijo = externalId.toLowerCase(Locale.ROOT);
        try (Stream<Path> ficheros = Files.list(carpeta)) {
            return ficheros
                    .filter(Files::isRegularFile)
                    .map(f -> f.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefijo)
                            && EXTENSIONES.stream().anyMatch(e -> n.toLowerCase(Locale.ROOT).endsWith(e)))
                    .sorted()
                    .findFirst();
        } catch (IOException e) {
            log.warn("No se puede leer la carpeta de demos {}: {}", carpeta, e.getMessage());
            return Optional.empty();
        }
    }

    /** Los del equipo que jugaron la partida, con su steamid y su nick de FACEIT para encontrarlos en la demo. */
    List<JugadorBuscado> buscados(Partida partida) {
        return participaciones.findByPartida(partida).stream()
                .map(Participacion::getJugador)
                .map(j -> {
                    Optional<Cuenta> c = cuentas.findByJugadorAndJuego(j, Juego.CS2);
                    return new JugadorBuscado(j.getSlug(), c.map(Cuenta::getSteamId).orElse(null),
                            c.map(Cuenta::getNombreExterno).orElse(null));
                })
                .toList();
    }

    /** Guarda las rondas de cada uno (si se analiza otra vez, se cambian) y dónde murió la gente. */
    private String guardar(Demo d, Partida partida, RespuestaAnalisis r) {
        List<JugadorAnalizado> encontrados = r.jugadores() == null ? List.of() : r.jugadores();
        if (encontrados.isEmpty()) {
            d.fallida("No está nadie del equipo en la demo.");
            demos.save(d);
            return d.getEstado();
        }
        rondas.borrarDePartida(partida);
        for (JugadorAnalizado ja : encontrados) {
            Optional<Jugador> jugador = jugadores.findBySlug(ja.id());
            if (jugador.isEmpty()) {
                continue;
            }
            for (RondaJugador f : ja.rondas()) {
                rondas.save(new Ronda(partida, jugador.get(), f.ronda(), f.lado(), f.gano(), f.kills(),
                        f.asistencias(), f.asistenciasFlash(), f.dano(), f.danoUtilidad(), f.murio(), f.muerteX(),
                        f.muerteY(), recortar(f.muerteZona()), f.tradeado(), f.trades(), f.apertura(),
                        f.equipamiento(), f.compra(), f.kast()));
            }
            // Si se le encontró por el nick, ya se sabe su steamid para la próxima.
            cuentas.findByJugadorAndJuego(jugador.get(), Juego.CS2)
                    .filter(c -> c.getSteamId() == null && ja.steamId() != null)
                    .ifPresent(c -> {
                        c.setSteamId(ja.steamId());
                        cuentas.save(c);
                    });
        }
        String mapa = r.mapa() != null ? r.mapa() : partida.getModo();
        if (mapa != null && r.muertes() != null) {
            for (MuerteAnonima m : r.muertes()) {
                muertes.save(new MuerteMapa(mapa, m.x(), m.y(), recortar(m.zona())));
            }
        }
        d.analizada();
        demos.save(d);
        return d.getEstado();
    }

    private static String recortar(String zona) {
        return zona == null || zona.length() <= 40 ? zona : zona.substring(0, 40);
    }
}
