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

/** Las opciones generales de Foliar. */
public class OpcionesFolio {
    public String prefijo = "Folio ";
    public long inicio = 1;
    public int digitos = 3;
    public float tamano = 11;
    public boolean negrita = true;
    public float margen = 24;
    public boolean recuadro = false;
    public int saltar = 0;
    public boolean conTotal = false;
    /** sup_izq, sup_centro, sup_der, inf_izq, inf_centro, inf_der */
    public String posicion = "sup_der";
    /** Posición libre: centro del folio en fracciones del ancho y alto de la hoja (0..1). */
    public boolean libre = false;
    public float libreX = 0.85f, libreY = 0.05f;
    public String sufijo = "";
    public String palabraDe = "de";
    public boolean conNumeros = true;
    public boolean enLetras = false;
    public String textoLetras = "FOLIO ";
    public Color color = new Color(191, 0, 0);
    /** "Helvetica", "Times", "Courier" o la ruta de una letra .ttf */
    public String fuente = "Helvetica";

    public OpcionesFolio copia() {
        OpcionesFolio o = new OpcionesFolio();
        o.prefijo = prefijo;
        o.inicio = inicio;
        o.digitos = digitos;
        o.tamano = tamano;
        o.negrita = negrita;
        o.margen = margen;
        o.recuadro = recuadro;
        o.saltar = saltar;
        o.conTotal = conTotal;
        o.posicion = posicion;
        o.libre = libre;
        o.libreX = libreX;
        o.libreY = libreY;
        o.sufijo = sufijo;
        o.palabraDe = palabraDe;
        o.conNumeros = conNumeros;
        o.enLetras = enLetras;
        o.textoLetras = textoLetras;
        o.color = color;
        o.fuente = fuente;
        return o;
    }
}
