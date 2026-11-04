/*
 * Foliar e Indexar para PDFsam Basic.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package org.pdfsam.tools.foliado;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Procesar muchos archivos de una vez: una carpeta con todas sus subcarpetas, o un ZIP (que también puede traer
 * otros ZIP adentro). Así trabaja:
 * <ol>
 * <li>preparar(): arma la lista de archivos. Si es ZIP, lo abre en una carpeta temporal (los ZIP de adentro
 * también).</li>
 * <li>ejecutar(): procesa cada PDF y lo guarda en el destino con LA MISMA estructura de carpetas. Lo que no es PDF
 * se copia igual. Si un archivo falla, se copia el original y se anota. No se detiene.</li>
 * <li>reintentar(): al final, para los protegidos, con la contraseña que dé el usuario.</li>
 * <li>finalizar(): si el resultado es ZIP, lo arma (con los ZIP de adentro otra vez como ZIP).</li>
 * </ol>
 */
final class Lote {
    static final String MARCA_ZIP = ".__zip__";

    /** Lo que hace la herramienta con UN archivo. */
    @FunctionalInterface
    interface Operacion {
        void aplicar(File entrada, File salida, String clave) throws IOException;
    }

    static final class Fallido {
        final String relativo;
        final String motivo; // "protegido", "danado" u "otro"
        String mensaje;
        boolean resuelto;

        Fallido(String relativo, String motivo, String mensaje) {
            this.relativo = relativo;
            this.motivo = motivo;
            this.mensaje = mensaje;
        }

        String visible() {
            return nombreVisible(relativo);
        }
    }

    final File origen;
    final boolean esZip;
    final Path temp;
    Path raiz;
    final List<String> carpetas = new ArrayList<>();
    final List<String> archivos = new ArrayList<>();
    final List<String> procesables = new ArrayList<>();
    final List<Fallido> fallidos = new ArrayList<>();
    int hechos;
    Path destinoTrabajo;
    final AtomicBoolean cancelar = new AtomicBoolean(false);

    Lote(File origen) throws IOException {
        this.origen = origen.getAbsoluteFile();
        this.esZip = this.origen.isFile() && this.origen.getName().toLowerCase(Locale.ROOT).endsWith(".zip");
        this.temp = Files.createTempDirectory("pdfsam_lote_");
    }

    // ------------------------------------------------------------ preparar
    Lote preparar() throws IOException {
        if (esZip) {
            raiz = temp.resolve("origen");
            extraerZip(origen.toPath(), raiz);
        } else {
            raiz = origen.toPath();
            boolean hayZip;
            try (Stream<Path> s = Files.walk(raiz)) {
                hayZip = s.anyMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"));
            }
            if (hayZip) {
                // los ZIP de adentro también se abren (en una copia: los originales no se tocan)
                Path copia = temp.resolve("origen");
                copiarCarpeta(raiz, copia);
                abrirZipsInternos(copia);
                raiz = copia;
            }
        }
        carpetas.clear();
        archivos.clear();
        procesables.clear();
        List<Path> todo;
        try (Stream<Path> s = Files.walk(raiz)) {
            todo = s.filter(p -> !p.equals(raiz)).sorted(Comparator.comparing(p -> raiz.relativize(p).toString().toLowerCase(Locale.ROOT))).toList();
        }
        for (Path p : todo) {
            String rel = raiz.relativize(p).toString();
            if (Files.isDirectory(p)) {
                carpetas.add(rel);
            } else {
                archivos.add(rel);
                if (rel.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    procesables.add(rel);
                }
            }
        }
        return this;
    }

    /** {carpeta: [a procesar, otros]} para la vista previa (los ZIP de adentro con su nombre normal). */
    Map<String, int[]> arbol() {
        Map<String, int[]> r = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        r.put(".", new int[2]);
        for (String c : carpetas) {
            r.putIfAbsent(nombreVisible(c), new int[2]);
        }
        for (String rel : archivos) {
            String padre = new File(rel).getParent();
            String c = padre == null ? "." : nombreVisible(padre);
            int[] v = r.computeIfAbsent(c, k -> new int[2]);
            if (procesables.contains(rel)) {
                v[0]++;
            } else {
                v[1]++;
            }
        }
        return r;
    }

    String nombreOrigen() {
        String n = origen.getName();
        return esZip ? Motor.sinExtension(n) : n;
    }

    // ------------------------------------------------------------ ejecutar
    interface Avance {
        void archivo(int hechos, int total, String relativo);
    }

    void ejecutar(Operacion op, Path destino, Avance avance) throws IOException {
        destinoTrabajo = destino;
        Files.createDirectories(destino);
        int total = procesables.size();
        // 1) todas las carpetas (también las vacías) y copiar lo que no se procesa
        for (String c : carpetas) {
            Files.createDirectories(destino.resolve(c));
        }
        for (String rel : archivos) {
            if (!procesables.contains(rel)) {
                copiar(raiz.resolve(rel), destino.resolve(rel));
            }
        }
        // 2) procesar
        hechos = 0;
        for (String rel : procesables) {
            if (cancelar.get()) {
                break;
            }
            if (avance != null) {
                avance.archivo(hechos, total, nombreVisible(rel));
            }
            File entrada = raiz.resolve(rel).toFile();
            File salida = destino.resolve(rel).toFile();
            Files.createDirectories(salida.toPath().getParent());
            try {
                op.aplicar(entrada, salida, null);
            } catch (Motor.ErrorProtegido e) {
                fallo(rel, "protegido", e.getMessage(), entrada, salida);
            } catch (Motor.ErrorDanado e) {
                fallo(rel, "danado", e.getMessage(), entrada, salida);
            } catch (Exception e) {
                String m = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
                fallo(rel, m.contains("password") || m.contains("encrypt") ? "protegido" : "otro",
                        String.valueOf(e.getMessage()), entrada, salida);
            }
            hechos++;
        }
        if (avance != null) {
            avance.archivo(hechos, total, "");
        }
    }

    private void fallo(String rel, String motivo, String mensaje, File entrada, File salida) throws IOException {
        copiar(entrada.toPath(), salida.toPath()); // el original queda en su lugar, sin cambios
        fallidos.add(new Fallido(rel, motivo, mensaje));
    }

    /** Con la contraseña (si la tiene): procesa de nuevo, o solo le quita la seguridad. */
    void reintentar(Fallido f, Operacion op, String clave, boolean procesar) throws IOException {
        File entrada = raiz.resolve(f.relativo).toFile();
        File salida = destinoTrabajo.resolve(f.relativo).toFile();
        if (procesar) {
            op.aplicar(entrada, salida, clave);
        } else {
            Motor.copiarSinSeguridad(entrada, salida, clave);
        }
        f.resuelto = true;
    }

    File archivoDe(Fallido f) {
        return raiz.resolve(f.relativo).toFile();
    }

    // ------------------------------------------------------------ finalizar
    Path finalizar(File comoZipEn) throws IOException {
        cerrarZipsInternos(destinoTrabajo);
        if (comoZipEn != null) {
            Files.createDirectories(comoZipEn.getAbsoluteFile().getParentFile().toPath());
            comprimirCarpeta(destinoTrabajo, comoZipEn.toPath());
        }
        Path r = comoZipEn != null ? comoZipEn.toPath() : destinoTrabajo;
        limpiar();
        return r;
    }

    void limpiar() {
        borrar(temp);
    }

    // ------------------------------------------------------------ ayudantes
    static String nombreVisible(String rel) {
        return rel.replace(MARCA_ZIP, "");
    }

    static void copiar(Path origen, Path destino) throws IOException {
        Files.createDirectories(destino.getParent());
        if (!origen.toAbsolutePath().normalize().equals(destino.toAbsolutePath().normalize())) {
            Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    static void copiarCarpeta(Path desde, Path hasta) throws IOException {
        Files.walkFileTree(desde, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(hasta.resolve(desde.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, hasta.resolve(desde.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void borrar(Path p) {
        if (p == null || !Files.exists(p)) {
            return;
        }
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
        } catch (IOException e) {
            // se queda en temporales, no es grave
        }
    }

    /** Extrae un ZIP (con los nombres en español bien leídos) y abre los ZIP de adentro. */
    static void extraerZip(Path zip, Path destino) throws IOException {
        Files.createDirectories(destino);
        try {
            extraer(zip, destino, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | ZipException e) {
            // ZIP hecho en Windows con tildes: los nombres vienen en la codificación vieja
            borrar(destino);
            Files.createDirectories(destino);
            extraer(zip, destino, Charset.forName("IBM850"));
        }
        abrirZipsInternos(destino);
    }

    private static void extraer(Path zip, Path destino, Charset juego) throws IOException {
        try (ZipFile z = new ZipFile(zip.toFile(), juego)) {
            var entradas = z.entries();
            while (entradas.hasMoreElements()) {
                ZipEntry e = entradas.nextElement();
                String nombre = e.getName().replace('\\', '/');
                if (nombre.startsWith("/") || List.of(nombre.split("/")).contains("..")) {
                    continue; // nombres peligrosos
                }
                Path fin = destino.resolve(nombre).normalize();
                if (!fin.startsWith(destino)) {
                    continue;
                }
                if (e.isDirectory()) {
                    Files.createDirectories(fin);
                    continue;
                }
                Files.createDirectories(fin.getParent());
                try (InputStream in = z.getInputStream(e)) {
                    Files.copy(in, fin, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Cada "x.zip" de adentro se cambia por la carpeta "x.zip.__zip__". */
    static void abrirZipsInternos(Path carpeta) throws IOException {
        List<Path> zips;
        try (Stream<Path> s = Files.walk(carpeta)) {
            zips = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")).toList();
        }
        for (Path z : zips) {
            Path dest = z.resolveSibling(z.getFileName() + MARCA_ZIP);
            try {
                extraerZip(z, dest);
                Files.delete(z);
            } catch (IOException e) {
                borrar(dest); // ZIP dañado: se copia tal cual
            }
        }
    }

    /** Lo contrario: cada carpeta "x.zip.__zip__" vuelve a ser "x.zip" (de adentro hacia afuera). */
    static void cerrarZipsInternos(Path carpeta) throws IOException {
        if (carpeta == null || !Files.isDirectory(carpeta)) {
            return;
        }
        List<Path> marcadas;
        try (Stream<Path> s = Files.walk(carpeta)) {
            marcadas = new ArrayList<>(s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().endsWith(MARCA_ZIP)).toList());
        }
        marcadas.sort(Comparator.comparingInt(p -> -p.getNameCount()));
        for (Path m : marcadas) {
            String n = m.getFileName().toString();
            Path zip = m.resolveSibling(n.substring(0, n.length() - MARCA_ZIP.length()));
            comprimirCarpeta(m, zip);
            borrar(m);
        }
    }

    static void comprimirCarpeta(Path carpeta, Path zip) throws IOException {
        Path tmp = zip.resolveSibling(zip.getFileName() + ".tmp_pdfsam");
        List<Path> todo;
        try (Stream<Path> s = Files.walk(carpeta)) {
            todo = s.filter(p -> !p.equals(carpeta)).sorted().toList();
        }
        try (OutputStream o = Files.newOutputStream(tmp); ZipOutputStream z = new ZipOutputStream(o)) {
            for (Path p : todo) {
                String rel = carpeta.relativize(p).toString().replace(File.separatorChar, '/');
                if (Files.isDirectory(p)) {
                    boolean vacia;
                    try (Stream<Path> h = Files.list(p)) {
                        vacia = h.findAny().isEmpty();
                    }
                    if (vacia) {
                        z.putNextEntry(new ZipEntry(rel + "/"));
                        z.closeEntry();
                    }
                    continue;
                }
                z.putNextEntry(new ZipEntry(rel));
                Files.copy(p, z);
                z.closeEntry();
            }
        }
        Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
    }

    /** "C:/x/nombre" -> "C:/x/nombre (2)" si ya existe. */
    static Path nombreLibre(Path p) {
        if (!Files.exists(p)) {
            return p;
        }
        String n = p.getFileName().toString();
        for (int i = 2; ; i++) {
            Path q = p.resolveSibling(n + " (" + i + ")");
            if (!Files.exists(q)) {
                return q;
            }
        }
    }
}
