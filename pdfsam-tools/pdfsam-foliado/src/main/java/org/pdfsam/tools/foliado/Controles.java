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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Piezas que usan las dos pantallas: números, color y letra. */
final class Controles {
    private Controles() {
    }

    /** Un número entero con flechas, que acepta lo que se escribe. */
    static Spinner<Integer> numero(int desde, int hasta, int valor, Runnable alCambiar) {
        Spinner<Integer> s = new Spinner<>();
        SpinnerValueFactory.IntegerSpinnerValueFactory f = new SpinnerValueFactory.IntegerSpinnerValueFactory(desde,
                hasta, Math.max(desde, Math.min(hasta, valor)));
        s.setValueFactory(f);
        s.setEditable(true);
        s.setPrefWidth(95);
        s.setMaxWidth(Double.MAX_VALUE);
        // solo números
        s.getEditor().setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("-?\\d*") ? c : null));
        s.valueProperty().addListener((o, a, b) -> alCambiar.run());
        // lo escrito cuenta aunque no se presione Enter
        s.getEditor().textProperty().addListener((o, a, b) -> {
            try {
                int v = Integer.parseInt(b);
                if (v >= desde && v <= hasta && (s.getValue() == null || v != s.getValue())) {
                    f.setValue(v);
                }
            } catch (NumberFormatException e) {
                // a medio escribir
            }
        });
        return s;
    }

    static int valor(Spinner<Integer> s, int normal) {
        Integer v = s.getValue();
        return v == null ? normal : v;
    }

    static Label nota(String texto) {
        Label l = new Label(texto);
        l.setWrapText(true);
        l.setOpacity(0.72);
        l.setStyle("-fx-font-size: 0.92em;");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    static Label titulo(String texto) {
        Label l = new Label(texto);
        l.setStyle("-fx-font-weight: bold;");
        l.setWrapText(true);
        return l;
    }

    static VBox bloque(javafx.scene.Node... hijos) {
        VBox v = new VBox(5, hijos);
        v.setFillWidth(true);
        return v;
    }

    /** Arriba la etiqueta, abajo el control. */
    static VBox conEtiqueta(String etiqueta, javafx.scene.Node control) {
        Label l = new Label(etiqueta);
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 0.92em;");
        return new VBox(2, l, control);
    }

    // ------------------------------------------------------------ color
    /** Botones rápidos (Rojo, Negro, Azul), "Más colores…" y una muestra con el código. */
    static final class SelectorColor extends VBox {
        final ObjectProperty<Color> color;
        private final Label muestra = new Label();

        SelectorColor(Color inicial, String[][] rapidos, Runnable alCambiar) {
            super(6);
            color = new SimpleObjectProperty<>(inicial);
            FlowPane fila = new FlowPane(4, 4);
            fila.setAlignment(Pos.CENTER_LEFT);
            for (String[] r : rapidos) {
                Button b = new Button(r[0]);
                b.setStyle("-fx-background-color: " + r[1] + "; -fx-text-fill: white; -fx-background-radius: 3; "
                        + "-fx-cursor: hand;");
                b.setOnAction(e -> color.set(Motor.colorDeHex(r[1])));
                fila.getChildren().add(b);
            }
            ColorPicker mas = new ColorPicker(Ui.fx(inicial));
            mas.setPromptText("Más colores…");
            mas.setPrefWidth(140);
            mas.setOnAction(e -> color.set(Ui.awt(mas.getValue())));
            Label lm = new Label("Más colores:");
            HBox f2 = new HBox(6, lm, mas);
            f2.setAlignment(Pos.CENTER_LEFT);
            getChildren().addAll(fila, f2, muestra);
            color.addListener((o, a, b) -> {
                mas.setValue(Ui.fx(b));
                mostrar();
                alCambiar.run();
            });
            mostrar();
        }

        private void mostrar() {
            Color c = color.get();
            boolean oscuro = c.getRed() + c.getGreen() + c.getBlue() < 1.8 * 255;
            muestra.setText("  Color elegido: " + Motor.hexDeColor(c).toUpperCase() + "  ");
            muestra.setStyle("-fx-background-color: " + Motor.hexDeColor(c) + "; -fx-text-fill: "
                    + (oscuro ? "white" : "black") + "; -fx-padding: 3 6 3 6; -fx-background-radius: 3;");
        }
    }

    // ------------------------------------------------------------ letra
    /**
     * Buscador de letras: las 3 básicas (★, siempre disponibles) y las de esta computadora. La negrita solo aplica a
     * las básicas; las de la computadora ya traen su versión "Negrita".
     */
    static final class SelectorLetra extends VBox {
        final StringProperty nombre = new SimpleStringProperty("Helvetica");
        final CheckBox negrita = new CheckBox("Negrita");
        private final TextField buscar = new TextField();
        private final ListView<String> lista = new ListView<>();
        private final Label estado = nota("Buscando las letras de esta computadora…");
        private Map<String, String> letras = Map.of();
        private boolean llenando;
        private String guardada;

        SelectorLetra(String inicialNombreORuta, boolean negritaInicial, Runnable alCambiar) {
            super(5);
            guardada = inicialNombreORuta;
            if (Motor.INTEGRADAS.contains(inicialNombreORuta)) {
                nombre.set(inicialNombreORuta);
            }
            negrita.setSelected(negritaInicial);
            buscar.setPromptText("Escriba para buscar (ej. arial negrita)");
            lista.setPrefHeight(150);
            lista.setCellFactory(v -> new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item + (Motor.INTEGRADAS.contains(item) ? "  ★" : ""));
                }
            });
            org.kordamp.ikonli.javafx.FontIcon lupa = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.unicons.UniconsLine.SEARCH);
            HBox filaBuscar = new HBox(6, lupa, buscar);
            filaBuscar.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(buscar, javafx.scene.layout.Priority.ALWAYS);
            getChildren().addAll(filaBuscar, lista, estado, negrita);
            buscar.textProperty().addListener((o, a, b) -> llenar());
            lista.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
                if (!llenando && b != null) {
                    nombre.set(b);
                }
            });
            nombre.addListener((o, a, b) -> {
                negrita.setDisable(!Motor.INTEGRADAS.contains(b));
                alCambiar.run();
            });
            negrita.setOnAction(e -> alCambiar.run());
            negrita.setDisable(!Motor.INTEGRADAS.contains(nombre.get()));
            llenar();
            Thread t = new Thread(() -> {
                Map<String, String> todas;
                try {
                    todas = LetrasInstaladas.todas();
                } catch (RuntimeException e) {
                    todas = Map.of();
                }
                Map<String, String> r = todas;
                Platform.runLater(() -> listas(r));
            }, "foliado-letras");
            t.setDaemon(true);
            t.start();
        }

        private void listas(Map<String, String> l) {
            letras = l;
            estado.setText(l.isEmpty() ?
                    "Solo están las letras básicas (no se encontraron otras)." :
                    l.size() + " letras de esta computadora. Escriba arriba para buscar.");
            // si la última vez eligió una letra de la computadora, se vuelve a marcar
            if (guardada != null && !Motor.INTEGRADAS.contains(guardada)) {
                String n = LetrasInstaladas.nombreDeRuta(guardada);
                if (n == null && l.containsKey(guardada)) {
                    n = guardada;
                }
                if (n != null) {
                    nombre.set(n);
                }
            }
            llenar();
        }

        private void llenar() {
            String[] palabras = buscar.getText() == null ? new String[0] :
                    buscar.getText().toLowerCase().trim().split("\\s+");
            List<String> nombres = new ArrayList<>();
            for (String n : Motor.INTEGRADAS) {
                if (coincide(n, palabras)) {
                    nombres.add(n);
                }
            }
            for (String n : letras.keySet()) {
                if (nombres.size() >= 400) {
                    break;
                }
                if (coincide(n, palabras)) {
                    nombres.add(n);
                }
            }
            llenando = true;
            lista.getItems().setAll(nombres);
            if (nombres.contains(nombre.get())) {
                lista.getSelectionModel().select(nombre.get());
                lista.scrollTo(nombre.get());
            }
            llenando = false;
        }

        private static boolean coincide(String n, String[] palabras) {
            String t = n.toLowerCase();
            for (String p : palabras) {
                if (!p.isEmpty() && !t.contains(p)) {
                    return false;
                }
            }
            return true;
        }

        /** Todos los nombres (para "Personalizar hojas"). */
        List<String> nombres() {
            List<String> r = new ArrayList<>(Motor.INTEGRADAS);
            r.addAll(letras.keySet());
            return r;
        }

        /** Lo que entiende el motor: "Helvetica"… o la ruta del archivo. */
        String ruta() {
            return LetrasInstaladas.rutaDe(nombre.get());
        }

        boolean negritaEfectiva() {
            return negrita.isSelected() && Motor.INTEGRADAS.contains(nombre.get());
        }
    }
}
