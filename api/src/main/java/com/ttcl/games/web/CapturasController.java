package com.ttcl.games.web;

import com.ttcl.games.dominio.CapturaImagen;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.servicio.CapturasServicio;
import com.ttcl.games.servicio.CapturasServicio.Imagen;
import com.ttcl.games.servicio.CapturasServicio.NuevaCaptura;
import com.ttcl.games.servicio.Vistas.CapturaVista;
import com.ttcl.games.servicio.Vistas.LecturaCaptura;
import com.ttcl.games.stats.Capturas;
import com.ttcl.games.stats.Capturas.Evolucion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Capturas de las estadísticas de cada uno en csstats.gg (P13): subir una, leerla, guardarla con la fecha de carga y
 * comparar dos. Solo CS2 ({@code ?juego=cs2}, por defecto).
 */
@RestController
public class CapturasController {

    private final CapturasServicio capturas;

    public CapturasController(CapturasServicio capturas) {
        this.capturas = capturas;
    }

    /** Lo que se guarda de cada captura, en orden y con su grupo: con esto la web monta el formulario. */
    @GetMapping("/api/capturas/campos")
    public List<Capturas.Campo> campos() {
        return capturas.campos();
    }

    /** Sus capturas, la primera cargada primero. */
    @GetMapping("/api/jugadores/{slug}/capturas")
    public List<CapturaVista> lista(@PathVariable String slug, @RequestParam(defaultValue = "cs2") Juego juego) {
        return capturas.lista(slug, juego);
    }

    /** Lee una captura (con Gemini, si hay clave) para revisar sus números. No guarda nada. */
    @PostMapping(value = "/api/jugadores/{slug}/capturas/leer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LecturaCaptura leer(@PathVariable String slug, @RequestPart("imagen") MultipartFile imagen) {
        return capturas.leer(slug, imagen(imagen));
    }

    /** Guarda una captura revisada, con la fecha de ahora y su imagen (opcional). */
    @PostMapping(value = "/api/jugadores/{slug}/capturas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CapturaVista guardar(
            @PathVariable String slug,
            @RequestParam(defaultValue = "cs2") Juego juego,
            @RequestPart("datos") NuevaCaptura datos,
            @RequestPart(value = "imagen", required = false) MultipartFile imagen) {
        return capturas.guardar(slug, juego, datos, imagen == null || imagen.isEmpty() ? null : imagen(imagen));
    }

    /** La imagen de una captura. */
    @GetMapping("/api/jugadores/{slug}/capturas/{id}/imagen")
    public ResponseEntity<byte[]> imagen(@PathVariable String slug, @PathVariable long id) {
        CapturaImagen i = capturas.imagen(slug, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(i.getTipo()))
                .cacheControl(CacheControl.noCache())
                .body(i.getDatos());
    }

    /** Borra una captura (por si se subió mal). */
    @DeleteMapping("/api/jugadores/{slug}/capturas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable String slug, @PathVariable long id) {
        capturas.borrar(slug, id);
    }

    /**
     * Compara dos capturas (por defecto, la primera y la última): cada número antes y después, y cómo jugó entre
     * medias (el tramo).
     */
    @GetMapping("/api/jugadores/{slug}/capturas/comparar")
    public Evolucion comparar(
            @PathVariable String slug,
            @RequestParam(defaultValue = "cs2") Juego juego,
            @RequestParam(required = false) Long desde,
            @RequestParam(required = false) Long hasta) {
        return capturas.comparar(slug, juego, desde, hasta);
    }

    private static Imagen imagen(MultipartFile archivo) {
        try {
            return Imagen.de(archivo.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
