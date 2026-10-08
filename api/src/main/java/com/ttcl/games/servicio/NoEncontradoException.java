package com.ttcl.games.servicio;

/** Lo pedido no existe (jugador, o partidas de ese jugador en ese juego). Se responde con 404. */
public class NoEncontradoException extends RuntimeException {

    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
