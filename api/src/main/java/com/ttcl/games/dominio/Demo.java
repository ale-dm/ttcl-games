package com.ttcl.games.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * La demo de una partida de CS2 y en qué punto está su análisis (P12). Se mira por turnos: la que hace más que no se
 * revisa, primero.
 */
@Entity
@Table(name = "demos")
public class Demo {

    public static final String PENDIENTE = "pendiente";
    public static final String ANALIZADA = "analizada";
    public static final String FALLIDA = "fallida";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partida_id")
    private Partida partida;

    /** La URL de la demo en FACEIT, solo para saberla: bajarla por la API de descargas de FACEIT es de pago. */
    private String url;

    private String estado;

    private int intentos;

    /** Por qué falló o por qué sigue pendiente (sin acceso a la demo), o null. */
    private String error;

    private Instant revisadaEn;

    protected Demo() {}

    public Demo(Partida partida, String url) {
        this.partida = partida;
        this.url = url;
        this.estado = PENDIENTE;
        this.revisadaEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Partida getPartida() {
        return partida;
    }

    public String getUrl() {
        return url;
    }

    public String getEstado() {
        return estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public String getError() {
        return error;
    }

    public Instant getRevisadaEn() {
        return revisadaEn;
    }

    public void analizada() {
        estado = ANALIZADA;
        error = null;
        intentos++;
        revisadaEn = Instant.now();
    }

    /** No vale (caducada, rota, sin rondas...): no se vuelve a intentar. */
    public void fallida(String motivo) {
        estado = FALLIDA;
        error = recortar(motivo);
        intentos++;
        revisadaEn = Instant.now();
    }

    /** Sigue pendiente: no se ha podido ahora (o aún no hay forma de conseguirla). */
    public void aplazada(String motivo, boolean contarIntento) {
        error = recortar(motivo);
        if (contarIntento) {
            intentos++;
        }
        revisadaEn = Instant.now();
    }

    private static String recortar(String texto) {
        return texto == null || texto.length() <= 500 ? texto : texto.substring(0, 500);
    }
}
