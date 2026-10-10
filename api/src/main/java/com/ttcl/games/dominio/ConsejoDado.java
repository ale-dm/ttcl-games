package com.ttcl.games.dominio;

import com.ttcl.games.juego.Juego;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Un consejo que el Duende le dio a un jugador (P6): qué recomendación, de qué métrica y con qué valor ese día. Con eso
 * se ve después si ha funcionado.
 */
@Entity
@Table(name = "consejos_dados")
public class ConsejoDado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugador_id")
    private Jugador jugador;

    private Juego juego;

    /** Id de la recomendación del Duende: debil_adr, kd_sin_victorias, tilt_sesion... */
    private String insight;

    /** Métrica de la que habla (adr, winrate...), o null si no habla de una. */
    private String metrica;

    /** Valor de la métrica ese día, con todas sus partidas. */
    private Double valor;

    /** "alto" o "medio". */
    private String nivel;

    private Instant dadoEn;

    protected ConsejoDado() {}

    public ConsejoDado(
            Jugador jugador, Juego juego, String insight, String metrica, Double valor, String nivel, Instant dadoEn) {
        this.jugador = jugador;
        this.juego = juego;
        this.insight = insight;
        this.metrica = metrica;
        this.valor = valor;
        this.nivel = nivel;
        this.dadoEn = dadoEn;
    }

    public Long getId() {
        return id;
    }

    public Jugador getJugador() {
        return jugador;
    }

    public Juego getJuego() {
        return juego;
    }

    public String getInsight() {
        return insight;
    }

    public String getMetrica() {
        return metrica;
    }

    public Double getValor() {
        return valor;
    }

    public String getNivel() {
        return nivel;
    }

    public Instant getDadoEn() {
        return dadoEn;
    }
}
