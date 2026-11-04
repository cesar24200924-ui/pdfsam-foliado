/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/** Recuerda las últimas opciones elegidas (un archivo de texto en la carpeta del usuario). */
final class Config {
    private static final File ARCHIVO = new File(System.getProperty("user.home"),
            ".pdfsam-foliado" + File.separator + "opciones.properties");
    private static Properties datos;

    private Config() {
    }

    private static synchronized Properties datos() {
        if (datos == null) {
            datos = new Properties();
            if (ARCHIVO.isFile()) {
                try (InputStream in = new FileInputStream(ARCHIVO)) {
                    datos.load(in);
                } catch (IOException e) {
                    // si no se puede leer, se empieza con lo normal
                }
            }
        }
        return datos;
    }

    static synchronized String texto(String clave, String normal) {
        return datos().getProperty(clave, normal);
    }

    static boolean si(String clave, boolean normal) {
        return Boolean.parseBoolean(texto(clave, String.valueOf(normal)));
    }

    static float numero(String clave, float normal) {
        try {
            return Float.parseFloat(texto(clave, String.valueOf(normal)));
        } catch (NumberFormatException e) {
            return normal;
        }
    }

    static synchronized void poner(String clave, Object valor) {
        datos().setProperty(clave, String.valueOf(valor));
    }

    static synchronized void guardar() {
        try {
            ARCHIVO.getParentFile().mkdirs();
            try (OutputStream out = new FileOutputStream(ARCHIVO)) {
                datos().store(out, "Foliar e Indexar");
            }
        } catch (IOException e) {
            // no es grave: la próxima vez empieza con lo normal
        }
    }
}
