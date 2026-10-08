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

/** Cuenta de un jugador en un juego. Un jugador puede tener una por juego. */
@Entity
@Table(name = "cuentas")
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugador_id")
    private Jugador jugador;

    private Juego juego;

    /** ID en la fuente (player_id de FACEIT, ID de Hi-Rez...). Null hasta que el worker lo resuelve por el nick. */
    private String externalId;

    /** Nick en la fuente. */
    private String nombreExterno;

    private Instant ultimaSync;

    protected Cuenta() {}

    public Cuenta(Jugador jugador, Juego juego, String nombreExterno) {
        this.jugador = jugador;
        this.juego = juego;
        this.nombreExterno = nombreExterno;
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

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getNombreExterno() {
        return nombreExterno;
    }

    public void setNombreExterno(String nombreExterno) {
        this.nombreExterno = nombreExterno;
    }

    public Instant getUltimaSync() {
        return ultimaSync;
    }

    public void setUltimaSync(Instant ultimaSync) {
        this.ultimaSync = ultimaSync;
    }
}
