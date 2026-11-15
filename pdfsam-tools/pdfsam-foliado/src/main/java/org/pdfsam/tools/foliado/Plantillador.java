/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mejora 5: el usuario escribe un EJEMPLO de cómo quiere el folio (p.ej. "FOLIO 1 [UNO]",
 * "FOLIO 1 DE 50 [UNO DE CINCUENTA]", "Folio uno (001)") y esta clase lo separa en las piezas
 * que ya entiende {@link OpcionesFolio}: prefijo, sufijo, cuántos dígitos (ceros a la
 * izquierda), si lleva el total y con qué palabra, y si además lleva el número en letras.
 * <p>
 * No cambia en nada cómo se dibuja el folio en la hoja (eso lo sigue haciendo {@link Motor} con
 * las mismas piezas de siempre): solo traduce un ejemplo de texto a esas piezas. El número de
 * inicio NUNCA sale de aquí, siempre de la casilla "Empezar en".
 * <p>
 * Reglas:
 * <ol>
 * <li>Si el ejemplo trae un tramo entre corchetes {@code [ ... ]}, eso es el número en letras:
 * se activa "en letras" y el texto antes del corchete (tal como se escribió, respetando
 * mayúsculas y minúsculas) queda como su prefijo.</li>
 * <li>De lo que queda (sin el corchete), el primer tramo de dígitos seguidos es "el número": su
 * cantidad de dígitos es cuántos dígitos va a tener el folio (p.ej. "001" = 3 dígitos, con ceros
 * a la izquierda; "1" = 1 dígito, sin ceros). Lo que hay ANTES es el prefijo; lo que hay DESPUÉS,
 * el sufijo (o el total, ver la regla 3).</li>
 * <li>Si justo después del número sigue una palabra y otro número (p.ej. "DE 50"), eso es el
 * total: se activa "con total" y se guarda la palabra tal como se escribió ("DE", "de"...).</li>
 * </ol>
 */
final class Plantillador {
    private Plantillador() {
    }

    /** Lo que se pudo entender del ejemplo. Los campos en null/false "no detectado" quieren decir:
     * no toque ese valor de OpcionesFolio, déjelo como está. */
    record Resultado(boolean conNumero, String prefijo, String sufijo, int digitos, boolean conTotal,
            String palabraDe, boolean enLetras, String textoLetras) {
    }

    private static final Pattern CORCHETE = Pattern.compile("\\[([^\\[\\]]*)]");
    private static final Pattern NUMERO = Pattern.compile("\\d+");
    /** "DE 50", "de 50"... la palabra puede ser cualquier cosa, no solo "de" (p.ej. otro idioma). */
    private static final Pattern TOTAL = Pattern.compile("^(\\s*\\S+\\s+)\\d+(.*)$", Pattern.DOTALL);

    static Resultado analizar(String ejemplo) {
        if (ejemplo == null) {
            return new Resultado(false, "", "", 0, false, "de", false, null);
        }
        String texto = ejemplo;
        boolean enLetras = false;
        String textoLetras = null;
        Matcher mc = CORCHETE.matcher(texto);
        if (mc.find()) {
            enLetras = true;
            textoLetras = texto.substring(0, mc.start());
            // por si el corchete no queda al final: lo de después se pega sin el corchete
            texto = (texto.substring(0, mc.start()) + " " + texto.substring(mc.end())).trim();
            if (textoLetras.isEmpty()) {
                textoLetras = ""; // corchete solo, sin texto antes: "[UNO]" -> prefijo vacío en letras
            }
        }
        Matcher mn = NUMERO.matcher(texto);
        if (!mn.find()) {
            // no hay ningún número suelto: todo es texto fijo (prefijo), no se toca lo demás
            return new Resultado(false, texto, "", 0, false, "de", enLetras, textoLetras);
        }
        String prefijo = texto.substring(0, mn.start());
        int digitos = mn.group().length();
        String resto = texto.substring(mn.end());
        boolean conTotal = false;
        String palabraDe = "de";
        String sufijo = resto;
        Matcher mt = TOTAL.matcher(resto);
        if (mt.matches()) {
            conTotal = true;
            palabraDe = mt.group(1).trim();
            sufijo = mt.group(2);
        }
        return new Resultado(true, prefijo, sufijo, digitos, conTotal, palabraDe, enLetras, textoLetras);
    }

    /** Aplica lo que se entendió a unas opciones (solo lo detectado; lo demás queda como estaba). */
    static void aplicar(Resultado r, OpcionesFolio o) {
        if (r.conNumero()) {
            o.prefijo = r.prefijo();
            o.sufijo = r.sufijo();
            o.digitos = r.digitos();
            o.conTotal = r.conTotal();
            o.palabraDe = r.palabraDe();
        }
        if (r.enLetras()) {
            o.enLetras = true;
            o.textoLetras = r.textoLetras();
        }
    }

    /** Para que el usuario vea cómo quedaría, con un número y un total de ejemplo (la vista previa de
     * la Mejora 5; el número real de verdad siempre sale de "Empezar en", esto es solo para mostrar). */
    static String previsualizar(String ejemplo, long numeroDeEjemplo, Long totalDeEjemplo) {
        Resultado r = analizar(ejemplo);
        OpcionesFolio o = new OpcionesFolio();
        aplicar(r, o);
        String principal = Motor.textoFolio(numeroDeEjemplo, o.prefijo, o.digitos, o.sufijo, totalDeEjemplo,
                o.conTotal, o.palabraDe);
        if (o.enLetras) {
            String enLetras = ((o.textoLetras == null ? "" : o.textoLetras) + Motor.numeroALetras(numeroDeEjemplo))
                    .trim();
            return principal + "   [" + enLetras + "]";
        }
        return principal;
    }
}
