# Guía técnica — PDFsam / Auditoría PDF

Este archivo va dentro del código fuente a propósito: se actualiza en cada entrega, para que
cualquier conversación nueva (con Claude o con quien sea) sepa de qué trata el programa, qué se
hizo y qué falta, sin tener que repetir todo desde cero.

## 1. Qué es esto

Dos versiones del mismo programa de Windows, hechas sobre PDFsam Basic (código abierto, Java +
JavaFX):

- **Rama `main`** → PDFsam con las herramientas agregadas **Foliar**, **Indexar PDF** y **Girar**
  (interactivo, con miniaturas). Marca y nombre originales de PDFsam.
- **Rama `auditoria`** → lo mismo, pero renombrado **"Auditoría PDF"** (logo e íconos propios,
  sin ninguna marca de PDFsam), con licencia AGPLv3 y créditos en "Acerca de".

Las dos ramas comparten casi todo el código; los cambios de funcionalidad (lo que hace el
programa) se hacen en `main` y se pasan a `auditoria` con `git cherry-pick`. Solo cambian entre
ramas: nombre, logo, íconos, pantalla "Acerca de" y textos de licencia.

El código de las herramientas nuevas vive en:
`pdfsam-tools/pdfsam-foliado/src/main/java/org/pdfsam/tools/foliado/`

Piezas principales:
- `FoliarPanel` / `GirarPanel` / `IndexarPanel` — pantalla de cada herramienta.
- `Motor` — hace el trabajo real sobre el PDF (abrir, foliar, indexar, guardar).
- `Lote` / `LoteUi` — procesar una carpeta o un comprimido completo (ZIP, RAR, 7z…).
- `Comprimidos` — abrir comprimidos (usa 7-Zip-JBinding).
- `Editables` — las casillas de folio que se pueden editar en Adobe.
- `Config` — qué recuerda el programa entre usos.
- `ManualPanel` — el manual que se ve dentro del programa.

Pruebas: `src/test/java/org/pdfsam/tools/foliado/` (`MotorTest`, `NuevasTest`, `PantallasTest`).
Corren solas al hacer `git push` a `main` (`.github/workflows/pruebas.yml`).

## 2. Cómo se arma (recordatorio)

1. Cambios de funcionalidad → commit en `main` → el push dispara `pruebas.yml` (compila y corre
   las pruebas en Linux).
2. Si pasa, se pasa a `auditoria` con `git cherry-pick` (si choca el manual o el "Acerca de",
   se deja el de `auditoria` y se reaplican los cambios de código).
3. Se lanzan a mano (`workflow_dispatch`) `portable.yml` (rama `main`) y `auditoria.yml` (rama
   `auditoria`). Arman portable + instalador (jpackage + WiX) y suben el ZIP con todo al release.
4. Entrega siempre en dos ZIP, cada uno con 3 carpetas: `1 - Portable`, `2 - Instalador`,
   `3 - Código fuente`.

## 3. Hecho en esta entrega (2026-11-14)

Quedaron listas, probadas (CI verde en `main`, 47 pruebas) y con pantalla las 4 mejoras que
faltaban: **Mejora 1 (resto)**, **Mejora 2**, **Mejora 5** y **Mejora 6**. (Mejoras 3 y 4 ya
habían quedado listas en una entrega anterior — ver el historial de git para el detalle de esas
dos si hace falta.)

- **Mejora 1 (resto) — Comprimidos "Extraer aquí":** `Lote.preparar()` ya no descomprime en una
  carpeta temporal que se borra al final. Ahora, si el origen es un comprimido (ZIP/RAR/7z…), lo
  extrae **al lado del archivo original**, con el mismo nombre (o "Nombre (2)", "(3)"... si ya
  existe esa carpeta — ver `Lote.nombreLibre()`), y esa carpeta extraída **no se borra al
  terminar**: queda ahí como una carpeta normal (campo nuevo `Lote.carpetaExtraida`). Los
  comprimidos que venían DENTRO de ese comprimido (para que sus PDF también se procesen) se
  dejan momentáneamente como carpeta "marcada" (`.__zip__`, el mismo truco de siempre) y
  `Lote.limpiar()` los vuelve a cerrar a ZIP de verdad dentro de esa carpeta ya persistente, en
  vez de dentro de la carpeta temporal (que antes se borraba junto con ellos). Pruebas:
  `NuevasTest.extraeComprimidoAlLadoDelArchivoYConNombreLibre()`.

- **Mejora 2 — Lista de PDF antes de guardar (Foliar y Girar):** nuevo paso `LoteUi.listaPdf()`
  entre la vista previa de carpetas y "¿dónde guardo?": muestra cada PDF de `Lote.procesables`
  con una casilla (para quitarlo del procesado: se copia igual, sin cambios, como ya pasa con lo
  que no es PDF) y botones **Subir**/**Bajar** para cambiar el orden (importa para la numeración
  seguida de Foliar). `Lote.aplicarSeleccion(List<String>)` aplica lo elegido. Como pasa por
  `LoteUi.procesar()`, esto cubre a la vez `FoliarPanel.lote()` y el lote de `GirarPanel`, sin
  tocar cada pantalla por separado. Indexar sigue con su propio patrón de lista (`Item` +
  `EditorHojas`), no le hizo falta este cambio. Prueba:
  `NuevasTest.aplicarSeleccionExcluyeUnArchivoDelProcesado()`.

- **Mejora 5 — Formato del folio a gusto ("el plantillador"):** clase nueva `Plantillador`
  (pura, sin JavaFX): recibe un ejemplo de texto (p.ej. `"FOLIO 1 DE 50 [UNO DE CINCUENTA]"`) y
  lo separa en prefijo, sufijo, cantidad de dígitos (ceros a la izquierda), si lleva el total y
  con qué palabra, y si además lleva el número en letras — sin tocar `Motor` ni cómo se dibuja el
  folio, solo traduce el ejemplo a las piezas que `OpcionesFolio` ya entendía. El número de
  inicio NUNCA sale del ejemplo (eso sigue siendo "Empieza en"). El contenido entre corchetes
  `[ ]` no se lee palabra por palabra: solo marca "aquí también en letras" (el texto en letras lo
  arma `Motor` pegando el mismo prefijo del número con el número real en letras, así que no hace
  falta — y no se puede — escribir un "total en letras" aparte). En `FoliarPanel` hay un campo
  nuevo "¿O prefiere escribir un ejemplo?" con botón "Usar este ejemplo" (sección "2. Texto del
  folio") que llama a `Plantillador` y llena las casillas de siempre. No quedó favoritos en
  `Config` (no alcanzó en esta entrega; ver sección 4 si se quiere agregar después). Pruebas:
  `PlantilladorTest` (12 casos) y `PantallasTest.foliarUsaEjemploDeFormato()` (con la pantalla de
  verdad).

- **Mejora 6 — Tramos por leyenda (Indexar):** clase nueva `Tramo` (desde, hasta, texto,
  empezarEn) y campo nuevo `OpcionesIndexar.tramos`: una lista de tramos por cada una de las 5
  leyendas (mismo orden que `Motor.LEYENDAS`/`movX`/`movY`). Mientras la lista de una leyenda esté
  vacía, esa leyenda sigue funcionando EXACTAMENTE como siempre (su texto fijo en todas las
  hojas) — es justo lo que comprueban las pruebas viejas, que no se tocaron y siguen pasando.
  Si una leyenda SÍ tiene tramos, cada hoja usa el tramo cuyo rango la cubre (el rango se cuenta
  entre las hojas que se van a indexar, 1 = la primera — no entre todas las hojas del PDF), con
  el texto y la numeración propios de ESE tramo ("empezarEn" + la posición dentro del tramo, con
  su propio total = hasta donde llegaría ese tramo). Las hojas que no caen en ningún tramo de esa
  leyenda se quedan SIN ella (de fábrica, ver sección 5). Toda la lógica nueva está en
  `Motor.indexarHoja()` (helper privado `buscarTramo()`), un bloque por cada una de las 5
  leyendas. Pantalla nueva en `IndexarPanel`: botón "Tramos por leyenda…" (sección "5. Más
  opciones") que abre una ventana para elegir la leyenda y agregar/cambiar/quitar sus tramos.
  **No se guardan en `Config`** (se pierden al cerrar el programa; ver sección 4). Pruebas:
  `MotorTest.tramosEnLeyendaDerecha()` y `MotorTest.hojaFueraDeTodoTramoQuedaSinEsaLeyenda()`.

## 4. Pendiente

### 4.1 Guardar tramos y favoritos del plantillador entre usos
`OpcionesIndexar.tramos` (Mejora 6) y los posibles favoritos del "ejemplo" de Foliar (Mejora 5)
no se guardan en `Config` todavía: cada vez que se abre el programa hay que armarlos de nuevo.
`Config` hoy solo guarda valores simples (texto/número/sí-no) — para listas de objetos haría
falta un formato propio (por ejemplo, una línea de texto por tramo con los 4 valores separados
por algún símbolo que no choque, o guardar como JSON si se agrega una librería chica para eso).

### 4.2 Indexar con "lista de PDF antes de guardar" al estilo Mejora 2
La Mejora 2 (sección 3) cubrió Foliar y Girar reusando `LoteUi`. Indexar NO pasa por `LoteUi`
(tiene su propio patrón `Item`/`EditorHojas`, que ya deja ver y editar cada PDF uno por uno antes
de guardar) — así que no le faltaba lo mismo, pero si se quiere el mismo control de
incluir/excluir y reordenar que ahora tienen Foliar/Girar, habría que agregarlo a esa lista
propia de Indexar.

### 4.3 Mejora 4 en Indexar (gap ya conocido, de una entrega anterior)
Indexar guarda cada PDF suelto directo en la carpeta que el usuario elige y solo arma un ZIP
aparte para lo que vino de un comprimido. Darle a Indexar las mismas 3 opciones de resultado que
ya tienen Foliar y Girar (Solo ZIP/ZIP y carpeta/Solo carpeta para TODOS sus archivos) requeriría
cambiar su guardado para que escriba primero a una carpeta temporal, como ya hace `Lote`.

### 4.4 Amigabilidad (pendiente de entregas anteriores, sigue en pie)
Botón "Personalizar ✎" con pasos numerados, mensajitos de ayuda al pasar el mouse, bienvenida
de 3 pasos, "Volver a lo de fábrica", "¿Cómo se usa?" (abre el manual), errores en palabras
sencillas, resumen antes de guardar, "Abrir el resultado"/"Abrir la carpeta".

### 4.5 Mejora 3 — confirmar en Adobe de verdad
Las marcas (`/Type /Annot`, `/Subtype /Widget`, `/FT /Tx`, etc.) ya están y las prueba
`NuevasTest.folioEditable()`, pero falta la confirmación real en Adobe (Acrobat o Reader), en
vista normal y en "Rellenar y firmar" — no se puede probar desde este entorno (sin Windows ni
Adobe instalado).

## 5. Decisiones ya tomadas (para no volver a preguntar)
- "Hasta el folio 20" → las hojas siguientes quedan SIN folio.
- Varios PDF → de fábrica "Solo ZIP" (igual pregunta cada vez).
- Hojas fuera de todo tramo (Mejora 6) → de fábrica quedan vacías (sin esa leyenda).
- El contenido escrito dentro de los corchetes `[ ]` del ejemplo de folio (Mejora 5) no se usa
  palabra por palabra: solo marca "aquí también en letras"; el texto en letras siempre es el
  mismo prefijo del número + el número real en letras (lo arma `Motor`, no el ejemplo).
- "Desde"/"hasta" de un tramo (Mejora 6) se cuentan entre las hojas que SÍ se indexan, no entre
  todas las hojas del PDF (coherente con cómo ya se cuenta "1/10" en la leyenda derecha).
- Todo lo predeterminado de hoy debe seguir funcionando igual para quien no personalice nada
  (es justo lo que comprueban las pruebas viejas, que no se tocaron).

## 6. Qué no se pudo probar desde aquí
Esta entrega (como las anteriores) se armó sin acceso a Windows, Adobe, mouse real ni a los
servidores de Java (Maven Central) desde el entorno donde se escribió el código — igual que en
la máquina de trabajo, por eso todo se compila y prueba con GitHub Actions (`pruebas.yml`). Las
47 pruebas automáticas (incluidas las nuevas de esta entrega) pasaron en GitHub antes de pasar
los cambios a `auditoria` y armar los instaladores/portables finales.
