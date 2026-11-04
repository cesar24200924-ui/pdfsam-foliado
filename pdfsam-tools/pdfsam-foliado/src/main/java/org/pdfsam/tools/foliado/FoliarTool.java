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

/** Herramienta "Foliar". */
public class FoliarTool implements Tool {

    static final String TOOL_ID = "foliar";

    private final ToolDescriptor descriptor = builder().category(ToolCategory.EDIT)
            .inputTypes(ToolInputOutputType.MULTIPLE_PDF, ToolInputOutputType.SINGLE_PDF).name("Foliar")
            .description("Pone el folio (Folio 001, 002…) en cada hoja, con vista previa, en la esquina que elija o arrastrándolo. También carpetas y comprimidos (ZIP, RAR, 7z...).")
            .priority(ToolPriority.HIGH.getPriority()).build();

    private FoliarPanel panel;

    @Override
    public ToolDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public synchronized Pane panel() {
        if (panel == null) {
            panel = new FoliarPanel();
        }
        return panel;
    }

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public Node graphic() {
        var icon = new FontIcon(UniconsLine.FILE_EDIT_ALT);
        icon.getStyleClass().addAll(this.descriptor().category().styleClass(), "tool-icon");
        icon.setAccessibleRole(AccessibleRole.IMAGE_VIEW);
        return icon;
    }
}
