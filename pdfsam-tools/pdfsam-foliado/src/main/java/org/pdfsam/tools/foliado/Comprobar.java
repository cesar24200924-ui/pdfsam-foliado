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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Revisión del programa ya armado (portable o instalado): abre un comprimido con el motor de 7-Zip y dice cuántos
 * archivos trae. Se usa al armar el programa, para comprobar que los RAR, 7z... se abren igual que en la computadora
 * del usuario. Uso: java -m org.pdfsam.tools.foliado/org.pdfsam.tools.foliado.Comprobar archivo [contraseña]
 */
public final class Comprobar {
    private Comprobar() {
    }

    public static void main(String[] args) throws Exception {
        File archivo = new File(args[0]);
        String clave = args.length > 1 ? args[1] : null;
        Path destino = Files.createTempDirectory("comprobar_");
        Comprimidos.extraer(archivo.toPath(), destino, (n, mala) -> mala ? null : clave);
        long archivos;
        try (Stream<Path> s = Files.walk(destino)) {
            archivos = s.filter(Files::isRegularFile).count();
        }
        System.out.println("OK: " + archivo.getName() + " -> " + archivos + " archivos");
        Lote.borrar(destino);
        if (archivos == 0) {
            System.exit(2);
        }
    }
}
