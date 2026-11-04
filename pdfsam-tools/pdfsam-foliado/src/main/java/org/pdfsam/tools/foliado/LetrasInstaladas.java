/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import org.apache.fontbox.ttf.NamingTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;

/** Las letras de la computadora: {"Arial": ruta, "Arial Negrita": ruta, ...}. Se buscan una sola vez. */
final class LetrasInstaladas {
    private static volatile Map<String, String> cache;

    private LetrasInstaladas() {
    }

    static Map<String, String> todas() {
        Map<String, String> r = cache;
        if (r == null) {
            synchronized (LetrasInstaladas.class) {
                if (cache == null) {
                    cache = buscar();
                }
                r = cache;
            }
        }
        return r;
    }

    private static Map<String, String> buscar() {
        Map<String, String> resultado = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Deque<File> carpetas = new ArrayDeque<>();
        String win = System.getenv("WINDIR");
        if (win != null) {
            carpetas.add(new File(win, "Fonts"));
        } else if (new File("C:\\Windows\\Fonts").isDirectory()) {
            carpetas.add(new File("C:\\Windows\\Fonts"));
        }
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) {
            carpetas.add(new File(local, "Microsoft" + File.separator + "Windows" + File.separator + "Fonts"));
        }
        carpetas.add(new File("/usr/share/fonts"));
        carpetas.add(new File(System.getProperty("user.home"), ".fonts"));
        carpetas.add(new File("/Library/Fonts"));
        while (!carpetas.isEmpty()) {
            File c = carpetas.poll();
            File[] hijos = c.listFiles();
            if (hijos == null) {
                continue;
            }
            for (File f : hijos) {
                if (f.isDirectory()) {
                    carpetas.add(f);
                } else if (f.getName().toLowerCase().endsWith(".ttf")) {
                    String nombre = nombreDe(f);
                    if (nombre != null && !Motor.INTEGRADAS.contains(nombre)) {
                        resultado.putIfAbsent(nombre, f.getAbsolutePath());
                    }
                }
            }
        }
        return resultado;
    }

    private static String nombreDe(File f) {
        try (TrueTypeFont ttf = new TTFParser().parse(f)) {
            NamingTable n = ttf.getNaming();
            if (n == null || n.getFontFamily() == null) {
                return null;
            }
            String familia = n.getFontFamily().trim();
            String estilo = n.getFontSubFamily() == null ? "" : n.getFontSubFamily().trim();
            String extra = switch (estilo) {
            case "", "Regular", "Normal", "Roman" -> "";
            case "Bold" -> " Negrita";
            case "Italic", "Oblique" -> " Cursiva";
            case "Bold Italic", "Bold Oblique" -> " Negrita Cursiva";
            default -> " " + estilo;
            };
            return (familia + extra).trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** De nombre (como se ve en la lista) a lo que entiende el motor. */
    static String rutaDe(String nombre) {
        if (nombre == null || Motor.INTEGRADAS.contains(nombre)) {
            return nombre == null ? "Helvetica" : nombre;
        }
        Map<String, String> t = cache;
        String r = t == null ? null : t.get(nombre);
        return r == null ? "Helvetica" : r;
    }

    /** De archivo a nombre (para volver a marcar en la lista la letra guardada). */
    static String nombreDeRuta(String ruta) {
        if (ruta == null || Motor.INTEGRADAS.contains(ruta)) {
            return ruta == null ? "Helvetica" : ruta;
        }
        Map<String, String> t = cache;
        if (t != null) {
            for (Map.Entry<String, String> e : t.entrySet()) {
                if (e.getValue().equalsIgnoreCase(ruta)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }
}
