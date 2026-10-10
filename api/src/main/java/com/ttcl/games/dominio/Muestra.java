package com.ttcl.games.dominio;

import com.ttcl.games.juego.Juego;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lo que hizo en una partida alguien que no es del equipo, con su nivel de FACEIT en esa partida (P8). Sin nick ni id
 * (ni del jugador ni de la partida): solo sirve para saber qué es lo normal en cada nivel.
 */
@Entity
@Table(name = "muestras")
public class Muestra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Juego juego;

    private int nivel;

    private String mapa;

    /** Día de la partida. */
    private LocalDate fecha;

    private Boolean gano;
    private Integer kills;
    private Integer muertes;
    private Integer asistencias;

    @Convert(converter = DatosConverter.class)
    private Map<String, Object> datos = new LinkedHashMap<>();

    protected Muestra() {}

    public Muestra(
            Juego juego, int nivel, String mapa, LocalDate fecha, Boolean gano, Integer kills, Integer muertes,
            Integer asistencias, Map<String, Object> datos) {
        this.juego = juego;
        this.nivel = nivel;
        this.mapa = mapa;
        this.fecha = fecha;
        this.gano = gano;
        this.kills = kills;
        this.muertes = muertes;
        this.asistencias = asistencias;
        this.datos = datos == null ? new LinkedHashMap<>() : new LinkedHashMap<>(datos);
    }

    public Long getId() {
        return id;
    }

    public Juego getJuego() {
        return juego;
    }

    public int getNivel() {
        return nivel;
    }

    public String getMapa() {
        return mapa;
    }

    public LocalDate getFecha() {
        return fecha;
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
