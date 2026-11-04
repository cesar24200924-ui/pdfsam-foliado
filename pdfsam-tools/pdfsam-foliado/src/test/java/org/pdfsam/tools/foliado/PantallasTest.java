/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Abre las dos pantallas con un PDF y guarda una foto para revisarlas a ojo. */
class PantallasTest {

    @BeforeAll
    static void iniciar() throws Exception {
        CountDownLatch l = new CountDownLatch(1);
        try {
            Platform.startup(l::countDown);
        } catch (IllegalStateException e) {
            l.countDown();
        }
        assertTrue(l.await(20, TimeUnit.SECONDS));
    }

    static <T> T enFx(Supplier<T> s) throws Exception {
        AtomicReference<T> r = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch l = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.set(s.get());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                l.countDown();
            }
        });
        assertTrue(l.await(30, TimeUnit.SECONDS));
        if (err.get() != null) {
            throw new AssertionError(err.get());
        }
        return r.get();
    }

    static void foto(Scene escena, String nombre) throws Exception {
        WritableImage img = enFx(() -> escena.snapshot(null));
        MotorTest.SALIDA.mkdirs();
        // sin SwingFXUtils: se copia píxel por píxel
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        var b = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                b.setRGB(x, y, img.getPixelReader().getArgb(x, y));
            }
        }
        ImageIO.write(b, "png", new File(MotorTest.SALIDA, nombre));
    }

    static Scene escena(Pane p) {
        Scene s = new Scene(p, 1400, 860);
        // el tema claro de PDFsam (leído de su carpeta, así las fotos se ven como en el programa)
        File temas = new File("../../pdfsam-themes/src/main/resources/themes/light");
        for (String css : List.of("colors.css", "theme.css", "tooltip.css", "progress.css", "list.css", "dialogs.css",
                "combo.css", "scrollbars.css", "table.css", "theme.last.css")) {
            File f = new File(temas, css);
            if (f.isFile()) {
                s.getStylesheets().add(f.toURI().toString());
            }
        }
        p.getStyleClass().addAll("pdfsam-container", "content-pane");
        return s;
    }

    @Test
    void foliar() throws Exception {
        File dir = Files.createTempDirectory("pp").toFile();
        File pdf = MotorTest.pdfDePrueba(dir, "acta.pdf");
        FoliarPanel panel = enFx(FoliarPanel::new);
        Scene s = enFx(() -> escena(panel));
        foto(s, "pantalla_foliar_vacia.png");
        enFx(() -> {
            panel.recibir(List.of(pdf));
            return null;
        });
        Thread.sleep(1500);
        foto(s, "pantalla_foliar.png");
        Thread.sleep(2500);
        foto(s, "pantalla_foliar.png");
        foto(s, "manual_foliar.png");
        enFx(() -> {
            panel.opcionesParaPrueba();
            return null;
        });
        Thread.sleep(1500);
        foto(s, "pantalla_foliar_letras.png"); // acomoda la pantalla (sin ventana solo se acomoda al tomar la foto)
        Thread.sleep(2500);
        foto(s, "pantalla_foliar_letras.png");
        foto(s, "manual_foliar_personalizar.png");
        assertNull(null);
    }

    @Test
    void indexar() throws Exception {
        File dir = Files.createTempDirectory("pp").toFile();
        File sub = new File(dir, "Expediente");
        sub.mkdirs();
        MotorTest.pdfDePrueba(sub, "08011990123456 REQUERIMIENTO.pdf");
        MotorTest.pdfDePrueba(sub, "08011990999999 ACTA.pdf");
        IndexarPanel panel = enFx(IndexarPanel::new);
        Scene s = enFx(() -> escena(panel));
        foto(s, "pantalla_indexar.png");
        enFx(() -> {
            panel.recibir(List.of(sub));
            return null;
        });
        Thread.sleep(1500);
        foto(s, "pantalla_indexar.png");
        Thread.sleep(2500);
        foto(s, "pantalla_indexar.png");
        foto(s, "manual_indexar.png");
    }

    @Test
    void girar() throws Exception {
        File dir = Files.createTempDirectory("pp").toFile();
        File pdf = MotorTest.pdfDePrueba(dir, "acta.pdf");
        GirarPanel panel = enFx(GirarPanel::new);
        Scene s = enFx(() -> escena(panel));
        foto(s, "pantalla_girar.png");
        enFx(() -> {
            panel.recibir(List.of(pdf));
            return null;
        });
        Thread.sleep(1500);
        foto(s, "pantalla_girar.png");
        Thread.sleep(3000);
        foto(s, "pantalla_girar.png");
        foto(s, "manual_girar.png");
        enFx(() -> {
            panel.paraPrueba();
            return null;
        });
        Thread.sleep(1500);
        foto(s, "manual_girar_eligiendo.png");
        Thread.sleep(2500);
        foto(s, "manual_girar_eligiendo.png");
    }

    @Test
    void manual() throws Exception {
        ManualPanel panel = enFx(ManualPanel::new);
        Scene s = enFx(() -> escena(panel));
        foto(s, "pantalla_manual.png");
        Thread.sleep(1000);
        foto(s, "pantalla_manual.png");
        enFx(() -> {
            panel.ir("girar");
            return null;
        });
        Thread.sleep(500);
        foto(s, "pantalla_manual_girar.png");
    }
}
