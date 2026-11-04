/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import org.sejda.sambox.cos.COSName;
import org.sejda.sambox.pdmodel.PDDocument;
import org.sejda.sambox.pdmodel.PDPageContentStream;
import org.sejda.sambox.pdmodel.PDResources;
import org.sejda.sambox.pdmodel.common.PDRectangle;
import org.sejda.sambox.pdmodel.font.PDFont;
import org.sejda.sambox.pdmodel.font.PDTrueTypeFont;
import org.sejda.sambox.pdmodel.font.PDType1Font;
import org.sejda.sambox.pdmodel.font.encoding.WinAnsiEncoding;
import org.sejda.sambox.pdmodel.interactive.annotation.PDAnnotation;
import org.sejda.sambox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.sejda.sambox.pdmodel.interactive.annotation.PDAppearanceCharacteristicsDictionary;
import org.sejda.sambox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.sejda.sambox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.sejda.sambox.pdmodel.interactive.form.PDAcroForm;
import org.sejda.sambox.pdmodel.interactive.form.PDTextField;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Texto que se puede cambiar después en Adobe ("Rellenar y firmar"): en vez de escribirlo fijo en la hoja, cada
 * folio o leyenda se pone como una casilla de formulario que ya trae el texto, con la misma letra, tamaño, color y
 * posición. Se ve y se imprime igual. Las letras de la computadora se meten completas para que Adobe pueda escribir
 * con ellas; si una no se puede usar, se pasa a Helvetica (básica de Adobe) y sigue editable.
 */
final class Editables {
    /** Mientras se foliá o indexa un documento con la opción marcada. */
    static final ThreadLocal<Editables> ACTUAL = new ThreadLocal<>();

    /** De qué archivo .ttf salió cada letra (para meterla completa en las casillas). */
    private static final Map<PDFont, File> ARCHIVOS = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    @FunctionalInterface
    interface Trabajo {
        void hacer() throws IOException;
    }

    private final PDDocument doc;
    private final PDAcroForm form;
    private final String prefijo;
    private final Map<PDFont, PDFont> paraCasilla = new HashMap<>();
    private final Map<PDFont, COSName> nombres = new HashMap<>();
    private int n;

    private Editables(PDDocument doc, String que) {
        this.doc = doc;
        PDAcroForm f = doc.getDocumentCatalog().getAcroForm();
        if (f == null) {
            f = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(f);
        }
        if (f.getDefaultResources() == null) {
            f.setDefaultResources(new PDResources());
        }
        f.setNeedAppearances(false);
        this.form = f;
        // nombres que no choquen con otras casillas (ni con otro foliado anterior)
        this.prefijo = que + "_" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT) + "_";
    }

    static void recordar(PDFont letra, File archivo) {
        ARCHIVOS.put(letra, archivo);
    }

    /** Hace 'trabajo' con las casillas editables activas (si doc no es null). */
    static void con(PDDocument doc, String que, Trabajo trabajo) throws IOException {
        if (doc == null) {
            trabajo.hacer();
            return;
        }
        Editables anterior = ACTUAL.get();
        ACTUAL.set(new Editables(doc, que));
        try {
            trabajo.hacer();
        } finally {
            if (anterior == null) {
                ACTUAL.remove();
            } else {
                ACTUAL.set(anterior);
            }
        }
    }

    /** La letra para la casilla: las básicas tal cual; las de la computadora, completas (no solo las usadas). */
    private PDFont letraCasilla(PDFont letra) {
        if (letra instanceof PDType1Font) {
            return letra;
        }
        return paraCasilla.computeIfAbsent(letra, l -> {
            File f = ARCHIVOS.get(l);
            if (f != null) {
                try {
                    return PDTrueTypeFont.load(f, WinAnsiEncoding.INSTANCE);
                } catch (IOException | RuntimeException e) {
                    // no se pudo: abajo
                }
            }
            return null;
        });
    }

    private static boolean sirve(PDFont l, String texto) {
        try {
            l.encode(texto);
            return true;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }

    /**
     * Pone el texto como casilla en la hoja. (x, y) = inicio de la línea base, como se ve la hoja. Devuelve false si
     * no se pudo (entonces se escribe fijo, como siempre).
     */
    boolean campo(Motor.Hoja h, float x, float y, String texto, PDFont letra, float tamano, Color color) {
        try {
            PDFont l = letraCasilla(letra);
            if (l == null || !sirve(l, texto)) {
                // esa letra no se puede usar en la casilla: se pasa a una básica de Adobe (sigue editable)
                l = letra instanceof PDType1Font && letra.getName() != null && letra.getName().contains("Bold") ?
                        PDType1Font.HELVETICA_BOLD() : PDType1Font.HELVETICA();
                if (!sirve(l, texto)) {
                    return false; // un carácter que ni la básica tiene: se escribe fijo
                }
            }
            float ancho = Motor.anchoTexto(l, texto, tamano);
            float holgura = 2;
            float abajo = tamano * 0.30f, arriba = tamano * 0.95f;
            // la casilla, como se ve la hoja (y hacia abajo)
            float vx0 = x - holgura, vx1 = x + ancho + holgura * 6, vy0 = y - arriba, vy1 = y + abajo;
            float w = vx1 - vx0, alto = vy1 - vy0;
            float[][] esquinas = { h.real(vx0, vy0), h.real(vx1, vy0), h.real(vx1, vy1), h.real(vx0, vy1) };
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (float[] e : esquinas) {
                minX = Math.min(minX, e[0]);
                minY = Math.min(minY, e[1]);
                maxX = Math.max(maxX, e[0]);
                maxY = Math.max(maxY, e[1]);
            }

            PDResources dr = form.getDefaultResources();
            COSName nombre = nombres.get(l);
            if (nombre == null) {
                nombre = dr.add(l);
                nombres.put(l, nombre);
            }
            String colorDa = String.format(Locale.ROOT, "%.3f %.3f %.3f rg", color.getRed() / 255f,
                    color.getGreen() / 255f, color.getBlue() / 255f);
            String da = String.format(Locale.ROOT, "/%s %.2f Tf %s", nombre.getName(), tamano, colorDa);

            PDTextField campo = new PDTextField(form);
            campo.setPartialName(prefijo + (++n));
            campo.setDefaultAppearance(da);
            campo.setDoNotSpellCheck(true);
            campo.getCOSObject().setString(COSName.V, texto);
            campo.getCOSObject().setString(COSName.DV, texto);

            PDAnnotationWidget widget = campo.getWidgets().get(0);
            widget.setRectangle(new PDRectangle(minX, minY, maxX - minX, maxY - minY));
            widget.setPage(h.pagina);
            widget.setPrinted(true);
            // que Adobe escriba derecho también en las hojas giradas
            PDAppearanceCharacteristicsDictionary mk = new PDAppearanceCharacteristicsDictionary(
                    new org.sejda.sambox.cos.COSDictionary());
            mk.setRotation(h.giro);
            widget.setAppearanceCharacteristics(mk);

            // cómo se ve (igual que el texto fijo): la casilla mide w x alto, girada como la hoja
            PDAppearanceStream ap = new PDAppearanceStream();
            ap.setResources(new PDResources());
            ap.setBBox(new PDRectangle(0, 0, w, alto));
            float[] g = h.giroTexto();
            ap.setMatrix(new AffineTransform(g[0], g[1], g[2], g[3], 0, 0));
            try (PDPageContentStream cs = new PDPageContentStream(doc, ap)) {
                cs.beginMarkedContent(COSName.TX);
                cs.saveGraphicsState();
                cs.beginText();
                cs.setFont(l, tamano);
                cs.setNonStrokingColor(color);
                cs.newLineAtOffset(holgura, abajo);
                cs.showText(texto);
                cs.endText();
                cs.restoreGraphicsState();
                cs.endMarkedContent();
            }
            PDAppearanceDictionary apd = new PDAppearanceDictionary();
            apd.setNormalAppearance(ap);
            widget.setAppearance(apd);

            List<PDAnnotation> anotaciones = new ArrayList<>(h.pagina.getAnnotations());
            anotaciones.add(widget);
            h.pagina.setAnnotations(anotaciones);
            List<org.sejda.sambox.pdmodel.interactive.form.PDField> campos = new ArrayList<>(form.getFields());
            campos.add(campo);
            form.setFields(campos);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
