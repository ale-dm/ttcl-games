package com.ttcl.games.dominio;

import com.ttcl.games.juego.Juego;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repositorios de Spring Data, juntos porque son de una línea cada uno. */
public final class Repositorios {

    private Repositorios() {}

    public interface JugadorRepo extends JpaRepository<Jugador, Long> {
        Optional<Jugador> findBySlug(String slug);

        List<Jugador> findAllByOrderByNombreAsc();
    }

    public interface CuentaRepo extends JpaRepository<Cuenta, Long> {
        @Query("select c from Cuenta c join fetch c.jugador order by c.juego")
        List<Cuenta> findAllConJugador();

        Optional<Cuenta> findByJugadorAndJuego(Jugador jugador, Juego juego);

        @Query("select max(c.ultimaSync) from Cuenta c")
        Optional<Instant> ultimaSync();
    }

    public interface PartidaRepo extends JpaRepository<Partida, Long> {
        Optional<Partida> findByJuegoAndExternalId(Juego juego, String externalId);

        @Query("select p.externalId from Partida p where p.juego = ?1")
        List<String> externalIdsDe(Juego juego);
    }

    public interface ParticipacionRepo extends JpaRepository<Participacion, Long> {
        /** Todas las participaciones con su partida y su jugador, para calcular estadísticas en memoria. */
        @Query("select p from Participacion p join fetch p.partida join fetch p.jugador")
        List<Participacion> findAllCompletas();

        Optional<Participacion> findByPartidaAndJugador(Partida partida, Jugador jugador);
    }

    public interface SyncRepo extends JpaRepository<Sync, Long> {}

    public interface ConsejoDadoRepo extends JpaRepository<ConsejoDado, Long> {
        /** Los dados desde una fecha, con su jugador, para calcular el seguimiento en memoria. */
        @Query("select c from ConsejoDado c join fetch c.jugador where c.dadoEn >= ?1")
        List<ConsejoDado> findAllDesde(Instant desde);

        /** Si ya se le dio esa recomendación hace poco (para no apuntarla dos veces). */
        boolean existsByJugadorAndJuegoAndInsightAndDadoEnAfter(
                Jugador jugador, Juego juego, String insight, Instant desde);
    }
}
