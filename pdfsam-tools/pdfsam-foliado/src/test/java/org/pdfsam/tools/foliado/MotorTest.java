/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sejda.sambox.pdmodel.PDDocument;
import org.sejda.sambox.pdmodel.PDPage;
import org.sejda.sambox.pdmodel.PDPageContentStream;
import org.sejda.sambox.pdmodel.common.PDRectangle;
import org.sejda.sambox.pdmodel.font.PDType1Font;
import org.sejda.sambox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del motor: folio, leyendas, hojas giradas, letras en español, carpetas y ZIP. */
class MotorTest {
    /** Las imágenes de prueba quedan aquí para revisarlas a ojo. */
    static final File SALIDA = new File("target/previas");

    @BeforeAll
    static void preparar() {
        SALIDA.mkdirs();
    }

    /** Un PDF de prueba: carta vertical, carta girada 90, oficio horizontal (girada 270), A4 girada 180. */
    static File pdfDePrueba(File carpeta, String nombre) throws Exception {
        File f = new File(carpeta, nombre);
        try (PDDocument d = new PDDocument()) {
            Object[][] hojas = { { 612f, 792f, 0 }, { 612f, 792f, 90 }, { 612f, 1008f, 270 }, { 595f, 842f, 180 },
                    { 612f, 792f, 0 } };
            int k = 1;
            for (Object[] h : hojas) {
                PDPage p = new PDPage(new PDRectangle((float) h[0], (float) h[1]));
                p.setRotation((int) h[2]);
                d.addPage(p);
                try (PDPageContentStream cs = new PDPageContentStream(d, p)) {
                    cs.setStrokingColor(0.6f, 0.6f, 0.6f);
                    cs.addRect(20, 20, (float) h[0] - 40, (float) h[1] - 40);
                    cs.stroke();
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA(), 30);
                    cs.newLineAtOffset(150, 400);
                    cs.showText("HOJA " + k++ + " giro " + h[2]);
                    cs.endText();
                }
            }
            d.writeTo(f);
        }
        return f;
    }

    static String texto(File pdf) throws Exception {
        try (PDDocument d = Motor.abrir(pdf, null)) {
            return new PDFTextStripper().getText(d);
        }
    }

    static void imagenes(File pdf, String prefijo) throws Exception {
        try (Motor.Previa p = new Motor.Previa(pdf, null)) {
            for (int i = 0; i < p.hojas(); i++) {
                BufferedImage b = p.imagen(i, 500, null);
                ImageIO.write(b, "png", new File(SALIDA, prefijo + "_hoja" + (i + 1) + ".png"));
            }
        }
    }

    @Test
    void letras() {
        assertEquals("UNO", Motor.numeroALetras(1));
        assertEquals("VEINTIUNO", Motor.numeroALetras(21));
        assertEquals("CIENTO VEINTITRÉS", Motor.numeroALetras(123));
        assertEquals("CIEN", Motor.numeroALetras(100));
        assertEquals("DOS MIL VEINTIUNO", Motor.numeroALetras(2021));
        assertEquals("VEINTIÚN MIL", Motor.numeroALetras(21000));
        assertEquals("UN MILLÓN DOSCIENTOS MIL", Motor.numeroALetras(1200000));
        assertEquals("TREINTA Y UN MIL QUINIENTOS", Motor.numeroALetras(31500));
    }

    @Test
    void textos() {
        assertEquals("Folio 007", Motor.textoFolio(7, "Folio ", 3, "", 120L, false, "de"));
        assertEquals("FOLIO 007 DE 120 - SAR", Motor.textoFolio(7, "FOLIO ", 3, " - SAR", 120L, true, "DE"));
        assertEquals("1-3, 5, 8-9", Motor.textoHojas(List.of(0, 1, 2, 4, 7, 8)));
        assertEquals(List.of(0, 1, 2, 4), Motor.listaPaginas("1-3, 5", 10));
        assertEquals(List.of(7, 8, 9), Motor.listaPaginas("8-", 10));
        assertThrows(IllegalArgumentException.class, () -> Motor.listaPaginas("5-20", 10));
        OpcionesIndexar o = new OpcionesIndexar();
        assertEquals("ACTA", Motor.textoDerechoIndexar("ACTA 2025 enero", o));
        o.bloque = 1;
        assertEquals("2025", Motor.textoDerechoIndexar("ACTA 2025 enero", o));
        o.delimitador = "_";
        o.bloque = 2;
        assertEquals("08011990123456", Motor.textoDerechoIndexar("REQ_SAR_08011990123456", o));
        assertEquals("Página 3 de 10", Motor.formatoNumeracion("Página {pagina} de {total}", 3, 10));
    }

    @Test
    void foliarTodasLasHojas() throws Exception {
        File dir = Files.createTempDirectory("pf").toFile();
        File pdf = pdfDePrueba(dir, "expediente.pdf");
        File salida = new File(dir, "expediente_foliado.pdf");
        OpcionesFolio o = new OpcionesFolio();
        o.conTotal = true;
        o.recuadro = true;
        Motor.foliar(pdf, salida, null, o, null, null, null);
        String t = texto(salida);
        for (int i = 1; i <= 5; i++) {
            assertTrue(t.contains("Folio 00" + i + " de 005"), t);
        }
        imagenes(salida, "foliar_sup_der");
        // el original no cambió
        assertFalse(texto(pdf).contains("Folio"));
    }

    @Test
    void foliarEnLetrasYPosiciones() throws Exception {
        File dir = Files.createTempDirectory("pf").toFile();
        File pdf = pdfDePrueba(dir, "acta.pdf");
        for (String pos : List.of("inf_izq", "inf_centro", "sup_izq")) {
            File salida = new File(dir, pos + ".pdf");
            OpcionesFolio o = new OpcionesFolio();
            o.posicion = pos;
            o.enLetras = true;
            o.inicio = 21;
            o.color = new Color(13, 51, 166);
            o.tamano = 13;
            o.recuadro = true;
            Motor.foliar(pdf, salida, null, o, null, null, null);
            String t = texto(salida);
            assertTrue(t.contains("FOLIO VEINTIUNO"), t);
            assertTrue(t.contains("FOLIO VEINTICINCO"), t);
            assertTrue(t.contains("Folio 021"), t);
            imagenes(salida, "foliar_letras_" + pos);
        }
    }

    @Test
    void foliarSaltarRangoYPersonalizar() throws Exception {
        File dir = Files.createTempDirectory("pf").toFile();
        File pdf = pdfDePrueba(dir, "a.pdf");
        File salida = new File(dir, "b.pdf");
        OpcionesFolio o = new OpcionesFolio();
        o.saltar = 1;
        Map<Integer, ReglaHoja> reglas = new HashMap<>();
        ReglaHoja omitir = new ReglaHoja();
        omitir.omitir = true;
        reglas.put(2, omitir);
        ReglaHoja mover = new ReglaHoja();
        mover.dx = -100;
        mover.dy = 50;
        mover.color = Color.BLACK;
        mover.tamano = 20f;
        mover.fuente = "Times";
        reglas.put(3, mover);
        Motor.foliar(pdf, salida, null, o, null, reglas, null);
        String t = texto(salida);
        // hoja 1 sin folio (saltar), hoja 3 omitida: quedan hojas 2, 4, 5 -> Folio 001, 002, 003
        assertTrue(t.contains("Folio 001") && t.contains("Folio 002") && t.contains("Folio 003"), t);
        assertFalse(t.contains("Folio 004"), t);
        imagenes(salida, "foliar_personalizado");
    }

    @Test
    void libre() throws Exception {
        File dir = Files.createTempDirectory("pf").toFile();
        File pdf = pdfDePrueba(dir, "a.pdf");
        File salida = new File(dir, "libre.pdf");
        OpcionesFolio o = new OpcionesFolio();
        o.libre = true;
        o.libreX = 0.5f;
        o.libreY = 0.5f;
        o.tamano = 24;
        Motor.foliar(pdf, salida, null, o, null, null, null);
        imagenes(salida, "foliar_libre_centro");
    }

    @Test
    void indexar() throws Exception {
        File dir = Files.createTempDirectory("pi").toFile();
        File pdf = pdfDePrueba(dir, "08011990123456 REQUERIMIENTO.pdf");
        File salida = new File(dir, "salida/indexado.pdf");
        OpcionesIndexar o = new OpcionesIndexar();
        o.abajoIzqOn = true;
        o.abajoIzqTexto = "CONFIDENCIAL";
        o.letrasOn = true;
        o.extraOn = true;
        o.extraPos = "bottom_center";
        o.extraFormato = "Página {pagina} de {total}";
        Motor.indexar(pdf, salida, null, pdf.getName(), o, null, null);
        String t = texto(salida);
        assertTrue(t.contains("DPOT"), t);
        assertTrue(t.contains("08011990123456 1/5"), t);
        assertTrue(t.contains("08011990123456 5/5"), t);
        assertTrue(t.contains("FOLIO TRES"), t);
        assertTrue(t.contains("Página 4 de 5"), t);
        assertTrue(t.contains("CONFIDENCIAL"), t);
        imagenes(salida, "indexar");
    }

    @Test
    void letrasDeLaComputadora() throws Exception {
        Map<String, String> todas = LetrasInstaladas.todas();
        System.out.println("Letras encontradas: " + todas.size());
        if (todas.isEmpty()) {
            return;
        }
        String nombre = todas.keySet().stream().filter(n -> n.toLowerCase().contains("dejavu sans")).findFirst()
                .orElse(todas.keySet().iterator().next());
        File dir = Files.createTempDirectory("pl").toFile();
        File pdf = pdfDePrueba(dir, "a.pdf");
        File salida = new File(dir, "letra.pdf");
        OpcionesFolio o = new OpcionesFolio();
        o.fuente = todas.get(nombre);
        o.prefijo = "Fólio Ñ ";
        o.tamano = 16;
        Motor.foliar(pdf, salida, null, o, null, null, null);
        String t = texto(salida);
        assertTrue(t.contains("Fólio Ñ 001"), t);
        imagenes(salida, "foliar_letra_computadora");
        assertTrue(salida.length() < 400_000, "la letra se incluye solo con lo usado: " + salida.length());
    }

    @Test
    void acentosConLetraBasica() throws Exception {
        File dir = Files.createTempDirectory("pa").toFile();
        File pdf = pdfDePrueba(dir, "a.pdf");
        File salida = new File(dir, "b.pdf");
        OpcionesFolio o = new OpcionesFolio();
        o.prefijo = "Página Nº ";
        o.sufijo = " — AUDITORÍA";
        Motor.foliar(pdf, salida, null, o, null, null, null);
        assertTrue(texto(salida).contains("Página Nº 001"));
    }

    @Test
    void vistaPreviaNoCambiaElPdf() throws Exception {
        File dir = Files.createTempDirectory("pv").toFile();
        File pdf = pdfDePrueba(dir, "a.pdf");
        long antes = pdf.length();
        OpcionesFolio o = new OpcionesFolio();
        try (Motor.Previa p = new Motor.Previa(pdf, null)) {
            for (int i = 0; i < 3; i++) {
                BufferedImage b = p.imagen(1, 400,
                        (doc, hoja, letras) -> Motor.foliarHoja(doc, hoja, 1, 5, o, null, letras));
                if (i == 2) {
                    ImageIO.write(b, "png", new File(SALIDA, "previa_hoja2_girada.png"));
                }
            }
            BufferedImage limpia = p.imagen(1, 400, null);
            ImageIO.write(limpia, "png", new File(SALIDA, "previa_hoja2_sin_folio.png"));
        }
        assertEquals(antes, pdf.length());
    }

    @Test
    void loteConZipAdentro() throws Exception {
        File dir = Files.createTempDirectory("plote").toFile();
        File origen = new File(dir, "Expediente 2025");
        File sub = new File(origen, "Enero");
        sub.mkdirs();
        pdfDePrueba(origen, "uno.pdf");
        pdfDePrueba(sub, "dos.pdf");
        Files.writeString(new File(sub, "nota.txt").toPath(), "hola");
        new File(origen, "vacia").mkdirs();
        // un ZIP dentro de la carpeta, con un PDF y una subcarpeta
        File tmp = Files.createTempDirectory("pz").toFile();
        File pz = pdfDePrueba(tmp, "tres.pdf");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(new File(origen, "anexos.zip").toPath()))) {
            z.putNextEntry(new ZipEntry("Anexo A/tres.pdf"));
            Files.copy(pz.toPath(), z);
            z.closeEntry();
        }
        Lote L = new Lote(origen).preparar();
        assertEquals(3, L.procesables.size(), L.procesables.toString());
        long[] siguiente = { 1 };
        OpcionesFolio o = new OpcionesFolio();
        Lote.Operacion op = (e, s, c) -> {
            OpcionesFolio uno = o.copia();
            uno.inicio = siguiente[0];
            try (var d = Motor.abrir(e, c)) {
                int n = d.getNumberOfPages();
                Motor.foliarDocumento(d, uno, null, null, null);
                Motor.guardar(d, s);
                siguiente[0] += n;
            }
        };
        File destino = new File(dir, "resultado");
        L.ejecutar(op, destino.toPath(), null);
        File res = L.finalizar(null).toFile();
        assertTrue(new File(res, "Enero/nota.txt").isFile());
        assertTrue(new File(res, "vacia").isDirectory());
        assertTrue(new File(res, "anexos.zip").isFile());
        assertFalse(new File(res, "anexos.zip" + Lote.MARCA_ZIP).exists());
        // numeración continua (en orden de carpetas y nombres): anexos.zip 1-5, Enero/dos 6-10, uno 11-15
        String dos = texto(new File(res, "Enero/dos.pdf")), uno = texto(new File(res, "uno.pdf"));
        assertTrue(dos.contains("Folio 006") && dos.contains("Folio 010"), dos);
        assertTrue(uno.contains("Folio 011") && uno.contains("Folio 015"), uno);
        assertEquals(16, siguiente[0]);
        // los originales no cambiaron
        assertFalse(texto(new File(origen, "uno.pdf")).contains("Folio"));
    }

    @Test
    void girarYOrdenar() throws Exception {
        File dir = Files.createTempDirectory("pg").toFile();
        File pdf = pdfDePrueba(dir, "a.pdf");
        File salida = new File(dir, "girado.pdf");
        // nuevo orden: 3, 1, 2, 5, 4 ; la 1 girada a la derecha, la 5 volteada
        Motor.girarYOrdenar(pdf, salida, null, List.of(new Motor.HojaGirada(2, 0), new Motor.HojaGirada(0, 90),
                new Motor.HojaGirada(1, 0), new Motor.HojaGirada(4, 180), new Motor.HojaGirada(3, 270)), null);
        try (PDDocument d = Motor.abrir(salida, null)) {
            assertEquals(5, d.getNumberOfPages());
            assertEquals(270, d.getPage(0).getRotation()); // la hoja 3 ya tenía 270
            assertEquals(90, d.getPage(1).getRotation()); // hoja 1: 0 + 90
            assertEquals(90, d.getPage(2).getRotation()); // hoja 2: ya tenía 90
            assertEquals(180, d.getPage(3).getRotation()); // hoja 5: 0 + 180
            assertEquals(90, d.getPage(4).getRotation()); // hoja 4: 180 + 270
            int[] esperado = { 3, 1, 2, 5, 4 };
            for (int k = 0; k < 5; k++) {
                PDFTextStripper st = new PDFTextStripper();
                st.setStartPage(k + 1);
                st.setEndPage(k + 1);
                String t = st.getText(d).replaceAll("\\s", "");
                assertTrue(t.contains("HOJA" + esperado[k]), "hoja " + (k + 1) + ": " + t);
            }
        }
        imagenes(salida, "girar_ordenado");
        // solo girar (mismo orden)
        File s2 = new File(dir, "g2.pdf");
        Motor.girarTodo(pdf, s2, null, -90);
        try (PDDocument d = Motor.abrir(s2, null)) {
            assertEquals(270, d.getPage(0).getRotation());
            assertEquals(0, d.getPage(1).getRotation());
        }
    }

    @Test
    void armarQuitarDuplicarInsertar() throws Exception {
        File dir = Files.createTempDirectory("pa").toFile();
        File a = pdfDePrueba(dir, "a.pdf");
        File otro = new File(dir, "otro.pdf");
        try (PDDocument d = new PDDocument()) {
            PDPage p = new PDPage(new PDRectangle(300, 300));
            d.addPage(p);
            try (PDPageContentStream cs = new PDPageContentStream(d, p)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA(), 20);
                cs.newLineAtOffset(50, 150);
                cs.showText("OTRO PDF");
                cs.endText();
            }
            d.writeTo(otro);
        }
        File salida = new File(dir, "armado.pdf");
        // hoja 1, copia de la hoja 1 girada, hoja en blanco, hoja del otro PDF, hoja 5 (se quitaron 2, 3 y 4)
        Motor.armar(a, Map.of(), List.of(new Motor.HojaArmada(a, 0, 0, 0, 0), new Motor.HojaArmada(a, 0, 90, 0, 0),
                new Motor.HojaArmada(null, -1, 0, 612, 792), new Motor.HojaArmada(otro, 0, 0, 0, 0),
                new Motor.HojaArmada(a, 4, 0, 0, 0)), salida, null);
        try (PDDocument d = Motor.abrir(salida, null)) {
            assertEquals(5, d.getNumberOfPages());
            assertEquals(0, d.getPage(0).getRotation());
            assertEquals(90, d.getPage(1).getRotation());
            String[] esperado = { "HOJA1", "HOJA1", "", "OTROPDF", "HOJA5" };
            for (int k = 0; k < 5; k++) {
                PDFTextStripper st = new PDFTextStripper();
                st.setStartPage(k + 1);
                st.setEndPage(k + 1);
                String t = st.getText(d).replaceAll("\\s", "");
                if (esperado[k].isEmpty()) {
                    assertEquals("", t);
                } else {
                    assertTrue(t.contains(esperado[k]), "hoja " + (k + 1) + ": " + t);
                }
            }
        }
        imagenes(salida, "armado");
    }
}
