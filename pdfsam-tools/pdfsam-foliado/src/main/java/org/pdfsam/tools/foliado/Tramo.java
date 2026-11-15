/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

/**
 * Mejora 6: un "tramo" de una leyenda de Indexar. Cada leyenda ({@link Motor#LEYENDAS}) puede
 * tener su propia lista de tramos en {@link OpcionesIndexar#tramos}: rangos de hojas (contadas
 * entre las que SÍ se indexan, 1 = la primera) con su propio texto y su propia numeración.
 * <p>
 * Si la lista de tramos de una leyenda está vacía, esa leyenda sigue funcionando exactamente
 * como siempre (su texto fijo en todas las hojas). Si tiene tramos, cada hoja usa el tramo cuyo
 * rango la cubre; las hojas que no caen en NINGÚN tramo se quedan SIN esa leyenda (de fábrica,
 * según la Mejora 6).
 */
public final class Tramo {
    /** Primera y última hoja del tramo (1 = la primera hoja que se indexa), ambas incluidas. */
    public int desde = 1;
    public int hasta = 1;
    /** Texto fijo de este tramo (reemplaza, solo dentro del tramo, el texto fijo de la leyenda). */
    public String texto = "";
    /** Con qué número empieza a contar este tramo (su propia numeración, aparte de las demás). */
    public int empezarEn = 1;

    public Tramo copia() {
        Tramo t = new Tramo();
        t.desde = desde;
        t.hasta = hasta;
        t.texto = texto;
        t.empezarEn = empezarEn;
        return t;
    }
}
