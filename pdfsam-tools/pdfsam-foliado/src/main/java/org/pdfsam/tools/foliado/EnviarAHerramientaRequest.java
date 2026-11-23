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
import java.util.List;
import java.util.Map;

/**
 * Evento para mandar la lista completa de archivos de Indexar, Foliar o Girar a otra de esas tres pantallas
 * (botones "Arreglar en Girar", "Ir a Indexar", "Ir a Foliar"), sin tener que agregarlos de nuevo a mano.
 * <p>
 * La pantalla que recibe este evento borra lo que ya tuviera cargado y queda solo con esta lista nueva (sin
 * avisar), sin importar cómo se haya llegado a ella. {@code destino} es el {@code TOOL_ID} de la pantalla que
 * debe recibir los archivos ({@link IndexarTool#TOOL_ID}, {@link FoliarTool#TOOL_ID} o {@link GirarTool#TOOL_ID}).
 * <p>
 * {@code relativas} y {@code origenes} son para que, si alguno de los archivos venía de un comprimido (ZIP, RAR,
 * 7z) abierto en Girar, esa procedencia no se pierda al pasar a Indexar: Indexar arma igual un solo comprimido de
 * salida con la misma estructura de carpetas, en vez de dejar los PDF sueltos. {@code relativas}: para los
 * archivos que venían de un comprimido, su ruta relativa dentro de él (empezando por la "carpeta marcada" del
 * comprimido de origen). {@code origenes}: un registro por cada comprimido de origen distinto representado en
 * {@code archivos}. Vacíos (como con el constructor de un solo argumento extra) para un envío normal, sin
 * comprimidos de por medio.
 */
record EnviarAHerramientaRequest(String destino, List<File> archivos, Map<File, String> relativas,
        List<Origen> origenes) {

    /** Un comprimido de origen (igual a los "Abierto" de Girar/Indexar, pero compartido entre pantallas). */
    record Origen(File archivo, Lote lote, String marcada) {
    }

    EnviarAHerramientaRequest(String destino, List<File> archivos) {
        this(destino, archivos, Map.of(), List.of());
    }
}
