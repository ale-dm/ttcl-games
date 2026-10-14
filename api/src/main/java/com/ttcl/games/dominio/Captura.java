package com.ttcl.games.dominio;

import com.ttcl.games.juego.Juego;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Una captura de las estadísticas de un jugador en otra web (P13: la página de su perfil en csstats.gg), con los
 * números que se leyeron de ella y la fecha en que se cargó. La imagen va aparte ({@link CapturaImagen}).
 */
@Entity
@Table(name = "capturas")
public class Captura {

    public static final String GEMINI = "gemini";
    public static final String MANO = "mano";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugador_id")
    private Jugador jugador;

    private Juego juego;

    private String fuente;

    private Instant cargadaEn;

    /** Los números (claves de {@code Capturas.CAMPOS}). */
    @Convert(converter = DatosConverter.class)
    private Map<String, Object> datos = new LinkedHashMap<>();

    /** "gemini" (y se revisó antes de guardar) o "mano". */
    private String leidaPor;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "capturas_mapas", joinColumns = @JoinColumn(name = "captura_id"))
    private List<MapaCaptura> mapas = new ArrayList<>();

    /** Un mapa de la captura: sus partidas o su winrate, lo que se vea (la otra cosa, null). */
    @Embeddable
    public record MapaCaptura(String mapa, Integer partidas, Double winrate) {}

    protected Captura() {}

    public Captura(
            Jugador jugador, Juego juego, String fuente, Instant cargadaEn, Map<String, Object> datos, String leidaPor,
            List<MapaCaptura> mapas) {
        this.jugador = jugador;
        this.juego = juego;
        this.fuente = fuente;
        this.cargadaEn = cargadaEn;
        this.datos = new LinkedHashMap<>(datos);
        this.leidaPor = leidaPor;
        this.mapas = new ArrayList<>(mapas);
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

    public String getFuente() {
        return fuente;
    }

    public Instant getCargadaEn() {
        return cargadaEn;
    }

    public Map<String, Object> getDatos() {
        return datos;
    }

    public String getLeidaPor() {
        return leidaPor;
    }

    public List<MapaCaptura> getMapas() {
        return mapas;
    }
}
