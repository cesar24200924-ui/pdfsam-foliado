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
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.unicons.UniconsLine;
import org.pdfsam.ui.components.support.Style;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Girar (y organizar) PDF viendo las hojas en miniatura:
 * <ul>
 * <li>Clic: elegir una hoja. Ctrl + clic: varias. Shift + clic: un rango. Ctrl + A: todas.</li>
 * <li>Botones al pasar el ratón sobre la hoja: girar izquierda/derecha, duplicar, quitar.</li>
 * <li>Arrastrar una hoja: cambiarla de lugar. Doble clic: verla en grande.</li>
 * <li>Insertar otro PDF u hoja en blanco, guardar solo las elegidas como PDF nuevo.</li>
 * <li>Deshacer / Rehacer (Ctrl + Z / Ctrl + Y). Supr: quitar las elegidas.</li>
 * <li>Girar una carpeta o un comprimido (ZIP, RAR, 7z...) completo, respetando las subcarpetas.</li>
 * </ul>
 * El PDF original nunca se modifica: se guarda una copia.
 */
class GirarPanel extends BorderPane {

    /** Una hoja en pantalla: de qué PDF y qué hoja viene (archivo null = hoja en blanco) y cuánto se giró. */
    private static final class Hoja {
        final File archivo;
        final int pagina;
        int giro;
        final float anchoBlanco, altoBlanco;

        Hoja(File archivo, int pagina, int giro, float anchoBlanco, float altoBlanco) {
            this.archivo = archivo;
            this.pagina = pagina;
            this.giro = giro;
            this.anchoBlanco = anchoBlanco;
            this.altoBlanco = altoBlanco;
        }

        Hoja copia() {
            return new Hoja(archivo, pagina, giro, anchoBlanco, altoBlanco);
        }

        String clave() {
            return archivo == null ? "blanco:" + anchoBlanco + "x" + altoBlanco : archivo.getAbsolutePath() + "#" + pagina;
        }
    }

    private static final ExecutorService HILO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "girar-miniaturas");
        t.setDaemon(true);
        return t;
    });

    private File archivo;
    /** PDF abiertos para ver las miniaturas (el principal y los insertados) */
    private final Map<File, Motor.Previa> previas = new HashMap<>();
    private final Map<File, String> claves = new HashMap<>();
    private final List<Hoja> hojas = new ArrayList<>();
    private final Set<Integer> seleccion = new TreeSet<>(); // posiciones en pantalla
    private Integer ultimo;
    private final Deque<List<Hoja>> deshacer = new ArrayDeque<>(), rehacer = new ArrayDeque<>();

    /** miniaturas ya hechas: clave de la hoja -> imagen (y el ancho con que se hizo) */
    private final Map<String, Image> imagenes = new HashMap<>();
    private final Map<String, Integer> anchoImagen = new HashMap<>();
    private final Set<String> pedidas = new LinkedHashSet<>();

    private final FlowPane cuadricula = new FlowPane(14, 14);
    private final ScrollPane scroll = new ScrollPane(cuadricula);
    private final StackPane centro = new StackPane();
    private final VBox zona;
    private final Slider tamano = new Slider(90, 320, 150);
    private final Label lblArchivo = new Label("");
    private final Label lblSel = new Label("Ninguna hoja elegida.");
    private final Label lblCambios = Controles.nota("");
    private final Button btnDeshacer, btnRehacer;
    private final List<Button> conPdf = new ArrayList<>();
    private int origenArrastre = -1;

    GirarPanel() {
        getStyleClass().addAll(Style.CONTAINER.css());
        Ui.estilo(this);

        // ---------- barra de arriba
        Button elegir = Ui.boton("Elegir PDF", UniconsLine.FILE_ALT, this::elegir);
        Button lote = Ui.boton("Girar una carpeta o comprimido", UniconsLine.FOLDER, () -> {
        });
        ContextMenu menu = new ContextMenu();
        MenuItem mc = new MenuItem("Elegir carpeta");
        mc.setOnAction(e -> {
            File f = Ui.elegirCarpeta(this, "Elegir la carpeta (se incluyen todas las subcarpetas)", null);
            if (f != null) {
                girarLote(f);
            }
        });
        MenuItem mz = new MenuItem("Elegir comprimido (ZIP, RAR, 7z...)");
        mz.setOnAction(e -> {
            File f = Ui.elegirComprimido(this);
            if (f != null) {
                girarLote(f);
            }
        });
        menu.getItems().addAll(mc, mz);
        lote.setOnAction(e -> menu.show(lote, Side.BOTTOM, 0, 0));

        Button izq = conPdf(espejo(Ui.boton("Girar izq.", UniconsLine.REDO, () -> girarSeleccion(-90))));
        Button der = conPdf(Ui.boton("Girar der.", UniconsLine.REDO, () -> girarSeleccion(90)));
        Button voltear = conPdf(Ui.boton("Voltear 180°", UniconsLine.SYNC, () -> girarSeleccion(180)));
        Button todasIzq = conPdf(espejo(Ui.boton("Todas a la izq.", UniconsLine.REDO, () -> girarTodas(-90))));
        Button todasDer = conPdf(Ui.boton("Todas a la der.", UniconsLine.REDO, () -> girarTodas(90)));
        Button duplicar = conPdf(Ui.boton("Duplicar", UniconsLine.COPY_ALT, this::duplicarSeleccion));
        Button quitar = conPdf(Ui.boton("Quitar", UniconsLine.TRASH_ALT, this::quitarSeleccion));
        Button insertar = conPdf(Ui.boton("Insertar PDF", UniconsLine.FILE_PLUS_ALT, this::insertarPdf));
        Button blanco = conPdf(Ui.boton("Hoja en blanco", UniconsLine.FILE_BLANK, this::hojaEnBlanco));
        Button todo = conPdf(Ui.boton("Seleccionar todo", UniconsLine.CHECK_SQUARE, this::seleccionarTodo));
        btnDeshacer = conPdf(espejo(Ui.boton("Deshacer", UniconsLine.REDO, this::deshacer)));
        btnRehacer = conPdf(Ui.boton("Rehacer", UniconsLine.REDO, this::rehacer));
        Button guardar = conPdf(Ui.boton("Guardar PDF", UniconsLine.SAVE, this::guardar));
        guardar.getStyleClass().addAll("footer-button", "run-button");

        tamano.setPrefWidth(130);
        tamano.valueProperty().addListener((o, a, b) -> redibujar());
        HBox zoom = new HBox(6, new Label("Tamaño"), tamano);
        zoom.setAlignment(Pos.CENTER_LEFT);

        FlowPane barra = new FlowPane(6, 6, elegir, lote, sep(), izq, der, voltear, todasIzq, todasDer, sep(),
                duplicar, quitar, insertar, blanco, sep(), todo, btnDeshacer, btnRehacer, sep(), zoom, guardar,
                Ui.boton("Manual", UniconsLine.BOOK_OPEN, () -> ManualPanel.abrir(this, "girar")));
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(8));
        lblArchivo.setStyle("-fx-font-weight: bold;");
        lblArchivo.setPadding(new Insets(0, 8, 6, 10));
        VBox arriba = new VBox(barra, lblArchivo);
        arriba.setStyle("-fx-border-color: transparent transparent rgba(127,127,127,0.35) transparent;");
        setTop(arriba);

        // ---------- miniaturas
        cuadricula.setPadding(new Insets(14));
        cuadricula.setAlignment(Pos.TOP_LEFT);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent;");
        scroll.vvalueProperty().addListener((o, a, b) -> pedirVisibles());
        scroll.viewportBoundsProperty().addListener((o, a, b) -> pedirVisibles());
        cuadricula.setOnMouseClicked(e -> {
            if (e.getTarget() == cuadricula) {
                seleccion.clear();
                actualizarSeleccion();
            }
        });

        Label t1 = new Label("Arrastre aquí el PDF");
        t1.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");
        Label t2 = new Label("O elíjalo con el botón. Verá todas sus hojas en miniatura.");
        Button bElegir = Ui.boton("Elegir PDF", UniconsLine.FILE_ALT, this::elegir);
        Button bLote = Ui.boton("Girar una carpeta o comprimido", UniconsLine.FOLDER,
                () -> menu.show(lote, Side.BOTTOM, 0, 0));
        HBox bz = new HBox(8, bElegir, bLote);
        bz.setAlignment(Pos.CENTER);
        zona = new VBox(10, t1, t2, bz);
        zona.setAlignment(Pos.CENTER);
        zona.setPadding(new Insets(30));
        zona.setMaxSize(560, 200);
        zona.getStyleClass().add("foliado-zona");
        centro.getChildren().addAll(scroll, zona);
        centro.setStyle("-fx-background-color: rgba(127,127,127,0.12);");
        centro.setMinSize(200, 200);
        centro.setPrefSize(200, 200);
        setCenter(centro);
        for (Node n : List.of(centro, cuadricula, scroll)) {
            n.setOnDragOver(e -> {
                if (e.getDragboard().hasFiles()) {
                    e.acceptTransferModes(TransferMode.COPY);
                    e.consume();
                }
            });
            n.setOnDragDropped(e -> {
                if (e.getDragboard().hasFiles()) {
                    List<File> f = List.copyOf(e.getDragboard().getFiles());
                    e.setDropCompleted(true);
                    e.consume();
                    Platform.runLater(() -> recibir(f));
                }
            });
        }

        // ---------- panel de la derecha
        Label titulo = new Label("Selección");
        titulo.setStyle("-fx-font-size: 1.2em; -fx-font-weight: bold;");
        lblSel.setWrapText(true);
        Label ayuda = new Label("Arrastre una hoja para cambiarla de lugar. Doble clic para verla en grande. "
                + "Ctrl + clic para elegir varias. Shift + clic para un rango. Al pasar el ratón sobre una hoja "
                + "aparecen los botones para girarla, duplicarla o quitarla. Supr quita las elegidas.");
        ayuda.setWrapText(true);
        ayuda.getStyleClass().add("foliado-ayuda");
        Button sDer = conPdf(Ui.boton("Girar seleccionadas a la der.", UniconsLine.REDO, () -> girarSeleccion(90)));
        Button sIzq = conPdf(
                espejo(Ui.boton("Girar seleccionadas a la izq.", UniconsLine.REDO, () -> girarSeleccion(-90))));
        Button sQuitar = conPdf(Ui.boton("Quitar seleccionadas", UniconsLine.TRASH_ALT, this::quitarSeleccion));
        sQuitar.setStyle("-fx-text-fill: #e05252;");
        Button sExtraer = conPdf(Ui.boton("Guardar selección como PDF", UniconsLine.FILE_EXPORT, this::extraer));
        Button sLote = Ui.boton("Girar una carpeta o comprimido", UniconsLine.FOLDER, () -> menu.show(lote, Side.BOTTOM, 0, 0));
        for (Button b : List.of(sDer, sIzq, sQuitar, sExtraer, sLote)) {
            b.setMaxWidth(Double.MAX_VALUE);
        }
        VBox panel = new VBox(8, titulo, lblSel, lblCambios, ayuda, sDer, sIzq, sQuitar, sExtraer, sLote);
        panel.setPadding(new Insets(14));
        panel.setPrefWidth(270);
        panel.setMinWidth(230);
        panel.setStyle("-fx-border-color: transparent transparent transparent rgba(127,127,127,0.35);");
        ScrollPane scrollPanel = new ScrollPane(panel);
        scrollPanel.setFitToWidth(true);
        scrollPanel.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPanel.setPrefWidth(285);
        setRight(scrollPanel);

        // teclas
        sceneProperty().addListener((o, a, s) -> {
            if (s != null) {
                s.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN),
                        () -> siVisible(this::deshacer));
                s.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN),
                        () -> siVisible(this::rehacer));
                s.getAccelerators().put(new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN),
                        () -> siVisible(this::seleccionarTodo));
            }
        });
        // Supr: solo cuando el foco está en las miniaturas (para no borrar hojas al escribir en otra parte)
        scroll.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE && !seleccion.isEmpty()) {
                quitarSeleccion();
                e.consume();
            }
        });
        mostrarZona(true);
        actualizarBotones();
    }

    private static Separator sep() {
        return new Separator(Orientation.VERTICAL);
    }

    /** El ícono de "girar a la derecha" al revés = a la izquierda (o deshacer). */
    private static Button espejo(Button b) {
        b.getGraphic().setScaleX(-1);
        return b;
    }

    private Button conPdf(Button b) {
        conPdf.add(b);
        return b;
    }

    private void siVisible(Runnable r) {
        if (getScene() != null && getParent() != null && archivo != null) {
            r.run();
        }
    }

    private void mostrarZona(boolean si) {
        zona.setVisible(si);
        zona.setManaged(si);
        scroll.setVisible(!si);
    }

    // ------------------------------------------------------------ abrir
    private void elegir() {
        List<File> f = Ui.elegirPdfs(this, false);
        if (f != null && !f.isEmpty()) {
            recibir(f);
        }
    }

    void recibir(List<File> rutas) {
        if (rutas.isEmpty()) {
            return;
        }
        File primero = rutas.get(0);
        if (rutas.size() == 1 && (primero.isDirectory() || Comprimidos.es(primero.getName()))) {
            girarLote(primero);
            return;
        }
        File pdf = rutas.stream().filter(f -> f.isFile() && f.getName().toLowerCase().endsWith(".pdf")).findFirst()
                .orElse(null);
        if (pdf == null) {
            Ui.aviso(this, "Arrastre o elija un archivo PDF.");
            return;
        }
        if (archivo != null) {
            // ya hay un PDF abierto: ¿abrir este otro o insertarlo?
            int r = Ui.elegir(this, "Ya hay un PDF abierto", "¿Qué quiere hacer con \"" + pdf.getName() + "\"?",
                    "Insertar sus hojas", "Abrir en lugar del otro");
            if (r < 0) {
                return;
            }
            if (r == 0) {
                insertar(pdf);
                return;
            }
            if (hayCambios() && !Ui.pregunta(this,
                    "Tiene cambios sin guardar en el PDF que está abierto. ¿Quiere abrir el otro de todas formas?",
                    "Cambios sin guardar")) {
                return;
            }
        }
        Motor.Previa p = abrirPrevia(pdf);
        if (p == null) {
            return;
        }
        cerrarTodo();
        previas.put(pdf, p);
        archivo = pdf;
        hojas.clear();
        for (int i = 0; i < p.hojas(); i++) {
            hojas.add(new Hoja(pdf, i, 0, 0, 0));
        }
        seleccion.clear();
        ultimo = null;
        deshacer.clear();
        rehacer.clear();
        imagenes.clear();
        anchoImagen.clear();
        pedidas.clear();
        lblArchivo.setText(pdf.getName() + "  ·  " + p.hojas() + " hojas");
        mostrarZona(false);
        actualizarSeleccion();
    }

    /** Abre un PDF para ver sus hojas (pide la contraseña si tiene). null si no se pudo. */
    private Motor.Previa abrirPrevia(File pdf) {
        String c = claves.get(pdf);
        if (c == null && Motor.pideClave(pdf)) {
            c = Ui.pedirClave(this, pdf.getName(), null);
            if (c == null) {
                return null;
            }
        }
        try {
            Motor.Previa p = new Motor.Previa(pdf, c);
            if (c != null) {
                claves.put(pdf, c);
            }
            return p;
        } catch (Exception e) {
            Ui.error(this, Ui.textoError(e));
            return null;
        }
    }

    private void cerrarTodo() {
        List<Motor.Previa> viejas = new ArrayList<>(previas.values());
        previas.clear();
        claves.clear();
        HILO.submit(() -> {
            for (Motor.Previa v : viejas) {
                try {
                    v.close();
                } catch (Exception e) {
                    // nada
                }
            }
        });
    }

    // ------------------------------------------------------------ miniaturas
    private int lado() {
        return (int) Math.round(tamano.getValue());
    }

    /** Vuelve a armar la cuadrícula (después de cambiar el orden, el giro o el tamaño). */
    private void redibujar() {
        List<Node> celdas = new ArrayList<>();
        for (int pos = 0; pos < hojas.size(); pos++) {
            celdas.add(celda(pos));
        }
        cuadricula.getChildren().setAll(celdas);
        Platform.runLater(this::pedirVisibles);
        actualizarBotones();
    }

    private String detalle(Hoja h, int pos) {
        if (h.archivo == null) {
            return "  (en blanco)";
        }
        if (!h.archivo.equals(archivo)) {
            return "  (" + Motor.sinExtension(h.archivo.getName()) + ", hoja " + (h.pagina + 1) + ")";
        }
        int veces = 0;
        for (int i = 0; i < pos; i++) {
            if (hojas.get(i).clave().equals(h.clave())) {
                veces++;
            }
        }
        if (veces > 0) {
            return "  (copia de " + (h.pagina + 1) + ")";
        }
        return h.pagina != pos ? "  (antes " + (h.pagina + 1) + ")" : "";
    }

    private Node celda(int pos) {
        Hoja h = hojas.get(pos);
        int lado = lado();
        ImageView iv = new ImageView();
        iv.setPreserveRatio(true);
        iv.setFitWidth(lado - 12);
        iv.setFitHeight(lado - 12);
        iv.setSmooth(true);
        iv.setRotate(h.giro);
        Image img = h.archivo == null ? blanco(h) : imagenes.get(h.clave());
        if (img != null) {
            iv.setImage(img);
        }
        StackPane marco = new StackPane(iv);
        marco.setPrefSize(lado, lado);
        marco.setMinSize(lado, lado);
        marco.setMaxSize(lado, lado);
        boolean elegida = seleccion.contains(pos);
        marco.setStyle("-fx-background-color: " + (elegida ? "rgba(43,125,233,0.18)" : "white") + "; "
                + "-fx-border-color: " + (elegida ? "#2b7de9" : "rgba(0,0,0,0.18)") + "; -fx-border-width: "
                + (elegida ? 3 : 1) + "; -fx-background-radius: 4; -fx-border-radius: 4;");
        if (img == null) {
            Label cargando = new Label("Hoja " + (h.pagina + 1) + "…");
            cargando.setOpacity(0.5);
            marco.getChildren().add(cargando);
        }

        // botones al pasar el ratón
        HBox botones = new HBox(3, botonChico(UniconsLine.REDO, "Girar a la izquierda", true, () -> girarUna(pos, -90)),
                botonChico(UniconsLine.REDO, "Girar a la derecha", false, () -> girarUna(pos, 90)),
                botonChico(UniconsLine.COPY_ALT, "Duplicar", false, () -> duplicar(List.of(pos))),
                botonChico(UniconsLine.TRASH_ALT, "Quitar", false, () -> quitar(List.of(pos))));
        botones.setAlignment(Pos.TOP_CENTER);
        botones.setPadding(new Insets(4));
        botones.setPickOnBounds(false);
        botones.setVisible(false);
        StackPane.setAlignment(botones, Pos.TOP_CENTER);
        marco.getChildren().add(botones);
        if (h.giro % 360 != 0) {
            Label g = new Label("↻ " + (h.giro > 180 ? h.giro - 360 : h.giro) + "°");
            g.setStyle("-fx-background-color: #e8a400; -fx-text-fill: white; -fx-padding: 1 5 1 5; "
                    + "-fx-background-radius: 3; -fx-font-size: 0.85em;");
            StackPane.setAlignment(g, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(g, new Insets(4));
            marco.getChildren().add(g);
        }

        Label numero = new Label((pos + 1) + detalle(h, pos));
        numero.setMaxWidth(lado + 20);
        numero.setStyle(elegida ? "-fx-font-weight: bold; -fx-text-fill: #2b7de9;" : "");
        VBox celda = new VBox(4, marco, numero);
        celda.setAlignment(Pos.TOP_CENTER);
        celda.setUserData(pos);
        celda.setOnMouseEntered(e -> botones.setVisible(true));
        celda.setOnMouseExited(e -> botones.setVisible(false));
        celda.setOnMouseClicked(e -> clic(e, pos));

        // arrastrar para cambiar de lugar
        celda.setOnDragDetected(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            origenArrastre = pos;
            Dragboard db = celda.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString("hoja:" + pos);
            db.setContent(cc);
            var foto = marco.snapshot(null, null);
            db.setDragView(foto, foto.getWidth() / 2, foto.getHeight() / 2);
            e.consume();
        });
        celda.setOnDragOver(e -> {
            if (origenArrastre >= 0 && e.getGestureSource() != celda) {
                e.acceptTransferModes(TransferMode.MOVE);
                boolean despues = e.getX() > celda.getWidth() / 2;
                celda.setStyle(despues ? "-fx-border-color: transparent #2b7de9 transparent transparent; "
                        + "-fx-border-width: 0 4 0 0;" : "-fx-border-color: transparent transparent transparent "
                        + "#2b7de9; -fx-border-width: 0 0 0 4;");
                e.consume();
            }
        });
        celda.setOnDragExited(e -> celda.setStyle(""));
        celda.setOnDragDropped(e -> {
            if (origenArrastre >= 0) {
                boolean despues = e.getX() > celda.getWidth() / 2;
                mover(origenArrastre, pos + (despues ? 1 : 0));
                origenArrastre = -1;
                e.setDropCompleted(true);
                e.consume();
            }
        });
        celda.setOnDragDone(e -> origenArrastre = -1);
        return celda;
    }

    private static Image blanco(Hoja h) {
        int w = 60, ht = (int) Math.max(20, Math.round(60 * h.altoBlanco / Math.max(1, h.anchoBlanco)));
        WritableImage img = new WritableImage(w, ht);
        for (int y = 0; y < ht; y++) {
            for (int x = 0; x < w; x++) {
                boolean borde = x == 0 || y == 0 || x == w - 1 || y == ht - 1;
                img.getPixelWriter().setArgb(x, y, borde ? 0xffcfcfcf : 0xffffffff);
            }
        }
        return img;
    }

    private Button botonChico(Ikon icono, String ayuda, boolean alReves, Runnable accion) {
        Button b = Ui.boton("", icono, accion);
        if (alReves) {
            b.getGraphic().setScaleX(-1);
        }
        b.setTooltip(new Tooltip(ayuda));
        // de color, para que se vean bien sobre la hoja blanca (el de quitar en rojo)
        boolean quitar = icono == UniconsLine.TRASH_ALT;
        String fondo = quitar ? "#d32f2f" : "#1f6fd1";
        b.setStyle("-fx-background-color: " + fondo + "; -fx-border-color: rgba(0,0,0,0.35); "
                + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 4 6 4 6; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 4, 0, 0, 1);");
        if (b.getGraphic() instanceof org.kordamp.ikonli.javafx.FontIcon fi) {
            fi.setIconColor(javafx.scene.paint.Color.WHITE);
            fi.setIconSize(16);
        }
        b.setFocusTraversable(false);
        return b;
    }

    /** Pide las miniaturas de las hojas que se están viendo (y las de cerca). */
    private void pedirVisibles() {
        if (archivo == null) {
            return;
        }
        Bounds vista = scroll.localToScene(scroll.getLayoutBounds());
        int lado = lado();
        for (Node n : cuadricula.getChildren()) {
            Bounds b = n.localToScene(n.getLayoutBounds());
            if (!b.intersects(vista.getMinX(), vista.getMinY() - 400, vista.getWidth(), vista.getHeight() + 800)) {
                continue;
            }
            int pos = (Integer) n.getUserData();
            if (pos >= hojas.size() || hojas.get(pos).archivo == null) {
                continue;
            }
            Hoja h = hojas.get(pos);
            Integer hecho = anchoImagen.get(h.clave());
            if ((hecho == null || hecho < lado * 0.9) && pedidas.add(h.clave())) {
                pedirImagen(h, lado);
            }
        }
    }

    private void pedirImagen(Hoja h, int lado) {
        Motor.Previa p = previas.get(h.archivo);
        String clave = h.clave();
        if (p == null) {
            pedidas.remove(clave);
            return;
        }
        int px = (int) Math.min(900, lado * 1.25);
        int pagina = h.pagina;
        HILO.submit(() -> {
            try {
                var fx = Ui.imagenFx(p.imagen(pagina, px, null));
                Platform.runLater(() -> {
                    pedidas.remove(clave);
                    if (!previas.containsValue(p)) {
                        return;
                    }
                    imagenes.put(clave, fx);
                    anchoImagen.put(clave, px);
                    ponerImagen(clave, fx);
                });
            } catch (Exception e) {
                Platform.runLater(() -> pedidas.remove(clave));
            }
        });
    }

    private void ponerImagen(String clave, Image img) {
        for (Node n : cuadricula.getChildren()) {
            int pos = (Integer) n.getUserData();
            if (pos < hojas.size() && hojas.get(pos).clave().equals(clave)) {
                StackPane marco = (StackPane) ((VBox) n).getChildren().get(0);
                ImageView iv = (ImageView) marco.getChildren().get(0);
                iv.setImage(img);
                marco.getChildren().removeIf(x -> x instanceof Label l && l.getText().startsWith("Hoja "));
            }
        }
    }

    // ------------------------------------------------------------ elegir hojas
    private void clic(MouseEvent e, int pos) {
        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }
        scroll.requestFocus();
        if (e.getClickCount() == 2) {
            verEnGrande(pos);
            return;
        }
        if (e.isShiftDown() && ultimo != null) {
            if (!e.isShortcutDown()) {
                seleccion.clear();
            }
            for (int i = Math.min(ultimo, pos); i <= Math.max(ultimo, pos); i++) {
                seleccion.add(i);
            }
        } else if (e.isShortcutDown()) {
            if (!seleccion.remove(pos)) {
                seleccion.add(pos);
            }
            ultimo = pos;
        } else {
            seleccion.clear();
            seleccion.add(pos);
            ultimo = pos;
        }
        actualizarSeleccion();
    }

    private void seleccionarTodo() {
        seleccion.clear();
        for (int i = 0; i < hojas.size(); i++) {
            seleccion.add(i);
        }
        actualizarSeleccion();
    }

    private void actualizarSeleccion() {
        seleccion.removeIf(i -> i >= hojas.size());
        if (seleccion.isEmpty()) {
            lblSel.setText("Ninguna hoja elegida.");
        } else if (seleccion.size() == hojas.size()) {
            lblSel.setText("Todas las hojas elegidas (" + hojas.size() + ").");
        } else {
            lblSel.setText(seleccion.size() == 1 ? "Hoja elegida: " + (seleccion.iterator().next() + 1) :
                    seleccion.size() + " hojas elegidas: " + Motor.textoHojas(seleccion));
        }
        List<Integer> giradas = new ArrayList<>();
        for (int i = 0; i < hojas.size(); i++) {
            if (hojas.get(i).giro % 360 != 0) {
                giradas.add(i);
            }
        }
        StringBuilder c = new StringBuilder();
        if (archivo != null) {
            c.append("El PDF queda con ").append(hojas.size()).append(hojas.size() == 1 ? " hoja." : " hojas.");
        }
        if (!giradas.isEmpty()) {
            c.append("\nGiradas: ").append(Motor.textoHojas(giradas));
        }
        if (archivo != null && hayCambios() && giradas.isEmpty()) {
            c.append("\nHay hojas cambiadas de lugar, quitadas o agregadas.");
        } else if (archivo != null && !hayCambios()) {
            c.append("\nSin cambios todavía.");
        }
        lblCambios.setText(c.toString());
        if (archivo != null) {
            lblArchivo.setText(archivo.getName() + "  ·  " + hojas.size() + " hojas");
        }
        redibujar();
    }

    // ------------------------------------------------------------ cambios (con deshacer)
    private List<Hoja> estado() {
        List<Hoja> r = new ArrayList<>();
        for (Hoja h : hojas) {
            r.add(h.copia());
        }
        return r;
    }

    private void poner(List<Hoja> e) {
        hojas.clear();
        hojas.addAll(e);
    }

    private void antes() {
        deshacer.push(estado());
        if (deshacer.size() > 200) {
            deshacer.removeLast();
        }
        rehacer.clear();
    }

    private void deshacer() {
        if (!deshacer.isEmpty()) {
            rehacer.push(estado());
            poner(deshacer.pop());
            actualizarSeleccion();
        }
    }

    private void rehacer() {
        if (!rehacer.isEmpty()) {
            deshacer.push(estado());
            poner(rehacer.pop());
            actualizarSeleccion();
        }
    }

    private boolean hayCambios() {
        Motor.Previa p = previas.get(archivo);
        if (p == null) {
            return false;
        }
        if (hojas.size() != p.hojas()) {
            return true;
        }
        for (int i = 0; i < hojas.size(); i++) {
            Hoja h = hojas.get(i);
            if (h.giro % 360 != 0 || h.pagina != i || !archivo.equals(h.archivo)) {
                return true;
            }
        }
        return false;
    }

    private static int normal(int g) {
        return ((g % 360) + 360) % 360;
    }

    private boolean hayElegidas() {
        if (seleccion.isEmpty()) {
            Ui.aviso(this, "Primero elija una o varias hojas (clic sobre ellas). Para elegir todas: Ctrl + A o "
                    + "\"Seleccionar todo\".");
            return false;
        }
        return true;
    }

    private void girarUna(int pos, int grados) {
        antes();
        hojas.get(pos).giro = normal(hojas.get(pos).giro + grados);
        actualizarSeleccion();
    }

    private void girarSeleccion(int grados) {
        if (!hayElegidas()) {
            return;
        }
        antes();
        for (Integer i : seleccion) {
            hojas.get(i).giro = normal(hojas.get(i).giro + grados);
        }
        actualizarSeleccion();
    }

    private void girarTodas(int grados) {
        antes();
        for (Hoja h : hojas) {
            h.giro = normal(h.giro + grados);
        }
        actualizarSeleccion();
    }

    private void quitarSeleccion() {
        if (hayElegidas()) {
            quitar(new ArrayList<>(seleccion));
        }
    }

    private void quitar(List<Integer> posiciones) {
        if (posiciones.size() >= hojas.size()) {
            Ui.aviso(this, "No se pueden quitar todas las hojas: el PDF tiene que quedar con al menos una.");
            return;
        }
        antes();
        List<Integer> orden = new ArrayList<>(posiciones);
        orden.sort((a, b) -> b - a);
        for (int i : orden) {
            hojas.remove(i);
        }
        seleccion.clear();
        ultimo = null;
        actualizarSeleccion();
    }

    private void duplicarSeleccion() {
        if (hayElegidas()) {
            duplicar(new ArrayList<>(seleccion));
        }
    }

    /** Cada hoja elegida queda con su copia justo al lado. */
    private void duplicar(List<Integer> posiciones) {
        antes();
        List<Integer> orden = new ArrayList<>(posiciones);
        orden.sort((a, b) -> b - a);
        for (int i : orden) {
            hojas.add(i + 1, hojas.get(i).copia());
        }
        seleccion.clear();
        actualizarSeleccion();
    }

    /** Dónde se insertan hojas nuevas: después de la última elegida (o al final). */
    private int lugarParaInsertar() {
        return seleccion.isEmpty() ? hojas.size() : ((TreeSet<Integer>) seleccion).last() + 1;
    }

    private void insertarPdf() {
        List<File> f = Ui.elegirPdfs(this, false);
        if (f != null && !f.isEmpty()) {
            insertar(f.get(0));
        }
    }

    private void insertar(File pdf) {
        Motor.Previa p = previas.get(pdf);
        if (p == null) {
            p = abrirPrevia(pdf);
            if (p == null) {
                return;
            }
            previas.put(pdf, p);
        }
        int lugar = lugarParaInsertar();
        antes();
        List<Hoja> nuevas = new ArrayList<>();
        for (int i = 0; i < p.hojas(); i++) {
            nuevas.add(new Hoja(pdf, i, 0, 0, 0));
        }
        hojas.addAll(lugar, nuevas);
        seleccion.clear();
        for (int k = 0; k < nuevas.size(); k++) {
            seleccion.add(lugar + k);
        }
        ultimo = lugar;
        actualizarSeleccion();
    }

    private void hojaEnBlanco() {
        int lugar = lugarParaInsertar();
        // del mismo tamaño que la hoja de al lado (o carta)
        float w = 612, h = 792;
        int vecina = Math.min(hojas.size() - 1, Math.max(0, lugar - 1));
        if (!hojas.isEmpty()) {
            Hoja v = hojas.get(vecina);
            if (v.archivo == null) {
                w = v.anchoBlanco;
                h = v.altoBlanco;
            } else if (previas.get(v.archivo) != null) {
                float[] t = previas.get(v.archivo).tamano(v.pagina);
                boolean deLado = v.giro % 180 != 0;
                w = deLado ? t[1] : t[0];
                h = deLado ? t[0] : t[1];
            }
        }
        antes();
        hojas.add(lugar, new Hoja(null, -1, 0, w, h));
        seleccion.clear();
        seleccion.add(lugar);
        ultimo = lugar;
        actualizarSeleccion();
    }

    /** Mueve la hoja de 'desde' para que quede antes de la posición 'hasta' (si van varias elegidas, van todas). */
    private void mover(int desde, int hasta) {
        List<Integer> mover = seleccion.contains(desde) ? new ArrayList<>(seleccion) : List.of(desde);
        antes();
        List<Hoja> movidas = new ArrayList<>();
        int antesDelDestino = 0;
        for (Integer i : mover) {
            movidas.add(hojas.get(i));
            if (i < hasta) {
                antesDelDestino++;
            }
        }
        List<Hoja> resto = new ArrayList<>();
        for (int i = 0; i < hojas.size(); i++) {
            if (!mover.contains(i)) {
                resto.add(hojas.get(i));
            }
        }
        int destino = Math.max(0, Math.min(resto.size(), hasta - antesDelDestino));
        resto.addAll(destino, movidas);
        hojas.clear();
        hojas.addAll(resto);
        seleccion.clear();
        for (int k = 0; k < movidas.size(); k++) {
            seleccion.add(destino + k);
        }
        ultimo = destino;
        actualizarSeleccion();
    }

    private void actualizarBotones() {
        boolean hay = archivo != null;
        for (Button b : conPdf) {
            b.setDisable(!hay);
        }
        btnDeshacer.setDisable(!hay || deshacer.isEmpty());
        btnRehacer.setDisable(!hay || rehacer.isEmpty());
    }

    // ------------------------------------------------------------ ver en grande
    private void verEnGrande(int inicio) {
        if (archivo == null) {
            return;
        }
        Stage s = new Stage();
        s.initModality(Modality.WINDOW_MODAL);
        Window w = Ui.ventana(this);
        if (w != null) {
            s.initOwner(w);
        }
        int[] pos = { inicio };
        ImageView iv = new ImageView();
        iv.setPreserveRatio(true);
        StackPane area = new StackPane(iv);
        area.setStyle("-fx-background-color: #5a5a5a;");
        area.setMinSize(200, 200);
        area.setPrefSize(900, 700);
        Label lbl = new Label();
        lbl.setStyle("-fx-font-weight: bold;");
        Runnable[] mostrar = new Runnable[1];
        mostrar[0] = () -> {
            if (hojas.isEmpty()) {
                s.close();
                return;
            }
            pos[0] = Math.min(pos[0], hojas.size() - 1);
            Hoja h = hojas.get(pos[0]);
            s.setTitle("Hoja " + (pos[0] + 1) + " de " + hojas.size());
            lbl.setText("Hoja " + (pos[0] + 1) + " de " + hojas.size() + detalle(h, pos[0])
                    + (h.giro != 0 ? "  ·  girada " + (h.giro > 180 ? h.giro - 360 : h.giro) + "°" : ""));
            iv.setRotate(h.giro);
            double W = Math.max(200, area.getWidth() - 30), H = Math.max(200, area.getHeight() - 30);
            boolean deLado = h.giro % 180 != 0;
            iv.setFitWidth(deLado ? H : W);
            iv.setFitHeight(deLado ? W : H);
            if (h.archivo == null) {
                iv.setImage(blanco(h));
                return;
            }
            Motor.Previa p = previas.get(h.archivo);
            String clave = h.clave();
            if (p == null) {
                return;
            }
            iv.setImage(imagenes.get(clave));
            HILO.submit(() -> {
                try {
                    var img = Ui.imagenFx(p.imagen(h.pagina, 1400, null));
                    Platform.runLater(() -> {
                        if (hojas.size() > pos[0] && hojas.get(pos[0]).clave().equals(clave)) {
                            iv.setImage(img);
                        }
                    });
                } catch (Exception e) {
                    // nada
                }
            });
        };
        Button ant = Ui.boton("Anterior", UniconsLine.ANGLE_LEFT, () -> {
            if (pos[0] > 0) {
                pos[0]--;
                mostrar[0].run();
            }
        });
        Button sig = Ui.boton("Siguiente", UniconsLine.ANGLE_RIGHT, () -> {
            if (pos[0] < hojas.size() - 1) {
                pos[0]++;
                mostrar[0].run();
            }
        });
        Button gi = espejo(Ui.boton("Girar izq.", UniconsLine.REDO, () -> {
            girarUna(pos[0], -90);
            mostrar[0].run();
        }));
        Button gd = Ui.boton("Girar der.", UniconsLine.REDO, () -> {
            girarUna(pos[0], 90);
            mostrar[0].run();
        });
        Button q = Ui.boton("Quitar", UniconsLine.TRASH_ALT, () -> {
            quitar(List.of(pos[0]));
            mostrar[0].run();
        });
        Button cerrar = Ui.boton("Cerrar", s::close);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(8, ant, sig, lbl, espacio, gi, gd, q, cerrar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(8));
        BorderPane raiz = new BorderPane(area);
        raiz.setTop(barra);
        raiz.getStyleClass().addAll(Style.CONTAINER.css());
        Scene sc = new Scene(raiz, 1000, 780);
        if (w != null && w.getScene() != null) {
            sc.getStylesheets().addAll(w.getScene().getStylesheets());
        }
        sc.setOnKeyPressed(e -> {
            switch (e.getCode()) {
            case LEFT, PAGE_UP -> ant.fire();
            case RIGHT, PAGE_DOWN -> sig.fire();
            case ESCAPE -> s.close();
            default -> {
            }
            }
        });
        area.widthProperty().addListener((o, a, b) -> mostrar[0].run());
        area.heightProperty().addListener((o, a, b) -> mostrar[0].run());
        s.setScene(sc);
        s.show();
        mostrar[0].run();
    }

    // ------------------------------------------------------------ guardar
    private List<Motor.HojaArmada> armadas(List<Hoja> lista) {
        List<Motor.HojaArmada> r = new ArrayList<>();
        for (Hoja h : lista) {
            r.add(new Motor.HojaArmada(h.archivo, h.pagina, h.giro, h.anchoBlanco, h.altoBlanco));
        }
        return r;
    }

    private void guardar() {
        if (archivo == null) {
            return;
        }
        if (!hayCambios() && !Ui.pregunta(this, "No hay cambios: el PDF quedaría igual. ¿Guardarlo de todas formas?",
                "Guardar PDF")) {
            return;
        }
        guardarComo(armadas(hojas), "girado", "Guardar el PDF");
    }

    /** Guardar selección como PDF: un PDF nuevo solo con las hojas elegidas (con sus giros). */
    private void extraer() {
        if (!hayElegidas()) {
            return;
        }
        List<Hoja> elegidas = new ArrayList<>();
        for (Integer i : seleccion) {
            elegidas.add(hojas.get(i));
        }
        guardarComo(armadas(elegidas), "seleccion", "Guardar las hojas elegidas como PDF");
    }

    private void guardarComo(List<Motor.HojaArmada> lista, String sufijo, String titulo) {
        File salida = Ui.guardarComo(this, Ui.sugerir(archivo, sufijo), "PDF", ".pdf", titulo);
        if (salida == null) {
            return;
        }
        for (File f : previas.keySet()) {
            if (salida.getAbsoluteFile().equals(f.getAbsoluteFile())) {
                Ui.aviso(this, "Elija otro nombre: así el PDF original no se reemplaza.");
                return;
            }
        }
        File principal = archivo;
        Map<File, String> c = new HashMap<>(claves);
        Ui.enSegundoPlano(this, "Guardando", false, p -> {
            Motor.armar(principal, c, lista, salida,
                    (h, t) -> p.poner(t == 0 ? 1 : (double) h / t, "Hoja " + h + " de " + t));
            return salida;
        }, r -> Ui.terminado(this, r, "PDF guardado", lista.size() + (lista.size() == 1 ? " hoja" : " hojas")), null);
    }

    /** Solo para la prueba de pantallas: hojas elegidas, una girada, una duplicada y una en blanco. */
    void paraPrueba() {
        girarUna(1, 90);
        duplicar(List.of(3));
        seleccion.clear();
        seleccion.add(0);
        hojaEnBlanco();
        seleccion.clear();
        seleccion.add(2);
        seleccion.add(3);
        actualizarSeleccion();
    }

    // ------------------------------------------------------------ carpetas y ZIP
    private void girarLote(File origen) {
        Stage v = new Stage();
        v.initModality(Modality.WINDOW_MODAL);
        Window w = Ui.ventana(this);
        if (w != null) {
            v.initOwner(w);
        }
        v.setTitle("Girar una carpeta o comprimido");
        Label t = new Label("Se giran TODAS las hojas de todos los PDF.");
        t.setStyle("-fx-font-weight: bold;");
        Label nombre = new Label(origen.getName());
        nombre.getStyleClass().add("foliado-acento");
        nombre.setStyle("-fx-font-weight: bold;");
        ToggleGroup g = new ToggleGroup();
        RadioButton r90 = new RadioButton("A la derecha (90°)");
        RadioButton r270 = new RadioButton("A la izquierda (90°)");
        RadioButton r180 = new RadioButton("Voltear (180°)");
        r90.setUserData(90);
        r270.setUserData(-90);
        r180.setUserData(180);
        for (RadioButton r : List.of(r90, r270, r180)) {
            r.setToggleGroup(g);
        }
        r90.setSelected(true);
        Button girar = Ui.boton("Girar", UniconsLine.REDO, () -> {
            int grados = (Integer) g.getSelectedToggle().getUserData();
            v.close();
            LoteUi.procesar(this, origen, (e, s, c) -> Motor.girarTodo(e, s, c, grados), "girado", "Girar PDF", null);
        });
        girar.getStyleClass().addAll("footer-button", "run-button");
        girar.setDefaultButton(true);
        Button cancelar = Ui.boton("Cancelar", v::close);
        HBox b = new HBox(8, cancelar, girar);
        b.setAlignment(Pos.CENTER_RIGHT);
        VBox caja = new VBox(8, t, nombre, r90, r270, r180, b);
        Ui.estilo(caja);
        caja.setPadding(new Insets(20));
        caja.setPrefWidth(420);
        caja.getStyleClass().addAll(Style.CONTAINER.css());
        Scene sc = new Scene(caja);
        if (w != null && w.getScene() != null) {
            sc.getStylesheets().addAll(w.getScene().getStylesheets());
        }
        v.setScene(sc);
        v.show();
    }
}
