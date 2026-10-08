package com.ttcl.games.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Historial de sincronizaciones: cuándo se miró cada cuenta y por qué falló si falló. */
@Entity
@Table(name = "syncs")
public class Sync {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id")
    private Cuenta cuenta;

    private Instant empezadaEn;
    private Instant terminadaEn;

    /** "ok" o "error". */
    private String estado;

    private int partidasNuevas;
    private String error;

    protected Sync() {}

    private Sync(Cuenta cuenta, Instant empezadaEn, String estado, int partidasNuevas, String error) {
        this.cuenta = cuenta;
        this.empezadaEn = empezadaEn;
        this.terminadaEn = Instant.now();
        this.estado = estado;
        this.partidasNuevas = partidasNuevas;
        this.error = error;
    }

    public static Sync ok(Cuenta cuenta, Instant empezadaEn, int partidasNuevas) {
        return new Sync(cuenta, empezadaEn, "ok", partidasNuevas, null);
    }

    public static Sync error(Cuenta cuenta, Instant empezadaEn, String error) {
        String corto = error == null ? "error desconocido" : error.substring(0, Math.min(500, error.length()));
        return new Sync(cuenta, empezadaEn, "error", 0, corto);
    }

    public String getEstado() {
        return estado;
    }

    public String getError() {
        return error;
    }

    public int getPartidasNuevas() {
        return partidasNuevas;
    }
}
