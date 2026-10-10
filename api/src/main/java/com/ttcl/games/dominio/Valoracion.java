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
 * Un 👍 o un 👎 de la web (P7) a una recomendación del Duende o a una respuesta del chat, con lo que se vio. Sirve para
 * revisar las peor valoradas y cambiar las reglas o el prompt.
 */
@Entity
@Table(name = "valoraciones")
public class Valoracion {

    public static final String CONSEJO = "consejo";
    public static final String RESPUESTA = "respuesta";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@link #CONSEJO} o {@link #RESPUESTA}. */
    private String tipo;

    /** Id de la recomendación (debil_adr, tilt_sesion...) o hash de la pregunta y la respuesta del chat. */
    private String clave;

    /** 1 (me sirve) o -1 (no me sirve). */
    private int voto;

    /** "reglas" o "gemini". */
    private String origen;

    private String modelo;

    /** Solo en el chat: de qué iba la pregunta según las reglas. */
    private String intencion;

    /** Solo en las recomendaciones: alto, medio, bien o info. */
    private String nivel;

    /** De quién se hablaba (en el chat, el primero del foco), o null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugador_id")
    private Jugador jugador;

    private Juego juego;

    private String lang;

    private String pregunta;

    private String texto;

    /** Id al azar del navegador que votó. */
    private String votante;

    private Instant votadaEn;

    protected Valoracion() {}

    public Valoracion(
            String tipo, String clave, int voto, String origen, String modelo, String intencion, String nivel,
            Jugador jugador, Juego juego, String lang, String pregunta, String texto, String votante,
            Instant votadaEn) {
        this.tipo = tipo;
        this.clave = clave;
        this.voto = voto;
        this.origen = origen;
        this.modelo = modelo;
        this.intencion = intencion;
        this.nivel = nivel;
        this.jugador = jugador;
        this.juego = juego;
        this.lang = lang;
        this.pregunta = pregunta;
        this.texto = texto;
        this.votante = votante;
        this.votadaEn = votadaEn;
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getClave() {
        return clave;
    }

    public int getVoto() {
        return voto;
    }

    public String getOrigen() {
        return origen;
    }

    public String getModelo() {
        return modelo;
    }

    public String getIntencion() {
        return intencion;
    }

    public String getNivel() {
        return nivel;
    }

    public Jugador getJugador() {
        return jugador;
    }

    public Juego getJuego() {
        return juego;
    }

    public String getLang() {
        return lang;
    }

    public String getPregunta() {
        return pregunta;
    }

    public String getTexto() {
        return texto;
    }

    public String getVotante() {
        return votante;
    }

    public Instant getVotadaEn() {
        return votadaEn;
    }
}
