/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import java.awt.Color;

/**
 * "Personalizar hojas": cambios solo para una hoja. Lo que queda en null usa lo general.
 * dx, dy = cuánto se movió (en puntos, en la hoja como se ve).
 */
public class ReglaHoja {
    public boolean omitir = false;
    public float dx = 0, dy = 0;
    public Color color;
    public Float tamano;
    /** nombre de la letra tal como se ve en la lista (se cambia por el archivo al procesar) */
    public String fuente;
    public Boolean negrita;
    public Boolean recuadro;

    public ReglaHoja copia() {
        ReglaHoja r = new ReglaHoja();
        r.omitir = omitir;
        r.dx = dx;
        r.dy = dy;
        r.color = color;
        r.tamano = tamano;
        r.fuente = fuente;
        r.negrita = negrita;
        r.recuadro = recuadro;
        return r;
    }
}
