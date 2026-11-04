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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.pdfsam.ui.components.support.Style;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Ventanas y avisos sencillos, con palabras claras. */
final class Ui {
    private static File ultimaCarpeta;

    private Ui() {
    }

    static Window ventana(Node n) {
        return n == null || n.getScene() == null ? null : n.getScene().getWindow();
    }

    private static void preparar(Dialog<?> d, Node n, String titulo) {
        Window w = ventana(n);
        if (w != null) {
            d.initOwner(w);
        }
        d.setTitle(titulo);
        d.setResizable(true);
        d.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        d.getDialogPane().setMinWidth(420);
    }

    static void aviso(Node n, String texto) {
        Alert a = new Alert(Alert.AlertType.WARNING, texto, ButtonType.OK);
        preparar(a, n, "Aviso");
        a.setHeaderText(null);
        a.showAndWait();
    }

    static void error(Node n, String texto) {
        Alert a = new Alert(Alert.AlertType.ERROR, texto, ButtonType.OK);
        preparar(a, n, "Algo salió mal");
        a.setHeaderText(null);
        a.showAndWait();
    }

    static boolean pregunta(Node n, String texto, String titulo) {
        ButtonType si = new ButtonType("Sí", ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("No", ButtonBar.ButtonData.NO);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, texto, si, no);
        preparar(a, n, titulo == null ? "Pregunta" : titulo);
        a.setHeaderText(null);
        return a.showAndWait().filter(b -> b == si).isPresent();
    }

    /** Sí / No / Cancelar: devuelve true, false o null. */
    static Boolean preguntaConCancelar(Node n, String texto, String titulo) {
        ButtonType si = new ButtonType("Sí", ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("No", ButtonBar.ButtonData.NO);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, texto, si, no, cancelar);
        preparar(a, n, titulo);
        a.setHeaderText(null);
        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty() || r.get() == cancelar) {
            return null;
        }
        return r.get() == si;
    }

    /** Dos botones con nombre propio: devuelve 0 (primero), 1 (segundo) o -1 (cancelar). */
    static int elegir(Node n, String titulo, String texto, String opcion1, String opcion2) {
        ButtonType b1 = new ButtonType(opcion1, ButtonBar.ButtonData.YES);
        ButtonType b2 = new ButtonType(opcion2, ButtonBar.ButtonData.NO);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, texto, b1, b2, cancelar);
        preparar(a, n, titulo);
        a.setHeaderText(titulo);
        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty() || r.get() == cancelar) {
            return -1;
        }
        return r.get() == b1 ? 0 : 1;
    }

    static String pedirTexto(Node n, String texto, String titulo, String inicial) {
        TextInputDialog d = new TextInputDialog(inicial == null ? "" : inicial);
        preparar(d, n, titulo);
        d.setHeaderText(null);
        d.setContentText(texto);
        return d.showAndWait().map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
    }

    static String pedirClave(Node n, String archivo, String nota) {
        Dialog<String> d = new Dialog<>();
        preparar(d, n, "Contraseña");
        d.setHeaderText("Este PDF tiene contraseña:\n" + archivo);
        PasswordField campo = new PasswordField();
        campo.setPromptText("Escriba la contraseña");
        VBox caja = new VBox(8, new Label("Contraseña:"), campo);
        if (nota != null) {
            Label l = new Label(nota);
            l.setWrapText(true);
            caja.getChildren().add(l);
        }
        caja.setPadding(new Insets(10));
        d.getDialogPane().setContent(caja);
        ButtonType ok = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        d.setResultConverter(b -> b == ok ? campo.getText() : null);
        Platform.runLater(campo::requestFocus);
        return d.showAndWait().filter(s -> !s.isEmpty()).orElse(null);
    }

    // ------------------------------------------------------------ archivos
    private static File inicio(File sugerido) {
        if (sugerido != null && sugerido.isDirectory()) {
            return sugerido;
        }
        if (ultimaCarpeta != null && ultimaCarpeta.isDirectory()) {
            return ultimaCarpeta;
        }
        return new File(System.getProperty("user.home"));
    }

    static List<File> elegirPdfs(Node n, boolean varios) {
        FileChooser fc = new FileChooser();
        fc.setTitle(varios ? "Elegir PDF" : "Elegir el PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf", "*.PDF"));
        fc.setInitialDirectory(inicio(null));
        List<File> r;
        if (varios) {
            r = fc.showOpenMultipleDialog(ventana(n));
        } else {
            File f = fc.showOpenDialog(ventana(n));
            r = f == null ? null : List.of(f);
        }
        if (r != null && !r.isEmpty()) {
            ultimaCarpeta = r.get(0).getParentFile();
        }
        return r;
    }

    static File elegirZip(Node n) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Elegir ZIP");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP", "*.zip", "*.ZIP"));
        fc.setInitialDirectory(inicio(null));
        File f = fc.showOpenDialog(ventana(n));
        if (f != null) {
            ultimaCarpeta = f.getParentFile();
        }
        return f;
    }

    static File elegirCarpeta(Node n, String titulo, File desde) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle(titulo);
        dc.setInitialDirectory(inicio(desde));
        File f = dc.showDialog(ventana(n));
        if (f != null) {
            ultimaCarpeta = f;
        }
        return f;
    }

    static File guardarComo(Node n, File sugerido, String tipo, String extension, String titulo) {
        FileChooser fc = new FileChooser();
        fc.setTitle(titulo);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(tipo, "*" + extension));
        fc.setInitialDirectory(inicio(sugerido.getParentFile()));
        fc.setInitialFileName(sugerido.getName());
        File f = fc.showSaveDialog(ventana(n));
        if (f != null && !f.getName().toLowerCase().endsWith(extension)) {
            f = new File(f.getParentFile(), f.getName() + extension);
        }
        if (f != null) {
            ultimaCarpeta = f.getParentFile();
        }
        return f;
    }

    /** "C:/x/acta.pdf" + "foliado" -> "C:/x/acta_foliado.pdf" */
    static File sugerir(File original, String sufijo) {
        return new File(original.getParentFile(), Motor.sinExtension(original.getName()) + "_" + sufijo + ".pdf");
    }

    static void abrirCarpetaDe(File f) {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                if (f.isFile()) {
                    new ProcessBuilder("explorer.exe", "/select,", f.getAbsolutePath()).start();
                } else {
                    new ProcessBuilder("explorer.exe", f.getAbsolutePath()).start();
                }
            } else if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(f.isFile() ? f.getParentFile() : f);
            }
        } catch (IOException | RuntimeException e) {
            // no se pudo abrir: no es grave
        }
    }

    static void abrirArchivo(File f) {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "\"\"", "\"" + f.getAbsolutePath() + "\"").start();
            } else if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(f);
            }
        } catch (IOException | RuntimeException e) {
            // no se pudo abrir: no es grave
        }
    }

    /** Al terminar: lo que pasó y botones para abrir el resultado. */
    static void terminado(Node n, File resultado, String titulo, String detalle) {
        Stage s = new Stage();
        s.initModality(Modality.WINDOW_MODAL);
        Window w = ventana(n);
        if (w != null) {
            s.initOwner(w);
        }
        s.setTitle(titulo);
        Label t = new Label("✓ " + titulo);
        t.setStyle("-fx-font-size: 1.3em; -fx-font-weight: bold;");
        Label d = new Label((detalle == null ? "" : detalle + "\n\n") + "Guardado en:\n" + resultado.getAbsolutePath());
        d.setWrapText(true);
        Button carpeta = boton("Abrir la carpeta", () -> abrirCarpetaDe(resultado));
        Button abrir = boton(resultado.isFile() && resultado.getName().toLowerCase().endsWith(".pdf") ?
                "Abrir el PDF" : "Abrir", () -> {
            if (resultado.isFile()) {
                abrirArchivo(resultado);
            } else {
                abrirCarpetaDe(resultado);
            }
        });
        Button cerrar = boton("Cerrar", s::close);
        cerrar.setDefaultButton(true);
        HBox botones = new HBox(8, abrir, carpeta, cerrar);
        botones.setAlignment(Pos.CENTER_RIGHT);
        VBox caja = new VBox(12, t, d, botones);
        caja.setPadding(new Insets(20));
        caja.setPrefWidth(520);
        caja.getStyleClass().addAll(Style.CONTAINER.css());
        Scene sc = new Scene(caja);
        if (w != null && w.getScene() != null) {
            sc.getStylesheets().addAll(w.getScene().getStylesheets());
        }
        s.setScene(sc);
        s.showAndWait();
    }

    static Button boton(String texto, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().addAll(Style.BUTTON.css());
        b.setOnAction(e -> accion.run());
        return b;
    }

    // ------------------------------------------------------------ trabajo en segundo plano
    /** Lo que avisa el trabajo mientras avanza. */
    interface Progreso {
        void poner(double fraccion, String texto);

        boolean cancelado();
    }

    @FunctionalInterface
    interface Trabajo<T> {
        T hacer(Progreso p) throws Exception;
    }

    /**
     * Hace el trabajo sin congelar la ventana, con una barra de avance y (si se puede) el botón Cancelar. Al terminar
     * llama a listo (o a fallo) en la ventana.
     */
    static <T> void enSegundoPlano(Node n, String titulo, boolean conCancelar, Trabajo<T> trabajo, Consumer<T> listo,
            Consumer<Exception> fallo) {
        Stage s = new Stage();
        s.initModality(Modality.WINDOW_MODAL);
        Window w = ventana(n);
        if (w != null) {
            s.initOwner(w);
        }
        s.setTitle(titulo);
        Label t = new Label(titulo + "…");
        t.setStyle("-fx-font-weight: bold;");
        Label detalle = new Label("");
        detalle.setWrapText(true);
        ProgressBar barra = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        barra.setMaxWidth(Double.MAX_VALUE);
        Label porcentaje = new Label("");
        HBox fila = new HBox(8, barra, porcentaje);
        HBox.setHgrow(barra, Priority.ALWAYS);
        fila.setAlignment(Pos.CENTER_LEFT);
        AtomicBoolean cancelar = new AtomicBoolean(false);
        Button bc = boton("Cancelar", () -> cancelar.set(true));
        bc.setVisible(conCancelar);
        bc.setManaged(conCancelar);
        HBox botones = new HBox(bc);
        botones.setAlignment(Pos.CENTER_RIGHT);
        VBox caja = new VBox(10, t, fila, detalle, botones);
        caja.setPadding(new Insets(20));
        caja.setPrefWidth(480);
        caja.getStyleClass().addAll(Style.CONTAINER.css());
        Scene sc = new Scene(caja);
        if (w != null && w.getScene() != null) {
            sc.getStylesheets().addAll(w.getScene().getStylesheets());
        }
        s.setScene(sc);
        s.setOnCloseRequest(e -> {
            e.consume();
            cancelar.set(true);
        });
        Progreso p = new Progreso() {
            private long ultimo;

            @Override
            public void poner(double fraccion, String texto) {
                long ahora = System.currentTimeMillis();
                if (ahora - ultimo < 80 && fraccion < 1) {
                    return;
                }
                ultimo = ahora;
                Platform.runLater(() -> {
                    if (fraccion < 0) {
                        barra.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                        porcentaje.setText("");
                    } else {
                        barra.setProgress(Math.min(1, fraccion));
                        porcentaje.setText(Math.round(Math.min(1, fraccion) * 100) + " %");
                    }
                    if (texto != null) {
                        detalle.setText(texto);
                    }
                });
            }

            @Override
            public boolean cancelado() {
                return cancelar.get();
            }
        };
        Thread hilo = new Thread(() -> {
            try {
                T r = trabajo.hacer(p);
                Platform.runLater(() -> {
                    s.close();
                    if (listo != null) {
                        listo.accept(r);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    s.close();
                    if (fallo != null) {
                        fallo.accept(e);
                    } else {
                        error(n, textoError(e));
                    }
                });
            }
        }, "foliado-trabajo");
        hilo.setDaemon(true);
        hilo.start();
        s.show();
    }

    static String textoError(Throwable e) {
        if (e instanceof Motor.ErrorProtegido) {
            return e.getMessage();
        }
        if (e instanceof Motor.ErrorDanado) {
            return e.getMessage();
        }
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            m = e.getClass().getSimpleName();
        }
        if (e instanceof java.nio.file.AccessDeniedException || m.toLowerCase().contains("being used by another")
                || m.toLowerCase().contains("access is denied")) {
            return "No se pudo guardar: el archivo está abierto en otro programa o no hay permiso en esa carpeta.\n\n" + m;
        }
        return m;
    }

    // ------------------------------------------------------------ imágenes
    static WritableImage imagenFx(BufferedImage b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = b.getRGB(0, 0, w, h, null, 0, w);
        WritableImage img = new WritableImage(w, h);
        img.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        return img;
    }

    static String colorWeb(java.awt.Color c) {
        return Motor.hexDeColor(c);
    }

    static javafx.scene.paint.Color fx(java.awt.Color c) {
        return javafx.scene.paint.Color.rgb(c.getRed(), c.getGreen(), c.getBlue());
    }

    static java.awt.Color awt(javafx.scene.paint.Color c) {
        return new java.awt.Color((float) c.getRed(), (float) c.getGreen(), (float) c.getBlue());
    }

    static TextField campo(String valor) {
        TextField t = new TextField(valor == null ? "" : valor);
        t.setMaxWidth(Double.MAX_VALUE);
        return t;
    }
}
