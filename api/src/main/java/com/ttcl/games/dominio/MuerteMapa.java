package com.ttcl.games.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dónde murió alguien en un mapa, en una partida analizada (P12). Sin decir quién ni en qué partida: con todas se
 * dibuja el mapa debajo del mapa de calor.
 */
@Entity
@Table(name = "muertes_mapa")
public class MuerteMapa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String mapa;
    private double x;
    private double y;
    private String zona;

    protected MuerteMapa() {}

    public MuerteMapa(String mapa, double x, double y, String zona) {
        this.mapa = mapa;
        this.x = x;
        this.y = y;
        this.zona = zona;
    }

    public String getMapa() {
        return mapa;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public String getZona() {
        return zona;
    }
}
