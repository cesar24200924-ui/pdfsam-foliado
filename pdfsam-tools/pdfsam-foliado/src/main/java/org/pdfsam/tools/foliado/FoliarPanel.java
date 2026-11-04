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
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.pdfsam.ui.components.support.Style;
import org.pdfsam.ui.components.support.Views;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Foliar: numeración tipo folio (Folio 001, 002…) en la esquina que se elija o en cualquier lugar (clic o arrastrar
 * en la vista previa). Color libre, letras de la computadora, y cada hoja se folia en la misma esquina tal como se
 * LEE (aunque la hoja esté girada o sea de otro tamaño). La vista previa usa exactamente la misma función que al
 * guardar. También para carpetas o comprimidos completos, con numeración continua (expediente).
 */
class FoliarPanel extends BorderPane {
    private static final String[][] COLORES = { { "Rojo", "#bf0000" }, { "Negro", "#000000" }, { "Azul", "#0d33a6" } };
    private static final String[][] POSICIONES = { { "sup_izq", "Arriba izq." }, { "inf_izq", "Abajo izq." },
            { "sup_centro", "Arriba centro" }, { "inf_centro", "Abajo centro" }, { "sup_der", "Arriba der." },
            { "inf_der", "Abajo der." } };

    private File archivo;
    private String clave;

    private final TextField prefijo = Ui.campo(Config.texto("foliar.prefijo", "Folio "));
    private final Spinner<Integer> inicio, digitos, tamano, margen, saltar;
    private final CheckBox conTotal = new CheckBox("Agregar el total (Folio 001 de 120)");
    private final TextField palabraDe = Ui.campo(Config.texto("foliar.palabraDe", "de"));
    private final TextField sufijo = Ui.campo(Config.texto("foliar.sufijo", ""));
    private final Label asiQueda = new Label();
    private final CheckBox enLetras = new CheckBox("Poner el folio en letras (FOLIO UNO, FOLIO DOS…)");
    private final TextField textoLetras = Ui.campo(Config.texto("foliar.textoLetras", "FOLIO "));
    private final CheckBox conNumeros = new CheckBox("También el folio en números (arriba)");
    private final ToggleGroup posicion = new ToggleGroup();
    private final RadioButton libre = new RadioButton("Libre (arrástrelo en la vista previa)");
    private float libreX = Config.numero("foliar.libreX", 0.85f), libreY = Config.numero("foliar.libreY", 0.05f);
    private final Controles.SelectorColor color;
    private final Controles.SelectorLetra letra;
    private final CheckBox recuadro = new CheckBox("Con recuadro");
    private final CheckBox editable = new CheckBox("Texto editable en Adobe (Rellenar y firmar)");
    private final TextField hojas = Ui.campo("");
    private final Button btnFoliar;
    private final VistaPrevia vista;
    private final EditorHojas editor;
    private final Label lblArchivo = new Label("");

    FoliarPanel() {
        Ui.estilo(this);
        Runnable r = this::refrescar;
        inicio = Controles.numero(0, 99999, (int) Config.numero("foliar.inicio", 1), r);
        digitos = Controles.numero(1, 6, (int) Config.numero("foliar.digitos", 3), r);
        tamano = Controles.numero(6, 40, (int) Config.numero("foliar.tamano", 11), r);
        margen = Controles.numero(4, 120, (int) Config.numero("foliar.margen", 24), r);
        saltar = Controles.numero(0, 999, 0, r);
        color = new Controles.SelectorColor(Motor.colorDeHex(Config.texto("foliar.color", "#bf0000")), COLORES, r);
        letra = new Controles.SelectorLetra(Config.texto("foliar.fuente", "Helvetica"),
                Config.si("foliar.negrita", true), r);
        conTotal.setSelected(Config.si("foliar.conTotal", false));
        enLetras.setSelected(Config.si("foliar.enLetras", false));
        conNumeros.setSelected(Config.si("foliar.conNumeros", true));
        recuadro.setSelected(Config.si("foliar.recuadro", false));
        editable.setSelected(Config.si("foliar.editable", false));

        // ---------- 1. archivo
        Button elegir = Ui.boton("Elegir PDF", UniconsLine.FILE_ALT, this::elegir);
        elegir.setMaxWidth(Double.MAX_VALUE);
        Button carpeta = Ui.boton("Foliar una carpeta o comprimido", UniconsLine.FOLDER, () -> {
        });
        carpeta.setMaxWidth(Double.MAX_VALUE);
        ContextMenu menu = new ContextMenu();
        MenuItem mc = new MenuItem("Elegir carpeta");
        mc.setOnAction(e -> {
            File f = Ui.elegirCarpeta(this, "Elegir la carpeta (se incluyen todas las subcarpetas)", null);
            if (f != null) {
                lote(f);
            }
        });
        MenuItem mz = new MenuItem("Elegir comprimido (ZIP, RAR, 7z...)");
        mz.setOnAction(e -> {
            File f = Ui.elegirComprimido(this);
            if (f != null) {
                lote(f);
            }
        });
        menu.getItems().addAll(mc, mz);
        carpeta.setOnAction(e -> menu.show(carpeta, Side.BOTTOM, 0, 0));
        lblArchivo.setWrapText(true);
        VBox pArchivo = Controles.bloque(elegir, carpeta, lblArchivo,
                Controles.nota("También puede arrastrar el PDF, una carpeta o un comprimido (ZIP, RAR, 7z...) a la vista previa."));

        // ---------- 2. texto
        GridPane g = new GridPane();
        g.setHgap(6);
        g.setVgap(4);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(33.3);
            g.getColumnConstraints().add(c);
        }
        g.add(Controles.conEtiqueta("Empieza en", inicio), 0, 0);
        g.add(Controles.conEtiqueta("Ceros (001)", digitos), 1, 0);
        g.add(Controles.conEtiqueta("Tamaño letra", tamano), 2, 0);
        GridPane g2 = new GridPane();
        g2.setHgap(6);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(50);
            g2.getColumnConstraints().add(c);
        }
        g2.add(Controles.conEtiqueta("Entre el número y el total", palabraDe), 0, 0);
        g2.add(Controles.conEtiqueta("Texto después del número", sufijo), 1, 0);
        asiQueda.setWrapText(true);
        asiQueda.getStyleClass().add("foliado-acento");
        asiQueda.setStyle("-fx-font-weight: bold;");
        VBox pTexto = Controles.bloque(Controles.titulo("Texto antes del número"), prefijo,
                Controles.nota("Ej.: 'Folio ', 'Pág. ', 'Fol. ' o vacío para solo el número"), g, conTotal, g2,
                asiQueda, Controles.nota(
                        "Todo el texto se puede cambiar: ej. 'FOLIO 001 DE 120', 'Pág. 001 / 120' o 'Fol. 001 - SAR'."));

        // ---------- 3. en letras
        VBox sub = Controles.bloque(Controles.conEtiqueta("Texto antes del número en letras", textoLetras),
                conNumeros);
        sub.setPadding(new Insets(0, 0, 0, 22));
        VBox pLetras = Controles.bloque(enLetras, sub, Controles.nota(
                "El folio en letras va justo debajo del folio en números, en la misma esquina, y siempre en español."));

        // ---------- 4. posición
        GridPane rej = new GridPane();
        rej.setHgap(10);
        rej.setVgap(4);
        String guardada = Config.texto("foliar.posicion", "sup_der");
        for (int i = 0; i < POSICIONES.length; i++) {
            RadioButton b = new RadioButton(POSICIONES[i][1]);
            b.setUserData(POSICIONES[i][0]);
            b.setToggleGroup(posicion);
            if (POSICIONES[i][0].equals(guardada)) {
                b.setSelected(true);
            }
            rej.add(b, i % 2, i / 2);
        }
        libre.setUserData("libre");
        libre.setToggleGroup(posicion);
        if ("libre".equals(guardada)) {
            libre.setSelected(true);
        }
        if (posicion.getSelectedToggle() == null) {
            posicion.getToggles().get(4).setSelected(true);
        }
        rej.add(libre, 0, 3, 2, 1);
        VBox pPos = Controles.bloque(rej, Controles.nota(
                "Cada hoja lleva el folio en la misma esquina tal como se lee, aunque esté girada o sea de otro tamaño."));

        // ---------- 7. más
        GridPane g3 = new GridPane();
        g3.setHgap(6);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(50);
            g3.getColumnConstraints().add(c);
        }
        g3.add(Controles.conEtiqueta("Distancia del borde", margen), 0, 0);
        g3.add(Controles.conEtiqueta("Hojas sin folio al inicio", saltar), 1, 0);
        VBox pMas = Controles.bloque(recuadro, g3, editable, Controles.nota("Así, en Adobe (Acrobat o Reader) con \"Rellenar y firmar\" le da clic al texto y lo cambia. Se ve y se imprime igual."));
        VBox pHojas = Controles.bloque(hojas, Controles.nota("Vacío = todas. Ej.: 3-50"));

        VBox opciones = new VBox(Style.DEFAULT_SPACING);
        opciones.getChildren().addAll(Views.titledPane("1. PDF a foliar", pArchivo),
                Views.titledPane("2. Texto del folio", pTexto), Views.titledPane("3. Folio en letras", pLetras),
                Views.titledPane("4. Posición", pPos), Views.titledPane("5. Color", color),
                Views.titledPane("6. Letra", letra), Views.titledPane("7. Más opciones", pMas),
                Views.titledPane("8. Hojas a foliar", pHojas));
        for (Node n : opciones.getChildren()) {
            ((TitledPane) n).setMaxWidth(Double.MAX_VALUE);
        }
        opciones.setPadding(new Insets(4, 8, 8, 4));
        ScrollPane scroll = new ScrollPane(opciones);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        btnFoliar = Ui.boton("Foliar y guardar", UniconsLine.FILE_EDIT_ALT, this::foliar);
        btnFoliar.getStyleClass().addAll("footer-button", "run-button");
        btnFoliar.setMaxWidth(Double.MAX_VALUE);
        btnFoliar.setDisable(true);
        Button manual = Ui.boton("Manual", UniconsLine.BOOK_OPEN, () -> ManualPanel.abrir(this, "foliar"));
        manual.setMaxWidth(Double.MAX_VALUE);
        VBox abajo = new VBox(6, btnFoliar, manual);
        abajo.setPadding(new Insets(8));
        VBox izq = new VBox(scroll, abajo);
        izq.setPrefWidth(420);
        izq.setMinWidth(340);
        izq.getStyleClass().addAll(Style.CONTAINER.css());
        setLeft(izq);

        // ---------- vista previa
        vista = new VistaPrevia("Arrastre aquí el PDF a foliar", "Verá el folio en la hoja antes de guardar.",
                List.of(Ui.boton("Elegir PDF", UniconsLine.FILE_ALT, this::elegir)), this::recibir);
        vista.lblAyuda.setText("Vista previa (todavía no se guarda nada). Puede arrastrar el folio con el ratón.");
        editor = new EditorHojas(this::generales, vista::indice, vista::total, letra::nombres, this::refrescar, true);
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
            public void clic(double fx, double fy) {
                arrastre(fx, fy, 0, 0);
            }

            @Override
            public void arrastre(double fx, double fy, double dx, double dy) {
                if (personalizando()) {
                    vista.marcaFlecha(dx, dy);
                } else {
                    float t = Controles.valor(tamano, 11);
                    vista.marcaRecuadro(fx, fy, t * 6, t * 1.9, Ui.fx(color.color.get()));
                }
            }

            @Override
            public void soltar(double fx, double fy, double dx, double dy) {
                if (personalizando()) {
                    // hoja personalizada: se mueve solo en las hojas que se están editando
                    editor.mover((float) dx, (float) dy);
                    return;
                }
                libreX = (float) fx;
                libreY = (float) fy;
                if (!libre.isSelected()) {
                    libre.setSelected(true);
                } else {
                    refrescar();
                }
            }
        });
        setCenter(vista);
        BorderPane.setMargin(vista, new Insets(0, 0, 0, 4));

        // cualquier cambio redibuja
        for (TextField t : List.of(prefijo, palabraDe, sufijo, textoLetras, hojas)) {
            t.textProperty().addListener((o, a, b) -> refrescar());
        }
        for (CheckBox c : List.of(conTotal, enLetras, conNumeros, recuadro)) {
            c.selectedProperty().addListener((o, a, b) -> refrescar());
        }
        posicion.selectedToggleProperty().addListener((o, a, b) -> refrescar());
        refrescar();
    }

    private boolean personalizando() {
        return editor.editando() && editor.grupo.contains(vista.indice());
    }

    private EditorHojas.Generales generales() {
        return new EditorHojas.Generales(color.color.get(), Controles.valor(tamano, 11), letra.nombre.get(),
                letra.negrita.isSelected(), recuadro.isSelected());
    }

    private void rotular() {
        if (!vista.hayPdf()) {
            return;
        }
        String marca = editor.reglas.containsKey(vista.indice()) ? "  ✎ personalizada" : "";
        vista.lblHoja.setText("Hoja " + (vista.indice() + 1) + " de " + vista.total() + marca);
    }

    // ------------------------------------------------------------ opciones
    OpcionesFolio opciones() {
        OpcionesFolio o = new OpcionesFolio();
        o.prefijo = prefijo.getText();
        o.inicio = Controles.valor(inicio, 1);
        o.digitos = Controles.valor(digitos, 3);
        o.tamano = Controles.valor(tamano, 11);
        o.margen = Controles.valor(margen, 24);
        o.saltar = Controles.valor(saltar, 0);
        o.conTotal = conTotal.isSelected();
        o.palabraDe = palabraDe.getText().isBlank() ? "de" : palabraDe.getText().trim();
        o.sufijo = sufijo.getText();
        o.enLetras = enLetras.isSelected();
        o.textoLetras = textoLetras.getText();
        o.conNumeros = conNumeros.isSelected();
        o.recuadro = recuadro.isSelected();
        o.editable = editable.isSelected();
        String pos = posicion.getSelectedToggle() == null ? "sup_der" :
                String.valueOf(posicion.getSelectedToggle().getUserData());
        o.libre = "libre".equals(pos);
        o.posicion = o.libre ? "sup_der" : pos;
        o.libreX = libreX;
        o.libreY = libreY;
        o.color = color.color.get();
        o.fuente = letra.ruta();
        o.negrita = letra.negritaEfectiva();
        return o;
    }

    private List<Integer> paginas() {
        String t = hojas.getText();
        return t == null || t.isBlank() ? null : Motor.listaPaginas(t, vista.total());
    }

    private void recordar(OpcionesFolio o) {
        Config.poner("foliar.prefijo", o.prefijo);
        Config.poner("foliar.inicio", o.inicio);
        Config.poner("foliar.digitos", o.digitos);
        Config.poner("foliar.tamano", o.tamano);
        Config.poner("foliar.margen", o.margen);
        Config.poner("foliar.conTotal", o.conTotal);
        Config.poner("foliar.palabraDe", o.palabraDe);
        Config.poner("foliar.sufijo", o.sufijo);
        Config.poner("foliar.enLetras", o.enLetras);
        Config.poner("foliar.textoLetras", o.textoLetras);
        Config.poner("foliar.conNumeros", o.conNumeros);
        Config.poner("foliar.recuadro", o.recuadro);
        Config.poner("foliar.editable", o.editable);
        Config.poner("foliar.posicion", o.libre ? "libre" : o.posicion);
        Config.poner("foliar.libreX", o.libreX);
        Config.poner("foliar.libreY", o.libreY);
        Config.poner("foliar.color", Motor.hexDeColor(o.color));
        Config.poner("foliar.fuente", o.fuente);
        Config.poner("foliar.negrita", letra.negrita.isSelected());
        Config.guardar();
    }

    // ------------------------------------------------------------ vista previa
    private void refrescar() {
        try {
            asiQueda.setText("Así queda: " + Motor.ejemploFolio(opciones()));
        } catch (RuntimeException e) {
            // a medio escribir
        }
        rotular();
        vista.refrescar();
    }

    /** Lo que se dibuja en la hoja que se está viendo (la misma función que al guardar). */
    private Motor.Dibujo dibujo() {
        if (!vista.hayPdf()) {
            return null;
        }
        OpcionesFolio o = opciones();
        List<Integer> paginas;
        try {
            paginas = paginas();
        } catch (IllegalArgumentException e) {
            paginas = null;
        }
        Map<Integer, ReglaHoja> porHoja = editor.porHoja();
        int i = vista.indice();
        List<Integer> elegidas = Motor.hojasFoliadas(vista.total(), o, paginas, porHoja);
        int pos = elegidas.indexOf(i);
        if (pos < 0) {
            return (doc, hoja, letras) -> {
            }; // esta hoja no lleva folio
        }
        long numero = o.inicio + pos, total = o.inicio + elegidas.size() - 1;
        ReglaHoja regla = porHoja.get(i);
        return (doc, hoja, letras) -> Motor.foliarHoja(doc, hoja, numero, total, o, regla, letras);
    }

    // ------------------------------------------------------------ archivo
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
            if (Ui.pregunta(this, "¿Foliar todos los PDF de \"" + primero.getName() + "\"?\n\n"
                    + "Se respetan las carpetas y subcarpetas, y el resultado se guarda aparte.", "Foliar una carpeta o comprimido")) {
                lote(primero);
            }
            return;
        }
        File pdf = rutas.stream().filter(f -> f.isFile() && f.getName().toLowerCase().endsWith(".pdf")).findFirst()
                .orElse(null);
        if (pdf == null) {
            Ui.aviso(this, "Eso no es un PDF. Elija un archivo .pdf, una carpeta o un comprimido (ZIP, RAR, 7z...).");
            return;
        }
        String c = null;
        if (Motor.pideClave(pdf)) {
            c = Ui.pedirClave(this, pdf.getName(), null);
            if (c == null) {
                return;
            }
        }
        try {
            Motor.Previa p = new Motor.Previa(pdf, c);
            archivo = pdf;
            clave = c;
            editor.olvidarTodo();
            editor.setVisible(true);
            editor.setManaged(true);
            vista.abrir(p);
            lblArchivo.setText(pdf.getName() + "  ·  " + p.hojas() + " hojas");
            btnFoliar.setDisable(false);
            refrescar();
        } catch (Exception e) {
            Ui.error(this, Ui.textoError(e));
        }
    }

    /** Solo para la prueba de pantallas: varias opciones marcadas y una hoja personalizándose. */
    void opcionesParaPrueba() {
        enLetras.setSelected(true);
        conTotal.setSelected(true);
        recuadro.setSelected(true);
        vista.irA(1);
        editor.editarActual();
    }

    // ------------------------------------------------------------ guardar
    private void foliar() {
        if (archivo == null) {
            return;
        }
        OpcionesFolio o = opciones();
        List<Integer> paginas;
        try {
            paginas = paginas();
        } catch (IllegalArgumentException e) {
            Ui.aviso(this, e.getMessage());
            return;
        }
        File salida = Ui.guardarComo(this, Ui.sugerir(archivo, "foliado"), "PDF", ".pdf", "Guardar el PDF foliado");
        if (salida == null) {
            return;
        }
        if (salida.getAbsoluteFile().equals(archivo.getAbsoluteFile())) {
            Ui.aviso(this, "Elija otro nombre: así el PDF original no se reemplaza.");
            return;
        }
        recordar(o);
        Map<Integer, ReglaHoja> porHoja = editor.porHoja();
        File entrada = archivo;
        String c = clave;
        Ui.enSegundoPlano(this, "Foliando", false, p -> {
            Motor.foliar(entrada, salida, c, o, paginas, porHoja,
                    (h, t) -> p.poner(t == 0 ? 1 : (double) h / t, "Hoja " + Math.min(h + 1, t) + " de " + t));
            return salida;
        }, r -> Ui.terminado(this, r, "PDF foliado", null), null);
    }

    private void lote(File origen) {
        OpcionesFolio o = opciones();
        Boolean continua = Ui.preguntaConCancelar(this,
                "¿Quiere que la numeración SIGA de un archivo al siguiente, como un solo expediente?\n\n"
                        + "Sí = el segundo PDF continúa donde terminó el primero (en orden de carpetas y nombres).\n"
                        + "No = cada PDF empieza otra vez en " + o.inicio + ".", "Numeración");
        if (continua == null) {
            return;
        }
        recordar(o);
        long[] siguiente = { o.inicio };
        Lote.Operacion op = (entrada, salida, c) -> {
            OpcionesFolio uno = o.copia();
            if (continua) {
                uno.inicio = siguiente[0];
            }
            int n;
            try (var d = Motor.abrir(entrada, c)) {
                n = d.getNumberOfPages();
                Motor.foliarDocumento(d, uno, null, null, null);
                Motor.guardar(d, salida);
            }
            siguiente[0] = uno.inicio + Math.max(0, n - uno.saltar);
        };
        LoteUi.procesar(this, origen, op, "foliado", "Foliar", () -> siguiente[0] = o.inicio);
    }
}
