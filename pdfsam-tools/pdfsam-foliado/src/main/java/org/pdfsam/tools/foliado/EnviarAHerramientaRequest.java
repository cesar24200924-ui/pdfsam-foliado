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

/**
 * Evento para mandar la lista completa de archivos de Indexar, Foliar o Girar a otra de esas tres pantallas
 * (botones "Arreglar en Girar", "Ir a Indexar", "Ir a Foliar"), sin tener que agregarlos de nuevo a mano.
 * <p>
 * La pantalla que recibe este evento borra lo que ya tuviera cargado y queda solo con esta lista nueva (sin
 * avisar), sin importar cómo se haya llegado a ella. {@code destino} es el {@code TOOL_ID} de la pantalla que
 * debe recibir los archivos ({@link IndexarTool#TOOL_ID}, {@link FoliarTool#TOOL_ID} o {@link GirarTool#TOOL_ID}).
 */
record EnviarAHerramientaRequest(String destino, List<File> archivos) {
}
