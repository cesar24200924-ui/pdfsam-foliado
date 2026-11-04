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

/** Las opciones de Indexar PDF (Indexador de PDFs v3). */
public class OpcionesIndexar {
    public boolean izqOn = true;
    public String izqTexto = "DPOT";
    public boolean abajoIzqOn = false;
    public String abajoIzqTexto = "";
    public boolean derOn = true;
    /** "delimitador" = parte del nombre del archivo; "libre" = texto libre */
    public String derModo = "delimitador";
    public String delimitador = " ";
    public int bloque = 0;
    public String derTexto = "";
    public boolean derNumeros = true;
    public boolean letrasOn = false;
    public String letrasTexto = "FOLIO ";
    public boolean extraOn = false;
    /** bottom_right o bottom_center */
    public String extraPos = "bottom_right";
    public String extraFormato = "{pagina}/{total}";
    public float tamano = 20;
    public Color color = new Color(255, 0, 0);
    public String fuente = "Helvetica";
    public boolean negrita = false;
    /** Más opciones: recuadro, distancia de los lados y de arriba/abajo (en puntos), hojas sin leyendas al inicio y
     * hojas a indexar ("3-50"; vacío = todas). */
    public boolean recuadro = false;
    public float lateral = 1.8f * Motor.CM;
    public float vertical = 0.80f * Motor.CM;
    public int saltar = 0;
    public String paginas = "";
}
