package com.ttcl.games.sync;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Lectura de JSON genérico (mapas y listas) de las APIs externas. Se navega a mano en vez de con clases: los campos
 * de FACEIT y Hi-Rez traen espacios, símbolos y tipos que cambian ("21" o 21), y aquí cuesta menos adaptarse.
 */
final class Json {

    private Json() {}

    static SimpleClientHttpRequestFactory factory(Duration lectura) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(lectura);
        return factory;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> mapa(Object valor) {
        return valor instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    static Map<String, Object> mapa(Object padre, String campo) {
        return mapa(mapa(padre).get(campo));
    }

    static List<?> lista(Object valor) {
        return valor instanceof List<?> l ? l : List.of();
    }

    static List<?> lista(Object padre, String campo) {
        return lista(mapa(padre).get(campo));
    }

    static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        String s = String.valueOf(valor).trim();
        return s.isEmpty() ? null : s;
    }

    /** Número a partir de 21, "21" o "1.31". Null si no es un número. */
    static Double numero(Object valor) {
        if (valor instanceof Number n) {
            return Double.isFinite(n.doubleValue()) ? n.doubleValue() : null;
        }
        String s = texto(valor);
        if (s == null) {
            return null;
        }
        try {
            double d = Double.parseDouble(s.replace(',', '.'));
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer entero(Object valor) {
        Double d = numero(valor);
        return d == null ? null : (int) Math.round(d);
    }

    /** Suma de varios campos numéricos; null si ninguno tiene valor. */
    static Integer sumaEnteros(Map<String, Object> origen, String... campos) {
        Integer total = null;
        for (String c : campos) {
            Integer v = entero(origen.get(c));
            if (v != null) {
                total = (total == null ? 0 : total) + v;
            }
        }
        return total;
    }
}
