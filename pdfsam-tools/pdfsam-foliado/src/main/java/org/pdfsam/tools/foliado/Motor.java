/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import org.sejda.io.SeekableSources;
import org.sejda.sambox.cos.COSBase;
import org.sejda.sambox.cos.COSDictionary;
import org.sejda.sambox.cos.COSName;
import org.sejda.sambox.input.PDFParser;
import org.sejda.sambox.output.WriteOption;
import org.sejda.sambox.pdmodel.PDDocument;
import org.sejda.sambox.pdmodel.PDPage;
import org.sejda.sambox.pdmodel.PDPageContentStream;
import org.sejda.sambox.pdmodel.PDPageTree;
import org.sejda.sambox.pdmodel.common.PDRectangle;
import org.sejda.sambox.pdmodel.encryption.InvalidPasswordException;
import org.sejda.sambox.pdmodel.font.PDFont;
import org.sejda.sambox.pdmodel.font.PDFontDescriptor;
import org.sejda.sambox.pdmodel.font.PDType0Font;
import org.sejda.sambox.pdmodel.font.PDType1Font;
import org.sejda.sambox.rendering.PDFRenderer;
import org.sejda.sambox.util.Matrix;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El motor: pone el folio (Foliar) o las leyendas (Indexar) en las hojas. La vista previa usa
 * EXACTAMENTE las mismas funciones que al guardar, así lo que se ve es lo que queda.
 * Todas las medidas se calculan en la hoja tal como se VE (respetando el giro y el tamaño de cada hoja).
 */
public final class Motor {

    public static final List<String> INTEGRADAS = List.of("Helvetica", "Times", "Courier");
    /** 1 cm en puntos */
    public static final float CM = 28.35f;

    private Motor() {
    }

    // ------------------------------------------------------------------ errores
    public static class ErrorProtegido extends IOException {
        public ErrorProtegido(String m) {
            super(m);
        }
    }

    public static class ErrorDanado extends IOException {
        public ErrorDanado(String m, Throwable causa) {
            super(m, causa);
        }
    }

    // ------------------------------------------------------------------ abrir y guardar
    public static PDDocument abrir(File archivo, String clave) throws IOException {
        try {
            if (clave == null || clave.isEmpty()) {
                return PDFParser.parse(SeekableSources.seekableSourceFrom(archivo));
            }
            return PDFParser.parse(SeekableSources.seekableSourceFrom(archivo), clave);
        } catch (InvalidPasswordException e) {
            throw new ErrorProtegido(clave == null || clave.isEmpty() ?
                    "El PDF está protegido con contraseña." :
                    "La contraseña no es correcta.");
        } catch (IOException e) {
            String m = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            if (m.contains("password") || m.contains("encrypt")) {
                throw new ErrorProtegido("El PDF está protegido con contraseña.");
            }
            throw new ErrorDanado("El PDF parece dañado y no se pudo abrir: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ErrorDanado("El PDF parece dañado y no se pudo abrir: " + e.getMessage(), e);
        }
    }

    /** ¿Hace falta contraseña para abrirlo? */
    public static boolean pideClave(File archivo) {
        try (PDDocument d = abrir(archivo, null)) {
            return false;
        } catch (ErrorProtegido e) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static int contarHojas(File archivo, String clave) throws IOException {
        try (PDDocument d = abrir(archivo, clave)) {
            return d.getNumberOfPages();
        }
    }

    /**
     * Guarda en un archivo temporal al lado y después lo pone en su lugar: si algo falla, nunca queda un
     * PDF a medias. Nunca se escribe encima del archivo que se está leyendo.
     */
    public static void guardar(PDDocument doc, File salida) throws IOException {
        File carpeta = salida.getAbsoluteFile().getParentFile();
        if (carpeta != null) {
            Files.createDirectories(carpeta.toPath());
        }
        File tmp = File.createTempFile("pdfsam_", ".pdf.tmp", carpeta);
        try {
            doc.writeTo(tmp, WriteOption.COMPRESS_STREAMS, WriteOption.XREF_STREAM, WriteOption.OBJECT_STREAMS);
            Files.move(tmp.toPath(), salida.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }

    /** Guarda sin cambios (sirve para quitar la seguridad a un PDF con contraseña). */
    public static void copiarSinSeguridad(File entrada, File salida, String clave) throws IOException {
        File tmp = File.createTempFile("pdfsam_", ".pdf", salida.getAbsoluteFile().getParentFile());
        try {
            try (PDDocument d = abrir(entrada, clave)) {
                guardar(d, tmp);
            }
            Files.move(tmp.toPath(), salida.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }

    // ------------------------------------------------------------------ rangos de hojas
    /** "1-3, 5, 8-" -> [0,1,2,4,7..total-1]. Vacío = todas. */
    public static List<Integer> listaPaginas(String texto, int total) {
        List<Integer> r = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            for (int i = 0; i < total; i++) {
                r.add(i);
            }
            return r;
        }
        Set<Integer> vistas = new LinkedHashSet<>();
        Pattern rango = Pattern.compile("(\\d*)\\s*-\\s*(\\d*)");
        for (String trozo : texto.split("[;,]")) {
            trozo = trozo.trim();
            if (trozo.isEmpty()) {
                continue;
            }
            int a, b;
            Matcher m = rango.matcher(trozo);
            if (m.matches()) {
                a = m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
                b = m.group(2).isEmpty() ? total : Integer.parseInt(m.group(2));
            } else if (trozo.matches("\\d+")) {
                a = b = Integer.parseInt(trozo);
            } else {
                throw new IllegalArgumentException("No entiendo '" + trozo + "'. Use números como 1-3, 5, 8-10.");
            }
            if (a < 1 || b > total || a > b) {
                throw new IllegalArgumentException(
                        "El rango '" + trozo + "' no es válido: el PDF tiene " + total + " hojas.");
            }
            for (int i = a - 1; i < b; i++) {
                vistas.add(i);
            }
        }
        r.addAll(vistas);
        return r;
    }

    /** [0,1,2,4,7,8] -> "1-3, 5, 8-9" (hojas contadas desde 1). */
    public static String textoHojas(java.util.Collection<Integer> indices) {
        List<Integer> hojas = new ArrayList<>();
        for (Integer i : indices) {
            hojas.add(i + 1);
        }
        hojas.sort(Integer::compareTo);
        StringBuilder sb = new StringBuilder();
        Integer inicio = null, previo = null;
        for (Integer h : hojas) {
            if (inicio == null) {
                inicio = previo = h;
            } else if (h == previo + 1) {
                previo = h;
            } else {
                agregarTramo(sb, inicio, previo);
                inicio = previo = h;
            }
        }
        if (inicio != null) {
            agregarTramo(sb, inicio, previo);
        }
        return sb.toString();
    }

    private static void agregarTramo(StringBuilder sb, int a, int b) {
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(a == b ? String.valueOf(a) : a + "-" + b);
    }

    // ------------------------------------------------------------------ números en letras
    private static final String[] UNIDADES = { "CERO", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE",
            "OCHO", "NUEVE", "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISÉIS", "DIECISIETE",
            "DIECIOCHO", "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDÓS", "VEINTITRÉS", "VEINTICUATRO",
            "VEINTICINCO", "VEINTISÉIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE" };
    private static final String[] DECENAS = { "", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA",
            "OCHENTA", "NOVENTA" };
    private static final String[] CENTENAS = { "", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS",
            "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS" };

    private static String hasta999(int n) {
        if (n < 30) {
            return UNIDADES[n];
        }
        if (n < 100) {
            int d = n / 10, u = n % 10;
            return DECENAS[d] + (u != 0 ? " Y " + UNIDADES[u] : "");
        }
        if (n == 100) {
            return "CIEN";
        }
        int c = n / 100, resto = n % 100;
        return CENTENAS[c] + (resto != 0 ? " " + hasta999(resto) : "");
    }

    private static String apocope(String texto) {
        if (texto.endsWith("VEINTIUNO")) {
            return texto.substring(0, texto.length() - 9) + "VEINTIÚN";
        }
        if (texto.endsWith("UNO")) {
            return texto.substring(0, texto.length() - 3) + "UN";
        }
        return texto;
    }

    /** 3 -> "TRES", 123 -> "CIENTO VEINTITRÉS", 2021 -> "DOS MIL VEINTIUNO" (español, mayúsculas). */
    public static String numeroALetras(long n) {
        if (n < 0) {
            return "MENOS " + numeroALetras(-n);
        }
        if (n < 1000) {
            return hasta999((int) n);
        }
        if (n < 1000000) {
            int miles = (int) (n / 1000), resto = (int) (n % 1000);
            String texto = miles == 1 ? "MIL" : apocope(hasta999(miles)) + " MIL";
            return texto + (resto != 0 ? " " + hasta999(resto) : "");
        }
        long millones = n / 1000000, resto = n % 1000000;
        String texto = millones == 1 ? "UN MILLÓN" : apocope(numeroALetras(millones)) + " MILLONES";
        return texto + (resto != 0 ? " " + numeroALetras(resto) : "");
    }

    // ------------------------------------------------------------------ colores
    public static Color colorDeHex(String texto) {
        try {
            String t = texto.trim().replace("#", "");
            return new Color(Integer.parseInt(t.substring(0, 2), 16), Integer.parseInt(t.substring(2, 4), 16),
                    Integer.parseInt(t.substring(4, 6), 16));
        } catch (RuntimeException e) {
            return new Color(191, 0, 0);
        }
    }

    public static String hexDeColor(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    // ------------------------------------------------------------------ hoja tal como se ve
    /** Tamaños de hoja conocidos (en puntos, vertical): para mostrar "Carta · vertical". */
    private static final Object[][] TAMANOS = { { "Carta", 612, 792 }, { "Oficio", 612, 1008 }, { "A4", 595, 842 },
            { "A3", 842, 1191 }, { "Media carta", 396, 612 } };

    public static String describirHoja(float ancho, float alto) {
        String orientacion = ancho > alto ? "horizontal" : "vertical";
        float a = Math.min(ancho, alto), b = Math.max(ancho, alto);
        for (Object[] t : TAMANOS) {
            if (Math.abs(a - (int) t[1]) < 6 && Math.abs(b - (int) t[2]) < 6) {
                return t[0] + " · " + orientacion;
            }
        }
        return String.format(Locale.ROOT, "%.1f × %.1f cm", ancho / 72 * 2.54, alto / 72 * 2.54) + " · "
                + orientacion;
    }

    /**
     * La hoja como se ve: ancho, alto (con el giro aplicado) y cómo pasar de "como se ve" (origen arriba a la
     * izquierda, y hacia abajo) a las coordenadas reales del PDF.
     */
    public static final class Hoja {
        public final float ancho, alto;
        private final float a, b, c, d, e, f; // X = a*x + c*y + e ; Y = b*x + d*y + f

        public Hoja(PDPage p) {
            PDRectangle r = p.getCropBox();
            float llx = r.getLowerLeftX(), lly = r.getLowerLeftY(), w = r.getWidth(), h = r.getHeight();
            int giro = ((p.getRotation() % 360) + 360) % 360;
            if (giro == 90) {
                ancho = h;
                alto = w;
                a = 0;
                c = 1;
                e = llx;
                b = 1;
                d = 0;
                f = lly;
            } else if (giro == 180) {
                ancho = w;
                alto = h;
                a = -1;
                c = 0;
                e = llx + w;
                b = 0;
                d = 1;
                f = lly;
            } else if (giro == 270) {
                ancho = h;
                alto = w;
                a = 0;
                c = -1;
                e = llx + w;
                b = -1;
                d = 0;
                f = lly + h;
            } else {
                ancho = w;
                alto = h;
                a = 1;
                c = 0;
                e = llx;
                b = 0;
                d = -1;
                f = lly + h;
            }
        }

        float[] real(float x, float y) {
            return new float[] { a * x + c * y + e, b * x + d * y + f };
        }

        /** Matriz del texto: el renglón empieza en (x, y = línea base) y se lee derecho, como se ve. */
        Matrix matrizTexto(float x, float y) {
            float[] p = real(x, y);
            return new Matrix(a, b, -c, -d, p[0], p[1]);
        }
    }

    // ------------------------------------------------------------------ letras
    /** Las letras cargadas en un documento (para no cargarlas una vez por hoja). */
    public static final class Letras {
        private final PDDocument doc;
        private final Map<String, PDFont> cache = new HashMap<>();

        public Letras(PDDocument doc) {
            this.doc = doc;
        }

        /** fuente: "Helvetica", "Times", "Courier" o la ruta de un archivo .ttf de la computadora. */
        public PDFont de(String fuente, boolean negrita) throws IOException {
            String f = fuente == null || fuente.isBlank() ? "Helvetica" : fuente;
            String clave = f + "|" + negrita;
            PDFont r = cache.get(clave);
            if (r == null) {
                File archivo = new File(f);
                if (!INTEGRADAS.contains(f) && archivo.isFile()) {
                    try {
                        r = PDType0Font.load(doc, archivo);
                    } catch (IOException | RuntimeException e) {
                        r = PDType1Font.HELVETICA(); // la letra no se pudo usar: se queda la básica
                    }
                } else {
                    r = switch (f) {
                    case "Times" -> negrita ? PDType1Font.TIMES_BOLD() : PDType1Font.TIMES_ROMAN();
                    case "Courier" -> negrita ? PDType1Font.COURIER_BOLD() : PDType1Font.COURIER();
                    default -> negrita ? PDType1Font.HELVETICA_BOLD() : PDType1Font.HELVETICA();
                    };
                }
                cache.put(clave, r);
            }
            return r;
        }
    }

    /** Las letras básicas no tienen todos los caracteres: lo que no se puede escribir se cambia por "?". */
    static String limpiar(PDFont letra, String texto) {
        StringBuilder sb = new StringBuilder();
        texto.codePoints().forEach(cp -> {
            String s = new String(Character.toChars(cp));
            try {
                letra.encode(s);
                sb.append(s);
            } catch (IOException | IllegalArgumentException | IllegalStateException e) {
                sb.append('?');
            }
        });
        return sb.toString();
    }

    static float anchoTexto(PDFont letra, String texto, float tamano) {
        try {
            return letra.getStringWidth(texto) / 1000f * tamano;
        } catch (IOException | RuntimeException e) {
            return texto.length() * tamano * 0.55f;
        }
    }

    /** Altura de las mayúsculas (para centrar el renglón en el recuadro). */
    static float altoMayusculas(PDFont letra) {
        try {
            PDFontDescriptor fd = letra.getFontDescriptor();
            if (fd != null) {
                float cap = fd.getCapHeight();
                if (cap > 0) {
                    return cap / 1000f;
                }
                float asc = fd.getAscent();
                if (asc > 0) {
                    return asc / 1000f * 0.75f;
                }
            }
        } catch (RuntimeException e) {
            // se usa el valor normal
        }
        return 0.718f;
    }

    // ------------------------------------------------------------------ dibujar
    private static PDPageContentStream lienzo(PDDocument doc, PDPage p) throws IOException {
        // APPEND con resetContext: lo que ya tiene la hoja no cambia nuestras medidas
        return new PDPageContentStream(doc, p, PDPageContentStream.AppendMode.APPEND, true, true);
    }

    private static void texto(PDPageContentStream cs, Hoja h, float x, float y, String t, PDFont letra,
            float tamano, Color color) throws IOException {
        cs.beginText();
        cs.setFont(letra, tamano);
        cs.setNonStrokingColor(color);
        cs.setTextMatrix(h.matrizTexto(x, y));
        cs.showText(t);
        cs.endText();
    }

    private static void recuadro(PDPageContentStream cs, Hoja h, float x0, float y0, float x1, float y1,
            Color color) throws IOException {
        cs.setStrokingColor(color);
        cs.setLineWidth(0.8f);
        float[] p1 = h.real(x0, y0), p2 = h.real(x1, y0), p3 = h.real(x1, y1), p4 = h.real(x0, y1);
        cs.moveTo(p1[0], p1[1]);
        cs.lineTo(p2[0], p2[1]);
        cs.lineTo(p3[0], p3[1]);
        cs.lineTo(p4[0], p4[1]);
        cs.closeAndStroke();
    }

    // ------------------------------------------------------------------ foliar
    public static String textoFolio(long numero, String prefijo, int digitos, String sufijo, Long total,
            boolean conTotal, String palabraDe) {
        StringBuilder t = new StringBuilder(prefijo == null ? "" : prefijo).append(ceros(numero, digitos));
        if (conTotal && total != null && total > 0) {
            t.append(' ').append(palabraDe == null || palabraDe.isBlank() ? "de" : palabraDe.trim()).append(' ')
                    .append(ceros(total, digitos));
        }
        return t.append(sufijo == null ? "" : sufijo).toString();
    }

    private static String ceros(long n, int digitos) {
        String s = String.valueOf(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < digitos; i++) {
            sb.append('0');
        }
        return (n < 0 ? "-" : "") + sb + s;
    }

    /** Ejemplo para "Así queda: …". */
    public static String ejemploFolio(OpcionesFolio o) {
        String e = textoFolio(o.inicio, o.prefijo, o.digitos, o.sufijo, (long) Math.max(o.inicio, 120), o.conTotal,
                o.palabraDe);
        if (o.enLetras) {
            String letras = (nulo(o.textoLetras) + numeroALetras(o.inicio)).trim();
            e = o.conNumeros ? e + "  /  " + letras : letras;
        }
        return e;
    }

    private static String nulo(String s) {
        return s == null ? "" : s;
    }

    /** Las hojas que llevan folio, en orden (las omitidas con "Personalizar hojas" no llevan ni cuentan). */
    public static List<Integer> hojasFoliadas(int totalHojas, OpcionesFolio o, List<Integer> paginas,
            Map<Integer, ReglaHoja> porHoja) {
        List<Integer> r = new ArrayList<>();
        List<Integer> base = paginas;
        if (base == null) {
            base = new ArrayList<>();
            for (int i = 0; i < totalHojas; i++) {
                base.add(i);
            }
        }
        for (Integer i : base) {
            ReglaHoja regla = porHoja == null ? null : porHoja.get(i);
            if (i >= o.saltar && (regla == null || !regla.omitir)) {
                r.add(i);
            }
        }
        return r;
    }

    /**
     * Pone el folio en todo el documento. paginas = índices a foliar (null = todas).
     * avance(hechas, total) se llama cada pocas hojas.
     */
    public static void foliarDocumento(PDDocument doc, OpcionesFolio o, List<Integer> paginas,
            Map<Integer, ReglaHoja> porHoja, BiConsumer<Integer, Integer> avance) throws IOException {
        Letras letras = new Letras(doc);
        List<Integer> elegidas = hojasFoliadas(doc.getNumberOfPages(), o, paginas, porHoja);
        long total = o.inicio + elegidas.size() - 1;
        for (int n = 0; n < elegidas.size(); n++) {
            if (avance != null && n % 5 == 0) {
                avance.accept(n, elegidas.size());
            }
            int i = elegidas.get(n);
            foliarHoja(doc, doc.getPage(i), o.inicio + n, total, o, porHoja == null ? null : porHoja.get(i), letras);
        }
        if (avance != null) {
            avance.accept(elegidas.size(), elegidas.size());
        }
    }

    public static void foliarHoja(PDDocument doc, PDPage p, long numero, long total, OpcionesFolio o,
            ReglaHoja cambios, Letras letras) throws IOException {
        Color color = o.color;
        float tamano = o.tamano;
        String fuente = o.fuente;
        boolean negrita = o.negrita && INTEGRADAS.contains(o.fuente);
        boolean conRecuadro = o.recuadro;
        float dx = 0, dy = 0;
        if (cambios != null) {
            if (cambios.omitir) {
                return;
            }
            dx = cambios.dx;
            dy = cambios.dy;
            if (cambios.color != null) {
                color = cambios.color;
            }
            if (cambios.tamano != null && cambios.tamano > 0) {
                tamano = cambios.tamano;
            }
            if (cambios.recuadro != null) {
                conRecuadro = cambios.recuadro;
            }
            if (cambios.fuente != null) {
                fuente = cambios.fuente;
            }
            if (cambios.negrita != null) {
                negrita = cambios.negrita;
            }
            negrita = negrita && INTEGRADAS.contains(fuente);
        }
        PDFont letra = letras.de(fuente, negrita);
        Hoja h = new Hoja(p);
        String texto = limpiar(letra,
                textoFolio(numero, o.prefijo, o.digitos, o.sufijo, total, o.conTotal, o.palabraDe));
        String enLetras = limpiar(letra, (nulo(o.textoLetras) + numeroALetras(numero)).trim());
        float cap = altoMayusculas(letra) * tamano;
        try (PDPageContentStream cs = lienzo(doc, p)) {
            if (o.enLetras && o.conNumeros) {
                // dos renglones: el folio en números y, justo debajo, en letras (FOLIO UNO)
                float w1 = anchoTexto(letra, texto, tamano), w2 = anchoTexto(letra, enLetras, tamano);
                float ancho = Math.max(w1, w2) + 10, alto = tamano * 1.9f + tamano * 1.25f;
                float[] caja = caja(h, o, ancho, alto);
                float x0 = caja[0] + dx, y0 = caja[1] + dy;
                if (conRecuadro) {
                    recuadro(cs, h, x0, y0, x0 + ancho, y0 + alto, color);
                }
                String lado = o.libre ? "centro" : o.posicion;
                float base1 = y0 + tamano * 1.9f / 2 + cap / 2;
                String[] renglones = { texto, enLetras };
                float[] anchos = { w1, w2 };
                for (int k = 0; k < 2; k++) {
                    float x;
                    if (lado.endsWith("der")) {
                        x = x0 + ancho - 5 - anchos[k];
                    } else if (lado.endsWith("izq")) {
                        x = x0 + 5;
                    } else {
                        x = x0 + (ancho - anchos[k]) / 2;
                    }
                    texto(cs, h, x, base1 + k * tamano * 1.25f, renglones[k], letra, tamano, color);
                }
                return;
            }
            String unico = o.enLetras ? enLetras : texto; // solo en letras: un renglón "FOLIO UNO"
            float ancho = anchoTexto(letra, unico, tamano) + 10, alto = tamano * 1.9f;
            float[] caja = caja(h, o, ancho, alto);
            float x0 = caja[0] + dx, y0 = caja[1] + dy;
            if (conRecuadro) {
                recuadro(cs, h, x0, y0, x0 + ancho, y0 + alto, color);
            }
            // renglón centrado en el recuadro
            texto(cs, h, x0 + 5, y0 + alto / 2 + cap / 2, unico, letra, tamano, color);
        }
    }

    /** Esquina de arriba a la izquierda del recuadro del folio (en la hoja como se ve). */
    static float[] caja(Hoja h, OpcionesFolio o, float ancho, float alto) {
        float x0, y0;
        if (o.libre) {
            x0 = o.libreX * h.ancho - ancho / 2;
            y0 = o.libreY * h.alto - alto / 2;
            x0 = Math.min(Math.max(0, x0), h.ancho - ancho);
            y0 = Math.min(Math.max(0, y0), h.alto - alto);
            return new float[] { x0, y0 };
        }
        String pos = o.posicion;
        if (pos.endsWith("der")) {
            x0 = h.ancho - o.margen - ancho;
        } else if (pos.endsWith("izq")) {
            x0 = o.margen;
        } else {
            x0 = (h.ancho - ancho) / 2;
        }
        y0 = pos.startsWith("sup") ? o.margen : h.alto - o.margen - alto;
        return new float[] { x0, y0 };
    }

    public static void foliar(File entrada, File salida, String clave, OpcionesFolio o, List<Integer> paginas,
            Map<Integer, ReglaHoja> porHoja, BiConsumer<Integer, Integer> avance) throws IOException {
        try (PDDocument doc = abrir(entrada, clave)) {
            foliarDocumento(doc, o, paginas, porHoja, avance);
            guardar(doc, salida);
        }
    }

    // ------------------------------------------------------------------ indexar
    /** Texto de la leyenda superior derecha SIN la numeración. */
    public static String textoDerechoIndexar(String nombreSinExt, OpcionesIndexar o) {
        if ("libre".equals(o.derModo)) {
            return nulo(o.derTexto).trim();
        }
        String delim = o.delimitador == null || o.delimitador.isEmpty() ? " " : o.delimitador;
        if (nombreSinExt.contains(delim)) {
            String[] bloques = nombreSinExt.split(Pattern.quote(delim), -1);
            int k = o.bloque;
            return (k >= 0 && k < bloques.length ? bloques[k] : bloques[0]).trim();
        }
        return nombreSinExt;
    }

    /** "Página {pagina} de {total}" -> "Página 3 de 10". */
    public static String formatoNumeracion(String plantilla, long pagina, long total) {
        String p = plantilla == null || plantilla.isBlank() ? "{pagina}/{total}" : plantilla;
        return p.replace("{pagina}", String.valueOf(pagina)).replace("{total}", String.valueOf(total));
    }

    public static String sinExtension(String nombre) {
        int i = nombre.lastIndexOf('.');
        return i > 0 ? nombre.substring(0, i) : nombre;
    }

    public static void indexarHoja(PDDocument doc, PDPage p, long numero, long total, String nombreSinExt,
            OpcionesIndexar o, ReglaHoja cambios, Letras letras) throws IOException {
        Color color = o.color;
        float tam = o.tamano;
        String fuente = o.fuente;
        boolean negrita = o.negrita && INTEGRADAS.contains(o.fuente);
        float dx = 0, dy = 0;
        if (cambios != null) {
            if (cambios.omitir) {
                return;
            }
            dx = cambios.dx;
            dy = cambios.dy;
            if (cambios.color != null) {
                color = cambios.color;
            }
            if (cambios.tamano != null && cambios.tamano > 0) {
                tam = cambios.tamano;
            }
            if (cambios.fuente != null) {
                fuente = cambios.fuente;
            }
            if (cambios.negrita != null) {
                negrita = cambios.negrita;
            }
            negrita = negrita && INTEGRADAS.contains(fuente);
        }
        PDFont letra = letras.de(fuente, negrita);
        Hoja h = new Hoja(p);
        float lateral = o.lateral, yArriba = o.vertical, yAbajo = h.alto - o.vertical;
        boolean caja = o.recuadro;
        try (PDPageContentStream cs = lienzo(doc, p)) {
            if (o.izqOn && !nulo(o.izqTexto).isBlank()) {
                leyenda(cs, h, lateral + dx, yArriba + dy, limpiar(letra, o.izqTexto.trim()), letra, tam, color, caja);
            }
            if (o.abajoIzqOn && !nulo(o.abajoIzqTexto).isBlank()) {
                leyenda(cs, h, lateral + dx, yAbajo + dy, limpiar(letra, o.abajoIzqTexto.trim()), letra, tam, color,
                        caja);
            }
            if (o.derOn) {
                String parte = textoDerechoIndexar(nombreSinExt, o);
                String num = o.derNumeros ? numero + "/" + total : "";
                String t = ((parte.isEmpty() ? "" : parte) + (parte.isEmpty() || num.isEmpty() ? "" : " ") + num).trim();
                if (!t.isEmpty()) {
                    t = limpiar(letra, t);
                    leyenda(cs, h, h.ancho - lateral - anchoTexto(letra, t, tam) + dx, yArriba + dy, t, letra, tam,
                            color, caja);
                }
            }
            if (o.letrasOn) {
                // el número de hoja en letras, justo debajo de la leyenda de arriba a la derecha
                String t = limpiar(letra, (nulo(o.letrasTexto) + numeroALetras(numero)).trim());
                leyenda(cs, h, h.ancho - lateral - anchoTexto(letra, t, tam) + dx,
                        yArriba + tam * (caja ? 1.45f : 1.2f) + dy, t, letra, tam, color, caja);
            }
            if (o.extraOn) {
                String t = limpiar(letra, formatoNumeracion(o.extraFormato, numero, total));
                float x = "bottom_center".equals(o.extraPos) ?
                        (h.ancho - anchoTexto(letra, t, tam)) / 2 :
                        h.ancho - lateral - anchoTexto(letra, t, tam);
                leyenda(cs, h, x + dx, yAbajo + dy, t, letra, tam, color, caja);
            }
        }
    }

    /** Una leyenda (y, si se pidió, su recuadro). y = línea base del renglón. */
    private static void leyenda(PDPageContentStream cs, Hoja h, float x, float y, String t, PDFont letra, float tam,
            Color color, boolean caja) throws IOException {
        if (caja) {
            float w = anchoTexto(letra, t, tam), cap = altoMayusculas(letra) * tam;
            recuadro(cs, h, x - 4, y - cap - 4, x + w + 4, y + tam * 0.25f + 3, color);
        }
        texto(cs, h, x, y, t, letra, tam, color);
    }

    /**
     * Las hojas que llevan leyendas, en orden: las de 'paginas' (null = todas), sin las primeras 'saltar' y sin las
     * que se dejaron en blanco con "Personalizar hojas". La numeración 1/10 cuenta solo estas.
     */
    public static List<Integer> hojasIndexadas(int totalHojas, OpcionesIndexar o, List<Integer> paginas,
            Map<Integer, ReglaHoja> porHoja) {
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i < totalHojas; i++) {
            ReglaHoja regla = porHoja == null ? null : porHoja.get(i);
            if (i >= o.saltar && (paginas == null || paginas.contains(i)) && (regla == null || !regla.omitir)) {
                r.add(i);
            }
        }
        return r;
    }

    /** Como listaPaginas, pero sin error si el rango pasa del final (para muchos PDF de distinto largo). */
    public static List<Integer> listaPaginasFlexible(String texto, int total) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        List<Integer> r = new ArrayList<>();
        for (String trozo : texto.split("[;,]")) {
            trozo = trozo.trim();
            if (trozo.isEmpty()) {
                continue;
            }
            Matcher m = Pattern.compile("(\\d*)\\s*-\\s*(\\d*)").matcher(trozo);
            int a, b;
            if (m.matches()) {
                a = m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
                b = m.group(2).isEmpty() ? total : Integer.parseInt(m.group(2));
            } else if (trozo.matches("\\d+")) {
                a = b = Integer.parseInt(trozo);
            } else {
                throw new IllegalArgumentException("No entiendo '" + trozo + "'. Use números como 1-3, 5, 8-10.");
            }
            for (int i = Math.max(1, a); i <= Math.min(total, b); i++) {
                if (!r.contains(i - 1)) {
                    r.add(i - 1);
                }
            }
        }
        return r;
    }

    public static void indexar(File entrada, File salida, String clave, String nombreOriginal, OpcionesIndexar o,
            Map<Integer, ReglaHoja> porHoja, BiConsumer<Integer, Integer> avance) throws IOException {
        String nombre = sinExtension(new File(nombreOriginal == null ? entrada.getName() : nombreOriginal).getName());
        try (PDDocument doc = abrir(entrada, clave)) {
            Letras letras = new Letras(doc);
            List<Integer> elegidas = hojasIndexadas(doc.getNumberOfPages(), o,
                    listaPaginasFlexible(o.paginas, doc.getNumberOfPages()), porHoja);
            int total = elegidas.size();
            for (int n = 0; n < total; n++) {
                if (avance != null && n % 5 == 0) {
                    avance.accept(n, total);
                }
                int i = elegidas.get(n);
                indexarHoja(doc, doc.getPage(i), n + 1, total, nombre, o, porHoja == null ? null : porHoja.get(i),
                        letras);
            }
            if (avance != null) {
                avance.accept(total, total);
            }
            guardar(doc, salida);
        }
    }

    // ------------------------------------------------------------------ girar y ordenar
    /** Una hoja en la pantalla de Girar: de qué hoja del original viene y cuánto se giró (0, 90, 180, 270). */
    public record HojaGirada(int origen, int giro) {
    }

    private static final COSName[] HEREDABLES = { COSName.RESOURCES, COSName.MEDIA_BOX, COSName.CROP_BOX,
            COSName.ROTATE };

    /**
     * Guarda el PDF con las hojas en el orden de 'hojas' y con su giro. Las hojas que no están en la lista no se
     * guardan.
     */
    public static void girarYOrdenar(File entrada, File salida, String clave, List<HojaGirada> hojas,
            BiConsumer<Integer, Integer> avance) throws IOException {
        try (PDDocument doc = abrir(entrada, clave)) {
            int total = doc.getNumberOfPages();
            List<PDPage> originales = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                originales.add(doc.getPage(i));
            }
            boolean mismoOrden = hojas.size() == total;
            for (int k = 0; mismoOrden && k < total; k++) {
                mismoOrden = hojas.get(k).origen() == k;
            }
            if (!mismoOrden) {
                // lo que la hoja heredaba de las carpetas del PDF se le pone a ella misma, así no se pierde al moverla
                for (PDPage p : originales) {
                    for (COSName clave2 : HEREDABLES) {
                        if (p.getCOSObject().getItem(clave2) == null) {
                            COSBase v = PDPageTree.getInheritableAttribute(p.getCOSObject(), clave2);
                            if (v != null) {
                                p.getCOSObject().setItem(clave2, v);
                            }
                        }
                    }
                }
                for (PDPage p : originales) {
                    doc.getPages().remove(p);
                }
                for (HojaGirada h : hojas) {
                    doc.getPages().add(originales.get(h.origen()));
                }
            }
            int n = 0;
            for (HojaGirada h : hojas) {
                if (h.giro() % 360 != 0) {
                    PDPage p = originales.get(h.origen());
                    p.setRotation((((p.getRotation() + h.giro()) % 360) + 360) % 360);
                }
                if (avance != null && ++n % 20 == 0) {
                    avance.accept(n, hojas.size());
                }
            }
            guardar(doc, salida);
        }
    }

    /**
     * Una hoja del PDF que se va a armar: de qué archivo y qué hoja viene (archivo null = hoja en blanco del tamaño
     * dado) y cuánto se gira.
     */
    public record HojaArmada(File archivo, int pagina, int giro, float anchoBlanco, float altoBlanco) {
    }

    /**
     * Arma el PDF con las hojas en este orden: girar, quitar, duplicar, insertar hojas de otros PDF y hojas en blanco.
     * Se parte del PDF principal (así se conservan sus marcadores y datos) y se rehace su lista de hojas.
     */
    public static void armar(File principal, Map<File, String> claves, List<HojaArmada> hojas, File salida,
            BiConsumer<Integer, Integer> avance) throws IOException {
        if (hojas.isEmpty()) {
            throw new IOException("El PDF tiene que quedar con al menos una hoja.");
        }
        Map<File, PDDocument> abiertos = new HashMap<>();
        try {
            PDDocument doc = abrir(principal, claves.get(principal));
            abiertos.put(principal, doc);
            List<PDPage> originales = new ArrayList<>();
            for (PDPage p : doc.getPages()) {
                originales.add(p);
            }
            heredar(originales);
            for (PDPage p : originales) {
                doc.getPages().remove(p);
            }
            Map<String, Boolean> usadas = new HashMap<>();
            int n = 0;
            for (HojaArmada h : hojas) {
                PDPage hoja;
                if (h.archivo() == null) {
                    hoja = new PDPage(new PDRectangle(h.anchoBlanco(), h.altoBlanco()));
                } else {
                    PDDocument origen = abiertos.get(h.archivo());
                    if (origen == null) {
                        origen = abrir(h.archivo(), claves.get(h.archivo()));
                        abiertos.put(h.archivo(), origen);
                        List<PDPage> lista = new ArrayList<>();
                        for (PDPage p : origen.getPages()) {
                            lista.add(p);
                        }
                        heredar(lista);
                    }
                    PDPage base = origen == doc ? originales.get(h.pagina()) : origen.getPage(h.pagina());
                    String clave = h.archivo().getAbsolutePath() + "#" + h.pagina();
                    if (origen == doc && usadas.putIfAbsent(clave, true) == null) {
                        hoja = base; // la primera vez va la hoja misma
                    } else {
                        // copia (hoja duplicada o de otro PDF): comparte el contenido, no lo repite
                        COSDictionary copia = new COSDictionary(base.getCOSObject());
                        copia.removeItem(COSName.PARENT);
                        hoja = new PDPage(copia);
                    }
                }
                if (h.giro() % 360 != 0) {
                    hoja.setRotation((((hoja.getRotation() + h.giro()) % 360) + 360) % 360);
                }
                doc.getPages().add(hoja);
                if (avance != null && ++n % 20 == 0) {
                    avance.accept(n, hojas.size());
                }
            }
            guardar(doc, salida);
        } finally {
            for (PDDocument d : abiertos.values()) {
                try {
                    d.close();
                } catch (IOException e) {
                    // nada
                }
            }
        }
    }

    /** Lo que la hoja heredaba de las "carpetas" internas del PDF se le pone a ella misma (así no se pierde). */
    private static void heredar(List<PDPage> paginas) {
        for (PDPage p : paginas) {
            for (COSName clave : HEREDABLES) {
                if (p.getCOSObject().getItem(clave) == null) {
                    COSBase v = PDPageTree.getInheritableAttribute(p.getCOSObject(), clave);
                    if (v != null) {
                        p.getCOSObject().setItem(clave, v);
                    }
                }
            }
        }
    }

    /** Gira TODAS las hojas (para carpetas y ZIP). grados: 90, -90 o 180. */
    public static void girarTodo(File entrada, File salida, String clave, int grados) throws IOException {
        try (PDDocument doc = abrir(entrada, clave)) {
            for (PDPage p : doc.getPages()) {
                p.setRotation((((p.getRotation() + grados) % 360) + 360) % 360);
            }
            guardar(doc, salida);
        }
    }

    // ------------------------------------------------------------------ vista previa
    /** Algo que se dibuja en una hoja (folio o leyendas). */
    @FunctionalInterface
    public interface Dibujo {
        void aplicar(PDDocument doc, PDPage hoja, Letras letras) throws IOException;
    }

    /**
     * Un PDF abierto solo para la vista previa: nunca se guarda. Se dibuja en la hoja, se hace la imagen y se
     * deja la hoja como estaba (así cada cambio de opción se ve al instante).
     */
    public static final class Previa implements AutoCloseable {
        private final PDDocument doc;
        private final Letras letras;

        public Previa(File archivo, String clave) throws IOException {
            this.doc = abrir(archivo, clave);
            this.letras = new Letras(doc);
        }

        public int hojas() {
            return doc.getNumberOfPages();
        }

        private final Map<Integer, float[]> tamanos = new java.util.concurrent.ConcurrentHashMap<>();

        /** Se mide cada hoja solo cuando se mira (rápido con miles de hojas). */
        public float[] tamano(int i) {
            float[] t = tamanos.get(i);
            if (t == null) {
                synchronized (this) {
                    Hoja h = new Hoja(doc.getPage(i));
                    t = new float[] { h.ancho, h.alto };
                }
                tamanos.put(i, t);
            }
            return t;
        }

        public synchronized BufferedImage imagen(int i, int anchoPx, Dibujo dibujo) throws IOException {
            PDPage p = doc.getPage(i);
            COSBase contenido = p.getCOSObject().getItem(COSName.CONTENTS);
            try {
                if (dibujo != null) {
                    dibujo.aplicar(doc, p, letras);
                }
                Hoja h = new Hoja(p);
                float escala = Math.max(0.05f, anchoPx / Math.max(1f, h.ancho));
                return new PDFRenderer(doc).renderImage(i, escala);
            } finally {
                if (contenido == null) {
                    p.getCOSObject().removeItem(COSName.CONTENTS);
                } else {
                    p.getCOSObject().setItem(COSName.CONTENTS, contenido);
                }
            }
        }

        @Override
        public synchronized void close() throws IOException {
            doc.close();
        }
    }
}
