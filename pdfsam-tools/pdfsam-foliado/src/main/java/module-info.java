/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
import org.pdfsam.model.tool.Tool;
import org.pdfsam.tools.foliado.FoliarTool;
import org.pdfsam.tools.foliado.IndexarTool;
import org.pdfsam.tools.foliado.GirarTool;
import org.pdfsam.tools.foliado.ManualTool;

module org.pdfsam.tools.foliado {
    exports org.pdfsam.tools.foliado;
    // para que el enlace Girar/Indexar/Foliar (@EventListener en GirarPanel, IndexarPanel y FoliarPanel,
    // clases package-private) pueda registrarse y recibir avisos en el programa ya armado como módulo; sin
    // esto, las pruebas (que corren sin el sistema de módulos) no lo notan, pero las tres pantallas se quedan
    // en blanco al abrirlas en el programa real.
    opens org.pdfsam.tools.foliado to org.pdfsam.eventstudio;

    requires org.pdfsam.ui.components;
    requires transitive org.pdfsam.model;
    requires javafx.graphics;
    requires javafx.controls;
    requires java.desktop;
    requires java.xml;
    requires org.sejda.sambox;
    requires org.sejda.io;
    requires org.apache.fontbox;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.unicons;
    // para abrir RAR, 7z, TAR, GZ... (motor de 7-Zip; LGPL)
    requires sevenzipjbinding;
    requires sevenzipjbinding.all.platforms;

    provides Tool with FoliarTool, IndexarTool, GirarTool, ManualTool;
}
