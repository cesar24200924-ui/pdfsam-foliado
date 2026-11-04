/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.unicons.UniconsLine;
import org.pdfsam.model.tool.Tool;
import org.pdfsam.model.tool.ToolCategory;
import org.pdfsam.model.tool.ToolDescriptor;
import org.pdfsam.model.tool.ToolInputOutputType;
import org.pdfsam.model.tool.ToolPriority;

import static org.pdfsam.model.tool.ToolDescriptorBuilder.builder;

/** Herramienta "Indexar PDF". */
public class IndexarTool implements Tool {

    static final String TOOL_ID = "indexar";

    private final ToolDescriptor descriptor = builder().category(ToolCategory.EDIT)
            .inputTypes(ToolInputOutputType.MULTIPLE_PDF, ToolInputOutputType.SINGLE_PDF).name("Indexar PDF")
            .description("Pone leyendas en cada hoja de muchos PDF a la vez")
            .priority(ToolPriority.HIGH.getPriority()).build();

    private IndexarPanel panel;

    @Override
    public ToolDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public synchronized Pane panel() {
        if (panel == null) {
            panel = new IndexarPanel();
        }
        return panel;
    }

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public Node graphic() {
        var icon = new FontIcon(UniconsLine. DPOT, parte del nombre del archivo con 1/10, pie de página y numeración.:TAG_ALT);
        icon.getStyleClass().addAll(this.descriptor().category().styleClass(), "tool-icon");
        icon.setAccessibleRole(AccessibleRole.IMAGE_VIEW);
        return icon;
    }
}
