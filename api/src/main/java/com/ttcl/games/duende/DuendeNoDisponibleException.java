package com.ttcl.games.duende;

/** El servicio Python del Duende no está levantado o ha fallado. */
public class DuendeNoDisponibleException extends RuntimeException {

    public DuendeNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
