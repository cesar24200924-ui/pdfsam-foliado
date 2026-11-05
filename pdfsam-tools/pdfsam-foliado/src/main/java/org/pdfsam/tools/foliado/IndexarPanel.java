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

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.pdfsam.ui.components.support.Style;
import org.pdfsam.ui.components.support.Views;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Indexar PDF: pone leyendas en cada hoja de muchos PDF a la vez.
 * <ul>
 * <li>Arriba a la izquierda: un texto (por defecto "DPOT").</li>
 * <li>Abajo a la izquierda: un texto opcional (ej. CONFIDENCIAL).</li>
 * <li>Arriba a la derecha: parte del nombre del archivo (cortado por un separador) o un texto libre, siempre con la
 * numeración "1/10".</li>
 * <li>Abajo: numeración adicional opcional, con formato ("Página 1 de 10"...).</li>
 * </ul>
 * Archivos sueltos o carpetas con subcarpetas; el resultado se guarda en otra carpeta con la misma estructura (nunca
 * encima de los originales). Con vista previa real.
 */
class IndexarPanel extends BorderPane {
    private static final String[][] COLORES = { { "Rojo", "#ff0000" }, { "Negro", "#000000" }, { "Azul", "#0d33a6" } };
    private static final String[][] FORMATOS = { { "1/10", "{pagina}/{total}" },
            { "Página 1 de 10", "Página {pagina} de {total}" }, { "PÁGINA 1 DE 10", "PÁGINA {pagina} DE {total}" },
            { "1 de 10", "{pagina} de {total}" }, { "1 DE 10", "{pagina} DE {total}" },
            { "Personalizado...", "__propio__" } };

    /** Un PDF de la lista: su ruta y cómo queda dentro de la carpeta de salida. */
    record Item(File origen, String relativa) {
        @Override
        public String toString() {
            return Lote.nombreVisible(relativa);
        }
    }

    /** Un comprimido (ZIP, RAR, 7z...) abierto en una carpeta temporal: sus PDF van a la lista y al guardar se arma
     * "carpeta/Nombre.zip" con todo lo que traía (RAR no se puede crear con programas libres: va en ZIP). */
    record Abierto(File archivo, Lote lote, String marcada) {
    }

    private final List<Abierto> abiertos = new ArrayList<>();

    private final List<Item> items = new ArrayList<>();
    private final ListView<Item> lista = new ListView<>();
    private final Label lblArchivos = Controles.nota("Arrastre aquí PDF, carpetas o comprimidos (ZIP, RAR, 7z...).");
    private int actual = -1;
    /** contraseñas que ya dio el usuario (para la vista previa y al guardar) */
    private final Map<File, String> claves = new java.util.HashMap<>();

    private final CheckBox izqOn = new CheckBox("Leyenda superior izquierda (encabezado)");
    private final TextField izqTexto;
    private final CheckBox abajoIzqOn = new CheckBox("Leyenda inferior izquierda (pie de página)");
    private final TextField abajoIzqTexto;
    private final CheckBox derOn = new CheckBox("Leyenda superior derecha (nombre o texto + 1/10)");
    private final ToggleGroup derModo = new ToggleGroup();
    private final RadioButton modoNombre = new RadioButton("Parte del nombre del archivo");
    private final RadioButton modoLibre = new RadioButton("Texto libre");
    private final TextField delimitador;
    private final Spinner<Integer> bloque;
    private final Label lblEjemplo = new Label();
    private final TextField derTexto;
    private final CheckBox derNumeros = new CheckBox("Agregar la numeración 1/10 al final");
    private final CheckBox letrasOn = new CheckBox("Folio en letras debajo (FOLIO UNO, FOLIO DOS…)");
    private final TextField letrasTexto;
    private final CheckBox extraOn = new CheckBox("Repetir la numeración abajo");
    private final ToggleGroup extraPos = new ToggleGroup();
    private final RadioButton abajoDer = new RadioButton("Abajo a la derecha");
    private final RadioButton abajoCentro = new RadioButton("Abajo al centro");
    private final ComboBox<String> formato = new ComboBox<>();
    private final TextField formatoPropio;
    private final Controles.SelectorLetra letra;
    private final Controles.SelectorColor color;
    private final Spinner<Integer> tamano;
    private final CheckBox recuadro = new CheckBox("Con recuadro");
    private final CheckBox editable = new CheckBox("Texto editable en Adobe (Rellenar y firmar)");
    private final Spinner<Integer> lateral, vertical, saltar;
    /** Mover cada leyenda en todas las hojas (puntos), en el orden de Motor.LEYENDAS. */
    @SuppressWarnings("unchecked")
    private final Spinner<Integer>[] movX = new Spinner[Motor.LEYENDAS.length], movY = new Spinner[Motor.LEYENDAS.length];
    /** Dónde quedó cada leyenda en la hoja que se ve (para agarrarlas con el ratón). */
    private volatile List<Motor.Marco> marcos = List.of();
    private volatile int marcosHoja = -1;
    private Motor.Marco agarrada;
    private final TextField hojas = Ui.campo("");
    private final Button btnIndexar;
    private final VistaPrevia vista;
    private final EditorHojas editor;

    IndexarPanel() {
        Ui.estilo(this);
        Runnable r = this::refrescar;
        OpcionesIndexar d = cargar();
        izqOn.setSelected(d.izqOn);
        izqTexto = Ui.campo(d.izqTexto);
        abajoIzqOn.setSelected(d.abajoIzqOn);
        abajoIzqTexto = Ui.campo(d.abajoIzqTexto);
        derOn.setSelected(d.derOn);
        modoNombre.setToggleGroup(derModo);
        modoLibre.setToggleGroup(derModo);
        modoNombre.setUserData("delimitador");
        modoLibre.setUserData("libre");
        ("libre".equals(d.derModo) ? modoLibre : modoNombre).setSelected(true);
        delimitador = Ui.campo(d.delimitador);
        delimitador.setPrefColumnCount(4);
        delimitador.setMaxWidth(70);
        bloque = Controles.numero(0, 20, d.bloque, r);
        derTexto = Ui.campo(d.derTexto);
        derNumeros.setSelected(d.derNumeros);
        letrasOn.setSelected(d.letrasOn);
        letrasTexto = Ui.campo(d.letrasTexto);
        extraOn.setSelected(d.extraOn);
        abajoDer.setToggleGroup(extraPos);
        abajoCentro.setToggleGroup(extraPos);
        abajoDer.setUserData("bottom_right");
        abajoCentro.setUserData("bottom_center");
        ("bottom_center".equals(d.extraPos) ? abajoCentro : abajoDer).setSelected(true);
        String conocido = null;
        for (String[] f : FORMATOS) {
            formato.getItems().add(f[0]);
            if (f[1].equals(d.extraFormato)) {
                conocido = f[0];
            }
        }
        formato.setValue(conocido != null ? conocido : "Personalizado...");
        formato.setMaxWidth(Double.MAX_VALUE);
        formatoPropio = Ui.campo(conocido == null ? d.extraFormato : "{pagina}/{total}");
        tamano = Controles.numero(6, 48, Math.round(d.tamano), r);
        recuadro.setSelected(d.recuadro);
        editable.setSelected(d.editable);
        lateral = Controles.numero(0, 300, Math.round(d.lateral), r);
        vertical = Controles.numero(0, 300, Math.round(d.vertical), r);
        saltar = Controles.numero(0, 999, 0, r);
        for (int k = 0; k < Motor.LEYENDAS.length; k++) {
            movX[k] = Controles.numero(-2000, 2000, Math.round(d.movX[k]), r);
            movY[k] = Controles.numero(-2000, 2000, Math.round(d.movY[k]), r);
        }
        letra = new Controles.SelectorLetra(d.fuente, d.negrita, r);
        color = new Controles.SelectorColor(d.color, COLORES, r);

        // ---------- 1. archivos
        Button agregarPdf = Ui.boton("Agregar PDF", UniconsLine.FILE_ALT, () -> {
            List<File> f = Ui.elegirPdfs(this, true);
            if (f != null) {
                recibir(f);
            }
        });
        Button agregarCarpeta = Ui.boton("Agregar carpeta", UniconsLine.FOLDER, () -> {
            File f = Ui.elegirCarpeta(this, "Elegir carpeta (se incluyen todas las subcarpetas)", null);
            if (f != null) {
                recibir(List.of(f));
            }
        });
        Button agregarComprimido = Ui.boton("Agregar ZIP / RAR / 7z", UniconsLine.FILE_ALT, () -> {
            File f = Ui.elegirComprimido(this);
            if (f != null) {
                recibir(List.of(f));
            }
        });
        Button limpiar = Ui.boton("Limpiar", UniconsLine.TRASH_ALT, this::limpiar);
        FlowPane fb = new FlowPane(4, 4, agregarPdf, agregarCarpeta, agregarComprimido, limpiar);
        lista.setPrefHeight(120);
        lista.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            if (b != null && b.intValue() >= 0 && b.intValue() != actual) {
                verArchivo(b.intValue());
            }
        });
        lista.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            e.consume();
        });
        lista.setOnDragDropped(e -> {
            boolean ok = e.getDragboard().hasFiles();
            if (ok) {
                recibir(List.copyOf(e.getDragboard().getFiles()));
            }
            e.setDropCompleted(ok);
            e.consume();
        });
        VBox pArchivos = Controles.bloque(fb, lista, lblArchivos);

        // ---------- 2. leyendas izquierdas
        VBox i1 = indentar(izqTexto);
        VBox i2 = indentar(abajoIzqTexto, Controles.nota("Ej.: CONFIDENCIAL / PROYECTO 2026"));
        VBox pIzq = Controles.bloque(izqOn, i1, abajoIzqOn, i2);

        // ---------- 3. leyenda derecha
        HBox fd = new HBox(10, Controles.conEtiqueta("Separador (espacio, - o _)", delimitador),
                Controles.conEtiqueta("Pedazo (0 = el primero)", bloque));
        lblEjemplo.setWrapText(true);
        lblEjemplo.getStyleClass().add("foliado-acento");
        lblEjemplo.setStyle("-fx-font-size: 0.92em;");
        VBox sub = indentar(modoNombre, indentar(fd, lblEjemplo), modoLibre, indentar(derTexto), derNumeros);
        VBox sub2 = indentar(Controles.conEtiqueta("Texto antes del número en letras", letrasTexto), Controles.nota(
                "Va justo debajo de la leyenda de arriba a la derecha, con el número de cada hoja, en español."));
        HBox fp = new HBox(10, abajoDer, abajoCentro);
        VBox sub3 = indentar(fp, Controles.conEtiqueta("Formato", formato), formatoPropio,
                Controles.nota("Personalizado: use {pagina} y {total}. Ej.: Hoja {pagina} de {total}"));
        VBox pDer = Controles.bloque(derOn, sub, letrasOn, sub2, extraOn, sub3);

        // ---------- 4. letra, color y tamaño
        HBox ft = new HBox(6, new Label("Tamaño letra"), tamano);
        ft.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox pLetra = Controles.bloque(letra, color, ft);

        // ---------- 5. más opciones (como en Foliar)
        javafx.scene.layout.GridPane g3 = new javafx.scene.layout.GridPane();
        g3.setHgap(6);
        g3.setVgap(6);
        for (int k = 0; k < 2; k++) {
            javafx.scene.layout.ColumnConstraints c = new javafx.scene.layout.ColumnConstraints();
            c.setPercentWidth(50);
            g3.getColumnConstraints().add(c);
        }
        g3.add(Controles.conEtiqueta("Distancia de los lados", lateral), 0, 0);
        g3.add(Controles.conEtiqueta("Distancia de arriba y abajo", vertical), 1, 0);
        g3.add(Controles.conEtiqueta("Hojas sin leyendas al inicio", saltar), 0, 1);
        javafx.scene.layout.GridPane g4 = new javafx.scene.layout.GridPane();
        g4.setHgap(6);
        g4.setVgap(4);
        g4.add(new Label("Leyenda"), 0, 0);
        g4.add(new Label("→ Derecha (−izq.)"), 1, 0);
        g4.add(new Label("↓ Abajo (−arriba)"), 2, 0);
        for (int k = 0; k < Motor.LEYENDAS.length; k++) {
            Label n = new Label(Motor.LEYENDAS[k]);
            n.setWrapText(true);
            g4.add(n, 0, k + 1);
            g4.add(movX[k], 1, k + 1);
            g4.add(movY[k], 2, k + 1);
        }
        Button aSuLugar = Ui.boton("Poner todas en su lugar", UniconsLine.REDO, () -> {
            for (int k = 0; k < Motor.LEYENDAS.length; k++) {
                movX[k].getValueFactory().setValue(0);
                movY[k].getValueFactory().setValue(0);
            }
        });
        VBox pMas = Controles.bloque(recuadro, editable, Controles.nota("Así, en Adobe (Acrobat o Reader) con \"Rellenar y firmar\" le da clic al texto y lo cambia. Se ve y se imprime igual."), g3, Controles.nota(
                "Distancias en puntos (28 puntos ≈ 1 cm). Las hojas del inicio que se saltan no cuentan en la "
                        + "numeración: la primera hoja con leyendas es la 1."),
                Controles.titulo("Mover cada leyenda por separado"), g4, aSuLugar,
                Controles.nota("Esto mueve la leyenda en todas las hojas, sin mover las demás. También puede "
                        + "agarrar una leyenda con el ratón en la vista previa y arrastrarla: al soltarla le pregunta "
                        + "si es solo para esa hoja o para todas."),
                Controles.titulo("Hojas a indexar"), hojas,
                Controles.nota("Vacío = todas. Ej.: 3-50. Vale para todos los PDF de la lista."));

        VBox opciones = new VBox(Style.DEFAULT_SPACING);
        opciones.getChildren().addAll(Views.titledPane("1. Archivos PDF", pArchivos),
                Views.titledPane("2. Leyendas izquierdas", pIzq),
                Views.titledPane("3. Leyenda derecha y numeración", pDer),
                Views.titledPane("4. Letra, color y tamaño", pLetra), Views.titledPane("5. Más opciones", pMas),
                Views.titledPane("6. Carpeta de salida",
                        Controles.nota("Al guardar elige la carpeta. Se crea la misma estructura de subcarpetas con "
                                + "los mismos nombres. No se puede usar la carpeta donde están los originales (así "
                                + "nunca se reemplazan).")));
        for (Node n : opciones.getChildren()) {
            ((TitledPane) n).setMaxWidth(Double.MAX_VALUE);
        }
        opciones.setPadding(new Insets(4, 8, 8, 4));
        ScrollPane scroll = new ScrollPane(opciones);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        btnIndexar = Ui.boton("Indexar y guardar", UniconsLine.TAG_ALT, this::procesar);
        btnIndexar.getStyleClass().addAll("footer-button", "run-button");
        btnIndexar.setMaxWidth(Double.MAX_VALUE);
        btnIndexar.setDisable(true);
        Button manual = Ui.boton("Manual", UniconsLine.BOOK_OPEN, () -> ManualPanel.abrir(this, "indexar"));
        manual.setMaxWidth(Double.MAX_VALUE);
        VBox abajo = new VBox(6, btnIndexar, manual);
        abajo.setPadding(new Insets(8));
        VBox izq = new VBox(scroll, abajo);
        izq.setPrefWidth(430);
        izq.setMinWidth(340);
        izq.getStyleClass().addAll(Style.CONTAINER.css());
        setLeft(izq);

        // ---------- vista previa
        Button bPdf = Ui.boton("Agregar PDF", UniconsLine.FILE_ALT, agregarPdf::fire);
        Button bCarpeta = Ui.boton("Agregar carpeta", UniconsLine.FOLDER, agregarCarpeta::fire);
        vista = new VistaPrevia("Arrastre aquí los PDF o una carpeta", "Verá las leyendas en la hoja antes de guardar.",
                List.of(bPdf, bCarpeta), this::recibir);
        vista.lblAyuda.setText("Vista previa (todavía no se guarda nada).");
        editor = new EditorHojas(this::generales, vista::indice, vista::total, letra::nombres, this::refrescar, false);
        editor.setVisible(false);
        editor.setManaged(false);
        vista.ponerDebajoDeNavegacion(editor);
        // al cambiar la letra general, cambia en todas las hojas (también en las personalizadas)
        letra.nombre.addListener((ob, a, b) -> editor.cambiarLetraEnTodas(b, null));
        letra.negrita.selectedProperty().addListener((ob, a, b) -> editor.cambiarLetraEnTodas(null, b));
        vista.alCambiarHoja(this::rotular);
        vista.dibujo(this::dibujo);
        vista.raton(new VistaPrevia.Raton() {
            @Override
            public void encima(double fx, double fy) {
                Motor.Marco m = fx < 0 ? null : leyendaEn(fx, fy);
                if (m != null) {
                    // se ve que se puede agarrar: manito y recuadro azul
                    vista.cursor(javafx.scene.Cursor.OPEN_HAND);
                    marcar(m, 0, 0);
                    vista.lblAyuda.setText("Mantenga apretado y arrastre para mover \"" + Motor.LEYENDAS[m.leyenda()]
                            + "\" (las demás no se mueven).");
                    sobre = true;
                } else if (sobre) {
                    vista.cursor(javafx.scene.Cursor.DEFAULT);
                    vista.limpiarMarcas();
                    rotular();
                    sobre = false;
                }
            }

            @Override
            public void clic(double fx, double fy) {
                agarrada = leyendaEn(fx, fy);
                if (agarrada != null) {
                    vista.cursor(javafx.scene.Cursor.CLOSED_HAND);
                    marcar(agarrada, 0, 0);
                }
            }

            @Override
            public void arrastre(double fx, double fy, double dx, double dy) {
                if (agarrada != null) {
                    marcar(agarrada, dx, dy); // el recuadro punteado muestra dónde quedará
                } else if (editor.editando()) {
                    vista.marcaFlecha(dx, dy);
                }
            }

            @Override
            public void soltar(double fx, double fy, double dx, double dy) {
                Motor.Marco m = agarrada;
                agarrada = null;
                sobre = false;
                vista.cursor(javafx.scene.Cursor.DEFAULT);
                if (m != null) {
                    if (Math.abs(dx) >= 1 || Math.abs(dy) >= 1) {
                        moverLeyenda(m.leyenda(), (float) dx, (float) dy);
                    }
                } else if (editor.editando() && editor.grupo.contains(vista.indice())) {
                    editor.mover((float) dx, (float) dy);
                }
            }
        });
        setCenter(vista);
        BorderPane.setMargin(vista, new Insets(0, 0, 0, 4));

        for (TextField t : List.of(izqTexto, abajoIzqTexto, delimitador, derTexto, letrasTexto, formatoPropio, hojas)) {
            t.textProperty().addListener((o, a, b) -> refrescar());
        }
        for (CheckBox c : List.of(izqOn, abajoIzqOn, derOn, derNumeros, letrasOn, extraOn, recuadro)) {
            c.selectedProperty().addListener((o, a, b) -> refrescar());
        }
        derModo.selectedToggleProperty().addListener((o, a, b) -> refrescar());
        extraPos.selectedToggleProperty().addListener((o, a, b) -> refrescar());
        formato.valueProperty().addListener((o, a, b) -> refrescar());
        refrescar();
    }

    private static VBox indentar(Node... hijos) {
        VBox v = new VBox(4, hijos);
        v.setPadding(new Insets(0, 0, 0, 22));
        return v;
    }

    private EditorHojas.Generales generales() {
        return new EditorHojas.Generales(color.color.get(), Controles.valor(tamano, 20), letra.nombre.get(),
                letra.negrita.isSelected(), false);
    }

    private void rotular() {
        if (!vista.hayPdf() || actual < 0) {
            return;
        }
        String marca = editor.reglas.containsKey(vista.indice()) ? "  ✎ personalizada" : "";
        vista.lblHoja.setText("Hoja " + (vista.indice() + 1) + " de " + vista.total() + marca);
        vista.lblAyuda.setText("Vista previa (todavía no se guarda nada). Arrastre una leyenda con el ratón para "
                + "moverla.  ·  " + Lote.nombreVisible(items.get(actual).relativa()));
    }

    // ------------------------------------------------------------ opciones
    OpcionesIndexar opciones() {
        OpcionesIndexar o = new OpcionesIndexar();
        o.izqOn = izqOn.isSelected();
        o.izqTexto = izqTexto.getText();
        o.abajoIzqOn = abajoIzqOn.isSelected();
        o.abajoIzqTexto = abajoIzqTexto.getText();
        o.derOn = derOn.isSelected();
        o.derModo = derModo.getSelectedToggle() == null ? "delimitador" :
                String.valueOf(derModo.getSelectedToggle().getUserData());
        o.delimitador = delimitador.getText().isEmpty() ? " " : delimitador.getText();
        o.bloque = Controles.valor(bloque, 0);
        o.derTexto = derTexto.getText();
        o.derNumeros = derNumeros.isSelected();
        o.letrasOn = letrasOn.isSelected();
        o.letrasTexto = letrasTexto.getText();
        o.extraOn = extraOn.isSelected();
        o.extraPos = extraPos.getSelectedToggle() == null ? "bottom_right" :
                String.valueOf(extraPos.getSelectedToggle().getUserData());
        String f = "{pagina}/{total}";
        for (String[] x : FORMATOS) {
            if (x[0].equals(formato.getValue())) {
                f = x[1];
            }
        }
        if ("__propio__".equals(f)) {
            f = formatoPropio.getText().isBlank() ? "{pagina}/{total}" : formatoPropio.getText().trim();
        }
        o.extraFormato = f;
        o.tamano = Controles.valor(tamano, 20);
        o.color = color.color.get();
        o.fuente = letra.ruta();
        o.negrita = letra.negritaEfectiva();
        o.recuadro = recuadro.isSelected();
        o.editable = editable.isSelected();
        o.lateral = Controles.valor(lateral, 51);
        o.vertical = Controles.valor(vertical, 23);
        o.saltar = Controles.valor(saltar, 0);
        o.paginas = hojas.getText() == null ? "" : hojas.getText().trim();
        for (int k = 0; k < Motor.LEYENDAS.length; k++) {
            o.movX[k] = Controles.valor(movX[k], 0);
            o.movY[k] = Controles.valor(movY[k], 0);
        }
        return o;
    }

    private static OpcionesIndexar cargar() {
        OpcionesIndexar o = new OpcionesIndexar();
        o.izqOn = Config.si("indexar.izqOn", o.izqOn);
        o.izqTexto = Config.texto("indexar.izqTexto", o.izqTexto);
        o.abajoIzqOn = Config.si("indexar.abajoIzqOn", o.abajoIzqOn);
        o.abajoIzqTexto = Config.texto("indexar.abajoIzqTexto", o.abajoIzqTexto);
        o.derOn = Config.si("indexar.derOn", o.derOn);
        o.derModo = Config.texto("indexar.derModo", o.derModo);
        o.delimitador = Config.texto("indexar.delimitador", o.delimitador);
        o.bloque = (int) Config.numero("indexar.bloque", o.bloque);
        o.derTexto = Config.texto("indexar.derTexto", o.derTexto);
        o.derNumeros = Config.si("indexar.derNumeros", o.derNumeros);
        o.letrasOn = Config.si("indexar.letrasOn", o.letrasOn);
        o.letrasTexto = Config.texto("indexar.letrasTexto", o.letrasTexto);
        o.extraOn = Config.si("indexar.extraOn", o.extraOn);
        o.extraPos = Config.texto("indexar.extraPos", o.extraPos);
        o.extraFormato = Config.texto("indexar.extraFormato", o.extraFormato);
        o.tamano = Config.numero("indexar.tamano", o.tamano);
        o.color = Motor.colorDeHex(Config.texto("indexar.color", Motor.hexDeColor(o.color)));
        o.fuente = Config.texto("indexar.fuente", o.fuente);
        o.negrita = Config.si("indexar.negrita", o.negrita);
        o.recuadro = Config.si("indexar.recuadro", o.recuadro);
        o.editable = Config.si("indexar.editable", false);
        o.lateral = Config.numero("indexar.lateral", o.lateral);
        o.vertical = Config.numero("indexar.vertical", o.vertical);
        for (int k = 0; k < o.movX.length; k++) {
            o.movX[k] = Config.numero("indexar.movX" + k, 0);
            o.movY[k] = Config.numero("indexar.movY" + k, 0);
        }
        return o;
    }

    private void recordar(OpcionesIndexar o) {
        Config.poner("indexar.izqOn", o.izqOn);
        Config.poner("indexar.izqTexto", o.izqTexto);
        Config.poner("indexar.abajoIzqOn", o.abajoIzqOn);
        Config.poner("indexar.abajoIzqTexto", o.abajoIzqTexto);
        Config.poner("indexar.derOn", o.derOn);
        Config.poner("indexar.derModo", o.derModo);
        Config.poner("indexar.delimitador", o.delimitador);
        Config.poner("indexar.bloque", o.bloque);
        Config.poner("indexar.derTexto", o.derTexto);
        Config.poner("indexar.derNumeros", o.derNumeros);
        Config.poner("indexar.letrasOn", o.letrasOn);
        Config.poner("indexar.letrasTexto", o.letrasTexto);
        Config.poner("indexar.extraOn", o.extraOn);
        Config.poner("indexar.extraPos", o.extraPos);
        Config.poner("indexar.extraFormato", o.extraFormato);
        Config.poner("indexar.tamano", o.tamano);
        Config.poner("indexar.color", Motor.hexDeColor(o.color));
        Config.poner("indexar.fuente", o.fuente);
        Config.poner("indexar.negrita", letra.negrita.isSelected());
        Config.poner("indexar.recuadro", o.recuadro);
        Config.poner("indexar.editable", o.editable);
        Config.poner("indexar.lateral", o.lateral);
        Config.poner("indexar.vertical", o.vertical);
        for (int k = 0; k < o.movX.length; k++) {
            Config.poner("indexar.movX" + k, o.movX[k]);
            Config.poner("indexar.movY" + k, o.movY[k]);
        }
        Config.guardar();
    }

    // ------------------------------------------------------------ vista previa
    private void refrescar() {
        OpcionesIndexar o = opciones();
        if (actual >= 0 && actual < items.size()) {
            String nombre = Motor.sinExtension(items.get(actual).origen().getName());
            OpcionesIndexar ej = opciones();
            ej.derModo = "delimitador";
            lblEjemplo.setText("Ej. con '" + nombre + "': " + Motor.textoDerechoIndexar(nombre, ej) + " 1/10");
        } else {
            lblEjemplo.setText("");
        }
        formatoPropio.setDisable(!"Personalizado...".equals(formato.getValue()));
        rotular();
        vista.refrescar();
    }

    private Motor.Dibujo dibujo() {
        if (!vista.hayPdf() || actual < 0) {
            return null;
        }
        OpcionesIndexar o = opciones();
        int i = vista.indice();
        String nombre = Motor.sinExtension(items.get(actual).origen().getName());
        Map<Integer, ReglaHoja> porHoja = editor.porHoja();
        List<Integer> paginas;
        try {
            paginas = Motor.listaPaginasFlexible(o.paginas, vista.total());
        } catch (IllegalArgumentException e) {
            paginas = null; // a medio escribir
        }
        List<Integer> elegidas = Motor.hojasIndexadas(vista.total(), o, paginas, porHoja);
        int pos = elegidas.indexOf(i);
        if (pos < 0) {
            return (doc, hoja, letras) -> {
            }; // esta hoja no lleva leyendas
        }
        long numero = pos + 1, total = elegidas.size();
        ReglaHoja regla = porHoja.get(i);
        return (doc, hoja, letras) -> {
            List<Motor.Marco> m = new ArrayList<>();
            Motor.indexarHoja(doc, hoja, numero, total, nombre, o, regla, letras, m);
            marcos = m;
            marcosHoja = i;
        };
    }

    private boolean sobre;

    /** Recuadro azul punteado alrededor de una leyenda (corrido dx, dy puntos). */
    private void marcar(Motor.Marco m, double dx, double dy) {
        float[] t = vista.tamanoHoja();
        double cx = (m.x0() + m.x1()) / 2 + dx, cy = (m.y0() + m.y1()) / 2 + dy;
        vista.marcaRecuadro(cx / t[0], cy / t[1], m.x1() - m.x0(), m.y1() - m.y0(),
                javafx.scene.paint.Color.web("#2b7de9"));
    }

    /** Para las pruebas. */
    Motor.Marco agarradaParaPrueba() {
        return agarrada;
    }

    List<Motor.Marco> marcosParaPrueba() {
        return marcos;
    }

    /** La leyenda que está debajo del ratón (fx, fy = fracción de la hoja), o null. */
    private Motor.Marco leyendaEn(double fx, double fy) {
        if (!vista.hayPdf() || marcosHoja != vista.indice()) {
            return null;
        }
        float[] t = vista.tamanoHoja();
        double x = fx * t[0], y = fy * t[1];
        Motor.Marco mejor = null;
        for (Motor.Marco m : marcos) {
            if (m.contiene(x, y, 4)) {
                mejor = m;
            }
        }
        return mejor;
    }

    /** Después de arrastrar UNA leyenda: solo en esta hoja o en todas. */
    private void moverLeyenda(int cual, float dx, float dy) {
        int r = Ui.elegir(this, "Mover \"" + Motor.LEYENDAS[cual] + "\"",
                "¿La muevo solo en esta hoja o en todas las hojas? Las demás leyendas se quedan donde están.",
                "Solo en esta hoja", "En todas las hojas");
        if (r == 0) {
            editor.moverLeyenda(vista.indice(), cual, dx, dy);
        } else if (r == 1) {
            movX[cual].getValueFactory().setValue(Controles.valor(movX[cual], 0) + Math.round(dx));
            movY[cual].getValueFactory().setValue(Controles.valor(movY[cual], 0) + Math.round(dy));
        } else {
            refrescar();
        }
    }

    // ------------------------------------------------------------ archivos
    void recibir(List<File> rutas) {
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
                L.pide = (archivo, mala) -> Ui.enFx(() -> Ui.pedirClaveComprimido(this, archivo, mala));
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
                        + "_indexado.zip" + Lote.MARCA_ZIP;
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
        lblArchivos.setText(items.size() + " archivos PDF");
        btnIndexar.setDisable(false);
        if (items.size() == agregados) {
            verArchivo(0);
        }
    }

    private void limpiar() {
        editor.olvidarTodo();
        editor.setVisible(false);
        editor.setManaged(false);
        items.clear();
        claves.clear();
        for (Abierto a : abiertos) {
            a.lote().limpiar();
        }
        abiertos.clear();
        actual = -1;
        lista.getItems().clear();
        btnIndexar.setDisable(true);
        vista.cerrar();
        vista.lblAyuda.setText("Vista previa (todavía no se guarda nada).");
        lblArchivos.setText("Arrastre aquí PDF, carpetas o comprimidos (ZIP, RAR, 7z...).");
        refrescar();
    }

    private void verArchivo(int k) {
        File f = items.get(k).origen();
        if (!claves.containsKey(f) && Motor.pideClave(f)) {
            String c = Ui.pedirClave(this, f.getName(), "Sin la contraseña no se puede ver ni indexar.");
            if (c == null) {
                vista.lblAyuda.setText(f.getName() + ": tiene contraseña.");
                return;
            }
            claves.put(f, c);
        }
        try {
            Motor.Previa p = new Motor.Previa(f, claves.get(f));
            actual = k;
            editor.cambiarDocumento(f.getAbsolutePath());
            editor.setVisible(true);
            editor.setManaged(true);
            vista.abrir(p);
            lista.getSelectionModel().select(k);
            lista.scrollTo(k);
            refrescar();
        } catch (Exception e) {
            claves.remove(f);
            vista.lblAyuda.setText(f.getName() + ": " + Ui.textoError(e));
        }
    }

    // ------------------------------------------------------------ guardar
    private void procesar() {
        if (items.isEmpty()) {
            return;
        }
        OpcionesIndexar o = opciones();
        try {
            Motor.listaPaginasFlexible(o.paginas, Integer.MAX_VALUE / 2);
        } catch (IllegalArgumentException e) {
            Ui.aviso(this, "Hojas a indexar: " + e.getMessage());
            return;
        }
        File carpeta = Ui.elegirCarpeta(this, "¿En qué carpeta guardo los PDF indexados?", null);
        if (carpeta == null) {
            return;
        }
        Path destino = carpeta.getAbsoluteFile().toPath().normalize();
        // nunca encima de los originales: la carpeta de salida no puede contener ningún original
        List<File> originales = new ArrayList<>();
        for (Item it : items) {
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
        recordar(o);
        List<Item> copia = List.copyOf(items);
        List<Map<Integer, ReglaHoja>> reglas = new ArrayList<>();
        for (Item it : copia) {
            reglas.add(EditorHojas.porHoja(editor.reglasDe(it.origen().getAbsolutePath())));
        }
        // lo que viene de comprimidos se arma en la carpeta temporal de Windows (OneDrive no la toca) y al final
        // solo se copia el ZIP terminado a la carpeta elegida
        try {
            trabajoZip = Files.createTempDirectory("pdfsam_indexar_");
        } catch (IOException e) {
            Ui.error(this, Ui.textoError(e));
            return;
        }
        Ui.enSegundoPlano(this, "Indexando", true, p -> {
            List<File> salidas = new ArrayList<>();
            List<String> fallidos = new ArrayList<>();
            List<Item> protegidos = new ArrayList<>();
            int hojas = 0, n = copia.size();
            for (int k = 0; k < n; k++) {
                if (p.cancelado()) {
                    break;
                }
                Item it = copia.get(k);
                String texto = "Archivo " + (k + 1) + " de " + n + ": " + it.origen().getName();
                File salida = salidaDe(it, destino).toFile();
                int kk = k;
                try {
                    Motor.indexar(it.origen(), salida, claves.get(it.origen()), it.origen().getName(), o, reglas.get(k),
                            (h, t) -> p.poner((kk + (t == 0 ? 1 : (double) h / t)) / n, texto));
                    salidas.add(salida);
                    hojas += Motor.contarHojas(salida, null);
                } catch (Motor.ErrorProtegido e) {
                    protegidos.add(it);
                } catch (Exception e) {
                    fallidos.add(Lote.nombreVisible(it.relativa()) + ": " + Ui.textoError(e));
                }
            }
            p.poner(1, "");
            return new Object[] { salidas, fallidos, hojas, protegidos };
        }, res -> terminar(res, destino, o, copia, reglas), null);
    }

    @SuppressWarnings("unchecked")
    private void terminar(Object[] res, Path destino, OpcionesIndexar o, List<Item> copia,
            List<Map<Integer, ReglaHoja>> reglas) {
        List<File> salidas = (List<File>) res[0];
        List<String> fallidos = (List<String>) res[1];
        int hojas = (Integer) res[2];
        List<Item> protegidos = (List<Item>) res[3];
        // los PDF con contraseña: se pide la contraseña de cada uno y se vuelven a intentar
        for (Item it : protegidos) {
            String c = Ui.pedirClave(this, Lote.nombreVisible(it.relativa()), "Si no la sabe, ese archivo no se indexa.");
            if (c == null) {
                fallidos.add(Lote.nombreVisible(it.relativa()) + ": tiene contraseña");
                continue;
            }
            try {
                File salida = salidaDe(it, destino).toFile();
                Motor.indexar(it.origen(), salida, c, it.origen().getName(), o, reglas.get(copia.indexOf(it)), null);
                salidas.add(salida);
                hojas += Motor.contarHojas(salida, null);
            } catch (Exception e) {
                fallidos.add(Lote.nombreVisible(it.relativa()) + ": " + Ui.textoError(e));
            }
        }
        List<Abierto> usados = new ArrayList<>();
        for (Abierto a : abiertos) {
            if (copia.stream().anyMatch(it -> it.relativa().startsWith(a.marcada() + "/"))) {
                usados.add(a);
            }
        }
        if (!usados.isEmpty()) {
            // los comprimidos: se arma el ZIP con lo indexado y todo lo demás que traían
            int hojasFin = hojas;
            Ui.enSegundoPlano(this, "Armando los ZIP", false, p -> {
                try {
                    for (Abierto a : usados) {
                        p.poner(-1, a.archivo().getName());
                        armarZip(destino, trabajoZip, a);
                    }
                } finally {
                    Lote.borrar(trabajoZip);
                }
                return null;
            }, r -> resumen(salidas, fallidos, hojasFin, destino, true),
                    e -> Ui.error(this, Ui.textoError(e)));
            return;
        }
        Lote.borrar(trabajoZip);
        resumen(salidas, fallidos, hojas, destino, false);
    }

    /** destino/…/Nombre_indexado.zip con lo indexado y, tal cual, todo lo demás que traía el comprimido. */
    /** Dónde se guarda cada PDF: los de comprimidos, en la carpeta temporal; los demás, en la carpeta elegida. */
    private Path salidaDe(Item it, Path destino) {
        return it.relativa().contains(Lote.MARCA_ZIP) ? trabajoZip.resolve(it.relativa()) : destino.resolve(it.relativa());
    }

    private Path trabajoZip;

    private static void armarZip(Path destino, Path trabajo, Abierto a) throws IOException {
        Path m = trabajo.resolve(a.marcada());
        Lote L = a.lote();
        Files.createDirectories(m);
        for (String c : L.carpetas) {
            Files.createDirectories(m.resolve(c));
        }
        for (String rel : L.archivos) {
            Path t = m.resolve(rel);
            if (!Files.exists(t)) {
                Lote.copiar(L.raiz.resolve(rel), t);
            }
        }
        Lote.cerrarZipsInternos(m);
        String n = m.getFileName().toString();
        String zip = n.substring(0, n.length() - Lote.MARCA_ZIP.length());
        // el ZIP se arma en la carpeta temporal y se copia ya terminado a la carpeta elegida
        Path listo = m.resolveSibling(zip + ".listo");
        Lote.comprimirCarpeta(m, listo);
        Lote.borrar(m);
        Path relativa = trabajo.relativize(m.resolveSibling(zip));
        Path fin = destino.resolve(relativa.toString());
        for (int i = 2; Files.exists(fin); i++) {
            fin = fin.resolveSibling(zip.substring(0, zip.length() - 4) + " (" + i + ").zip");
        }
        Lote.copiarConReintentos(listo, fin);
        Files.deleteIfExists(listo);
    }

    private void resumen(List<File> salidas, List<String> fallidos, int hojas, Path destino, boolean conZip) {
        StringBuilder d = new StringBuilder(salidas.size() + " archivos · " + hojas + " hojas indexadas");
        if (!fallidos.isEmpty()) {
            d.append("\n\nNo se pudieron procesar:\n");
            for (int i = 0; i < Math.min(8, fallidos.size()); i++) {
                d.append(fallidos.get(i)).append('\n');
            }
        }
        if (conZip) {
            d.append("\n\nLo que venía en comprimidos (ZIP, RAR, 7z...) quedó en un ZIP con el mismo nombre y "
                    + "\"_indexado\" al final, con todas sus carpetas y archivos.");
        }
        Ui.terminado(this, salidas.size() == 1 && !conZip ? salidas.get(0) : destino.toFile(), "PDF indexados",
                d.toString());
    }
}
