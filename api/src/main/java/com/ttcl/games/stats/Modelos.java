package com.ttcl.games.stats;

import com.ttcl.games.dominio.Participacion;
import com.ttcl.games.dominio.Ronda;
import com.ttcl.games.juego.Juego;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Tipos de las estadísticas. Son los mismos que viajan en JSON a la web y al Duende (camelCase). */
public final class Modelos {

    private Modelos() {}

    /**
     * Una participación ya leída de la base, sin entidades JPA: lo que necesitan los cálculos.
     *
     * @param duracionSeg duración de la partida, o null si la fuente no la da
     */
    public record FilaParticipacion(
            long partidaId,
            Juego juego,
            Instant jugadaEn,
            Integer duracionSeg,
            String modo,
            Boolean gano,
            Integer kills,
            Integer muertes,
            Integer asistencias,
            Map<String, Object> datos) {

        public static FilaParticipacion de(Participacion p) {
            return new FilaParticipacion(
                    p.getPartida().getId(),
                    p.getJuego(),
                    p.getPartida().getJugadaEn(),
                    p.getPartida().getDuracionSeg(),
                    p.getPartida().getModo(),
                    p.getGano(),
                    p.getKills(),
                    p.getMuertes(),
                    p.getAsistencias(),
                    p.getDatos());
        }
    }

    /**
     * Resumen de un jugador en un juego.
     *
     * @param winrate porcentaje 0-100 sobre las partidas con resultado conocido
     * @param kd kills totales entre muertes totales
     * @param datosMedios medias de lo específico del juego (adr, hs_pct, entry_pct... o kda, dano_min, oro_min...)
     * @param forma últimos resultados, de más reciente a más antigua: V victoria, D derrota, ? sin dato
     */
    public record ResumenJuego(
            Juego juego,
            int partidas,
            int victorias,
            int derrotas,
            Double winrate,
            Double kd,
            Double killsMedia,
            Double muertesMedia,
            Double asistenciasMedia,
            Map<String, Double> datosMedios,
            String forma,
            Instant ultimaPartida) {}

    /** Media del resto del equipo en un juego (sin el jugador que se está mirando). */
    public record MediasEquipo(
            int jugadores,
            Double winrate,
            Double kd,
            Double killsMedia,
            Double muertesMedia,
            Double asistenciasMedia,
            Map<String, Double> datosMedios) {}

    /** Rendimiento por mapa (CS2) o por dios (SMITE 2). */
    public record FilaDesglose(String clave, int partidas, int victorias, Double winrate, Double kd) {}

    /** Un jugador del equipo en una partida. Mismo resultado que otro del equipo = mismo bando. */
    public record Presencia(String slug, String nombre, Boolean gano) {}

    /**
     * Cómo le va a un jugador con un compañero del equipo, o solo (entonces {@code slug} y {@code nombre} son null).
     *
     * @param partidasSin partidas sin ese compañero (en la fila de solo: con alguien del equipo), para comparar
     * @param winrateSin winrate en esas otras partidas, o null si no hay ninguna con resultado
     */
    public record FilaSinergia(
            String slug, String nombre, int partidas, int victorias, Double winrate, Double kd, int partidasSin,
            Double winrateSin) {}

    /**
     * Con quién juega mejor.
     *
     * @param solo partidas sin nadie del equipo, o null si hay menos de las mínimas
     * @param companeros uno por compañero con partidas suficientes juntos, el que más primero
     */
    public record Sinergias(FilaSinergia solo, List<FilaSinergia> companeros) {}

    public record Miembro(String slug, String nombre) {}

    /** Dos o tres del equipo y cómo les va cuando juegan juntos en el mismo bando (aunque haya alguien más). */
    public record Grupo(List<Miembro> jugadores, int partidas, int victorias, Double winrate) {}

    /**
     * Cómo le va en un tipo de partida (la 3ª o más de la sesión, la que sigue a una derrota, las de noche...) y, para
     * comparar, en todas las demás.
     *
     * @param clave "1", "2" o "3+" (orden en la sesión); "victoria" o "derrota" (lo que pasó en la anterior de la
     *     sesión); "manana", "tarde", "noche" o "madrugada" (hora del día en la zona del equipo)
     * @param partidasResto partidas que no son de este tipo
     * @param winrateResto winrate en esas otras partidas, o null si no hay ninguna con resultado
     */
    public record FilaMomento(
            String clave, int partidas, int victorias, Double winrate, Double kd, int partidasResto,
            Double winrateResto) {}

    /**
     * Sesiones de juego: partidas seguidas, con menos de {@code Estadisticas.PAUSA_SESION} entre el final de una y el
     * principio de la siguiente. Cada lista, en orden fijo y sin las filas que no tienen partidas.
     *
     * @param porOrden 1ª, 2ª y de la 3ª en adelante dentro de la sesión
     * @param trasResultado partidas que siguen a una victoria o a una derrota en la misma sesión
     * @param porFranja mañana (6-14 h), tarde (14-20 h), noche (20-24 h) y madrugada (0-6 h)
     */
    public record Sesiones(
            int sesiones, Double partidasPorSesion, List<FilaMomento> porOrden, List<FilaMomento> trasResultado,
            List<FilaMomento> porFranja) {}

    /**
     * Resumen de un jugador en los últimos días, para el chat ("¿cómo voy esta semana?").
     *
     * @param equipo media del resto del equipo en ese mismo periodo (null si nadie más jugó)
     */
    public record ResumenPeriodo(Periodo periodo, ResumenJuego resumen, MediasEquipo equipo) {}

    /** Un consejo que se le dio, tal como se guardó: lo que hace falta para su seguimiento. */
    public record ConsejoAnterior(String insight, String metrica, Double valor, Instant dadoEn) {}

    /**
     * Cómo ha ido un consejo que hablaba de una métrica: su valor el día que se dio y en las partidas jugadas desde
     * entonces. El Duende decide con esto si ha funcionado.
     *
     * @param valor valor de la métrica ese día, con todas sus partidas de entonces
     * @param dias días desde que se dio
     * @param partidasDesde partidas jugadas desde entonces
     * @param valorDesde valor de la métrica en esas partidas, o null si no hay ninguna
     */
    public record SeguimientoConsejo(
            String insight, String metrica, Double valor, Instant dadoEn, int dias, int partidasDesde,
            Double valorDesde) {}

    /**
     * Una métrica de un jugador frente a las partidas de jugadores de su nivel (P8).
     *
     * @param referencia lo normal en ese nivel: la mediana de sus partidas o, en las que salen de totales (entradas,
     *     clutches...), el porcentaje de todas juntas
     * @param percentil porcentaje de esas partidas con un valor más bajo que el suyo (los empates cuentan la mitad),
     *     sin girar en las que es mejor bajo (muertes); null en las que salen de totales o si él no tiene valor
     * @param muestras partidas de ese nivel con dato de esta métrica
     */
    public record MetricaNivel(String metrica, double referencia, Double percentil, int muestras) {}

    /**
     * Cómo queda un jugador frente a los jugadores de su nivel de FACEIT (P8).
     *
     * @param elo ELO de FACEIT, o null si la fuente no lo dio
     * @param partidas partidas de jugadores de ese nivel que hay guardadas
     * @param metricas solo las que tienen muestra suficiente (vacía si aún no hay)
     */
    public record ComparativaNivel(int nivel, Integer elo, int partidas, List<MetricaNivel> metricas) {}

    /**
     * Algo especial de una partida frente al historial del jugador (P10). El Duende lo convierte en una frase.
     *
     * @param tipo racha_victorias, racha_derrotas, fin_racha_victorias, fin_racha_derrotas, racha_victorias_clave,
     *     racha_derrotas_clave, estreno_clave, mejor_mes, peor_mes, sobre_media o bajo_media
     * @param metrica de qué métrica habla (kills_media, adr, hs_pct, kd, kda, dano), si habla de una
     * @param valor el de esta partida
     * @param referencia con qué se compara: el mejor o el peor de los 30 días anteriores, o su media
     * @param n partidas de la racha (o de la racha que se acaba), o partidas con las que se compara
     * @param clave mapa o dios, en las rachas y estrenos de un mapa o dios
     */
    public record Hecho(String tipo, String metrica, Double valor, Double referencia, Integer n, String clave) {}

    /** Cómo le ha ido a un jugador en un juego en los últimos 7 días (P11: resumen semanal). */
    public record FilaSemana(String slug, String nombre, int partidas, int victorias, Double winrate, Double kd) {}

    /**
     * La semana de un juego: cuántas partidas jugó el equipo, cada jugador y el mejor y el peor (con un mínimo de
     * partidas; el peor es null si solo llega uno).
     */
    public record SemanaJuego(Juego juego, int partidas, List<FilaSemana> jugadores, FilaSemana mejor, FilaSemana peor) {}

    // ─── Demos (P12) ────────────────────────────────────────────────────────

    /**
     * Lo que hizo un jugador en una ronda, ya leído de la base (P12).
     *
     * @param mapa el de la partida
     * @param lado "CT" o "T"
     * @param gano si su equipo ganó la ronda
     * @param muerteZona dónde murió, para leer ("Top of Mid", "Bombsite A"): ver {@link Rondas#nombreZona}
     * @param apertura "ganada" o "perdida" si el primer duelo de la ronda fue suyo; si no, null
     * @param compra "pistola", "eco", "forzada" o "completa"
     */
    public record FilaRonda(
            long partidaId,
            Instant jugadaEn,
            String mapa,
            int ronda,
            String lado,
            Boolean gano,
            int kills,
            int asistencias,
            int asistenciasFlash,
            int dano,
            int danoUtilidad,
            boolean murio,
            Double muerteX,
            Double muerteY,
            String muerteZona,
            boolean tradeado,
            int trades,
            String apertura,
            Integer equipamiento,
            String compra,
            boolean kast) {

        public static FilaRonda de(Ronda r) {
            return new FilaRonda(
                    r.getPartida().getId(), r.getPartida().getJugadaEn(), r.getPartida().getModo(), r.getRonda(),
                    r.getLado(), r.getGano(), r.getKills(), r.getAsistencias(), r.getAsistenciasFlash(), r.getDano(),
                    r.getDanoUtilidad(), r.isMurio(), r.getMuerteX(), r.getMuerteY(),
                    Rondas.nombreZona(r.getMuerteZona()), r.isTradeado(), r.getTrades(), r.getApertura(),
                    r.getEquipamiento(), r.getCompra(), r.isKast());
        }
    }

    /**
     * Lo que dicen las rondas de unas partidas analizadas (P12). Los porcentajes, de 0 a 100.
     *
     * @param rating rating propio al estilo del 2.0 de HLTV (1,00 es lo normal): ver {@link Rondas#rating}
     * @param kast % de rondas con kill, asistencia, sobreviviendo o siendo tradeado
     * @param adr daño por ronda, como mucho 100 por rival
     * @param aperturas primeros duelos de la ronda en los que estuvo, y cuántos ganó
     * @param trades kills a rivales que acababan de matar a un compañero (en menos de 5 s)
     * @param muertesTradeadas sus muertes que un compañero vengó en menos de 5 s
     * @param flashPartida asistencias de flash por partida
     * @param utilidadRonda daño de granadas y molotov por ronda
     * @param winrateRondas % de rondas ganadas
     */
    public record MetricasRondas(
            int partidas,
            int rondas,
            Double rating,
            Double kast,
            Double adr,
            Double kpr,
            Double dpr,
            int aperturas,
            int aperturasGanadas,
            Double aperturaPct,
            int trades,
            Double tradesPartida,
            int muertes,
            int muertesTradeadas,
            Double tradeadasPct,
            Double flashPartida,
            Double utilidadRonda,
            Double winrateRondas) {}

    /** Cómo le va de CT o de T. */
    public record FilaLado(String lado, int rondas, int ganadas, Double winrate, Double rating, Double kast, Double adr) {}

    /** Cómo le va según la compra de la ronda: pistola, eco, forzada o completa. */
    public record FilaCompra(String compra, int rondas, int ganadas, Double winrate, Double kpr) {}

    /** Cuántas veces muere en una zona de un mapa y cuántas sin que le tradeen. */
    public record ZonaMuerte(String zona, int muertes, int sinTrade) {}

    /** Sus muertes en un mapa: en total, sin trade y por zona (las que más sin trade primero). */
    public record MapaMuertes(String mapa, int partidas, int muertes, int sinTrade, List<ZonaMuerte> zonas) {}

    /**
     * Todo lo de sus demos (P12).
     *
     * @param equipo la media del resto del equipo (cada jugador pesa igual; partidas y rondas, sumadas), o null si
     *     nadie más tiene partidas analizadas
     * @param lados CT y T, los que tengan rondas
     * @param economia por tipo de compra, los que tengan rondas
     * @param mapas sus muertes por mapa, el de más muertes primero
     */
    public record ResumenDemos(
            MetricasRondas metricas, MetricasRondas equipo, List<FilaLado> lados, List<FilaCompra> economia,
            List<MapaMuertes> mapas) {}

    public record Punto(double x, double y) {}

    /** Dónde murió (coordenadas del juego) y si le tradearon. */
    public record PuntoMuerte(double x, double y, String zona, boolean tradeado) {}

    /** Dónde poner el nombre de una zona en el dibujo del mapa. */
    public record EtiquetaZona(String zona, double x, double y) {}

    /**
     * Mapa de calor de sus muertes en un mapa (P12).
     *
     * @param mapas los mapas en los que tiene muertes analizadas, el de más primero
     * @param fondo dónde muere la gente en ese mapa (todas las partidas analizadas, sin decir quién): dibuja el mapa
     * @param zonas el nombre de cada zona, en el centro de sus muertes
     */
    public record CalorMapa(
            String mapa, List<String> mapas, List<PuntoMuerte> muertes, List<Punto> fondo, List<EtiquetaZona> zonas) {}

    /** Un punto de la gráfica de partidas. */
    public record PuntoSerie(
            long partidaId, Instant fecha, Boolean gano, Integer kills, Integer muertes, Integer asistencias,
            String clave) {}

    /**
     * Una métrica comparada entre dos jugadores.
     *
     * @param mejor "alto", "bajo" o "neutral": hacia dónde gana la métrica
     * @param ventaja "a", "b" o null si no hay datos de ambos o empatan
     */
    public record FilaComparacion(String metrica, Double a, Double b, String mejor, String ventaja) {}
}
