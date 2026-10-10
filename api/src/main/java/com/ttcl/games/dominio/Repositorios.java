package com.ttcl.games.dominio;

import com.ttcl.games.juego.Juego;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

        /** Los del equipo que jugaron una partida (P12: a quién buscar en su demo). */
        @Query("select p from Participacion p join fetch p.jugador where p.partida = ?1 order by p.id")
        List<Participacion> findByPartida(Partida partida);

        /** Las de las partidas guardadas en (desde, hasta], la primera guardada primero (P11: novedades). */
        @Query("select p from Participacion p join fetch p.partida pa join fetch p.jugador "
                + "where pa.guardadaEn > ?1 and pa.guardadaEn <= ?2 order by pa.guardadaEn, pa.jugadaEn, p.id")
        List<Participacion> findGuardadasEntre(Instant desde, Instant hasta);
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

    public interface MuestraRepo extends JpaRepository<Muestra, Long> {
        /** Las partidas de jugadores de esos niveles en un juego, para saber qué es lo normal en cada uno. */
        List<Muestra> findByJuegoAndNivelIn(Juego juego, Collection<Integer> niveles);
    }

    public interface DemoRepo extends JpaRepository<Demo, Long> {
        Optional<Demo> findByPartida(Partida partida);

        /** Las de un estado con su partida, la que hace más que no se revisa primero (P12: se miran por turnos). */
        @Query("select d from Demo d join fetch d.partida where d.estado = ?1 order by d.revisadaEn, d.id")
        List<Demo> findPorRevisar(String estado, Pageable pagina);

        long countByEstado(String estado);
    }

    public interface RondaRepo extends JpaRepository<Ronda, Long> {
        /** Las rondas de las partidas jugadas desde {@code desde}, con su partida y su jugador (P12). */
        @Query("select r from Ronda r join fetch r.partida p join fetch r.jugador where p.jugadaEn >= ?1")
        List<Ronda> findDesde(Instant desde);

        /** Las de un jugador en una partida, en orden. */
        @Query("select r from Ronda r join fetch r.partida where r.partida.id = ?1 and r.jugador = ?2 order by r.ronda")
        List<Ronda> findDePartida(long partidaId, Jugador jugador);

        /** Qué partidas de un jugador están analizadas (tienen rondas). */
        @Query("select distinct r.partida.id from Ronda r where r.jugador = ?1")
        List<Long> partidasAnalizadas(Jugador jugador);

        @Modifying
        @Query("delete from Ronda r where r.partida = ?1")
        void borrarDePartida(Partida partida);
    }

    public interface MuerteMapaRepo extends JpaRepository<MuerteMapa, Long> {
        List<MuerteMapa> findByMapa(String mapa);
    }

    public interface ValoracionRepo extends JpaRepository<Valoracion, Long> {
        /** Todas, con su jugador si lo hay, la más reciente primero (para el resumen, en memoria). */
        @Query("select v from Valoracion v left join fetch v.jugador order by v.votadaEn desc, v.id desc")
        List<Valoracion> findAllRecientes();

        /** El voto de un navegador a una recomendación de un jugador en un juego. */
        Optional<Valoracion> findFirstByVotanteAndTipoAndClaveAndJugadorAndJuego(
                String votante, String tipo, String clave, Jugador jugador, Juego juego);

        /** El voto de un navegador a una respuesta del chat (la clave ya dice cuál). */
        Optional<Valoracion> findFirstByVotanteAndTipoAndClave(String votante, String tipo, String clave);
    }
}
