package com.ttcl.games.dominio;

import com.ttcl.games.juego.Juego;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lo que hizo un jugador del equipo en una partida. Las columnas comunes sirven para comparar entre juegos; lo
 * específico de cada juego (ADR y HS% en CS2, daño u oro en SMITE 2...) va en {@code datos}.
 */
@Entity
@Table(name = "participaciones")
public class Participacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partida_id")
    private Partida partida;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugador_id")
    private Jugador jugador;

    /** Duplicado de partidas.juego para agregar sin join. */
    private Juego juego;

    private Boolean gano;
    private Integer kills;
    private Integer muertes;
    private Integer asistencias;

    @Convert(converter = DatosConverter.class)
    private Map<String, Object> datos = new LinkedHashMap<>();

    protected Participacion() {}

    public Participacion(Partida partida, Jugador jugador) {
        this.partida = partida;
        this.jugador = jugador;
        this.juego = partida.getJuego();
    }

    /** Copia los números de una participación externa (al crearla o al volver a sincronizarla). */
    public void actualizar(Boolean gano, Integer kills, Integer muertes, Integer asistencias, Map<String, Object> datos) {
        this.gano = gano;
        this.kills = kills;
        this.muertes = muertes;
        this.asistencias = asistencias;
        this.datos = datos == null ? new LinkedHashMap<>() : new LinkedHashMap<>(datos);
    }

    public Long getId() {
        return id;
    }

    public Partida getPartida() {
        return partida;
    }

    public Jugador getJugador() {
        return jugador;
    }

    public Juego getJuego() {
        return juego;
    }

    public Boolean getGano() {
        return gano;
    }

    public Integer getKills() {
        return kills;
    }

    public Integer getMuertes() {
        return muertes;
    }

    public Integer getAsistencias() {
        return asistencias;
    }

    public Map<String, Object> getDatos() {
        return datos;
    }
}
