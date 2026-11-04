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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * "Personalizar hojas" para Foliar e Indexar: lo general se aplica a todas las hojas, y aquí se cambian solo
 * algunas (la que se está viendo, o varias como 5, 8-12): no poner nada, moverlo (flechas o arrastrándolo en la
 * vista previa), color, tamaño, letra y negrita (y recuadro en Foliar). La vista previa muestra el cambio al
 * instante.
 */
class EditorHojas extends VBox {
    /** puntos que se mueve con cada clic de flecha (≈ 1 mm) */
    static final float PASO = 3;

    /** Lo general de la pantalla, para empezar a personalizar desde ahí. */
    record Generales(Color color, float tamano, String fuente, boolean negrita, boolean recuadro) {
    }

    private final Supplier<Generales> generales;
    private final IntSupplier hojaActual, totalHojas;
    private final Supplier<List<String>> letras;
    private final Runnable alCambiar;

    Map<Integer, ReglaHoja> reglas = new TreeMap<>();
    private final Map<String, Map<Integer, ReglaHoja>> porDoc = new HashMap<>();
    final List<Integer> grupo = new ArrayList<>();
    private boolean cargando;

    private final Label lblLista = new Label();
    private final Button btnQuitarTodo;
    private final VBox panel = new VBox(6);
    private final Label lblEditando = new Label();
    private final CheckBox omitir = new CheckBox("No poner nada en estas hojas");
    private final ColorPicker color = new ColorPicker();
    private final Spinner<Integer> tamano = new Spinner<>(6, 60, 11);
    private final ComboBox<String> fuente = new ComboBox<>();
    private final CheckBox negrita = new CheckBox("Negrita");
    private final CheckBox recuadro = new CheckBox("Con recuadro");

    EditorHojas(Supplier<Generales> generales, IntSupplier hojaActual, IntSupplier totalHojas,
            Supplier<List<String>> letras, Runnable alCambiar, boolean conRecuadro) {
        this.generales = generales;
        this.hojaActual = hojaActual;
        this.totalHojas = totalHojas;
        this.letras = letras;
        this.alCambiar = alCambiar;

        Button esta = Ui.boton("Personalizar esta hoja", UniconsLine.PEN, this::editarActual);
        Button varias = Ui.boton("Personalizar varias…", UniconsLine.LIST_UL, this::editarVarias);
        btnQuitarTodo = Ui.boton("Quitar todas las personalizaciones", UniconsLine.TIMES, this::quitarTodo);
        FlowPane barra = new FlowPane(8, 6, esta, varias, lblLista, btnQuitarTodo);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(6, 8, 6, 8));
        barra.getStyleClass().add("foliado-pastel-azul");

        // panel de cambios (solo visible mientras se edita)
        lblEditando.setStyle("-fx-font-weight: bold;");
        Button listo = Ui.boton("Listo", UniconsLine.CHECK, () -> terminar(true));
        Button todas = Ui.boton("Aplicar a todas las hojas de este PDF", UniconsLine.COPY_ALT, this::aplicarATodas);
        Button quitar = Ui.boton("Quitar personalización", UniconsLine.REDO, this::quitarGrupo);
        FlowPane p1 = new FlowPane(8, 6, lblEditando, listo, todas, quitar);
        p1.setAlignment(Pos.CENTER_LEFT);

        omitir.setOnAction(e -> cambio());
        Label mover = new Label("Mover:");
        HBox fm = new HBox(3, mover, Ui.boton("", UniconsLine.ANGLE_UP, () -> mover(0f, -PASO)), Ui.boton("", UniconsLine.ANGLE_DOWN, () -> mover(0f, PASO)),
                Ui.boton("", UniconsLine.ANGLE_LEFT, () -> mover(-PASO, 0f)), Ui.boton("", UniconsLine.ANGLE_RIGHT, () -> mover(PASO, 0f)),
                Ui.boton("A su lugar", () -> mover(null, null)));
        fm.setAlignment(Pos.CENTER_LEFT);
        color.setOnAction(e -> cambio());
        color.getCustomColors().addAll(javafx.scene.paint.Color.web("#ff0000"), javafx.scene.paint.Color.BLACK,
                javafx.scene.paint.Color.web("#0d33a6"));
        tamano.setEditable(true);
        tamano.setPrefWidth(80);
        tamano.valueProperty().addListener((o, a, b) -> cambio());
        fuente.setPrefWidth(200);
        fuente.setOnAction(e -> cambio());
        negrita.setOnAction(e -> cambio());
        recuadro.setOnAction(e -> cambio());
        HBox fc = new HBox(4, new Label("Color"), color);
        fc.setAlignment(Pos.CENTER_LEFT);
        HBox ft = new HBox(4, new Label("Tamaño"), tamano);
        ft.setAlignment(Pos.CENTER_LEFT);
        HBox fl = new HBox(4, new Label("Letra"), fuente, negrita);
        fl.setAlignment(Pos.CENTER_LEFT);
        FlowPane p2 = new FlowPane(14, 6, omitir, fm, fc, ft, fl);
        if (conRecuadro) {
            p2.getChildren().add(recuadro);
        }
        p2.setAlignment(Pos.CENTER_LEFT);
        Label nota = new Label("También puede arrastrarlo con el ratón en la vista previa.");
        nota.setOpacity(0.75);
        panel.getChildren().addAll(p1, p2, nota);
        panel.setPadding(new Insets(8));
        panel.getStyleClass().add("foliado-pastel-amarillo");
        panel.setVisible(false);
        panel.setManaged(false);

        getChildren().addAll(barra, panel);
        mostrarLista();
    }

    // ---------------------------------------------------------------- documentos (Indexar)
    /** Cada PDF tiene sus propias hojas personalizadas. */
    void cambiarDocumento(String clave) {
        terminar(false);
        reglas = porDoc.computeIfAbsent(clave, k -> new TreeMap<>());
        mostrarLista();
    }

    Map<Integer, ReglaHoja> reglasDe(String clave) {
        return porDoc.getOrDefault(clave, Map.of());
    }

    void olvidarTodo() {
        porDoc.clear();
        reglas = new TreeMap<>();
        terminar(false);
    }

    // ---------------------------------------------------------------- editar
    boolean editando() {
        return !grupo.isEmpty();
    }

    void editarActual() {
        empezar(List.of(hojaActual().getAsInt()));
    }

    private IntSupplier hojaActual() {
        return hojaActual;
    }

    void editarVarias() {
        String texto = Ui.pedirTexto(this, "¿Qué hojas quiere personalizar? Ej.: 5, 8-12", "Personalizar varias…",
                grupo.isEmpty() ? "" : Motor.textoHojas(grupo));
        if (texto == null) {
            return;
        }
        try {
            List<Integer> hojas = Motor.listaPaginas(texto, totalHojas.getAsInt());
            if (!hojas.isEmpty()) {
                empezar(hojas);
            }
        } catch (IllegalArgumentException e) {
            Ui.aviso(this, e.getMessage());
        }
    }

    private void empezar(List<Integer> hojas) {
        Generales g = generales.get();
        ReglaHoja base = new ReglaHoja();
        base.color = g.color();
        base.tamano = g.tamano();
        base.fuente = g.fuente();
        base.negrita = g.negrita();
        base.recuadro = g.recuadro();
        ReglaHoja modelo = reglas.get(hojas.get(0));
        if (modelo != null) {
            base = modelo.copia();
            if (base.color == null) {
                base.color = g.color();
            }
            if (base.tamano == null) {
                base.tamano = g.tamano();
            }
            if (base.fuente == null) {
                base.fuente = g.fuente();
            }
            if (base.negrita == null) {
                base.negrita = g.negrita();
            }
            if (base.recuadro == null) {
                base.recuadro = g.recuadro();
            }
        }
        for (Integer i : hojas) {
            if (!reglas.containsKey(i)) {
                reglas.put(i, base.copia());
            }
        }
        grupo.clear();
        grupo.addAll(hojas);
        cargando = true;
        color.setValue(Ui.fx(base.color));
        tamano.getValueFactory().setValue(Math.round(base.tamano));
        List<String> nombres = letras.get();
        fuente.getItems().setAll(nombres);
        fuente.setValue(nombres.contains(base.fuente) ? base.fuente : "Helvetica");
        negrita.setSelected(Boolean.TRUE.equals(base.negrita));
        recuadro.setSelected(Boolean.TRUE.equals(base.recuadro));
        omitir.setSelected(base.omitir);
        cargando = false;
        lblEditando.setText("Editando hojas: " + Motor.textoHojas(grupo));
        panel.setVisible(true);
        panel.setManaged(true);
        mostrarLista();
        alCambiar.run();
    }

    void terminar(boolean refrescar) {
        grupo.clear();
        panel.setVisible(false);
        panel.setManaged(false);
        mostrarLista();
        if (refrescar) {
            alCambiar.run();
        }
    }

    /** Lo que tiene esta personalización (movido, color, tamaño, letra) pasa a TODAS las hojas del PDF. */
    private void aplicarATodas() {
        if (grupo.isEmpty()) {
            return;
        }
        ReglaHoja modelo = reglas.get(grupo.get(0));
        int total = totalHojas.getAsInt();
        if (modelo == null || !Ui.pregunta(this, "¿Poner estos mismos cambios en las " + total
                + " hojas de este PDF?", "Personalizar hojas")) {
            return;
        }
        grupo.clear();
        for (int i = 0; i < total; i++) {
            reglas.put(i, modelo.copia());
            grupo.add(i);
        }
        lblEditando.setText("Editando hojas: " + Motor.textoHojas(grupo));
        mostrarLista();
        alCambiar.run();
    }

    private void quitarGrupo() {
        for (Integer i : grupo) {
            reglas.remove(i);
        }
        terminar(true);
    }

    private void quitarTodo() {
        if (!reglas.isEmpty() && Ui.pregunta(this, "¿Quitar todas las hojas personalizadas de este PDF?",
                "Personalizar hojas")) {
            reglas.clear();
            terminar(true);
        }
    }

    // ---------------------------------------------------------------- cambios
    private void cambio() {
        if (cargando || grupo.isEmpty()) {
            return;
        }
        Integer tam = tamano.getValue();
        for (Integer i : grupo) {
            ReglaHoja r = reglas.computeIfAbsent(i, k -> new ReglaHoja());
            r.color = Ui.awt(color.getValue());
            r.tamano = tam == null ? null : tam.floatValue();
            r.fuente = fuente.getValue() == null ? "Helvetica" : fuente.getValue();
            r.negrita = negrita.isSelected();
            r.recuadro = recuadro.isSelected();
            r.omitir = omitir.isSelected();
        }
        mostrarLista();
        alCambiar.run();
    }

    /** Mueve (en puntos) lo de las hojas que se editan. null, null = a su lugar. */
    void mover(Float dx, Float dy) {
        if (grupo.isEmpty()) {
            editarActual();
        }
        for (Integer i : grupo) {
            ReglaHoja r = reglas.computeIfAbsent(i, k -> new ReglaHoja());
            if (dx == null) {
                r.dx = 0;
                r.dy = 0;
            } else {
                r.dx += dx;
                r.dy += dy;
            }
        }
        alCambiar.run();
    }

    private void mostrarLista() {
        if (!reglas.isEmpty()) {
            lblLista.setText("Hojas personalizadas: " + Motor.textoHojas(reglas.keySet()));
            btnQuitarTodo.setDisable(false);
        } else {
            lblLista.setText("Todas las hojas usan las opciones generales.");
            btnQuitarTodo.setDisable(true);
        }
    }

    /**
     * La letra general cambió: pasa a TODAS las hojas personalizadas (de este PDF y de los otros de la lista).
     * nombre o negrita en null = no cambia eso.
     */
    void cambiarLetraEnTodas(String nombre, Boolean conNegrita) {
        List<Map<Integer, ReglaHoja>> todas = new ArrayList<>(porDoc.values());
        if (!porDoc.containsValue(reglas)) {
            todas.add(reglas);
        }
        for (Map<Integer, ReglaHoja> m : todas) {
            for (ReglaHoja r : m.values()) {
                if (nombre != null) {
                    r.fuente = nombre;
                }
                if (conNegrita != null) {
                    r.negrita = conNegrita;
                }
            }
        }
        if (!grupo.isEmpty()) {
            cargando = true;
            if (nombre != null) {
                if (!fuente.getItems().contains(nombre)) {
                    fuente.getItems().add(nombre);
                }
                fuente.setValue(nombre);
            }
            if (conNegrita != null) {
                negrita.setSelected(conNegrita);
            }
            cargando = false;
        }
        alCambiar.run();
    }

    // ---------------------------------------------------------------- para el motor
    /** Las reglas listas para el motor (la letra pasa de nombre a archivo). */
    Map<Integer, ReglaHoja> porHoja() {
        return porHoja(reglas);
    }

    static Map<Integer, ReglaHoja> porHoja(Map<Integer, ReglaHoja> reglas) {
        Map<Integer, ReglaHoja> salida = new HashMap<>();
        for (Map.Entry<Integer, ReglaHoja> e : reglas.entrySet()) {
            ReglaHoja r = e.getValue().copia();
            if (r.fuente != null) {
                r.fuente = LetrasInstaladas.rutaDe(r.fuente);
            }
            salida.put(e.getKey(), r);
        }
        return salida;
    }
}
