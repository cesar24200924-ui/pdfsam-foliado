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
            return relativa;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final ListView<Item> lista = new ListView<>();
    private final Label lblArchivos = Controles.nota("Arrastre aquí PDF o carpetas (se incluyen las subcarpetas).");
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
    private final Button btnIndexar;
    private final VistaPrevia vista;
    private final EditorHojas editor;

    IndexarPanel() {
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
        Button limpiar = Ui.boton("Limpiar", UniconsLine.TRASH_ALT, this::limpiar);
        FlowPane fb = new FlowPane(4, 4, agregarPdf, agregarCarpeta, limpiar);
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
        lblEjemplo.setStyle("-fx-text-fill: -fx-accent; -fx-font-size: 0.92em;");
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

        VBox opciones = new VBox(Style.DEFAULT_SPACING);
        opciones.getChildren().addAll(Views.titledPane("1. Archivos PDF", pArchivos),
                Views.titledPane("2. Leyendas izquierdas", pIzq),
                Views.titledPane("3. Leyenda derecha y numeración", pDer),
                Views.titledPane("4. Letra, color y tamaño", pLetra), Views.titledPane("5. Carpeta de salida",
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
        vista.alCambiarHoja(this::rotular);
        vista.dibujo(this::dibujo);
        vista.raton(new VistaPrevia.Raton() {
            @Override
            public void clic(double fx, double fy) {
            }

            @Override
            public void arrastre(double fx, double fy, double dx, double dy) {
                if (editor.editando()) {
                    vista.marcaFlecha(dx, dy);
                }
            }

            @Override
            public void soltar(double fx, double fy, double dx, double dy) {
                if (editor.editando() && editor.grupo.contains(vista.indice())) {
                    editor.mover((float) dx, (float) dy);
                }
            }
        });
        setCenter(vista);
        BorderPane.setMargin(vista, new Insets(0, 0, 0, 4));

        for (TextField t : List.of(izqTexto, abajoIzqTexto, delimitador, derTexto, letrasTexto, formatoPropio)) {
            t.textProperty().addListener((o, a, b) -> refrescar());
        }
        for (CheckBox c : List.of(izqOn, abajoIzqOn, derOn, derNumeros, letrasOn, extraOn)) {
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
        vista.lblAyuda.setText("Vista previa (todavía no se guarda nada).  ·  " + items.get(actual).relativa());
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
        long total = vista.total();
        String nombre = Motor.sinExtension(items.get(actual).origen().getName());
        ReglaHoja regla = editor.porHoja().get(i);
        return (doc, hoja, letras) -> Motor.indexarHoja(doc, hoja, i + 1, total, nombre, o, regla, letras);
    }

    // ------------------------------------------------------------ archivos
    void recibir(List<File> rutas) {
        List<Item> nuevos = new ArrayList<>();
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
            if (!rutas.isEmpty() && nuevos.isEmpty()) {
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
        actual = -1;
        lista.getItems().clear();
        btnIndexar.setDisable(true);
        vista.cerrar();
        vista.lblAyuda.setText("Vista previa (todavía no se guarda nada).");
        lblArchivos.setText("Arrastre aquí PDF o carpetas (se incluyen las subcarpetas).");
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
        File carpeta = Ui.elegirCarpeta(this, "¿En qué carpeta guardo los PDF indexados?", null);
        if (carpeta == null) {
            return;
        }
        Path destino = carpeta.getAbsoluteFile().toPath().normalize();
        // nunca encima de los originales: la carpeta de salida no puede contener ningún original
        for (Item it : items) {
            if (it.origen().getAbsoluteFile().toPath().normalize().startsWith(destino)) {
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
                File salida = destino.resolve(it.relativa()).toFile();
                int kk = k;
                try {
                    Motor.indexar(it.origen(), salida, claves.get(it.origen()), it.origen().getName(), o, reglas.get(k),
                            (h, t) -> p.poner((kk + (t == 0 ? 1 : (double) h / t)) / n, texto));
                    salidas.add(salida);
                    hojas += Motor.contarHojas(salida, null);
                } catch (Motor.ErrorProtegido e) {
                    protegidos.add(it);
                } catch (Exception e) {
                    fallidos.add(it.relativa() + ": " + Ui.textoError(e));
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
            String c = Ui.pedirClave(this, it.relativa(), "Si no la sabe, ese archivo no se indexa.");
            if (c == null) {
                fallidos.add(it.relativa() + ": tiene contraseña");
                continue;
            }
            try {
                File salida = destino.resolve(it.relativa()).toFile();
                Motor.indexar(it.origen(), salida, c, it.origen().getName(), o, reglas.get(copia.indexOf(it)), null);
                salidas.add(salida);
                hojas += Motor.contarHojas(salida, null);
            } catch (Exception e) {
                fallidos.add(it.relativa() + ": " + Ui.textoError(e));
            }
        }
        StringBuilder d = new StringBuilder(salidas.size() + " archivos · " + hojas + " hojas indexadas");
        if (!fallidos.isEmpty()) {
            d.append("\n\nNo se pudieron procesar:\n");
            for (int i = 0; i < Math.min(8, fallidos.size()); i++) {
                d.append(fallidos.get(i)).append('\n');
            }
        }
        Ui.terminado(this, salidas.size() == 1 ? salidas.get(0) : destino.toFile(), "PDF indexados", d.toString());
    }
}
