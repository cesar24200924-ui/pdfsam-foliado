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
import javafx.scene.control.ListView;
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
import org.pdfsam.eventstudio.annotation.EventListener;
import org.pdfsam.model.ui.SetActiveContentItemRequest;
import org.pdfsam.ui.components.support.Style;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.pdfsam.eventstudio.StaticStudio.eventStudio;

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
 * Trabaja con una LISTA de PDF, igual que Indexar: se le pueden agregar PDF sueltos, carpetas enteras (con sus
 * subcarpetas) y comprimidos (ZIP, RAR, 7z...). Se hace clic en cualquiera de la lista para ver sus hojas en
 * miniatura y arreglarlo; lo que se le hace a cada PDF se recuerda aunque se cambie a otro ({@link Edicion}).
 * Al guardar, cada PDF se guarda POR SEPARADO: los de carpetas vuelven a su misma carpeta dentro de la carpeta
 * elegida y los que vinieron de un comprimido se vuelven a armar en un ZIP ("Nombre_girado.zip"); los PDF que no
 * se tocaron se copian tal cual. Aparte queda el atajo "Girar una carpeta o comprimido", que gira todo de un solo
 * golpe sin revisarlo uno por uno ({@link #girarLote}).
 * <p>
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

    /** Un PDF de la lista: de dónde viene y cómo queda dentro de la carpeta de salida (igual que en Indexar). */
    record Item(File origen, String relativa) {
        @Override
        public String toString() {
            return Lote.nombreVisible(relativa);
        }
    }

    /** Un comprimido (ZIP, RAR, 7z...) abierto: sus PDF van a la lista y al guardar se arma "Nombre_girado.zip"
     * con todo lo que traía (RAR no se puede crear con programas libres: va en ZIP). */
    record Abierto(File archivo, Lote lote, String marcada) {
    }

    /** Lo que lleva hecho un PDF de la lista: sus hojas tal como quedaron y su deshacer/rehacer. */
    private static final class Edicion {
        final List<Hoja> hojas = new ArrayList<>();
        final Deque<List<Hoja>> deshacer = new ArrayDeque<>(), rehacer = new ArrayDeque<>();
    }

    private static final ExecutorService HILO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "girar-miniaturas");
        t.setDaemon(true);
        return t;
    });

    private final List<Item> items = new ArrayList<>();
    private final List<Abierto> abiertos = new ArrayList<>();
    private final ListView<Item> lista = new ListView<>();
    private final Label lblArchivos = Controles.nota("Arrastre aquí PDF, carpetas o comprimidos (ZIP, RAR, 7z...).");
    /** lo que lleva hecho cada PDF de la lista (para no perderlo al cambiar de archivo) */
    private final Map<File, Edicion> ediciones = new HashMap<>();
    /** cuántas hojas traía cada PDF al abrirlo (para saber si tiene cambios sin abrirlo de nuevo) */
    private final Map<File, Integer> hojasOriginales = new HashMap<>();
    private int actual = -1;
    private Path trabajoZip;

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
    private final Button btnDeshacer, btnRehacer, btnGuardar, btnIrIndexar, btnIrFoliar;
    private final List<Button> conPdf = new ArrayList<>();
    private int origenArrastre = -1;

    GirarPanel() {
        getStyleClass().addAll(Style.CONTAINER.css());
        Ui.estilo(this);

        // ---------- barra de arriba
        Button elegir = Ui.boton("Agregar PDF", UniconsLine.FILE_ALT, this::elegir);
        elegir.setTooltip(new Tooltip("Agrega uno o varios PDF a la lista de la derecha. Después hace clic en "
                + "cualquiera de la lista para ver sus hojas y arreglarlo. Cada PDF se guarda por separado."));
        Button lote = Ui.boton("Girar una carpeta o comprimido", UniconsLine.FOLDER, () -> {
        });
        lote.setTooltip(new Tooltip("Atajo rápido: gira TODAS las hojas de TODOS los PDF de una carpeta o de un "
                + "comprimido, de un solo golpe y sin revisarlos uno por uno. Si los quiere ver y arreglar a mano, "
                + "use \"Agregar carpeta\" o \"Agregar ZIP / RAR / 7z\" de la lista de la derecha."));
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
        Button insertar = conPdf(Ui.boton("Insertar hojas aquí", UniconsLine.FILE_PLUS_ALT, this::insertarPdf));
        insertar.setTooltip(new Tooltip("Mete las hojas de OTRO PDF dentro del que está viendo ahora mismo (quedan "
                + "después de la hoja elegida). No agrega ese PDF a la lista: lo mezcla con este. Para agregarlo a "
                + "la lista como un archivo aparte, use \"Agregar PDF\"."));
        Button blanco = conPdf(Ui.boton("Hoja en blanco", UniconsLine.FILE_BLANK, this::hojaEnBlanco));
        Button todo = conPdf(Ui.boton("Seleccionar todo", UniconsLine.CHECK_SQUARE, this::seleccionarTodo));
        btnDeshacer = conPdf(espejo(Ui.boton("Deshacer", UniconsLine.REDO, this::deshacer)));
        btnRehacer = conPdf(Ui.boton("Rehacer", UniconsLine.REDO, this::rehacer));
        Button guardar = conPdf(Ui.boton("Guardar PDF", UniconsLine.SAVE, this::guardar));
        guardar.getStyleClass().addAll("footer-button", "run-button");
        guardar.setTooltip(new Tooltip("Guarda el resultado. Con un solo PDF le pregunta el nombre. Con varios, "
                + "le pregunta la carpeta y guarda CADA PDF por separado: los de carpetas vuelven a su misma "
                + "carpeta y los que venían en un comprimido se vuelven a armar en un ZIP. Los PDF que no tocó "
                + "se copian igualitos. Los originales nunca se cambian."));
        btnGuardar = guardar;

        btnIrIndexar = Ui.boton("Ir a Indexar", UniconsLine.TAG_ALT, () -> enviarA(IndexarTool.TOOL_ID));
        btnIrIndexar.setTooltip(new Tooltip("Manda esta lista (ya arreglada) a Indexar, para ponerle las "
                + "leyendas. Lo que ya hubiera en Indexar se borra y queda solo esta lista. Funciona aunque no "
                + "haya entrado a Girar desde Indexar."));
        btnIrFoliar = Ui.boton("Ir a Foliar", UniconsLine.FILE_EDIT_ALT, () -> enviarA(FoliarTool.TOOL_ID));
        btnIrFoliar.setTooltip(new Tooltip("Manda esta lista (ya arreglada) a Foliar. Si hay más de un PDF, "
                + "Foliar solo abre el primero (trabaja uno a la vez); los demás se pueden mandar después de "
                + "uno en uno. Lo que ya hubiera en Foliar se borra. Funciona aunque no haya entrado a Girar "
                + "desde Foliar."));

        tamano.setPrefWidth(130);
        tamano.valueProperty().addListener((o, a, b) -> redibujar());
        HBox zoom = new HBox(6, new Label("Tamaño"), tamano);
        zoom.setAlignment(Pos.CENTER_LEFT);

        FlowPane barra = new FlowPane(6, 6, elegir, lote, sep(), izq, der, voltear, todasIzq, todasDer, sep(),
                duplicar, quitar, insertar, blanco, sep(), todo, btnDeshacer, btnRehacer, sep(), zoom, guardar,
                btnIrIndexar, btnIrFoliar,
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

        Label t1 = new Label("Arrastre aquí PDF, carpetas o comprimidos");
        t1.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");
        Label t2 = new Label("Todos van a la lista de la derecha. Haga clic en uno y verá sus hojas en miniatura "
                + "para arreglarlo. Al final cada PDF se guarda por separado.");
        t2.setWrapText(true);
        t2.setMaxWidth(480);
        Button bElegir = Ui.boton("Agregar PDF", UniconsLine.FILE_ALT, this::elegir);
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

        // ---------- panel de la derecha: primero la lista de PDF (igual que en Indexar)
        Label tArchivos = new Label("Archivos PDF");
        tArchivos.setStyle("-fx-font-size: 1.2em; -fx-font-weight: bold;");
        Button aPdf = Ui.boton("Agregar PDF", UniconsLine.FILE_ALT, this::elegir);
        aPdf.setTooltip(new Tooltip("Agrega uno o varios PDF a esta lista. Puede elegir varios de un solo golpe."));
        Button aCarpeta = Ui.boton("Agregar carpeta", UniconsLine.FOLDER, () -> {
            File f = Ui.elegirCarpeta(this, "Elegir carpeta (se incluyen todas las subcarpetas)", null);
            if (f != null) {
                recibir(List.of(f));
            }
        });
        aCarpeta.setTooltip(new Tooltip("Agrega a la lista todos los PDF de una carpeta y de sus subcarpetas. Al "
                + "guardar, cada PDF vuelve a su misma carpeta dentro de la carpeta que usted elija."));
        Button aZip = Ui.boton("Agregar ZIP / RAR / 7z", UniconsLine.FILE_ALT, () -> {
            File f = Ui.elegirComprimido(this);
            if (f != null) {
                recibir(List.of(f));
            }
        });
        aZip.setTooltip(new Tooltip("Abre un comprimido (ZIP, RAR, 7z, TAR...) y agrega sus PDF a la lista para "
                + "verlos y arreglarlos uno por uno. Al guardar se vuelve a armar un ZIP con todo lo que traía."));
        Button aLimpiar = Ui.boton("Limpiar", UniconsLine.TRASH_ALT, this::limpiar);
        aLimpiar.setTooltip(new Tooltip("Vacía la lista y olvida todos los cambios que no haya guardado."));
        FlowPane botonesLista = new FlowPane(4, 4, aPdf, aCarpeta, aZip, aLimpiar);
        lista.setPrefHeight(130);
        lista.setMinHeight(90);
        lista.setTooltip(new Tooltip("Los PDF que se van a girar. Haga clic en uno para ver sus hojas en "
                + "miniatura y arreglarlo. Lo que le haga a cada PDF se queda guardado aunque se cambie a otro."));
        lista.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            if (b != null && b.intValue() >= 0 && b.intValue() != actual) {
                verArchivo(b.intValue());
            }
        });
        lista.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
                e.consume();
            }
        });
        lista.setOnDragDropped(e -> {
            if (e.getDragboard().hasFiles()) {
                List<File> f = List.copyOf(e.getDragboard().getFiles());
                e.setDropCompleted(true);
                e.consume();
                Platform.runLater(() -> recibir(f));
            }
        });
        lblArchivos.setWrapText(true);

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
        VBox panel = new VBox(8, tArchivos, botonesLista, lista, lblArchivos, new Separator(Orientation.HORIZONTAL),
                titulo, lblSel, lblCambios, ayuda, sDer, sIzq, sQuitar, sExtraer, sLote);
        panel.setPadding(new Insets(14));
        panel.setPrefWidth(270);
        panel.setMinWidth(230);
        panel.setStyle("-fx-border-color: transparent rgba(127,127,127,0.35) transparent transparent;");
        ScrollPane scrollPanel = new ScrollPane(panel);
        scrollPanel.setFitToWidth(true);
        scrollPanel.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPanel.setPrefWidth(285);
        setLeft(scrollPanel);

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
        eventStudio().addAnnotatedListeners(this);
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
        List<File> f = Ui.elegirPdfs(this, true);
        if (f != null && !f.isEmpty()) {
            recibir(f);
        }
    }

    /** Recibe PDF sueltos, carpetas y comprimidos y los deja en la lista (igual que en Indexar). */
    void recibir(List<File> rutas) {
        if (rutas.isEmpty()) {
            return;
        }
        // los comprimidos (sueltos o dentro de las carpetas) se abren aparte, en segundo plano
        List<File[]> comprimidos = new ArrayList<>(); // {archivo, carpeta base de la salida}
        for (File r : rutas) {
            if (r.isFile() && Comprimidos.es(r.getName())) {
                comprimidos.add(new File[] { r.getAbsoluteFile(), null });
            } else if (r.isDirectory()) {
                Path carpeta = r.getAbsoluteFile().toPath();
                Path base = carpeta.getParent() == null ? carpeta : carpeta.getParent();
                try (Stream<Path> s = Files.walk(carpeta)) {
                    s.filter(Files::isRegularFile).filter(p -> Comprimidos.es(p.getFileName().toString()))
                            .sorted().forEach(p -> comprimidos.add(new File[] { p.toFile(), base.toFile() }));
                } catch (IOException e) {
                    // se ven abajo
                }
            }
        }
        if (!comprimidos.isEmpty()) {
            abrirComprimidos(comprimidos, rutas.stream().filter(f -> !(f.isFile() && Comprimidos.es(f.getName())))
                    .toList());
            return;
        }
        agregar(rutas, List.of());
    }

    private void abrirComprimidos(List<File[]> comprimidos, List<File> resto) {
        Ui.enSegundoPlano(this, "Abriendo comprimidos", false, p -> {
            List<Item> nuevos = new ArrayList<>();
            List<String> problemas = new ArrayList<>();
            int k = 0;
            for (File[] c : comprimidos) {
                p.poner((double) k++ / comprimidos.size(), "Abriendo " + c[0].getName());
                Lote L = new Lote(c[0]);
                L.pide = (arch, mala) -> Ui.enFx(() -> Ui.pedirClaveComprimido(this, arch, mala));
                try {
                    L.preparar();
                } catch (Exception e) {
                    L.limpiar();
                    problemas.add(c[0].getName() + ": " + Ui.textoError(e));
                    continue;
                }
                String prefijo = c[1] == null ? "" :
                        c[1].toPath().relativize(c[0].toPath().getParent()).toString().replace('\\', '/');
                String marcada = (prefijo.isEmpty() ? "" : prefijo + "/") + Comprimidos.base(c[0].getName())
                        + "_girado.zip" + Lote.MARCA_ZIP;
                abiertos.add(new Abierto(c[0], L, marcada));
                for (String rel : L.procesables) {
                    nuevos.add(new Item(L.raiz.resolve(rel).toFile(), marcada + "/" + rel.replace('\\', '/')));
                }
                for (Lote.Fallido f : L.fallidos) {
                    problemas.add(c[0].getName() + " › " + f.visible() + ": " + f.mensaje);
                }
            }
            return new Object[] { nuevos, problemas };
        }, res -> {
            @SuppressWarnings("unchecked")
            List<Item> nuevos = (List<Item>) ((Object[]) res)[0];
            @SuppressWarnings("unchecked")
            List<String> problemas = (List<String>) ((Object[]) res)[1];
            if (!problemas.isEmpty()) {
                Ui.aviso(this, "Algunos comprimidos no se abrieron completos:\n\n" + String.join("\n",
                        problemas.subList(0, Math.min(10, problemas.size()))));
            }
            agregar(resto, nuevos);
        }, e -> Ui.error(this, Ui.textoError(e)));
    }

    private void agregar(List<File> rutas, List<Item> deComprimidos) {
        List<Item> nuevos = new ArrayList<>(deComprimidos);
        for (File r : rutas) {
            if (r.isDirectory()) {
                Path carpeta = r.getAbsoluteFile().toPath();
                Path base = carpeta.getParent() == null ? carpeta : carpeta.getParent(); // la carpeta elegida se
                                                                                         // recrea en la salida
                try (Stream<Path> s = Files.walk(carpeta)) {
                    s.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                            .sorted((a, b) -> a.toString().compareToIgnoreCase(b.toString()))
                            .forEach(p -> nuevos.add(new Item(p.toFile(), base.relativize(p).toString())));
                } catch (IOException e) {
                    Ui.error(this, Ui.textoError(e));
                }
            } else if (r.isFile() && r.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                nuevos.add(new Item(r.getAbsoluteFile(), r.getName()));
            }
        }
        Set<String> existentes = new HashSet<>();
        for (Item it : items) {
            existentes.add(it.origen().getAbsolutePath().toLowerCase(Locale.ROOT));
        }
        int agregados = 0;
        for (Item it : nuevos) {
            if (existentes.add(it.origen().getAbsolutePath().toLowerCase(Locale.ROOT))) {
                items.add(it);
                agregados++;
            }
        }
        if (agregados == 0) {
            if (nuevos.isEmpty()) {
                Ui.aviso(this, "No se encontraron archivos PDF.");
            }
            return;
        }
        lista.getItems().setAll(items);
        lblArchivos.setText(items.size() + (items.size() == 1 ? " archivo PDF" : " archivos PDF"));
        if (items.size() == agregados) {
            verArchivo(0);
        } else {
            actualizarSeleccion();
        }
    }

    /** Guarda lo que lleva hecho el PDF que se está viendo, para no perderlo al cambiarse a otro. */
    private void guardarEdicionActual() {
        if (archivo == null) {
            return;
        }
        Edicion ed = ediciones.computeIfAbsent(archivo, f -> new Edicion());
        ed.hojas.clear();
        for (Hoja h : hojas) {
            ed.hojas.add(h.copia());
        }
        ed.deshacer.clear();
        ed.deshacer.addAll(deshacer);
        ed.rehacer.clear();
        ed.rehacer.addAll(rehacer);
    }

    /** Muestra en las miniaturas el PDF número k de la lista (con lo que ya llevara hecho). */
    private void verArchivo(int k) {
        if (k < 0 || k >= items.size()) {
            return;
        }
        File pdf = items.get(k).origen();
        Motor.Previa p = previas.get(pdf);
        if (p == null) {
            p = abrirPrevia(pdf);
            if (p == null) {
                return;
            }
        }
        guardarEdicionActual();
        // se cierran los PDF del archivo anterior (las miniaturas se vuelven a hacer para el nuevo)
        List<Motor.Previa> viejas = new ArrayList<>();
        for (Map.Entry<File, Motor.Previa> e : previas.entrySet()) {
            if (!e.getKey().equals(pdf)) {
                viejas.add(e.getValue());
            }
        }
        previas.clear();
        previas.put(pdf, p);
        cerrarEnSegundoPlano(viejas);

        actual = k;
        archivo = pdf;
        hojasOriginales.putIfAbsent(pdf, p.hojas());
        hojas.clear();
        deshacer.clear();
        rehacer.clear();
        Edicion ed = ediciones.get(pdf);
        if (ed != null) {
            for (Hoja h : ed.hojas) {
                hojas.add(h.copia());
            }
            deshacer.addAll(ed.deshacer);
            rehacer.addAll(ed.rehacer);
            // si había hojas insertadas de otros PDF, se vuelven a abrir para poder verlas y guardarlas
            for (Hoja h : hojas) {
                if (h.archivo != null && !previas.containsKey(h.archivo)) {
                    Motor.Previa q = abrirPrevia(h.archivo);
                    if (q != null) {
                        previas.put(h.archivo, q);
                    }
                }
            }
        } else {
            for (int i = 0; i < p.hojas(); i++) {
                hojas.add(new Hoja(pdf, i, 0, 0, 0));
            }
        }
        seleccion.clear();
        ultimo = null;
        imagenes.clear();
        anchoImagen.clear();
        pedidas.clear();
        lista.getSelectionModel().select(k);
        lista.scrollTo(k);
        lblArchivo.setText(pdf.getName() + "  ·  " + hojas.size() + " hojas");
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
        cerrarEnSegundoPlano(viejas);
    }

    private static void cerrarEnSegundoPlano(List<Motor.Previa> viejas) {
        if (viejas.isEmpty()) {
            return;
        }
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

    /** Vacía la lista y olvida todo lo que no se haya guardado. */
    private void limpiar() {
        items.clear();
        lista.getItems().clear();
        ediciones.clear();
        hojasOriginales.clear();
        for (Abierto a : abiertos) {
            a.lote().limpiar();
        }
        abiertos.clear();
        actual = -1;
        archivo = null;
        hojas.clear();
        seleccion.clear();
        ultimo = null;
        deshacer.clear();
        rehacer.clear();
        imagenes.clear();
        anchoImagen.clear();
        pedidas.clear();
        cerrarTodo();
        lblArchivos.setText("Arrastre aquí PDF, carpetas o comprimidos (ZIP, RAR, 7z...).");
        lblArchivo.setText("");
        mostrarZona(true);
        actualizarSeleccion();
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
        return archivo != null && hayCambios(archivo, hojas);
    }

    /** ¿Ese PDF quedó distinto de como estaba? (sirve también para los que no se están viendo). */
    private boolean hayCambios(File f, List<Hoja> lista) {
        Integer original = hojasOriginales.get(f);
        if (original == null) {
            return false;
        }
        if (lista.size() != original) {
            return true;
        }
        for (int i = 0; i < lista.size(); i++) {
            Hoja h = lista.get(i);
            if (h.giro % 360 != 0 || h.pagina != i || !f.equals(h.archivo)) {
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
        boolean varios = items.size() > 1 || !abiertos.isEmpty();
        btnGuardar.setText(varios ? "Girar y guardar (" + items.size() + ")" : "Guardar PDF");
        boolean hayLista = !items.isEmpty();
        btnIrIndexar.setDisable(!hayLista);
        btnIrFoliar.setDisable(!hayLista);
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
        Ui.estiloDialogo(sc.getRoot());
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
        guardarEdicionActual();
        // un solo PDF suelto: se sigue guardando como siempre, preguntando el nombre
        if (items.size() <= 1 && abiertos.isEmpty()) {
            if (!hayCambios() && !Ui.pregunta(this,
                    "No hay cambios: el PDF quedaría igual. ¿Guardarlo de todas formas?", "Guardar PDF")) {
                return;
            }
            guardarComo(armadas(hojas), "girado", "Guardar el PDF");
            return;
        }
        guardarTodos();
    }

    /** Guarda TODOS los PDF de la lista, cada uno por su lado: los de carpetas vuelven a su misma carpeta dentro
     * de la carpeta elegida, y los que vinieron de un comprimido se vuelven a armar en un ZIP. Los PDF que no se
     * tocaron se copian tal cual. */
    private void guardarTodos() {
        List<Item> copia = List.copyOf(items);
        List<List<Motor.HojaArmada>> trabajos = new ArrayList<>(); // null = ese PDF no se tocó
        int conCambios = 0;
        for (Item it : copia) {
            Edicion ed = ediciones.get(it.origen());
            boolean cambio = ed != null && hayCambios(it.origen(), ed.hojas);
            trabajos.add(cambio ? armadas(ed.hojas) : null);
            if (cambio) {
                conCambios++;
            }
        }
        if (conCambios == 0 && !Ui.pregunta(this, "Todavía no ha cambiado ningún PDF: quedarían igual que los "
                + "originales. ¿Guardarlos de todas formas?", "Girar y guardar")) {
            return;
        }
        File carpeta = Ui.elegirCarpeta(this, "¿En qué carpeta guardo los PDF?", null);
        if (carpeta == null) {
            return;
        }
        Path destino = carpeta.getAbsoluteFile().toPath().normalize();
        // nunca encima de los originales: la carpeta de salida no puede contener ningún original
        List<File> originales = new ArrayList<>();
        for (Item it : copia) {
            originales.add(it.origen());
        }
        for (Abierto a : abiertos) {
            originales.add(a.archivo());
        }
        for (File f : originales) {
            if (f.getAbsoluteFile().toPath().normalize().startsWith(destino)) {
                Ui.aviso(this, "Esa carpeta tiene adentro los PDF originales y se reemplazarían. Elija otra carpeta "
                        + "de salida (por ejemplo, una carpeta nueva).");
                return;
            }
        }
        // lo que vino de comprimidos se arma en la carpeta temporal de Windows (OneDrive no la toca) y al final
        // solo se copia el ZIP terminado a la carpeta elegida
        try {
            trabajoZip = Files.createTempDirectory("pdfsam_girar_");
        } catch (IOException e) {
            Ui.error(this, Ui.textoError(e));
            return;
        }
        Path trabajo = trabajoZip;
        Map<File, String> c = new HashMap<>(claves);
        Ui.enSegundoPlano(this, "Girando", true, p -> {
            List<File> salidas = new ArrayList<>();
            List<String> fallidos = new ArrayList<>();
            int n = copia.size();
            for (int k = 0; k < n; k++) {
                if (p.cancelado()) {
                    break;
                }
                Item it = copia.get(k);
                String texto = "Archivo " + (k + 1) + " de " + n + ": " + it.origen().getName();
                File salida = salidaDe(it, destino, trabajo).toFile();
                int kk = k;
                try {
                    List<Motor.HojaArmada> hs = trabajos.get(k);
                    if (hs == null) {
                        p.poner((double) kk / n, texto);
                        Lote.copiar(it.origen().toPath(), salida.toPath());
                    } else {
                        Motor.armar(it.origen(), c, hs, salida,
                                (h, t) -> p.poner((kk + (t == 0 ? 1 : (double) h / t)) / n, texto));
                    }
                    salidas.add(salida);
                } catch (Exception e) {
                    fallidos.add(Lote.nombreVisible(it.relativa()) + ": " + Ui.textoError(e));
                }
            }
            p.poner(1, "");
            return new Object[] { salidas, fallidos };
        }, res -> terminarGuardado((Object[]) res, destino), e -> {
            Lote.borrar(trabajo);
            Ui.error(this, Ui.textoError(e));
        });
    }

    /** Dónde se guarda cada PDF: los de comprimidos, en la carpeta temporal; los demás, en la carpeta elegida. */
    private static Path salidaDe(Item it, Path destino, Path trabajo) {
        return it.relativa().contains(Lote.MARCA_ZIP) ? trabajo.resolve(it.relativa())
                : destino.resolve(it.relativa());
    }

    @SuppressWarnings("unchecked")
    private void terminarGuardado(Object[] res, Path destino) {
        List<File> salidas = (List<File>) res[0];
        List<String> fallidos = (List<String>) res[1];
        List<Abierto> usados = new ArrayList<>();
        for (Abierto a : abiertos) {
            if (items.stream().anyMatch(it -> it.relativa().startsWith(a.marcada() + "/"))) {
                usados.add(a);
            }
        }
        Path trabajo = trabajoZip;
        if (!usados.isEmpty()) {
            Ui.enSegundoPlano(this, "Armando los ZIP", false, p -> {
                try {
                    for (Abierto a : usados) {
                        p.poner(-1, a.archivo().getName());
                        Lote.armarZipDeSalida(destino, trabajo, a.lote(), a.marcada());
                    }
                } finally {
                    Lote.borrar(trabajo);
                }
                return null;
            }, r -> resumenGuardado(salidas, fallidos, destino, true), e -> Ui.error(this, Ui.textoError(e)));
            return;
        }
        Lote.borrar(trabajo);
        resumenGuardado(salidas, fallidos, destino, false);
    }

    private void resumenGuardado(List<File> salidas, List<String> fallidos, Path destino, boolean conZip) {
        StringBuilder d = new StringBuilder(
                salidas.size() + (salidas.size() == 1 ? " archivo guardado" : " archivos guardados"));
        if (!fallidos.isEmpty()) {
            d.append("\n\nNo se pudieron procesar:\n");
            for (int i = 0; i < Math.min(8, fallidos.size()); i++) {
                d.append(fallidos.get(i)).append('\n');
            }
        }
        if (conZip) {
            d.append("\n\nLo que venía en comprimidos (ZIP, RAR, 7z...) quedó en un ZIP con el mismo nombre y "
                    + "\"_girado\" al final, con todas sus carpetas y archivos.");
        }
        Ui.terminado(this, destino.toFile(), "PDF guardados", d.toString());
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

    // ------------------------------------------------------------ enlace con Indexar y Foliar
    /** Manda la lista actual (ya con lo girado/arreglado) a Indexar o Foliar y cambia a esa pantalla (botones
     * "Ir a Indexar" / "Ir a Foliar"). Cada PDF se arma en una carpeta temporal con sus hojas tal como quedaron
     * (igual que al guardar); los que no se tocaron se copian tal cual. */
    private void enviarA(String toolId) {
        if (items.isEmpty()) {
            return;
        }
        guardarEdicionActual();
        List<Item> copia = List.copyOf(items);
        List<List<Motor.HojaArmada>> trabajos = new ArrayList<>();
        for (Item it : copia) {
            Edicion ed = ediciones.get(it.origen());
            boolean cambio = ed != null && hayCambios(it.origen(), ed.hojas);
            trabajos.add(cambio ? armadas(ed.hojas) : null);
        }
        Path trabajo;
        try {
            trabajo = Files.createTempDirectory("pdfsam_girar_envio_");
        } catch (IOException e) {
            Ui.error(this, Ui.textoError(e));
            return;
        }
        Map<File, String> c = new HashMap<>(claves);
        Set<String> usados = new HashSet<>();
        Map<File, String> relativas = new HashMap<>();
        // los que venían de un comprimido abierto acá: para que la pantalla de destino arme igual un solo
        // comprimido de salida con la misma estructura, en vez de dejarlos sueltos (sin esto, "Ir a Indexar" con
        // un PDF que vino de un RAR dejaba los PDF indexados sueltos en la carpeta elegida)
        List<EnviarAHerramientaRequest.Origen> origenes = new ArrayList<>();
        for (Abierto a : abiertos) {
            if (copia.stream().anyMatch(it -> it.relativa().startsWith(a.marcada() + "/"))) {
                origenes.add(new EnviarAHerramientaRequest.Origen(a.archivo(), a.lote(), a.marcada()));
            }
        }
        Ui.enSegundoPlano(this, "Preparando para enviar", true, p -> {
            List<File> salidas = new ArrayList<>();
            List<String> fallidos = new ArrayList<>();
            int n = copia.size();
            for (int k = 0; k < n; k++) {
                if (p.cancelado()) {
                    break;
                }
                Item it = copia.get(k);
                String texto = "Archivo " + (k + 1) + " de " + n + ": " + it.origen().getName();
                boolean deComprimido = it.relativa().contains(Lote.MARCA_ZIP);
                File salida;
                if (deComprimido) {
                    // se conserva la misma ruta de adentro del comprimido (no solo el nombre), para que la
                    // pantalla de destino sepa reconstruir la misma estructura de carpetas al guardar
                    salida = trabajo.resolve(it.relativa()).toFile();
                    File carpeta = salida.getParentFile();
                    if (carpeta != null) {
                        carpeta.mkdirs();
                    }
                } else {
                    String nombre = nombreUnico(usados, new File(Lote.nombreVisible(it.relativa())).getName());
                    salida = trabajo.resolve(nombre).toFile();
                }
                int kk = k;
                try {
                    List<Motor.HojaArmada> hs = trabajos.get(k);
                    if (hs == null) {
                        p.poner((double) kk / n, texto);
                        Lote.copiar(it.origen().toPath(), salida.toPath());
                    } else {
                        Motor.armar(it.origen(), c, hs, salida,
                                (h, t) -> p.poner((kk + (t == 0 ? 1 : (double) h / t)) / n, texto));
                    }
                    salidas.add(salida);
                    if (deComprimido) {
                        relativas.put(salida, it.relativa());
                    }
                } catch (Exception e) {
                    fallidos.add(Lote.nombreVisible(it.relativa()) + ": " + Ui.textoError(e));
                }
            }
            p.poner(1, "");
            return new Object[] { salidas, fallidos };
        }, res -> terminarEnvio((Object[]) res, toolId, relativas, origenes), e -> Ui.error(this, Ui.textoError(e)));
    }

    /** Solo para las pruebas: lo que falló (si algo falló) al preparar el último envío a otra pantalla. */
    private volatile List<String> ultimosFallosEnvio = List.of();

    @SuppressWarnings("unchecked")
    private void terminarEnvio(Object[] res, String toolId, Map<File, String> relativas,
            List<EnviarAHerramientaRequest.Origen> origenes) {
        List<File> salidas = (List<File>) res[0];
        List<String> fallidos = (List<String>) res[1];
        ultimosFallosEnvio = fallidos;
        if (salidas.isEmpty()) {
            if (!fallidos.isEmpty()) {
                Ui.aviso(this, "No se pudo preparar ningún PDF para enviar:\n\n" + String.join("\n",
                        fallidos.subList(0, Math.min(10, fallidos.size()))));
            }
            return;
        }
        eventStudio().broadcast(new EnviarAHerramientaRequest(toolId, salidas, relativas, origenes));
        eventStudio().broadcast(new SetActiveContentItemRequest(toolId));
        if (!fallidos.isEmpty()) {
            Ui.aviso(this, "Algunos PDF no se pudieron preparar para enviar:\n\n" + String.join("\n",
                    fallidos.subList(0, Math.min(10, fallidos.size()))));
        }
    }

    /** Solo para las pruebas: lo que falló (si algo falló) al preparar el último envío. */
    List<String> ultimosFallosEnvioParaPrueba() {
        return ultimosFallosEnvio;
    }

    /** Nombre de archivo sin repetir dentro de la misma carpeta temporal de envío ("archivo (2).pdf"...). */
    private static String nombreUnico(Set<String> usados, String nombre) {
        String base = nombre, n = base;
        int i = 2;
        while (!usados.add(n.toLowerCase(Locale.ROOT))) {
            int punto = base.lastIndexOf('.');
            n = punto < 0 ? base + " (" + i + ")" : base.substring(0, punto) + " (" + i + ")" + base.substring(punto);
            i++;
        }
        return n;
    }

    /** Llega desde Indexar o Foliar (botón "Arreglar en Girar"): borra la lista actual y queda solo con la
     * nueva, sin importar si se había entrado a Girar desde el menú o desde otra pantalla. */
    @EventListener
    public void onEnviarAHerramienta(EnviarAHerramientaRequest r) {
        if (GirarTool.TOOL_ID.equals(r.destino())) {
            limpiar();
            recibir(r.archivos());
        }
    }

    /** Solo para las pruebas: dispara el botón "Ir a Indexar" / "Ir a Foliar" (toolId = IndexarTool.TOOL_ID o
     * FoliarTool.TOOL_ID). */
    void enviarAParaPrueba(String toolId) {
        enviarA(toolId);
    }

    /** Solo para las pruebas: cuántos PDF hay en la lista. */
    int cuantosEnLista() {
        return items.size();
    }

    /** Solo para las pruebas: nombre del PDF que se está viendo (null si no hay ninguno). */
    String nombreEnPantalla() {
        return archivo == null ? null : archivo.getName();
    }

    /** Solo para las pruebas: cuántas hojas se están viendo. */
    int cuantasHojasEnPantalla() {
        return hojas.size();
    }

    /** Solo para las pruebas: ¿el PDF que se está viendo quedó distinto del original? */
    boolean tieneCambiosEnPantalla() {
        return hayCambios();
    }

    /** Solo para las pruebas: cambiarse al PDF número k de la lista. */
    void verParaPrueba(int k) {
        verArchivo(k);
    }

    /** Solo para las pruebas: quitar la primera hoja del PDF que se está viendo. */
    void quitarPrimeraParaPrueba() {
        quitar(List.of(0));
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
        Ui.estiloDialogo(sc.getRoot());
        v.setScene(sc);
        v.show();
    }
}
