package org.pdfsam.gui;
/*
 * This file is part of the PDF Split And Merge source code
 * Created on 09/01/23
 * Copyright 2023 by Sober Lemur S.r.l. (info@soberlemur.com).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import jakarta.inject.Inject;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.unicons.UniconsLine;
import org.pdfsam.eventstudio.annotation.EventListener;
import org.pdfsam.gui.components.content.home.HomeContentItem;
import org.pdfsam.model.ui.SetActiveContentItemRequest;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import org.pdfsam.gui.components.sidebar.VerticalSidebar;
import org.pdfsam.ui.components.support.Style;

import static org.pdfsam.eventstudio.StaticStudio.eventStudio;
import static org.pdfsam.i18n.I18nContext.i18n;

/**
 * @author Andrea Vacondio
 */
public class AppContainer extends BorderPane {
    private final ScrollPane center = new ScrollPane();
    /** barrita de arriba con el botón para regresar al Inicio (en todas las pantallas menos en el Inicio) */
    private final HBox barraInicio = new HBox();

    @Inject
    AppContainer(VerticalSidebar sidebar) {
        setId("app-container");
        center.getStyleClass().addAll(Style.CONTAINER.css());
        center.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        center.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        center.setFitToWidth(true);
        center.setFitToHeight(true);
        setCenter(center);
        setLeft(sidebar);

        Button inicio = new Button(i18n().tr("Home"));
        FontIcon icono = new FontIcon(UniconsLine.ARROW_LEFT);
        icono.getStyleClass().add("inicio-icono");
        inicio.setGraphic(icono);
        inicio.getStyleClass().addAll(Style.BUTTON.css());
        inicio.setOnAction(e -> eventStudio().broadcast(new SetActiveContentItemRequest(HomeContentItem.ID)));
        barraInicio.getChildren().add(inicio);
        barraInicio.setAlignment(Pos.CENTER_LEFT);
        barraInicio.setPadding(new Insets(6, 10, 6, 10));
        barraInicio.getStyleClass().add("barra-inicio");
        setTop(barraInicio);
        mostrarInicio(false);
        eventStudio().addAnnotatedListeners(this);
    }

    private void mostrarInicio(boolean si) {
        barraInicio.setVisible(si);
        barraInicio.setManaged(si);
    }

    @EventListener
    public void onSetActiveContentItem(SetActiveContentItemRequest request) {
        mostrarInicio(!HomeContentItem.ID.equals(request.id()));
    }

    public void setContent(Pane panel) {
        if (!panel.getStyleClass().contains("content-pane")) {
            panel.getStyleClass().add("content-pane");
        }
        center.setContent(panel);
    }

    public Node getContent() {
        return center.getContent();
    }
}
