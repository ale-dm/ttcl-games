package com.ttcl.games.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * En Windows, la JDK crea un socket Unix en la carpeta temporal cada vez que abre un selector NIO (Tomcat lo hace al
 * arrancar). Si la ruta tiene caracteres fuera de ASCII, como en un usuario "JavierGalvañ", falla con "Unable to
 * establish loopback connection" y la API no arranca. Aquí se le da una carpeta con ruta ASCII.
 */
public final class CarpetaTemporal {

    private static final String PROPIEDAD = "jdk.net.unixdomain.tmpdir";

    private CarpetaTemporal() {}

    /** Llamar al principio de main(), antes de que nada abra un selector. No hace nada si no hace falta. */
    public static void asegurarRutaAscii() {
        if (System.getProperty(PROPIEDAD) != null) {
            return;
        }
        if (!System.getProperty("os.name", "").startsWith("Windows") || esAscii(rutaReal(temporal()))) {
            return;
        }
        List<String> candidatas = new ArrayList<>();
        if (System.getenv("PUBLIC") != null) {
            candidatas.add(System.getenv("PUBLIC"));
        }
        if (System.getenv("SystemRoot") != null) {
            candidatas.add(System.getenv("SystemRoot") + "\\Temp");
        }
        for (String base : candidatas) {
            Path carpeta = Path.of(base, "ttcl-tmp");
            if (!esAscii(carpeta.toString())) {
                continue;
            }
            try {
                Files.createDirectories(carpeta);
                if (Files.isWritable(carpeta)) {
                    System.setProperty(PROPIEDAD, carpeta.toString());
                    return;
                }
            } catch (IOException | RuntimeException e) {
                // Se prueba la siguiente.
            }
        }
    }

    private static String temporal() {
        String temp = System.getenv("TEMP");
        return temp != null ? temp : System.getProperty("java.io.tmpdir", "");
    }

    /**
     * Ruta larga de verdad: TEMP suele venir en formato corto ("C:\Users\JAVIER~1\...") y parece ASCII, pero la JDK
     * acaba usando la larga ("C:\Users\JavierGalvañ\...").
     */
    private static String rutaReal(String ruta) {
        try {
            return Path.of(ruta).toRealPath().toString();
        } catch (IOException | RuntimeException e) {
            return ruta;
        }
    }

    private static boolean esAscii(String texto) {
        return texto.chars().allMatch(c -> c < 128);
    }
}
