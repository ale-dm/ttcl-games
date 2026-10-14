package com.ttcl.games.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** La imagen de una captura (P13), aparte de sus números para no cargarla al listar. */
@Entity
@Table(name = "capturas_imagenes")
public class CapturaImagen {

    @Id
    private Long capturaId;

    /** image/png, image/jpeg o image/webp. */
    private String tipo;

    private byte[] datos;

    protected CapturaImagen() {}

    public CapturaImagen(Long capturaId, String tipo, byte[] datos) {
        this.capturaId = capturaId;
        this.tipo = tipo;
        this.datos = datos;
    }

    public Long getCapturaId() {
        return capturaId;
    }

    public String getTipo() {
        return tipo;
    }

    public byte[] getDatos() {
        return datos;
    }
}
