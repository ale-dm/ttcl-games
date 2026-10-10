package com.ttcl.games.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Lo que hizo un jugador del equipo en una ronda de una partida de CS2, sacado de su demo (P12). */
@Entity
@Table(name = "rondas")
public class Ronda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partida_id")
    private Partida partida;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugador_id")
    private Jugador jugador;

    private int ronda;
    private String lado;
    private Boolean gano;
    private int kills;
    private int asistencias;
    private int asistenciasFlash;
    private int dano;
    private int danoUtilidad;
    private boolean murio;
    @Column(name = "muerte_x")
    private Double muerteX;
    @Column(name = "muerte_y")
    private Double muerteY;
    private String muerteZona;
    private boolean tradeado;
    private int trades;
    private String apertura;
    private Integer equipamiento;
    private String compra;
    private boolean kast;

    protected Ronda() {}

    /** Todo de una vez: así lo da el trabajador de análisis (y así lo generan los datos de ejemplo). */
    public Ronda(
            Partida partida, Jugador jugador, int ronda, String lado, Boolean gano, int kills, int asistencias,
            int asistenciasFlash, int dano, int danoUtilidad, boolean murio, Double muerteX, Double muerteY,
            String muerteZona, boolean tradeado, int trades, String apertura, Integer equipamiento, String compra,
            boolean kast) {
        this.partida = partida;
        this.jugador = jugador;
        this.ronda = ronda;
        this.lado = lado;
        this.gano = gano;
        this.kills = kills;
        this.asistencias = asistencias;
        this.asistenciasFlash = asistenciasFlash;
        this.dano = dano;
        this.danoUtilidad = danoUtilidad;
        this.murio = murio;
        this.muerteX = muerteX;
        this.muerteY = muerteY;
        this.muerteZona = muerteZona;
        this.tradeado = tradeado;
        this.trades = trades;
        this.apertura = apertura;
        this.equipamiento = equipamiento;
        this.compra = compra;
        this.kast = kast;
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

    public int getRonda() {
        return ronda;
    }

    public String getLado() {
        return lado;
    }

    public Boolean getGano() {
        return gano;
    }

    public int getKills() {
        return kills;
    }

    public int getAsistencias() {
        return asistencias;
    }

    public int getAsistenciasFlash() {
        return asistenciasFlash;
    }

    public int getDano() {
        return dano;
    }

    public int getDanoUtilidad() {
        return danoUtilidad;
    }

    public boolean isMurio() {
        return murio;
    }

    public Double getMuerteX() {
        return muerteX;
    }

    public Double getMuerteY() {
        return muerteY;
    }

    public String getMuerteZona() {
        return muerteZona;
    }

    public boolean isTradeado() {
        return tradeado;
    }

    public int getTrades() {
        return trades;
    }

    public String getApertura() {
        return apertura;
    }

    public Integer getEquipamiento() {
        return equipamiento;
    }

    public String getCompra() {
        return compra;
    }

    public boolean isKast() {
        return kast;
    }
}
