/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import org.kordamp.ikonli.unicons.UniconsLine;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * La vista previa real: la hoja con el folio o las leyendas puestas, sin guardar nada. Se actualiza sola cada vez
 * que cambia una opción. Se puede hacer clic y arrastrar sobre la hoja.
 */
class VistaPrevia extends BorderPane {

    /** Lo que la pantalla quiere saber del ratón (fracciones 0..1 de la hoja y movimientos en puntos). */
    interface Raton {
        void clic(double fx, double fy);

        void arrastre(double fx, double fy, double dxPuntos, double dyPuntos);

        void soltar(double fx, double fy, double dxPuntos, double dyPuntos);
    }

    private static final ExecutorService HILO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "foliado-vista-previa");
        t.setDaemon(true);
        return t;
    });

    final Label lblHoja = new Label("");
    final Label lblTamano = new Label("");
    final Label lblAyuda = new Label("");
    private final ImageView imagen = new ImageView();
    private final Pane marcas = new Pane();
    private final StackPane area = new StackPane();
    private final VBox zona;
    private final VBox arriba = new VBox();
    private final PauseTransition espera = new PauseTransition(Duration.millis(150));
    private final AtomicLong turno = new AtomicLong();

    private Motor.Previa previa;
    private int indice, total;
    private Supplier<Motor.Dibujo> dibujo = () -> null;
    private Runnable alCambiarHoja = () -> {
    };
    private Raton raton;
    private double inicioX, inicioY;
    private boolean arrastrando;

    VistaPrevia(String textoZona, String subZona, List<Node> botonesZona, Consumer<List<File>> recibir) {
        getStyleClass().add("foliado-vista-previa");
        Button ant = Ui.boton("Anterior", UniconsLine.ANGLE_LEFT, () -> ir(-1));
        Button sig = Ui.boton("Siguiente", UniconsLine.ANGLE_RIGHT, () -> ir(1));
        lblHoja.setStyle("-fx-font-weight: bold;");
        HBox nav = new HBox(8, ant, sig, lblHoja, lblTamano);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setPadding(new Insets(6, 8, 2, 8));
        lblAyuda.setWrapText(true);
        lblAyuda.setOpacity(0.75);
        lblAyuda.setPadding(new Insets(0, 8, 6, 8));
        arriba.getChildren().addAll(nav, lblAyuda);
        setTop(arriba);

        Label t1 = new Label(textoZona);
        t1.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");
        Label t2 = new Label(subZona);
        t2.setWrapText(true);
        HBox bz = new HBox(8);
        bz.getChildren().addAll(botonesZona);
        bz.setAlignment(Pos.CENTER);
        zona = new VBox(10, t1, t2, bz);
        zona.setAlignment(Pos.CENTER);
        zona.setPadding(new Insets(30));
        zona.setMaxSize(520, 200);
        zona.setStyle("-fx-border-color: -fx-accent; -fx-border-style: dashed; -fx-border-width: 2; "
                + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: rgba(127,127,127,0.06);");

        imagen.setPreserveRatio(true);
        imagen.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 10, 0, 0, 2);");
        marcas.setMouseTransparent(true);
        marcas.setManaged(false);
        area.getChildren().addAll(imagen, marcas, zona);
        area.setPadding(new Insets(16));
        // el área toma el espacio que sobra (la imagen nunca la agranda ni se sale de ella)
        area.setMinSize(150, 150);
        area.setPrefSize(150, 150);
        Rectangle recorte = new Rectangle();
        recorte.widthProperty().bind(area.widthProperty());
        recorte.heightProperty().bind(area.heightProperty());
        area.setClip(recorte);
        area.setStyle("-fx-background-color: rgba(127,127,127,0.12);");
        setCenter(area);
        setMinSize(250, 250);

        area.widthProperty().addListener((o, a, b) -> refrescar());
        area.heightProperty().addListener((o, a, b) -> refrescar());
        espera.setOnFinished(e -> dibujar());

        imagen.setOnMousePressed(this::presionar);
        imagen.setOnMouseDragged(this::mover);
        imagen.setOnMouseReleased(this::soltar);

        // arrastrar y soltar archivos o carpetas
        for (Node n : List.of(area, this)) {
            n.setOnDragOver(e -> {
                if (e.getDragboard().hasFiles()) {
                    e.acceptTransferModes(TransferMode.COPY);
                }
                e.consume();
            });
            n.setOnDragDropped(e -> {
                boolean ok = e.getDragboard().hasFiles();
                if (ok) {
                    List<File> f = List.copyOf(e.getDragboard().getFiles());
                    Platform.runLater(() -> recibir.accept(f));
                }
                e.setDropCompleted(ok);
                e.consume();
            });
        }
        mostrarZona(true);
    }

    /** Para poner la barra "Personalizar hojas" debajo de la navegación. */
    void ponerDebajoDeNavegacion(Node n) {
        arriba.getChildren().add(n);
    }

    void alCambiarHoja(Runnable r) {
        this.alCambiarHoja = r;
    }

    void raton(Raton r) {
        this.raton = r;
    }

    void dibujo(Supplier<Motor.Dibujo> d) {
        this.dibujo = d;
    }

    private void mostrarZona(boolean si) {
        zona.setVisible(si);
        zona.setManaged(si);
        imagen.setVisible(!si);
    }

    /** Muestra un PDF (el anterior se cierra). */
    void abrir(Motor.Previa p) {
        cerrar();
        this.previa = p;
        this.total = p == null ? 0 : p.hojas();
        this.indice = 0;
        mostrarZona(p == null);
        alCambiarHoja.run();
        refrescar();
    }

    void cerrar() {
        if (previa != null) {
            Motor.Previa vieja = previa;
            previa = null;
            HILO.submit(() -> {
                try {
                    vieja.close();
                } catch (Exception e) {
                    // nada
                }
            });
        }
        imagen.setImage(null);
        marcas.getChildren().clear();
        lblHoja.setText("");
        lblTamano.setText("");
        total = 0;
        mostrarZona(true);
    }

    boolean hayPdf() {
        return previa != null;
    }

    int indice() {
        return indice;
    }

    int total() {
        return total;
    }

    float[] tamanoHoja() {
        return previa == null ? new float[] { 612, 792 } : previa.tamano(indice);
    }

    void ir(int paso) {
        if (previa != null) {
            int nuevo = Math.max(0, Math.min(total - 1, indice + paso));
            if (nuevo != indice) {
                indice = nuevo;
                alCambiarHoja.run();
                refrescar();
            }
        }
    }

    void irA(int i) {
        if (previa != null) {
            indice = Math.max(0, Math.min(total - 1, i));
            alCambiarHoja.run();
            refrescar();
        }
    }

    /** Pide redibujar (espera 150 ms por si siguen cambiando opciones). */
    void refrescar() {
        espera.playFromStart();
    }

    private void dibujar() {
        Motor.Previa p = previa;
        if (p == null) {
            return;
        }
        if (area.getWidth() < 60 || area.getHeight() < 60) {
            return; // todavía no tiene tamaño: se dibuja cuando lo tenga
        }
        float[] tam = p.tamano(indice);
        double W = Math.max(150, area.getWidth() - 40), H = Math.max(150, area.getHeight() - 40);
        int ancho = (int) Math.max(100, Math.min(W, H * tam[0] / tam[1]));
        Motor.Dibujo d;
        try {
            d = dibujo.get();
        } catch (RuntimeException e) {
            return; // alguna opción todavía está a medio escribir
        }
        int i = indice;
        long mio = turno.incrementAndGet();
        lblTamano.setText("·  " + Motor.describirHoja(tam[0], tam[1]));
        HILO.submit(() -> {
            if (mio != turno.get()) {
                return; // ya hay un pedido más nuevo
            }
            try {
                BufferedImage b = p.imagen(i, ancho, d);
                var fx = Ui.imagenFx(b);
                Platform.runLater(() -> {
                    if (mio == turno.get() && previa == p) {
                        imagen.setImage(fx);
                        imagen.setFitWidth(fx.getWidth());
                        imagen.setFitHeight(fx.getHeight());
                        marcas.getChildren().clear();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> lblAyuda.setText("No se pudo mostrar la hoja: " + Ui.textoError(e)));
            }
        });
    }

    // ------------------------------------------------------------ ratón
    private double[] fraccion(MouseEvent e) {
        double w = imagen.getBoundsInLocal().getWidth(), h = imagen.getBoundsInLocal().getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        return new double[] { Math.min(1, Math.max(0, e.getX() / w)), Math.min(1, Math.max(0, e.getY() / h)) };
    }

    /** Puntos de la hoja por cada píxel de la imagen. */
    private double escala() {
        double w = imagen.getBoundsInLocal().getWidth();
        return w <= 0 ? 1 : tamanoHoja()[0] / w;
    }

    private void presionar(MouseEvent e) {
        if (previa == null || raton == null) {
            return;
        }
        inicioX = e.getX();
        inicioY = e.getY();
        arrastrando = true;
        double[] f = fraccion(e);
        if (f != null) {
            raton.clic(f[0], f[1]);
        }
    }

    private void mover(MouseEvent e) {
        if (!arrastrando || raton == null) {
            return;
        }
        double[] f = fraccion(e);
        if (f != null) {
            raton.arrastre(f[0], f[1], (e.getX() - inicioX) * escala(), (e.getY() - inicioY) * escala());
        }
    }

    private void soltar(MouseEvent e) {
        if (!arrastrando || raton == null) {
            return;
        }
        arrastrando = false;
        marcas.getChildren().clear();
        double[] f = fraccion(e);
        if (f != null) {
            raton.soltar(f[0], f[1], (e.getX() - inicioX) * escala(), (e.getY() - inicioY) * escala());
        }
    }

    private double[] enMarcas(double xImagen, double yImagen) {
        var p = imagen.localToScene(xImagen, yImagen);
        var q = marcas.sceneToLocal(p);
        return new double[] { q.getX(), q.getY() };
    }

    /** Recuadro punteado donde quedará el folio (tamaño en puntos de la hoja). */
    void marcaRecuadro(double fx, double fy, double anchoPuntos, double altoPuntos, Color color) {
        marcas.getChildren().clear();
        double w = imagen.getBoundsInLocal().getWidth(), h = imagen.getBoundsInLocal().getHeight();
        double esc = 1 / escala();
        double mw = Math.max(30, anchoPuntos * esc), mh = Math.max(10, altoPuntos * esc);
        double[] c = enMarcas(fx * w, fy * h);
        Rectangle r = new Rectangle(c[0] - mw / 2, c[1] - mh / 2, mw, mh);
        r.setFill(Color.TRANSPARENT);
        r.setStroke(color);
        r.setStrokeWidth(2);
        r.getStrokeDashArray().addAll(6.0, 3.0);
        marcas.getChildren().add(r);
    }

    /** Flecha desde donde empezó el arrastre. */
    void marcaFlecha(double dxPuntos, double dyPuntos) {
        marcas.getChildren().clear();
        double esc = 1 / escala();
        double[] a = enMarcas(inicioX, inicioY);
        double[] b = enMarcas(inicioX + dxPuntos * esc, inicioY + dyPuntos * esc);
        Line l = new Line(a[0], a[1], b[0], b[1]);
        l.setStroke(Color.web("#2b7de9"));
        l.setStrokeWidth(2.5);
        l.getStrokeDashArray().addAll(6.0, 3.0);
        marcas.getChildren().add(l);
    }
}
