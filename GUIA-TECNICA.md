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

- **Mejora 1 (parte 1 de 2 — el error):** arreglado el error
  `No se encontró …_indexado.zip.__zip__` en Indexar.
  - **Causa:** al armar el ZIP final, `IndexarPanel.armarZip()` llama a
    `Lote.cerrarZipsInternos(m)` pasándole como `carpeta` la MISMA carpeta de trabajo, que
    también termina en `.__zip__` (para que el método sepa que es un comprimido). Pero
    `cerrarZipsInternos` usaba `Files.walk(carpeta)`, que **incluye la carpeta de partida**, así
    que la trataba como uno de los comprimidos internos a cerrar: la convertía en ZIP y la
    borraba **antes de tiempo**. El código de `armarZip()` que seguía después ya no la encontraba.
  - **Arreglo:** en `Lote.cerrarZipsInternos`, se excluye explícitamente la carpeta de partida
    del filtro (`p -> !p.equals(carpeta)`), para que solo cierre las marcadas **de adentro**.
    Un solo método tocado, una línea de cambio real.
  - **Prueba agregada:** `NuevasTest.cerrarZipsInternosNoSeIncluyeASiMisma()` reproduce
    exactamente el caso real (carpeta raíz `Expediente_indexado.zip.__zip__` con un comprimido
    interno adentro) y comprueba que la raíz sobrevive y el interno sí se cierra.
  - **Pendiente de la Mejora 1 (parte 2):** el rediseño de cómo ENTRAN los comprimidos
    ("Extraer aquí" al lado del archivo, con contraseña si tiene, trabajarlo como carpeta normal)
    todavía no está hecho — ver sección 4.

- **Mejora 3 — Texto editable en Adobe:** en `Editables.campo()` se agregan explícitamente
  `/Type /Annot` y `/Subtype /Widget` al widget, y `/FT /Tx` al campo (antes el código confiaba
  en que la librería SAMBox los pusiera solos y, según lo visto en el PDF real, no siempre pasaba).
  `/F` (bit Imprimir), `/P` (página) y `/DA` (apariencia) ya se ponían. Prueba ampliada en
  `NuevasTest.folioEditable()` para comprobar las 6 marcas. **Falta confirmar en Adobe de verdad**
  (vista normal y "Rellenar y firmar") — no se puede probar desde aquí.

- **Mejora 4 (Foliar y Girar) — varios PDF en un ZIP:** antes, al procesar una carpeta o
  comprimido con Foliar o Girar, el resultado SIEMPRE salía como carpeta (nunca se preguntaba).
  Ahora, si hay más de un PDF a procesar, siempre se pregunta "¿Cómo quiere el resultado?" con
  tres botones: **Solo ZIP** (resaltado de fábrica), **ZIP y carpeta**, **Solo carpeta**. El
  nombre sugerido se sigue pudiendo cambiar en el propio cuadro de "Guardar como" (no hizo falta
  una pantalla nueva para eso). Técnico: `Ui.elegir3()` (diálogo de 3 botones), `Lote.finalizar()`
  ahora puede entregar ZIP y carpeta a la vez (antes era uno u otro), `LoteUi.elegirDestino()`
  pregunta siempre que `procesables.size() > 1` (antes solo cuando el ORIGEN ya era un .zip).
  Prueba: `NuevasTest.finalizarZipYCarpetaALaVez()`.
  - **Indexar NO quedó cubierto todavía.** Indexar guarda cada PDF suelto directo en la carpeta
    que el usuario elige (ver `IndexarPanel.procesar()`/`terminar()`) y solo arma un ZIP aparte
    para lo que vino de un comprimido (`armarZip()`, ya arreglado en la Mejora 1 parte 1). Darle
    a Indexar las mismas 3 opciones (Solo ZIP/ZIP y carpeta/Solo carpeta) para TODOS sus archivos
    requiere cambiar su guardado para que escriba primero a una carpeta temporal (como ya hace
    `Lote`) y recién al final la convierta en ZIP y/o la copie — no es un cambio chico, queda
    anotado en la sección 4 como pendiente.

## 4. Pendiente

### 4.0 Mejora 4 en Indexar
Ver el detalle en la sección 3 — darle a Indexar las mismas 3 opciones de resultado que ya
tienen Foliar y Girar.

### 4.1 Resto de la Mejora 1 — Comprimidos "Extraer aquí"
Hoy, Foliar/Indexar/Girar abren el ZIP/RAR en una carpeta TEMPORAL que se borra al terminar
(ver `Lote.preparar()`, usa `Files.createTempDirectory`). Se pidió cambiar a: descomprimir
AL LADO del archivo original (si ya existe esa carpeta, usar "Nombre (2)"), pedir contraseña si
tiene, y trabajarla ahí como una carpeta cualquiera. Archivos a tocar: `Lote.preparar()`
(dónde se descomprime) y el texto de la pregunta inicial en cada Panel (`recibir()`).

### 4.2 Mejora 2 — Lista de PDF antes de guardar (Foliar y Girar)
Indexar YA tiene este patrón (clase interna `Item` + `EditorHojas`, lista con vista previa,
clic para editar). Falta reusar ese mismo patrón en `FoliarPanel` y `GirarPanel` para cuando
se mete un comprimido/carpeta con varios PDF: hoy `FoliarPanel.lote()` los folia de un tirón
sin dejar ver ni editar cada uno.

### 4.3 Mejora 3 — "Texto editable en Adobe"
Revisar `Editables.campo()`: agregar `/Subtype /Widget` y `/Type /Annot` al diccionario del
campo, y revisar que tenga `/F 4` (visible), `/P` (página), `/FT /Tx` (tipo texto), `/DA`
(apariencia) y que la fuente esté en `/DR` del AcroForm. Sin esto, Adobe no lo reconoce como
casilla de formulario real y solo dibuja el texto, pero no deja hacerle clic para cambiarlo.
Falta probar en Adobe de verdad (no se puede desde aquí) y en "Rellenar y firmar", no solo en
vista normal.

### 4.4 Mejora 4 — Varios PDF en un ZIP
**Importante — esto NO es lo mismo que la Mejora 1.** Ya existe `Lote.finalizar(comoZipEn)`,
que arma un ZIP cuando el ORIGEN fue un comprimido o una carpeta (ver prueba
`NuevasTest` sección "carpeta o comprimido completo"). Lo que falta es para el caso de
**varios PDF sueltos elegidos a mano** (no una carpeta ni un ZIP): hoy, si en `FoliarPanel`
se arrastran 3 PDF sueltos, `recibir()` solo toma el PRIMERO (`rutas.stream().filter(...)
.findFirst()`) y los otros dos se ignoran en silencio. Falta: detectar varios PDF sueltos,
procesarlos todos, y preguntar "¿Solo ZIP o ZIP y carpeta?" (de fábrica: Solo ZIP), con nombre
automático editable. Revisar también Indexar y Girar para el mismo caso.

### 4.5 Mejora 5 — Formato del folio a gusto (el "plantillador")
Nueva clase pequeña que reciba un ejemplo de texto (ej. "FOLIO 1 [UNO]"), lo separe en piezas
usando `Motor.numeroALetras` para reconocer dónde está el número y dónde el número en letras,
y arme con eso el texto de cada hoja. Se guarda en `Config` con lista de favoritos. El número de
inicio NUNCA sale del ejemplo, solo de "Empezar en". Falta decidir en código cómo se marca cada
pieza como "cuenta" o "texto fijo" en la pantalla.

### 4.6 Mejora 6 — Desde/hasta y tramos por leyenda
Cada leyenda (clase `OpcionesIndexar`, ver también `ReglaHoja`) necesita su propio rango de
hojas y, dentro de eso, "tramos" (rangos con texto distinto y su propio "Empezar en"). Toca
`OpcionesIndexar`, `ReglaHoja` y la parte de `Motor` que arma el texto final de cada leyenda.
Es la mejora más grande de las 6.

### 4.7 Amigabilidad (en paralelo con todas las anteriores)
Botón "Personalizar ✎" con pasos numerados, mensajitos de ayuda al pasar el mouse, bienvenida
de 3 pasos, "Volver a lo de fábrica", "¿Cómo se usa?" (abre el manual), errores en palabras
sencillas, resumen antes de guardar, "Abrir el resultado"/"Abrir la carpeta".

## 5. Decisiones ya tomadas (para no volver a preguntar)
- "Hasta el folio 20" → las hojas siguientes quedan SIN folio.
- Varios PDF → de fábrica "Solo ZIP" (igual pregunta cada vez).
- Hojas fuera de todo tramo → de fábrica quedan vacías.
- Todo lo predeterminado de hoy debe seguir funcionando igual para quien no personalice nada.

## 6. Qué no se pudo probar desde aquí
Esta entrega se armó sin acceso a Windows, Adobe, mouse real ni a los servidores de Java
(Maven Central) desde el entorno donde se escribió el código — igual que en la máquina de
trabajo, por eso todo se compila y prueba con GitHub Actions (`pruebas.yml`). El arreglo de esta
entrega se verifica con la prueba automática agregada; falta la confirmación de que
`pruebas.yml` pase en GitHub antes de pasarlo a `auditoria`.
