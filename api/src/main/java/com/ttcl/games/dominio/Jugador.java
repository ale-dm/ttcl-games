package com.ttcl.games.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Un miembro del equipo. */
@Entity
@Table(name = "jugadores")
public class Jugador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador de URL, p. ej. "ale". */
    private String slug;

    private String nombre;

    /** Jugador inventado por el generador de datos de ejemplo. */
    private boolean demo;

    private Instant creadoEn = Instant.now();

    protected Jugador() {}

    public Jugador(String slug, String nombre, boolean demo) {
        this.slug = slug;
        this.nombre = nombre;
        this.demo = demo;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isDemo() {
        return demo;
    }
}
