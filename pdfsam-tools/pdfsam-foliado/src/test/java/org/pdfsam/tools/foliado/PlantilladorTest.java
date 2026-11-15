/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de Mejora 5: el ejemplo de formato de folio se separa bien en sus piezas. */
class PlantilladorTest {

    @Test
    void ejemploSencilloSinCeros() {
        Plantillador.Resultado r = Plantillador.analizar("Folio 1");
        assertTrue(r.conNumero());
        assertEquals("Folio ", r.prefijo());
        assertEquals("", r.sufijo());
        assertEquals(1, r.digitos());
        assertFalse(r.conTotal());
        assertFalse(r.enLetras());
    }

    @Test
    void ejemploDeFabricaConCeros() {
        // de fábrica: "Folio 001" -> prefijo "Folio ", 3 dígitos, con ceros a la izquierda
        Plantillador.Resultado r = Plantillador.analizar("Folio 001");
        assertTrue(r.conNumero());
        assertEquals("Folio ", r.prefijo());
        assertEquals(3, r.digitos());
        assertFalse(r.conTotal());
        OpcionesFolio o = new OpcionesFolio();
        Plantillador.aplicar(r, o);
        assertEquals("Folio 001", Motor.textoFolio(1, o.prefijo, o.digitos, o.sufijo, null, o.conTotal, o.palabraDe));
        assertEquals("Folio 002", Motor.textoFolio(2, o.prefijo, o.digitos, o.sufijo, null, o.conTotal, o.palabraDe));
    }

    @Test
    void conTotalYSufijo() {
        Plantillador.Resultado r = Plantillador.analizar("FOLIO 1 DE 50 (expediente)");
        assertTrue(r.conNumero());
        assertEquals("FOLIO ", r.prefijo());
        assertEquals(1, r.digitos());
        assertTrue(r.conTotal());
        assertEquals("DE", r.palabraDe());
        assertEquals(" (expediente)", r.sufijo());
        assertEquals("FOLIO 1 DE 50 (expediente)",
                Motor.textoFolio(1, r.prefijo(), r.digitos(), r.sufijo(), 50L, r.conTotal(), r.palabraDe()));
    }

    @Test
    void conNumeroEnLetrasEntreCorchetes() {
        // el contenido del corchete no se usa; solo marca que también va en letras
        Plantillador.Resultado r = Plantillador.analizar("FOLIO 1 [UNO]");
        assertTrue(r.conNumero());
        assertTrue(r.enLetras());
        assertEquals("FOLIO ", r.prefijo());
        assertEquals("FOLIO ", r.textoLetras(), "el prefijo en letras es el mismo que el del número");
        assertEquals(1, r.digitos());
        assertEquals("FOLIO UNO", (r.textoLetras() + Motor.numeroALetras(1)).trim());
    }

    @Test
    void conTotalYLetrasJuntos() {
        // Motor no arma un "total en letras": lo del corchete se ignora, solo activa "en letras"
        Plantillador.Resultado r = Plantillador.analizar("FOLIO 1 DE 50 [UNO DE CINCUENTA]");
        assertTrue(r.conNumero());
        assertTrue(r.conTotal());
        assertTrue(r.enLetras());
        assertEquals("FOLIO ", r.prefijo());
        assertEquals("FOLIO ", r.textoLetras());
        assertEquals("DE", r.palabraDe());
        assertEquals("", r.sufijo());
    }

    @Test
    void corcheteSinNumeroSuelto() {
        // "FOLIO uno (1)": el corchete usa paréntesis en el ejemplo visual, pero esta clase busca
        // específicamente "[ ]"; con paréntesis el "(1)" cuenta como el número normal.
        Plantillador.Resultado r = Plantillador.analizar("FOLIO uno (1)");
        assertTrue(r.conNumero());
        assertFalse(r.enLetras());
        assertEquals("FOLIO uno (", r.prefijo());
        assertEquals(")", r.sufijo());
    }

    @Test
    void respetaMayusculasYMinusculas() {
        Plantillador.Resultado r = Plantillador.analizar("folio 007");
        assertEquals("folio ", r.prefijo());
        assertEquals(3, r.digitos());
    }

    @Test
    void sinNumeroNoTocaNada() {
        Plantillador.Resultado r = Plantillador.analizar("Expediente confidencial");
        assertFalse(r.conNumero());
        OpcionesFolio o = new OpcionesFolio();
        o.prefijo = "Sin tocar ";
        o.digitos = 9;
        Plantillador.aplicar(r, o);
        assertEquals("Sin tocar ", o.prefijo, "no se toca si no hay número en el ejemplo");
        assertEquals(9, o.digitos);
    }

    @Test
    void ejemploVacioNoRompeNada() {
        Plantillador.Resultado r = Plantillador.analizar("");
        assertFalse(r.conNumero());
        assertFalse(r.enLetras());
    }

    @Test
    void aplicarNoActivaLetrasSiNoHabiaCorchete() {
        Plantillador.Resultado r = Plantillador.analizar("Folio 1");
        OpcionesFolio o = new OpcionesFolio();
        o.enLetras = true;
        o.textoLetras = "No tocar";
        Plantillador.aplicar(r, o);
        assertTrue(o.enLetras, "aplicar no lo apaga si el ejemplo no trae corchete");
        assertEquals("No tocar", o.textoLetras);
    }

    @Test
    void vistaPreviaSinLetras() {
        String p = Plantillador.previsualizar("FOLIO 1 DE 50", 7, 50L);
        assertEquals("FOLIO 7 DE 50", p);
    }

    @Test
    void vistaPreviaConLetras() {
        String p = Plantillador.previsualizar("FOLIO 1 [UNO]", 7, null);
        assertEquals("FOLIO 7   [FOLIO " + Motor.numeroALetras(7) + "]", p);
    }
}
