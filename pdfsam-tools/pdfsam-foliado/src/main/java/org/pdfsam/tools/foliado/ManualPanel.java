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
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.unicons.UniconsLine;
import org.pdfsam.ui.components.support.Style;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El manual de Foliar, Indexar y Girar, dentro de PDFsam (con sus imágenes). Se lee del mismo archivo HTML que viene en
 * el portable, así siempre dicen lo mismo.
 */
class ManualPanel extends BorderPane {
    static final String CARPETA = "/org/pdfsam/tools/foliado/manual/";
    static final String ARCHIVO = "Manual-Foliar-Indexar-Girar.html";
    /** las imágenes del manual (para copiarlas al abrirlo en el navegador) */
    static final String[] IMAGENES = { "foliar.png", "foliar_personalizar.png", "indexar.png", "girar.png",
            "girar_eligiendo.png" };

    private final VBox contenido = new VBox(10);
    private final ScrollPane scroll = new ScrollPane(contenido);
    private final Map<String, Node> secciones = new HashMap<>();
    private double base = 14;

    ManualPanel() {
        getStyleClass().addAll(Style.CONTAINER.css());
        contenido.setPadding(new Insets(20, 30, 40, 30));
        contenido.setMaxWidth(980);
        contenido.setFillWidth(true);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(scroll);

        Button navegador = Ui.boton("Abrir en el navegador (para imprimir)", UniconsLine.EXTERNAL_LINK_ALT,
                this::abrirEnNavegador);
        Button inicio = Ui.boton("Ir al inicio", UniconsLine.ANGLE_UP, () -> scroll.setVvalue(0));
        Button foliar = Ui.boton("Foliar", () -> ir("foliar"));
        Button indexar = Ui.boton("Indexar", () -> ir("indexar"));
        Button girar = Ui.boton("Girar", () -> ir("girar"));
        Button faq = Ui.boton("Preguntas frecuentes", () -> ir("faq"));
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(6, inicio, foliar, indexar, girar, faq, espacio, navegador);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(8));
        barra.setStyle("-fx-border-color: transparent transparent rgba(127,127,127,0.35) transparent;");
        setTop(barra);
        try {
            cargar();
        } catch (Exception e) {
            contenido.getChildren().setAll(new Label("No se pudo cargar el manual: " + e.getMessage()));
        }
    }

    /** Abre el manual en una ventana aparte, en la parte que corresponde (foliar, indexar, girar…). */
    static void abrir(Node desde, String seccion) {
        Stage s = new Stage();
        Window w = Ui.ventana(desde);
        if (w != null) {
            s.initOwner(w);
        }
        s.setTitle("Manual — Foliar, Indexar y Girar");
        ManualPanel m = new ManualPanel();
        Scene sc = new Scene(m, 1050, 800);
        if (w != null && w.getScene() != null) {
            sc.getStylesheets().addAll(w.getScene().getStylesheets());
        }
        s.setScene(sc);
        s.show();
        if (seccion != null) {
            Platform.runLater(() -> Platform.runLater(() -> m.ir(seccion)));
        }
    }

    /** Lleva el manual hasta una parte (por su id en el HTML). */
    void ir(String id) {
        Node n = secciones.get(id);
        if (n == null) {
            return;
        }
        contenido.applyCss();
        contenido.layout();
        double alto = contenido.getHeight() - scroll.getViewportBounds().getHeight();
        if (alto > 0) {
            scroll.setVvalue(Math.min(1, Math.max(0, n.getBoundsInParent().getMinY() / alto)));
        }
    }

    // ------------------------------------------------------------ leer el HTML
    private void cargar() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setExpandEntityReferences(false);
        Document d;
        try (InputStream in = ManualPanel.class.getResourceAsStream(CARPETA + ARCHIVO)) {
            if (in == null) {
                throw new IllegalStateException("no está el archivo del manual");
            }
            d = f.newDocumentBuilder().parse(in);
        }
        Element body = (Element) d.getElementsByTagName("body").item(0);
        contenido.getChildren().setAll(bloques(body));
    }

    /** Los bloques (títulos, párrafos, listas, tablas…) de un elemento. */
    private List<Node> bloques(Element padre) {
        List<Node> r = new ArrayList<>();
        List<org.w3c.dom.Node> enLinea = new ArrayList<>();
        org.w3c.dom.NodeList hijos = padre.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            org.w3c.dom.Node h = hijos.item(i);
            if (h instanceof Element e && esBloque(e.getTagName())) {
                cerrarLinea(enLinea, r);
                Node n = bloque(e);
                if (n != null) {
                    String id = e.getAttribute("id");
                    if (!id.isEmpty()) {
                        secciones.put(id, n);
                    }
                    r.add(n);
                }
            } else {
                enLinea.add(h);
            }
        }
        cerrarLinea(enLinea, r);
        return r;
    }

    private void cerrarLinea(List<org.w3c.dom.Node> enLinea, List<Node> r) {
        boolean hayTexto = enLinea.stream().anyMatch(n -> !n.getTextContent().isBlank());
        if (hayTexto) {
            TextFlow t = new TextFlow();
            for (org.w3c.dom.Node n : enLinea) {
                enLinea(n, t, false, false, false);
            }
            limpiarBordes(t);
            r.add(t);
        }
        enLinea.clear();
    }

    private static boolean esBloque(String tag) {
        return switch (tag) {
        case "h1", "h2", "h3", "p", "ul", "ol", "table", "div", "details", "img", "figure" -> true;
        default -> false;
        };
    }

    private Node bloque(Element e) {
        switch (e.getTagName()) {
        case "h1", "h2", "h3" -> {
            double tam = switch (e.getTagName()) {
            case "h1" -> base * 2;
            case "h2" -> base * 1.55;
            default -> base * 1.2;
            };
            TextFlow t = flujo(e, tam, true);
            String color = "h1".equals(e.getTagName()) ? "#b00000" : "h2".equals(e.getTagName()) ? "#0d33a6" : "";
            for (Node n : t.getChildren()) {
                if (n instanceof Text x && !color.isEmpty()) {
                    x.setStyle("-fx-fill: " + color + ";");
                }
            }
            VBox.setMargin(t, new Insets("h3".equals(e.getTagName()) ? 10 : 22, 0, 2, 0));
            if (!"h3".equals(e.getTagName())) {
                VBox caja = new VBox(4, t, new Region());
                caja.setStyle("-fx-border-color: transparent transparent " + (color.isEmpty() ? "#ccd" : color)
                        + " transparent; -fx-border-width: 0 0 2 0; -fx-padding: 0 0 4 0;");
                VBox.setMargin(caja, new Insets(22, 0, 4, 0));
                return caja;
            }
            return t;
        }
        case "p" -> {
            return flujo(e, base, false);
        }
        case "ul", "ol" -> {
            VBox v = new VBox(4);
            int k = 1;
            org.w3c.dom.NodeList li = e.getChildNodes();
            for (int i = 0; i < li.getLength(); i++) {
                if (li.item(i) instanceof Element item && "li".equals(item.getTagName())) {
                    Label vineta = new Label("ol".equals(e.getTagName()) ? (k++) + "." : "•");
                    vineta.setMinWidth(22);
                    vineta.setStyle("-fx-font-size: " + base + "px;");
                    VBox dentro = new VBox(4);
                    dentro.getChildren().addAll(bloques(item));
                    HBox.setHgrow(dentro, Priority.ALWAYS);
                    HBox fila = new HBox(4, vineta, dentro);
                    v.getChildren().add(fila);
                }
            }
            v.setPadding(new Insets(0, 0, 0, 10));
            return v;
        }
        case "table" -> {
            return tabla(e);
        }
        case "div" -> {
            VBox v = new VBox(6);
            v.getChildren().addAll(bloques(e));
            String clase = e.getAttribute("class");
            if ("paso".equals(clase)) {
                v.setStyle("-fx-background-color: #f4f7fd; -fx-border-color: transparent transparent transparent "
                        + "#0d33a6; -fx-border-width: 0 0 0 4; -fx-padding: 8 14 8 14;");
            } else if ("ojo".equals(clase)) {
                v.setStyle("-fx-background-color: #fff8e6; -fx-border-color: transparent transparent transparent "
                        + "#e0a800; -fx-border-width: 0 0 0 4; -fx-padding: 8 14 8 14;");
            } else if ("indice".equals(clase)) {
                v.setStyle("-fx-background-color: rgba(127,127,127,0.08); -fx-padding: 10 14 10 14; "
                        + "-fx-background-radius: 6;");
            }
            return v;
        }
        case "details" -> {
            String titulo = "";
            List<Node> dentro = new ArrayList<>();
            org.w3c.dom.NodeList hijos = e.getChildNodes();
            Element sinResumen = (Element) e.cloneNode(true);
            for (int i = 0; i < hijos.getLength(); i++) {
                if (hijos.item(i) instanceof Element s && "summary".equals(s.getTagName())) {
                    titulo = s.getTextContent().trim();
                }
            }
            org.w3c.dom.NodeList copia = sinResumen.getElementsByTagName("summary");
            while (copia.getLength() > 0) {
                copia.item(0).getParentNode().removeChild(copia.item(0));
            }
            dentro.addAll(bloques(sinResumen));
            VBox v = new VBox(6);
            v.getChildren().addAll(dentro);
            v.setPadding(new Insets(6, 10, 8, 10));
            TitledPane tp = new TitledPane(titulo, v);
            tp.setExpanded(false);
            tp.setAnimated(false);
            tp.setMaxWidth(Double.MAX_VALUE);
            return tp;
        }
        case "img" -> {
            return imagen(e.getAttribute("src"), e.getAttribute("alt"));
        }
        case "figure" -> {
            VBox v = new VBox(6);
            v.getChildren().addAll(bloques(e));
            v.setAlignment(Pos.TOP_CENTER);
            return v;
        }
        default -> {
            return null;
        }
        }
    }

    private Node imagen(String src, String alt) {
        try (InputStream in = ManualPanel.class.getResourceAsStream(CARPETA + src)) {
            if (in == null) {
                return null;
            }
            Image img = new Image(in);
            ImageView iv = new ImageView(img);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setFitWidth(Math.min(img.getWidth(), 900));
            iv.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 8, 0, 0, 2);");
            VBox v = new VBox(4, iv);
            v.setAlignment(Pos.CENTER);
            v.setPadding(new Insets(6, 0, 6, 0));
            if (alt != null && !alt.isBlank()) {
                Label pie = new Label(alt);
                pie.setWrapText(true);
                pie.setOpacity(0.7);
                pie.setStyle("-fx-font-style: italic;");
                v.getChildren().add(pie);
            }
            return v;
        } catch (Exception ex) {
            return null;
        }
    }

    private Node tabla(Element e) {
        GridPane g = new GridPane();
        g.setStyle("-fx-border-color: #ccd; -fx-border-width: 1 0 0 1;");
        org.w3c.dom.NodeList filas = e.getElementsByTagName("tr");
        int columnas = 0;
        for (int f = 0; f < filas.getLength(); f++) {
            Element tr = (Element) filas.item(f);
            int c = 0;
            org.w3c.dom.NodeList celdas = tr.getChildNodes();
            for (int k = 0; k < celdas.getLength(); k++) {
                if (celdas.item(k) instanceof Element td && ("td".equals(td.getTagName())
                        || "th".equals(td.getTagName()))) {
                    boolean titulo = "th".equals(td.getTagName());
                    VBox celda = new VBox(4);
                    celda.getChildren().addAll(bloques(td));
                    if (titulo) {
                        for (Node n : celda.getChildren()) {
                            if (n instanceof TextFlow t) {
                                negrita(t);
                            }
                        }
                    }
                    celda.setPadding(new Insets(6, 8, 6, 8));
                    celda.setStyle("-fx-border-color: #ccd; -fx-border-width: 0 1 1 0;"
                            + (titulo ? " -fx-background-color: #eef1f8;" : ""));
                    celda.setMaxWidth(Double.MAX_VALUE);
                    celda.setMaxHeight(Double.MAX_VALUE);
                    g.add(celda, c++, f);
                }
            }
            columnas = Math.max(columnas, c);
        }
        for (int c = 0; c < columnas; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / columnas);
            cc.setFillWidth(true);
            g.getColumnConstraints().add(cc);
        }
        return g;
    }

    private static void negrita(TextFlow t) {
        for (Node n : t.getChildren()) {
            if (n instanceof Text x) {
                x.setFont(Font.font(x.getFont().getFamily(), FontWeight.BOLD, x.getFont().getSize()));
            }
        }
    }

    // ------------------------------------------------------------ texto en línea
    private TextFlow flujo(Element e, double tam, boolean negrita) {
        TextFlow t = new TextFlow();
        double antes = base;
        base = tam;
        org.w3c.dom.NodeList hijos = e.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            enLinea(hijos.item(i), t, negrita, false, false);
        }
        base = antes;
        limpiarBordes(t);
        return t;
    }

    private void enLinea(org.w3c.dom.Node n, TextFlow t, boolean negrita, boolean cursiva, boolean codigo) {
        if (n.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
            String s = n.getTextContent().replaceAll("\\s+", " ");
            if (!s.isEmpty()) {
                Text x = new Text(s);
                x.setFont(codigo ? Font.font("Consolas", base * 0.95) :
                        Font.font("Segoe UI", negrita ? FontWeight.BOLD : FontWeight.NORMAL,
                                cursiva ? FontPosture.ITALIC : FontPosture.REGULAR, base));
                if (codigo) {
                    x.setStyle("-fx-fill: #8a1f11;");
                } else {
                    x.getStyleClass().add("manual-texto");
                }
                t.getChildren().add(x);
            }
            return;
        }
        if (!(n instanceof Element e)) {
            return;
        }
        String tag = e.getTagName();
        if ("a".equals(tag)) {
            String href = e.getAttribute("href");
            Hyperlink h = new Hyperlink(e.getTextContent().trim());
            h.setStyle("-fx-font-size: " + base + "px; -fx-padding: 0; -fx-text-fill: #0d33a6; -fx-underline: true;");
            if (href.startsWith("#")) {
                h.setOnAction(x -> ir(href.substring(1)));
            }
            t.getChildren().add(h);
            return;
        }
        if ("br".equals(tag)) {
            t.getChildren().add(new Text("\n"));
            return;
        }
        boolean b = negrita || "b".equals(tag) || "strong".equals(tag);
        boolean c = cursiva || "i".equals(tag) || "em".equals(tag);
        boolean k = codigo || "code".equals(tag);
        org.w3c.dom.NodeList hijos = e.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            enLinea(hijos.item(i), t, b, c, k);
        }
    }

    /** Quita espacios sobrantes al principio y al final del párrafo. */
    private static void limpiarBordes(TextFlow t) {
        if (!t.getChildren().isEmpty() && t.getChildren().get(0) instanceof Text x) {
            x.setText(x.getText().replaceAll("^\\s+", ""));
        }
        int u = t.getChildren().size() - 1;
        if (u >= 0 && t.getChildren().get(u) instanceof Text x) {
            x.setText(x.getText().replaceAll("\\s+$", ""));
        }
    }

    // ------------------------------------------------------------ navegador
    /** Copia el manual y sus imágenes a una carpeta temporal y lo abre en el navegador. */
    private void abrirEnNavegador() {
        try {
            File dir = Files.createTempDirectory("manual_foliar_").toFile();
            File img = new File(dir, "img");
            img.mkdirs();
            try (InputStream in = ManualPanel.class.getResourceAsStream(CARPETA + ARCHIVO)) {
                Files.copy(in, new File(dir, ARCHIVO).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            for (String n : IMAGENES) {
                try (InputStream in = ManualPanel.class.getResourceAsStream(CARPETA + "img/" + n)) {
                    if (in != null) {
                        Files.copy(in, new File(img, n).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Ui.abrirArchivo(new File(dir, ARCHIVO));
        } catch (Exception e) {
            Ui.error(this, Ui.textoError(e));
        }
    }
}
