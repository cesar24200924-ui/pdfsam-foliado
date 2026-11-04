/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.sejda.sambox.pdmodel.PDDocument;
import org.sejda.sambox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.sejda.sambox.pdmodel.interactive.form.PDAcroForm;
import org.sejda.sambox.pdmodel.interactive.form.PDField;
import org.sejda.sambox.pdmodel.interactive.form.PDTextField;
import org.sejda.sambox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de: comprimidos (RAR, 7z...), leyendas que se mueven solas y texto editable en Adobe. */
class NuevasTest {

    // ------------------------------------------------------------ nombres de comprimidos
    @Test
    void nombresDeComprimidos() {
        assertTrue(Comprimidos.es("Expediente.RAR"));
        assertTrue(Comprimidos.es("a.7z"));
        assertTrue(Comprimidos.es("a.tar.gz"));
        assertTrue(Comprimidos.es("a.part1.rar"));
        assertFalse(Comprimidos.es("a.part2.rar"));
        assertFalse(Comprimidos.es("a.r00"));
        assertTrue(Comprimidos.es("a.7z.001"));
        assertFalse(Comprimidos.es("a.7z.002"));
        assertFalse(Comprimidos.es("a.pdf"));
        assertEquals("Expediente", Comprimidos.base("Expediente.rar"));
        assertEquals("x", Comprimidos.base("x.tar.gz"));
        assertEquals("x", Comprimidos.base("x.part1.rar"));
        assertEquals("x", Comprimidos.base("x.7z.001"));
        assertEquals("x", Comprimidos.base("x.tgz"));
    }

    // ------------------------------------------------------------ comprimidos de verdad (con 7z / rar si están)
    private static boolean hay(String programa) {
        try {
            Process p = new ProcessBuilder(programa).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor(20, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void correr(File dir, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start();
        String salida = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), String.join(" ", cmd) + "\n" + salida);
    }

    /** Carpeta "Expediente" con 2 PDF (uno en subcarpeta) y una nota. */
    private static File contenido(File dir) throws Exception {
        File exp = new File(dir, "Expediente");
        new File(exp, "Anexos").mkdirs();
        MotorTest.pdfDePrueba(exp, "acta.pdf");
        MotorTest.pdfDePrueba(new File(exp, "Anexos"), "anexo uno.pdf");
        Files.writeString(new File(exp, "nota.txt").toPath(), "hola");
        return exp;
    }

    /** Folia el comprimido completo y devuelve los nombres que quedan en el ZIP del resultado. */
    private static List<String> foliarComprimido(File archivo, String clave, File dir) throws Exception {
        Lote L = new Lote(archivo);
        List<Boolean> preguntas = new ArrayList<>();
        L.pide = (nombre, mala) -> {
            preguntas.add(mala);
            return clave;
        };
        L.preparar();
        assertTrue(L.esZip);
        assertEquals(2, L.procesables.size(), L.procesables.toString());
        OpcionesFolio o = new OpcionesFolio();
        L.ejecutar((e, s, c) -> Motor.foliar(e, s, c, o, null, null, null), L.temp.resolve("resultado"), null);
        File zip = new File(dir, Comprimidos.base(archivo.getName()) + "_foliado.zip");
        L.finalizar(zip);
        List<String> nombres = new ArrayList<>();
        try (ZipFile z = new ZipFile(zip)) {
            z.stream().map(ZipEntry::getName).forEach(nombres::add);
            ZipEntry acta = z.stream().filter(e -> e.getName().endsWith("acta.pdf")).findFirst()
                    .orElseThrow(() -> new AssertionError("sin acta.pdf: " + nombres + " / " + L.procesables));
            File f = File.createTempFile("acta_sacada", ".pdf", dir);
            f.delete();
            Files.copy(z.getInputStream(acta), f.toPath());
            assertTrue(MotorTest.texto(f).contains("Folio"), "el PDF de adentro quedó foliado");
        }
        Collections.sort(nombres);
        return nombres;
    }

    @Test
    void sieteZipConContrasena() throws Exception {
        Assumptions.assumeTrue(hay("7z"), "no hay 7z en esta computadora");
        File dir = Files.createTempDirectory("p7z").toFile();
        contenido(dir);
        // contraseña también en los nombres (-mhe=on): se pide al abrir
        correr(dir, "7z", "a", "-pSAR2025", "-mhe=on", "Expediente.7z", "Expediente");
        List<String> n = foliarComprimido(new File(dir, "Expediente.7z"), "SAR2025", dir);
        assertTrue(n.contains("Expediente/nota.txt"), n.toString());
        assertTrue(n.contains("Expediente/Anexos/anexo uno.pdf"), n.toString());
        // sin contraseña: no se abre
        Lote L = new Lote(new File(dir, "Expediente.7z"));
        L.pide = (a, m) -> null;
        assertThrows(Comprimidos.SinClave.class, L::preparar);
        L.limpiar();
    }

    @Test
    void tarGzYZipDentroDe7z() throws Exception {
        Assumptions.assumeTrue(hay("7z") && hay("tar"), "no hay 7z o tar");
        File dir = Files.createTempDirectory("ptgz").toFile();
        contenido(dir);
        correr(dir, "tar", "czf", "Expediente.tar.gz", "Expediente");
        List<String> n = foliarComprimido(new File(dir, "Expediente.tar.gz"), null, dir);
        assertTrue(n.contains("Expediente/acta.pdf"), n.toString());
        // en partes (.001, .002...)
        correr(dir, "7z", "a", "-mx0", "-v2k", "partes.7z", "Expediente");
        assertTrue(new File(dir, "partes.7z.002").isFile(), "se hizo en varias partes");
        List<String> p = foliarComprimido(new File(dir, "partes.7z.001"), null, dir);
        assertTrue(p.contains("Expediente/nota.txt"), p.toString());
    }

    @Test
    void rarConContrasena() throws Exception {
        Assumptions.assumeTrue(hay("rar"), "no hay rar en esta computadora");
        File dir = Files.createTempDirectory("prar").toFile();
        contenido(dir);
        correr(dir, "rar", "a", "-ma5", "-pclave1", "Expediente.rar", "Expediente");
        // la primera contraseña está mal: la vuelve a pedir
        Lote L = new Lote(new File(dir, "Expediente.rar"));
        List<Boolean> veces = new ArrayList<>();
        L.pide = (a, mala) -> {
            veces.add(mala);
            return veces.size() == 1 ? "mala" : "clave1";
        };
        L.preparar();
        assertEquals(List.of(false, true), veces);
        assertEquals(2, L.procesables.size());
        L.limpiar();
        List<String> n = foliarComprimido(new File(dir, "Expediente.rar"), "clave1", dir);
        assertTrue(n.contains("Expediente/Anexos/anexo uno.pdf"), n.toString());
    }

    // ------------------------------------------------------------ leyendas que se mueven solas
    private static Map<Integer, Motor.Marco> marcos(OpcionesIndexar o, ReglaHoja r) throws Exception {
        File dir = Files.createTempDirectory("pmar").toFile();
        File pdf = MotorTest.pdfDePrueba(dir, "08011990123456 ACTA.pdf");
        try (PDDocument d = Motor.abrir(pdf, null)) {
            List<Motor.Marco> m = new ArrayList<>();
            Motor.indexarHoja(d, d.getPage(0), 1, 5, "08011990123456 ACTA", o, r, new Motor.Letras(d), m);
            Map<Integer, Motor.Marco> porLeyenda = new java.util.HashMap<>();
            for (Motor.Marco x : m) {
                porLeyenda.put(x.leyenda(), x);
            }
            return porLeyenda;
        }
    }

    @Test
    void cadaLeyendaSeMueveSola() throws Exception {
        OpcionesIndexar o = new OpcionesIndexar();
        o.abajoIzqOn = true;
        o.abajoIzqTexto = "PIE";
        o.letrasOn = true;
        o.extraOn = true;
        Map<Integer, Motor.Marco> antes = marcos(o, null);
        assertEquals(5, antes.size());
        // en todas las hojas: solo "arriba a la derecha" 50 a la izquierda y 30 abajo
        o.movX[2] = -50;
        o.movY[2] = 30;
        // y en esta hoja sola: "abajo a la izquierda" 20 a la derecha
        ReglaHoja r = new ReglaHoja();
        r.ldx[1] = 20;
        Map<Integer, Motor.Marco> despues = marcos(o, r);
        assertEquals(antes.get(2).x0() - 50, despues.get(2).x0(), 0.01);
        assertEquals(antes.get(2).y0() + 30, despues.get(2).y0(), 0.01);
        assertEquals(antes.get(1).x0() + 20, despues.get(1).x0(), 0.01);
        for (int k : new int[] { 0, 3, 4 }) {
            assertEquals(antes.get(k), despues.get(k), "la leyenda " + k + " no se movió");
        }
        // la copia de la regla lleva lo movido
        assertEquals(20, r.copia().ldx[1], 0.01);
    }

    // ------------------------------------------------------------ texto editable en Adobe
    @Test
    void folioEditable() throws Exception {
        File dir = Files.createTempDirectory("ped").toFile();
        File pdf = MotorTest.pdfDePrueba(dir, "a.pdf");
        File salida = new File(dir, "editable.pdf");
        OpcionesFolio o = new OpcionesFolio();
        o.editable = true;
        o.recuadro = true;
        Motor.foliar(pdf, salida, null, o, null, null, null);
        try (PDDocument d = Motor.abrir(salida, null)) {
            PDAcroForm f = d.getDocumentCatalog().getAcroForm();
            assertNotNull(f);
            List<String> valores = new ArrayList<>();
            for (PDField c : f.getFields()) {
                assertTrue(c instanceof PDTextField);
                valores.add(c.getValueAsString());
                PDAnnotationWidget w = c.getWidgets().get(0);
                assertNotNull(w.getAppearance());
            }
            assertEquals(List.of("Folio 001", "Folio 002", "Folio 003", "Folio 004", "Folio 005"), valores);
            for (int i = 0; i < 5; i++) {
                assertEquals(1, d.getPage(i).getAnnotations().size(), "hoja " + (i + 1));
            }
            // se ve igual que el fijo
            PDFRenderer r = new PDFRenderer(d);
            for (int i = 0; i < 5; i++) {
                BufferedImage img = r.renderImage(i, 1.5f);
                ImageIO.write(img, "png", new File(MotorTest.SALIDA, "editable_hoja" + (i + 1) + ".png"));
            }
        }
        // Indexar también
        OpcionesIndexar oi = new OpcionesIndexar();
        oi.editable = true;
        File s2 = new File(dir, "indexado_editable.pdf");
        Motor.indexar(pdf, s2, null, "08011990123456 ACTA.pdf", oi, null, null);
        try (PDDocument d = Motor.abrir(s2, null)) {
            List<String> v = new ArrayList<>();
            for (PDField c : d.getDocumentCatalog().getAcroForm().getFields()) {
                v.add(c.getValueAsString());
            }
            assertTrue(v.contains("DPOT") && v.contains("08011990123456 1/5"), v.toString());
            BufferedImage img = new PDFRenderer(d).renderImage(1, 1.5f);
            ImageIO.write(img, "png", new File(MotorTest.SALIDA, "indexar_editable_hoja2.png"));
        }
    }
}
