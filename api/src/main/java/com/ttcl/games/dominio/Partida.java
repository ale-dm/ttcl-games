package com.ttcl.games.dominio;

import com.ttcl.games.juego.Juego;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Partida. Es compartida: si dos miembros del equipo jugaron juntos, hay una sola fila y dos participaciones.
 */
@Entity
@Table(name = "partidas")
public class Partida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Juego juego;

    private String externalId;

    private Instant jugadaEn;

    private Integer duracionSeg;

    /** Cola, mapa o modo de juego. */
    private String modo;

    protected Partida() {}

    public Partida(Juego juego, String externalId, Instant jugadaEn, Integer duracionSeg, String modo) {
        this.juego = juego;
        this.externalId = externalId;
        this.jugadaEn = jugadaEn;
        this.duracionSeg = duracionSeg;
        this.modo = modo;
    }

    public Long getId() {
        return id;
    }

    public Juego getJuego() {
        return juego;
    }

    public String getExternalId() {
        return externalId;
    }

    public Instant getJugadaEn() {
        return jugadaEn;
    }

    public Integer getDuracionSeg() {
        return duracionSeg;
    }

    public String getModo() {
        return modo;
    }
}
