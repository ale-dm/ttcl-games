package com.ttcl.games.sync;

import com.ttcl.games.config.TtclProperties;
import com.ttcl.games.juego.Juego;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sincroniza cada {@code ttcl.sync.intervalo-min} minutos (el primer intento, un minuto después de arrancar). Solo
 * con los juegos que tienen credenciales: sin FACEIT_API_KEY no se toca CS2, y lo mismo con SMITE 2.
 */
@Component
public class SyncProgramado {

    private static final Logger log = LoggerFactory.getLogger(SyncProgramado.class);

    private final Sincronizador sincronizador;
    private final TtclProperties props;
    private final Map<Juego, FuenteJuego> fuentes = new EnumMap<>(Juego.class);

    public SyncProgramado(Sincronizador sincronizador, TtclProperties props) {
        this.sincronizador = sincronizador;
        this.props = props;
        if (props.faceit().configurada()) {
            fuentes.put(Juego.CS2, new FaceitFuente(props.faceit().apiKey(), props.faceit().base()));
        }
        if (props.smite2().configurada()) {
            fuentes.put(Juego.SMITE2,
                    new HirezFuente(props.smite2().base(), props.smite2().devId(), props.smite2().authKey()));
        }
    }

    @Scheduled(
            initialDelay = 1,
            fixedDelayString = "${ttcl.sync.intervalo-min:60}",
            timeUnit = TimeUnit.MINUTES)
    public void sincronizar() {
        if (fuentes.isEmpty()) {
            log.debug("Sin fuentes configuradas: no se sincroniza nada");
            return;
        }
        var resultados = sincronizador.sincronizarTodo(fuentes, props.sync().limite());
        long ok = resultados.stream().filter(Sincronizador.ResultadoCuenta::ok).count();
        log.info("Sincronización terminada: {} cuentas bien, {} con error", ok, resultados.size() - ok);
    }
}
