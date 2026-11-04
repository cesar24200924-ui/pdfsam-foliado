/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.pdfsam.ui.components.support.Style;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pasos en pantalla para procesar una carpeta (con subcarpetas) o un ZIP:
 * <ol>
 * <li>Vista previa: árbol de carpetas y cuántos archivos se van a procesar.</li>
 * <li>Pregunta dónde guardar (y si es ZIP, si el resultado va como ZIP o como carpeta).</li>
 * <li>Barra de porcentaje con "Archivo 35 de 120" y botón Cancelar.</li>
 * <li>Al final, si hubo PDF protegidos o dañados: pregunta si les quita la seguridad, y después si los procesa.</li>
 * <li>Resumen final.</li>
 * </ol>
 */
final class LoteUi {
    private LoteUi() {
    }

    /** Una fila del árbol de la vista previa. */
    public static final class Fila {
        private final String nombre, procesar, otros;

        Fila(String nombre, int p, int o) {
            this.nombre = nombre;
            this.procesar = p == 0 ? "" : String.valueOf(p);
            this.otros = o == 0 ? "" : String.valueOf(o);
        }

        public String getNombre() {
            return nombre;
        }

        public String getProcesar() {
            return procesar;
        }

        public String getOtros() {
            return otros;
        }
    }

    /** operacion: lo que se hace con UN archivo. alEmpezar: se llama justo antes de procesar (para reiniciar
     * contadores). */
    static void procesar(Node padre, File origen, Lote.Operacion operacion, String sufijo, String titulo,
            Runnable alEmpezar) {
        Lote L;
        try {
            L = new Lote(origen);
        } catch (Exception e) {
            Ui.error(padre, Ui.textoError(e));
            return;
        }
        Ui.enSegundoPlano(padre, titulo, false, p -> {
            p.poner(-1, "Revisando carpetas y archivos…");
            return L.preparar();
        }, r -> vistaPrevia(padre, L, operacion, sufijo, titulo, alEmpezar), e -> {
            L.limpiar();
            Ui.error(padre, Ui.textoError(e));
        });
    }

    private static void vistaPrevia(Node padre, Lote L, Lote.Operacion op, String sufijo, String titulo,
            Runnable alEmpezar) {
        if (L.procesables.isEmpty()) {
            L.limpiar();
            Ui.aviso(padre, "No se encontraron archivos PDF para procesar en esa carpeta o ZIP.");
            return;
        }
        Stage v = new Stage();
        v.initModality(Modality.WINDOW_MODAL);
        Window w = Ui.ventana(padre);
        if (w != null) {
            v.initOwner(w);
        }
        v.setTitle("Vista previa");
        Label t = new Label(titulo);
        t.setStyle("-fx-font-size: 1.3em; -fx-font-weight: bold;");
        Map<String, int[]> arbol = L.arbol();
        int otros = L.archivos.size() - L.procesables.size();
        Label resumen = new Label(L.procesables.size() + " archivos se van a procesar · " + otros
                + " archivos se copian igual · " + arbol.size() + " carpetas");
        resumen.setWrapText(true);

        TreeTableView<Fila> tabla = new TreeTableView<>();
        TreeTableColumn<Fila, String> c1 = new TreeTableColumn<>("Carpeta");
        c1.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().getValue().getNombre()));
        c1.setPrefWidth(360);
        TreeTableColumn<Fila, String> c2 = new TreeTableColumn<>("A procesar");
        c2.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().getValue().getProcesar()));
        c2.setPrefWidth(95);
        c2.setStyle("-fx-alignment: CENTER;");
        TreeTableColumn<Fila, String> c3 = new TreeTableColumn<>("Otros");
        c3.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().getValue().getOtros()));
        c3.setPrefWidth(80);
        c3.setStyle("-fx-alignment: CENTER;");
        tabla.getColumns().addAll(List.of(c1, c2, c3));
        int[] r0 = arbol.getOrDefault(".", new int[2]);
        TreeItem<Fila> raiz = new TreeItem<>(
                new Fila(L.nombreOrigen() + (L.esZip ? ".zip" : ""), r0[0], r0[1]));
        raiz.setExpanded(true);
        Map<String, TreeItem<Fila>> nodos = new HashMap<>();
        nodos.put(".", raiz);
        for (Map.Entry<String, int[]> e : arbol.entrySet()) {
            String carpeta = e.getKey();
            if (".".equals(carpeta)) {
                continue;
            }
            agregar(nodos, carpeta, e.getValue());
        }
        tabla.setRoot(raiz);
        tabla.setPrefHeight(360);
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Button seguir = Ui.boton("Procesar", () -> {
            v.close();
            elegirDestino(padre, L, op, sufijo, titulo, alEmpezar);
        });
        seguir.getStyleClass().addAll("footer-button", "run-button");
        seguir.setDefaultButton(true);
        Button cancelar = Ui.boton("Cancelar", () -> {
            v.close();
            L.limpiar();
        });
        v.setOnCloseRequest(e -> L.limpiar());
        HBox botones = new HBox(8, cancelar, seguir);
        botones.setAlignment(Pos.CENTER_RIGHT);
        VBox caja = new VBox(10, t, resumen, tabla, botones);
        caja.setPadding(new Insets(18));
        caja.setPrefSize(640, 520);
        caja.getStyleClass().addAll(Style.CONTAINER.css());
        Scene sc = new Scene(caja);
        if (w != null && w.getScene() != null) {
            sc.getStylesheets().addAll(w.getScene().getStylesheets());
        }
        v.setScene(sc);
        v.show();
    }

    private static TreeItem<Fila> agregar(Map<String, TreeItem<Fila>> nodos, String carpeta, int[] v) {
        TreeItem<Fila> ya = nodos.get(carpeta);
        if (ya != null) {
            if (v != null) {
                ya.setValue(new Fila(ya.getValue().getNombre(), v[0], v[1]));
            }
            return ya;
        }
        String padre = new File(carpeta).getParent();
        TreeItem<Fila> nodoPadre = padre == null ? nodos.get(".") : agregar(nodos, padre, null);
        String nombre = new File(carpeta).getName();
        String icono = "";
        TreeItem<Fila> n = new TreeItem<>(new Fila(icono + nombre, v == null ? 0 : v[0], v == null ? 0 : v[1]));
        n.setExpanded(true);
        nodoPadre.getChildren().add(n);
        nodos.put(carpeta, n);
        return n;
    }

    private static void elegirDestino(Node padre, Lote L, Lote.Operacion op, String sufijo, String titulo,
            Runnable alEmpezar) {
        String nombre = L.nombreOrigen() + "_" + sufijo;
        String formato = "carpeta";
        if (L.esZip) {
            int r = Ui.elegir(padre, "¿Cómo quiere el resultado?",
                    "Las carpetas y subcarpetas quedan igual en los dos casos.", "Como ZIP", "Como carpeta");
            if (r < 0) {
                L.limpiar();
                return;
            }
            formato = r == 0 ? "zip" : "carpeta";
        }
        File destinoZip = null;
        Path trabajo;
        if ("zip".equals(formato)) {
            destinoZip = Ui.guardarComo(padre, new File(L.origen.getParentFile(), nombre + ".zip"), "ZIP", ".zip",
                    "Guardar el ZIP como");
            if (destinoZip == null) {
                L.limpiar();
                return;
            }
            trabajo = L.temp.resolve("resultado");
        } else {
            File carpeta = Ui.elegirCarpeta(padre, "¿Dónde guardo la carpeta con el resultado?",
                    L.origen.getParentFile());
            if (carpeta == null) {
                L.limpiar();
                return;
            }
            trabajo = Lote.nombreLibre(carpeta.toPath().resolve(nombre));
        }
        if (alEmpezar != null) {
            alEmpezar.run();
        }
        File zipFinal = destinoZip;
        int total = L.procesables.size();
        Ui.enSegundoPlano(padre, titulo, true, p -> {
            Thread vigia = new Thread(() -> {
                while (!L.cancelar.get()) {
                    if (p.cancelado()) {
                        L.cancelar.set(true);
                    }
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });
            vigia.setDaemon(true);
            vigia.start();
            try {
                L.ejecutar(op, trabajo, (h, tot, rel) -> p.poner(tot == 0 ? 1 : (double) h / tot,
                        rel.isEmpty() ? "" : "Archivo " + Math.min(h + 1, tot) + " de " + tot + ": "
                                + new File(rel).getName()));
            } finally {
                boolean cancelado = L.cancelar.get();
                vigia.interrupt();
                if (!cancelado) {
                    L.cancelar.set(false);
                }
            }
            return p.cancelado();
        }, cancelado -> despues(padre, L, op, zipFinal, cancelado, total), e -> {
            L.limpiar();
            Ui.error(padre, Ui.textoError(e));
        });
    }

    private static void despues(Node padre, Lote L, Lote.Operacion op, File destinoZip, boolean cancelado,
            int total) {
        List<Lote.Fallido> reparables = new ArrayList<>();
        for (Lote.Fallido f : L.fallidos) {
            if ("protegido".equals(f.motivo)) {
                reparables.add(f);
            }
        }
        if (!reparables.isEmpty() && !cancelado) {
            StringBuilder lista = new StringBuilder();
            for (int i = 0; i < Math.min(15, reparables.size()); i++) {
                lista.append("• ").append(reparables.get(i).visible()).append("  (protegido)\n");
            }
            if (reparables.size() > 15) {
                lista.append("… y ").append(reparables.size() - 15).append(" más\n");
            }
            boolean quitar = Ui.pregunta(padre, "Estos archivos tienen contraseña y no se pudieron procesar (se "
                    + "copiaron sin cambios):\n\n" + lista + "\n¿Quiere quitarles la seguridad con su contraseña?",
                    "Archivos protegidos");
            if (quitar) {
                boolean procesarTambien = Ui.pregunta(padre, "¿Quiere también procesarlos con esta herramienta?",
                        "Archivos protegidos");
                Map<Lote.Fallido, String> claves = new HashMap<>();
                for (Lote.Fallido f : reparables) {
                    String c = Ui.pedirClave(padre, f.visible(), "Si no la sabe, el archivo se queda como el original.");
                    if (c != null) {
                        claves.put(f, c);
                    }
                }
                Ui.enSegundoPlano(padre, "Quitando la seguridad", true, p -> {
                    int k = 0;
                    for (Lote.Fallido f : reparables) {
                        if (p.cancelado()) {
                            break;
                        }
                        p.poner((double) k++ / reparables.size(), f.visible());
                        String c = claves.get(f);
                        if (c == null) {
                            continue;
                        }
                        try {
                            L.reintentar(f, op, c, procesarTambien);
                        } catch (Exception e) {
                            f.mensaje = Ui.textoError(e);
                        }
                    }
                    return null;
                }, r -> fin(padre, L, destinoZip, cancelado, total), e -> fin(padre, L, destinoZip, cancelado, total));
                return;
            }
        }
        fin(padre, L, destinoZip, cancelado, total);
    }

    private static void fin(Node padre, Lote L, File destinoZip, boolean cancelado, int total) {
        Path resultado;
        try {
            resultado = L.finalizar(destinoZip);
        } catch (Exception e) {
            Ui.error(padre, Ui.textoError(e));
            return;
        }
        List<Lote.Fallido> sinResolver = new ArrayList<>();
        for (Lote.Fallido f : L.fallidos) {
            if (!f.resuelto) {
                sinResolver.add(f);
            }
        }
        int bien = L.hechos - sinResolver.size();
        StringBuilder d = new StringBuilder(bien + " de " + total + " archivos procesados.");
        if (cancelado) {
            d.append(" Se canceló: lo que ya se hizo se quedó guardado.");
        }
        if (!sinResolver.isEmpty()) {
            d.append("\n\nQuedaron sin procesar (están como el original):\n");
            for (int i = 0; i < Math.min(10, sinResolver.size()); i++) {
                Lote.Fallido f = sinResolver.get(i);
                d.append("• ").append(f.visible()).append(" — ").append(
                        "protegido".equals(f.motivo) ? "tiene contraseña" : "danado".equals(f.motivo) ? "dañado" : f.mensaje)
                        .append('\n');
            }
            if (sinResolver.size() > 10) {
                d.append("… y ").append(sinResolver.size() - 10).append(" más");
            }
        }
        Ui.terminado(padre, resultado.toFile(), cancelado ? "Proceso cancelado" : "Proceso terminado", d.toString());
    }
}
