/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveOpenCallback;
import net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback;
import net.sf.sevenzipjbinding.ICryptoGetTextPassword;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Abrir archivos comprimidos de cualquier tipo conocido: ZIP, RAR (también RAR5 y en partes), 7z (también en partes
 * .001), TAR, GZ, BZ2, XZ, LZMA, Z, CAB, ISO, ARJ, LZH, CPIO, RPM, DEB, WIM, XAR... Se usa el motor de 7-Zip
 * (7-Zip-JBinding, licencia LGPL con la restricción de unRAR: solo abre, nunca crea RAR).
 * <p>
 * Crear RAR no se puede con programas libres, así que el resultado siempre se arma como ZIP.
 */
final class Comprimidos {
    private Comprimidos() {
    }

    /** Lo que pregunta la contraseña. Devuelve null si el usuario no la sabe (se salta el archivo). */
    @FunctionalInterface
    interface PideClave {
        String pedir(String archivo, boolean incorrecta);
    }

    /** El comprimido tiene contraseña y no se dio (o se canceló). */
    static final class SinClave extends IOException {
        SinClave(String archivo) {
            super("\"" + archivo + "\" tiene contraseña y no se abrió.");
        }
    }

    /** Extensiones que se abren (la primera parte, si viene en partes). */
    static final List<String> EXTENSIONES = List.of("zip", "zipx", "rar", "7z", "tar", "gz", "gzip", "tgz", "taz",
            "bz2", "bzip2", "tbz", "tbz2", "xz", "txz", "lzma", "tlz", "lz", "z", "cab", "iso", "arj", "lzh", "lha",
            "cpio", "rpm", "deb", "wim", "swm", "xar", "udf", "dmg", "vhd", "001");

    /** Los que traen un solo archivo adentro (como .tar.gz): si adentro hay otro comprimido, se abre directo. */
    private static final Set<String> DE_UNO = Set.of("gz", "gzip", "tgz", "taz", "bz2", "bzip2", "tbz", "tbz2", "xz",
            "txz", "lzma", "tlz", "lz", "z", "001");

    private static final Pattern PARTE_RAR = Pattern.compile("(?i).*\\.part0*(\\d+)\\.rar$");
    private static final Pattern PARTE_VIEJA = Pattern.compile("(?i).*\\.r\\d\\d$");
    private static final Pattern PARTE_NUMERO = Pattern.compile("(?i).*\\.(\\d{3})$");

    private static volatile Boolean motorListo;
    private static volatile String motorError;

    static String extension(String nombre) {
        String n = nombre.toLowerCase(Locale.ROOT);
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1);
    }

    /** ¿Es un comprimido que se abre? (Las partes 2, 3... de un RAR o 7z no: se abren con la primera.) */
    static boolean es(String nombre) {
        if (nombre == null) {
            return false;
        }
        if (esParteSecundaria(nombre)) {
            return false;
        }
        return EXTENSIONES.contains(extension(nombre));
    }

    /** x.part2.rar, x.r00, x.002... */
    static boolean esParteSecundaria(String nombre) {
        var m = PARTE_RAR.matcher(nombre);
        if (m.matches()) {
            return Integer.parseInt(m.group(1)) > 1;
        }
        if (PARTE_VIEJA.matcher(nombre).matches()) {
            return true;
        }
        var n = PARTE_NUMERO.matcher(nombre);
        return n.matches() && Integer.parseInt(n.group(1)) > 1;
    }

    /** "Expediente.rar" -> "Expediente"; "x.tar.gz" -> "x"; "x.part1.rar" -> "x"; "x.7z.001" -> "x". */
    static String base(String nombre) {
        String n = nombre;
        n = n.replaceAll("(?i)\\.\\d{3}$", "");
        n = n.replaceAll("(?i)\\.part0*\\d+\\.rar$", "");
        int i = n.lastIndexOf('.');
        if (i > 0 && EXTENSIONES.contains(n.substring(i + 1).toLowerCase(Locale.ROOT))) {
            n = n.substring(0, i);
        }
        if (n.toLowerCase(Locale.ROOT).endsWith(".tar")) {
            n = n.substring(0, n.length() - 4);
        }
        return n.isEmpty() ? nombre : n;
    }

    /** Patrones para el cuadro de "Abrir". */
    static String[] patrones() {
        List<String> r = new ArrayList<>();
        for (String e : EXTENSIONES) {
            r.add("*." + e);
            r.add("*." + e.toUpperCase(Locale.ROOT));
        }
        r.add("*.tar.gz");
        return r.toArray(String[]::new);
    }

    // ------------------------------------------------------------ motor de 7-Zip
    private static synchronized boolean motor() {
        if (motorListo == null) {
            try {
                Path dir = Files.createTempDirectory("pdfsam_7z_");
                dir.toFile().deleteOnExit();
                SevenZip.initSevenZipFromPlatformJAR(dir.toFile());
                motorListo = true;
            } catch (Throwable e) {
                motorListo = false;
                motorError = String.valueOf(e.getMessage());
            }
        }
        return motorListo;
    }

    /**
     * Abre 'archivo' en 'destino' (carpeta nueva). Si trae un solo comprimido adentro (.tar.gz), también lo abre.
     * Lanza SinClave si tiene contraseña y no se dio.
     */
    static void extraer(Path archivo, Path destino, PideClave pide) throws IOException {
        Files.createDirectories(destino);
        String nombre = archivo.getFileName().toString();
        if (motor()) {
            extraer7z(archivo, destino, pide);
        } else if ("zip".equals(extension(nombre))) {
            Lote.extraerZipSolo(archivo, destino); // sin el motor de 7-Zip, al menos los ZIP sin contraseña
        } else {
            throw new IOException("No se pudo abrir \"" + nombre + "\" (" + motorError + ").");
        }
        if (DE_UNO.contains(extension(nombre))) {
            List<Path> dentro;
            try (Stream<Path> s = Files.list(destino)) {
                dentro = s.toList();
            }
            if (dentro.size() == 1 && Files.isRegularFile(dentro.get(0)) && es(dentro.get(0).getFileName().toString())) {
                Path interno = dentro.get(0);
                Path tmp = destino.resolveSibling(destino.getFileName() + ".tmp_uno");
                Files.move(interno, tmp);
                try {
                    extraer(tmp, destino, pide);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
        }
    }

    /** Las partes que se usaron para abrir el último archivo (para borrarlas de la copia temporal). */
    static List<Path> partes(Path primero) {
        List<Path> r = new ArrayList<>();
        String n = primero.getFileName().toString();
        Path dir = primero.toAbsolutePath().getParent();
        if (dir == null) {
            return r;
        }
        String raiz;
        Pattern p;
        var m = PARTE_RAR.matcher(n);
        if (m.matches()) {
            raiz = n.replaceAll("(?i)\\.part0*\\d+\\.rar$", "");
            p = Pattern.compile(Pattern.quote(raiz) + "(?i)\\.part0*\\d+\\.rar");
        } else if (PARTE_NUMERO.matcher(n).matches()) {
            raiz = n.substring(0, n.length() - 4);
            p = Pattern.compile(Pattern.quote(raiz) + "\\.\\d{3}");
        } else if ("rar".equals(extension(n))) {
            raiz = n.substring(0, n.length() - 4);
            p = Pattern.compile(Pattern.quote(raiz) + "(?i)\\.r\\d\\d");
        } else {
            return r;
        }
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(x -> !x.equals(primero) && p.matcher(x.getFileName().toString()).matches()).forEach(r::add);
        } catch (IOException e) {
            // sin partes
        }
        return r;
    }

    private static void extraer7z(Path archivo, Path destino, PideClave pide) throws IOException {
        String nombre = archivo.getFileName().toString();
        Clave clave = new Clave(nombre, pide);
        Abridor ab = new Abridor(archivo.toAbsolutePath(), clave);
        IInArchive arch = null;
        try {
            while (true) {
                try {
                    arch = SevenZip.openInArchive(null, ab.getStream(archivo.toAbsolutePath().toString()), ab);
                    break;
                } catch (SevenZipException e) {
                    ab.cerrar();
                    if (clave.cancelada) {
                        throw new SinClave(nombre);
                    }
                    if (clave.usada) {
                        // tenía contraseña en los nombres y era incorrecta: preguntar otra vez
                        clave.incorrecta();
                        continue;
                    }
                    throw new IOException("\"" + nombre + "\" está dañado o no es un comprimido que se pueda abrir.");
                }
            }
            // sin nombre adentro (un .gz hecho con tar): "x.tar.gz" -> "x.tar"; "x.tgz" -> "x.tar"
            String ext = extension(nombre);
            String sinNombre = Set.of("tgz", "taz", "tbz", "tbz2", "txz", "tlz").contains(ext) ?
                    base(nombre) + ".tar" : nombre.substring(0, Math.max(1, nombre.length() - ext.length() - 1));
            for (ISimpleInArchiveItem item : arch.getSimpleInterface().getArchiveItems()) {
                String ruta = item.getPath();
                if (ruta == null || ruta.isBlank()) {
                    ruta = sinNombre;
                }
                ruta = ruta.replace('\\', '/');
                while (ruta.startsWith("/")) {
                    ruta = ruta.substring(1);
                }
                if (DE_UNO.contains(ext)) {
                    ruta = ruta.substring(ruta.lastIndexOf('/') + 1); // traen un solo archivo: solo su nombre
                    if (ruta.isBlank()) {
                        ruta = sinNombre;
                    }
                }
                if (ruta.matches("^[A-Za-z]:.*") || List.of(ruta.split("/")).contains("..")) {
                    continue; // nombres peligrosos
                }
                Path fin = destino.resolve(ruta).normalize();
                if (!fin.startsWith(destino)) {
                    continue;
                }
                if (item.isFolder()) {
                    Files.createDirectories(fin);
                    continue;
                }
                Files.createDirectories(fin.getParent());
                while (true) {
                    ExtractOperationResult res;
                    try (OutputStream o = Files.newOutputStream(fin)) {
                        if (item.isEncrypted()) {
                            String c = clave.dar();
                            if (c == null) {
                                throw new SinClave(nombre);
                            }
                            res = item.extractSlow(datos -> escribir(o, datos), c);
                        } else {
                            res = item.extractSlow(datos -> escribir(o, datos));
                        }
                    }
                    if (res == ExtractOperationResult.OK) {
                        break;
                    }
                    Files.deleteIfExists(fin);
                    if (item.isEncrypted() && (res == ExtractOperationResult.WRONG_PASSWORD
                            || res == ExtractOperationResult.CRCERROR || res == ExtractOperationResult.DATAERROR
                            || res == ExtractOperationResult.UNSUPPORTEDMETHOD)) {
                        clave.incorrecta();
                        continue;
                    }
                    throw new IOException("\"" + nombre + "\" está dañado (" + ruta + ").");
                }
            }
        } finally {
            if (arch != null) {
                try {
                    arch.close();
                } catch (SevenZipException e) {
                    // nada
                }
            }
            ab.cerrar();
        }
    }

    private static int escribir(OutputStream o, byte[] datos) throws SevenZipException {
        try {
            o.write(datos);
            return datos.length;
        } catch (IOException e) {
            throw new SevenZipException("No se pudo escribir: " + e.getMessage(), e);
        }
    }

    /** La contraseña de un comprimido: se pregunta solo cuando hace falta, y otra vez si era incorrecta. */
    private static final class Clave {
        final String nombre;
        final PideClave pide;
        String clave;
        boolean usada, cancelada, mala;

        Clave(String nombre, PideClave pide) {
            this.nombre = nombre;
            this.pide = pide;
        }

        String dar() {
            usada = true;
            if (clave == null && !cancelada) {
                clave = pide == null ? null : pide.pedir(nombre, mala);
                if (clave == null) {
                    cancelada = true;
                }
            }
            return clave;
        }

        void incorrecta() throws SinClave {
            mala = true;
            clave = null;
            usada = false;
            if (cancelada || pide == null) {
                throw new SinClave(nombre);
            }
        }
    }

    /** Abre las partes (x.part2.rar, x.002...) cuando el motor las pide, y da la contraseña de los nombres. */
    private static final class Abridor implements IArchiveOpenVolumeCallback, IArchiveOpenCallback, ICryptoGetTextPassword {
        private final Map<String, RandomAccessFile> abiertos = new HashMap<>();
        private final Set<String> usados = new LinkedHashSet<>();
        private final Clave clave;
        private String actual;

        Abridor(Path primero, Clave clave) {
            this.actual = primero.toString();
            this.clave = clave;
        }

        @Override
        public Object getProperty(PropID propID) {
            return propID == PropID.NAME ? actual : null;
        }

        @Override
        public IInStream getStream(String nombre) throws SevenZipException {
            try {
                File f = new File(nombre);
                if (!f.isAbsolute()) {
                    f = new File(new File(actual).getParentFile(), nombre);
                }
                if (!f.isFile()) {
                    return null;
                }
                String clave = f.getAbsolutePath();
                RandomAccessFile r = abiertos.get(clave);
                if (r == null) {
                    r = new RandomAccessFile(f, "r");
                    abiertos.put(clave, r);
                }
                r.seek(0);
                actual = clave;
                usados.add(clave);
                return new RandomAccessFileInStream(r);
            } catch (IOException e) {
                throw new SevenZipException("No se pudo abrir " + nombre, e);
            }
        }

        @Override
        public void setTotal(Long files, Long bytes) {
        }

        @Override
        public void setCompleted(Long files, Long bytes) {
        }

        @Override
        public String cryptoGetTextPassword() throws SevenZipException {
            String c = clave.dar();
            return c == null ? "" : c;
        }

        void cerrar() {
            for (RandomAccessFile r : abiertos.values()) {
                try {
                    r.close();
                } catch (IOException e) {
                    // nada
                }
            }
            abiertos.clear();
        }
    }
}
