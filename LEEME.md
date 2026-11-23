# LEEME — PDFsam (Foliar e Indexar) y Auditoría PDF

**Este es el documento principal del proyecto.** Va adentro del código fuente a propósito: la idea es que
cualquier conversación nueva (con Claude o con quien sea) lea SOLO este archivo y ya sepa de qué se trata el
programa, cómo trabajar con el usuario, cómo está hecho por dentro, qué cuidados hay que tener al programar,
qué ya se decidió (para no volver a preguntarlo) y qué falta.

Última actualización: **23 de noviembre de 2026**.

---

## Índice

1. [Qué es esto, en dos líneas](#1-qué-es-esto-en-dos-líneas)
2. [Cómo trabajar con el usuario (leer primero)](#2-cómo-trabajar-con-el-usuario-leer-primero)
3. [Las dos versiones y en qué se diferencian](#3-las-dos-versiones-y-en-qué-se-diferencian)
4. [Mapa del código](#4-mapa-del-código)
5. [Cómo se arma el programa (sin Windows)](#5-cómo-se-arma-el-programa-sin-windows)
6. [Cómo se entrega](#6-cómo-se-entrega)
7. [Cuidados al programar (lo que se rompe fácil)](#7-cuidados-al-programar-lo-que-se-rompe-fácil)
8. [Decisiones ya tomadas (no volver a preguntar)](#8-decisiones-ya-tomadas-no-volver-a-preguntar)
9. [Historial de mejoras](#9-historial-de-mejoras)
10. [Lo que falta](#10-lo-que-falta)
11. [Pruebas](#11-pruebas)
12. [Licencias](#12-licencias)
13. [Preguntas frecuentes](#13-preguntas-frecuentes)

---

## 1. Qué es esto, en dos líneas

Dos versiones del mismo programa de Windows, hechas sobre **PDFsam Basic** (código abierto, Java + JavaFX),
con tres herramientas agregadas para trabajo de auditoría: **Foliar**, **Indexar PDF** y un **Girar** nuevo
con miniaturas.

| | Rama `main` | Rama `auditoria` |
|---|---|---|
| Nombre | PDFsam (Foliar e Indexar) | **Auditoría PDF** |
| Marca | la original de PDFsam | logo e íconos propios, sin marca de PDFsam |
| Releases | tag `portable` | tag `auditoria` |
| Archivos | `PDFsam-Foliar-Indexar-*.zip` | `Auditoria-PDF-*.zip` |

Repositorio: **`cesar24200924-ui/pdfsam-foliado`** (la cuenta anterior, `cesar242009-blip`, se quedó sin
espacio; está invitada como colaboradora).

---

## 2. Cómo trabajar con el usuario (leer primero)

Esto importa tanto como el código. El usuario es **auditor tributario en la SAR (Honduras)** y **no es
programador**.

- **Todo en español**, incluidos los avisos de avance mientras se trabaja ("ya voy por tal cosa"). Él escribe
  informal y a veces en mayúsculas.
- **Nada de jerga técnica sin explicarla.** Instrucciones simples, paso a paso.
- **Preguntar antes si hay dudas de alcance**, con opciones para elegir. Si dice "no hagas nada", "solo leé"
  o "solo mirá": únicamente analizar y explicar, **no tocar el código**.
- **Cambios quirúrgicos.** Nunca reescribir código que ya funciona sin necesidad.
- **Código completo y listo para usar**, no fragmentos a medias.
- **No reportar avances intermedios** si pidió juntar varias mejoras: entregar cuando TODO esté listo y
  probado (salvo que él pregunte el estado mientras tanto).
- **Pantallas chicas:** usa el programa en computadoras con pantalla pequeña. Las ventanas y formularios
  tienen que **caber en pantalla**, con barra para bajar y **los botones siempre visibles**.
- **Cintitas de ayuda (tooltips):** cada opción de una ventana con muchas opciones debe tener su ayuda al
  pasar el ratón, bien explicada. Entre más explicada, mejor.
- **Manual:** siempre mantener la sección **"Preguntas frecuentes"** y agregarle preguntas por cada función
  nueva. Las mejoras grandes se explican paso a paso, por largo que salga.
- **Vista previa:** cuando se arma una herramienta con opciones, debe tener vista previa que se actualice
  sola al cambiar las opciones.
- Antes de avisar que algo quedó listo: revisar que **no quede la palabra "Curiosity"** en la versión
  Auditoría PDF, revisar los colores en **tema claro y oscuro**, probar todo lo que se pueda y **decir con
  honestidad qué no se pudo probar**.

---

## 3. Las dos versiones y en qué se diferencian

Las dos ramas comparten **casi todo** el código. Los cambios de funcionalidad se hacen en `main` y se pasan
a `auditoria` con `git cherry-pick`.

Archivos que **sí** cambian entre ramas (y solo estos):

| Archivo | Qué cambia |
|---|---|
| `pdfsam-basic/src/main/resources/pdfsam.properties` | nombre, enlaces, copyright |
| `pdfsam-basic/src/main/java/org/pdfsam/basic/App.java` | Auditoría PDF arranca sin buscar actualizaciones, sin noticias, sin donaciones, sin módulos de pago y en español |
| `pdfsam-gui/.../about/AboutPane.java` | créditos a PDFsam Basic y licencia |
| `pdfsam-basic/src/assembly/...`, `images/basic/*.png`, `*.ico` | logo, íconos y pantalla de inicio |
| `CAMBIOS-Y-LICENCIA.txt` | **solo existe en `auditoria`** |
| `README.md`, `pdfsam-basic/pom.xml`, `.github/workflows/*` | nombres y armado |
| manual HTML y `ManualPanel.java`, `Config.java` | textos (dicen "el programa" en vez de "PDFsam") |

**Todo lo demás es idéntico**, incluido el motor y las tres herramientas nuevas.

---

## 4. Mapa del código

Proyecto Maven multi-módulo, **Java 25 con JavaFX** (Liberica "full"). El código nuevo vive en un módulo
propio:

```
pdfsam-tools/pdfsam-foliado/src/main/java/org/pdfsam/tools/foliado/
```

### Las piezas

| Clase | Para qué sirve |
|---|---|
| `Motor` | **El corazón.** Pone el folio y las leyendas, y arma los PDF (`armar`). La vista previa usa EXACTAMENTE las mismas funciones que al guardar. Todas las medidas se calculan en la hoja **tal como se ve** (respetando giro y tamaño). |
| `FoliarPanel` | Pantalla de Foliar (folio 001, 002… con vista previa real). |
| `IndexarPanel` | Pantalla de Indexar (leyendas en muchos PDF a la vez). **Es el modelo a seguir** para trabajar con listas de archivos. |
| `GirarPanel` | Pantalla de Girar con miniaturas y lista de archivos. |
| `Lote` | Procesar muchos archivos: una carpeta con subcarpetas o un comprimido. `preparar()` arma la lista; `ejecutar()` procesa; `finalizar()` deja el resultado. También tiene las utilidades compartidas de ZIP. |
| `LoteUi` | Los pasos en pantalla del lote: vista previa del árbol, lista de PDF, dónde guardar, barra de avance, resumen. |
| `Comprimidos` | Abre ZIP, RAR (y RAR5 y en partes), 7z, TAR, GZ, BZ2, XZ, CAB, ISO y más, con el motor de 7-Zip (7-Zip-JBinding). |
| `Editables` | Folios y leyendas como casillas de formulario, para poder cambiarlas después en Adobe ("Rellenar y firmar"). |
| `EditorHojas` | "Personalizar hojas": cambios solo para algunas hojas. |
| `ReglaHoja` | Los cambios de una hoja personalizada (lo que queda en `null` usa lo general). |
| `Plantillador` | Traduce un EJEMPLO de folio ("FOLIO 1 DE 50 [UNO DE CINCUENTA]") a las piezas de `OpcionesFolio`. |
| `Tramo` | Un tramo de hojas de una leyenda de Indexar, con su propio texto y numeración. |
| `OpcionesFolio`, `OpcionesIndexar` | Todas las opciones de cada herramienta. |
| `Config` | Lo que el programa recuerda entre usos. **Solo valores simples** (texto, número, sí/no). |
| `ManualPanel` | El manual dentro del programa; lee el mismo HTML que viene en el portable. |
| `VistaPrevia`, `Controles`, `Ui`, `LetrasInstaladas` | Piezas de pantalla y utilidades. |
| `Comprobar` | Revisión del programa ya armado: abre un comprimido y dice cuántos archivos trae. |
| `EnviarAHerramientaRequest` | Evento (record) para el enlace entre Girar, Indexar y Foliar: lleva el `destino` (TOOL_ID) y la lista de archivos. Ver "Enlace entre Girar, Indexar y Foliar" más abajo. |

### Enlace entre Girar, Indexar y Foliar

Los botones **"Arreglar en Girar"** (en Foliar e Indexar) e **"Ir a Indexar"** / **"Ir a Foliar"** (en Girar)
usan el bus de eventos que ya trae PDFsam, sin tocar nada de la lógica propia de cada herramienta:

- `org.pdfsam.eventstudio.StaticStudio.eventStudio()` — bus estático de pub/sub de PDFsam.
- Cada panel (`GirarPanel`, `IndexarPanel`, `FoliarPanel`) tiene un método `@EventListener public void
  onEnviarAHerramienta(EnviarAHerramientaRequest r)`, registrado al final del constructor con
  `eventStudio().addAnnotatedListeners(this)`.
- Al tocar el botón se manda `eventStudio().broadcast(new EnviarAHerramientaRequest(TOOL_ID_destino,
  listaDeArchivos))` y después `eventStudio().broadcast(new SetActiveContentItemRequest(TOOL_ID_destino))`
  (este último ya existía en PDFsam, cambia la pantalla activa).
- Cada panel, al escuchar el evento, primero limpia su lista actual (sin avisar) y después agrega lo que
  llegó — igual que si el usuario lo hubiera agregado a mano.
- Girar manda sus archivos **ya armados** (con las hojas tal como quedaron: giradas, quitadas, insertadas…)
  a una carpeta temporal, igual que al guardar; los que no se tocaron se copian tal cual.
- **Ojo con los diálogos modales (`Ui.aviso`, `Ui.error`, `Ui.pregunta`) dentro de un `@EventListener` que se
  dispara desde el callback de `Ui.enSegundoPlano` de OTRO panel:** si el diálogo se muestra de forma
  sincrónica ahí mismo (`showAndWait()`), bloquea el hilo de JavaFX **antes** de que el panel que mandó el
  evento termine su propio `terminarEnvio()`/broadcast, y en las pruebas automáticas (sin nadie que cierre el
  diálogo) todo el enlace se queda pegado para siempre sin ningún error visible. La solución: mostrar esos
  avisos con `Platform.runLater(...)`, para que se vean DESPUÉS de que el evento ya terminó de procesarse
  (ver el aviso de "Foliar trabaja un PDF a la vez" en `FoliarPanel.onEnviarAHerramienta`).

### Cómo se registra una herramienta nueva

Hay que tocar **tres** lugares o no aparece:

1. `src/main/resources/META-INF/services/org.pdfsam.model.tool.Tool` (una línea con la clase `…Tool`).
2. `src/main/java/module-info.java` (`provides org.pdfsam.model.tool.Tool with …`).
3. El `pom.xml` padre, si es un módulo nuevo.

### Manual

`pdfsam-tools/pdfsam-foliado/src/main/resources/org/pdfsam/tools/foliado/manual/`
con `Manual-Foliar-Indexar-Girar.html` y la carpeta `img/`.

---

## 5. Cómo se arma el programa (sin Windows)

**Esto es lo más importante para una conversación nueva:** desde el entorno donde se escribe el código **no
hay** Windows, ni Adobe, ni ratón de verdad, **ni acceso a Maven Central** (los servidores de Java están
bloqueados). O sea: **aquí no se puede compilar ni probar nada**. Todo se compila y se prueba en **GitHub
Actions**.

Hay tres flujos en `.github/workflows/`:

| Flujo | Dónde corre | Qué hace | Cuándo |
|---|---|---|---|
| `pruebas.yml` | Linux | compila todo y corre las pruebas de `pdfsam-foliado` | solo con `push` a `main` |
| `portable.yml` | Windows | arma portable + instalador de **PDFsam** | a mano (`workflow_dispatch`) |
| `auditoria.yml` | Windows | arma portable + instalador de **Auditoría PDF** | a mano (`workflow_dispatch`) |

### Trampa de nombres repetidos (¡leer esto!)

Además de las ramas `main` y `auditoria`, el repo tiene **etiquetas (tags)** llamadas `auditoria` y
`portable` (son los anclajes de los releases). Git no distingue bien entre rama y tag con el mismo nombre:

- `git fetch origin auditoria` puede traer el **TAG** en vez de la rama. Usar siempre la ruta completa:
  ```
  git fetch origin refs/heads/auditoria:refs/remotes/origin/auditoria --force
  ```
- `git push origin auditoria` falla con *"src refspec auditoria matches more than one"*. Antes de subir:
  ```
  git tag -d auditoria portable
  git push origin refs/heads/auditoria:refs/heads/auditoria
  ```
  (`git tag -d` borra solo las etiquetas locales, no las del servidor.)

### Pasos completos de una entrega

1. Cambios de código y pruebas en `main` → commit → `git push origin main`.
2. Esperar `pruebas.yml` (se dispara solo con el push). Revisar con `gh run view <id> --log` que diga
   **"BUILD SUCCESS"** y **"Failures: 0, Errors: 0"**. **No seguir si falla**: corregir y repetir.
3. `git fetch origin refs/heads/auditoria:refs/remotes/origin/auditoria --force`
   y `git checkout auditoria` (o `git switch -c auditoria origin/auditoria`).
4. `git cherry-pick <commits de main>`. Si choca el manual o el "Acerca de" (son los únicos archivos
   distintos entre ramas), **quedarse con la versión de `auditoria`** en esos archivos y reaplicar a mano
   solo los cambios de código de ese commit.
5. `git tag -d auditoria portable` y `git push origin refs/heads/auditoria:refs/heads/auditoria`.
6. Lanzar los dos armados finales:
   ```
   gh api -X POST repos/cesar24200924-ui/pdfsam-foliado/actions/workflows/portable.yml/dispatches  -f ref=main
   gh api -X POST repos/cesar24200924-ui/pdfsam-foliado/actions/workflows/auditoria.yml/dispatches -f ref=auditoria
   ```
7. Esperar a que los dos terminen (`gh api repos/.../actions/runs/<id>` → `status`/`conclusion` =
   `completed`/`success`). Tardan bastante más que `pruebas.yml` (jpackage + WiX).
8. Bajar los archivos de los Releases:
   ```
   gh api repos/cesar24200924-ui/pdfsam-foliado/releases/tags/<tag> --jq '.assets[]|.name+" "+.url'
   curl -L -H "Accept: application/octet-stream" -H "Authorization: Bearer $(gh auth token)" <url> -o <archivo>
   ```
   `gh release download` **no funciona** en este tipo de sesión (da error de GraphQL): por eso `gh api` + `curl`.

Si solo pidió el código fuente, no hace falta lanzar `portable.yml`/`auditoria.yml`: basta con bajar el asset
`*-codigo-fuente.zip` del último release, o armarlo con `git archive` sobre la rama que corresponda.

### Nota sobre el acceso a GitHub

En una sesión nueva, el repositorio puede no estar conectado. Hay que pedirlo primero (herramienta
`add_repo` con acceso de escritura) y después clonarlo. Sin eso, `gh` responde 403.

---

## 6. Cómo se entrega

Entrega estándar: **dos ZIP juntos**, uno de PDFsam (rama `main`) y otro de Auditoría PDF (rama
`auditoria`), cada uno con tres carpetas:

```
1 - Portable
2 - Instalador
3 - Código fuente
```

Si solo pide el código fuente, un solo ZIP (o uno por programa) con el código, sin portable ni instalador.
**Este `LEEME.md` siempre va adentro del código fuente.**

---

## 7. Cuidados al programar (lo que se rompe fácil)

1. **Nunca decir "ya quedó" sin ver el CI verde.** Aquí no se compila: el único que sabe si compila es
   GitHub Actions.
2. **La vista previa y el guardado deben usar las MISMAS funciones de `Motor`.** Es la regla de oro del
   diseño: si se separan, lo que se ve deja de ser lo que queda.
3. **Lo predeterminado no se toca.** Todo lo que hoy funciona sin personalizar nada tiene que seguir
   funcionando igual: es justo lo que comprueban las pruebas viejas, que **no se tocan**.
4. **`module-info.java`:** si una clase nueva usa un paquete que el módulo no declaraba, no compila. Es el
   error más común al agregar código.
5. **RAR solo se abre, nunca se crea** (restricción de unRAR). Por eso todo resultado de un comprimido sale
   en **ZIP**.
6. **Carpetas marcadas `.__zip__`** (`Lote.MARCA_ZIP`): es un truco interno para saber que esa carpeta tiene
   que volver a cerrarse como ZIP. No tocarlas a mano. `cerrarZipsInternos` **no** se incluye a sí misma a
   propósito (si no, se borraba antes de tiempo).
7. **OneDrive:** los ZIP se arman en una carpeta temporal de Windows y a la carpeta del usuario solo se copia
   el ZIP ya terminado. Si se arma directo en OneDrive, OneDrive bloquea la carpeta a medio camino.
   Usar `Lote.armarZipDeSalida`, que ya hace esto bien.
8. **Nunca escribir encima de los originales.** Antes de guardar en lote se comprueba que la carpeta de
   salida no contenga ningún archivo original, y se avisa en palabras claras.
9. **Los comprimidos se extraen AL LADO del original** y **no** se borran al terminar (quedan como carpeta
   normal, con "(2)", "(3)"… si ya existía).
10. **Tema claro y tema oscuro:** revisar los colores en los dos. Hay CSS propio en
    `src/main/resources/org/pdfsam/tools/foliado/*.css`.
11. **La palabra "Curiosity" no debe aparecer** en la versión Auditoría PDF (es otro proyecto del usuario).
12. **`Config` solo guarda valores simples.** Para listas de objetos (tramos, favoritos) todavía no hay
    formato: ver "Lo que falta".
13. **La numeración se cuenta entre las hojas que SÍ se procesan**, no entre todas las del PDF. Vale para el
    "1/10" de Indexar y para los tramos.
14. **Actualizar el manual** (y su FAQ) junto con cada función nueva. El manual es un HTML que se ve dentro
    del programa.
15. **Pruebas de pantalla:** corren con `xvfb` en Linux y hacen fotos. Si se agrega algo a una pantalla, hay
    que revisar que no se rompan.
16. **No hay checkstyle**, así que el estilo no rompe el build, pero el código del proyecto va con líneas de
    ~120 caracteres y comentarios en español. Seguir ese estilo.

---

## 8. Decisiones ya tomadas (no volver a preguntar)

- "Hasta el folio 20" → las hojas siguientes quedan **sin** folio.
- Varios PDF → de fábrica **"Solo ZIP"** (igual pregunta cada vez).
- Hojas fuera de todo tramo (Mejora 6) → de fábrica quedan **sin** esa leyenda.
- El contenido entre corchetes `[ ]` del ejemplo de folio (Mejora 5) **no** se usa palabra por palabra: solo
  marca "aquí también en letras". El texto en letras siempre es el mismo prefijo del número + el número en
  letras (lo arma `Motor`).
- El número de inicio **nunca** sale del ejemplo: siempre de la casilla "Empieza en".
- "Desde"/"hasta" de un tramo se cuentan entre las hojas que SÍ se indexan.
- **Girar con varios PDF** (decidido el 23/11/2026):
  - Cada PDF se guarda **por separado**, respetando carpetas y ZIP (como Indexar).
  - El giro masivo de una carpeta o comprimido **se queda** como atajo aparte.
  - Insertar hojas de otro PDF se hace con un **botón propio**, "Insertar hojas aquí".
  - Las miniaturas se cargan **solo del PDF elegido** (no todas a la vez).
- **Enlace entre Girar, Indexar y Foliar** (decidido el 23/11/2026):
  - Foliar e Indexar mandan a Girar **toda su lista/archivo actual** (no solo el que se está viendo).
  - Los tres botones son simétricos: "Arreglar en Girar" está en Foliar Y en Indexar; "Ir a Indexar" e "Ir a
    Foliar" están ambos en Girar.
  - Al mandar archivos a otra pantalla, **se borra lo que ya hubiera ahí, sin avisar** ("se borra sin avisar"
    fue la opción recomendada y la que eligió el usuario).
  - El folio/las leyendas de Foliar e Indexar se vuelven a aplicar **solas, al guardar** (no se guardan ya
    puestas en el PDF que vuelve de Girar).
  - Los botones "Ir a Indexar"/"Ir a Foliar" de Girar están **siempre disponibles**, sin importar si se
    entró a Girar desde el menú o desde otra pantalla.
  - Las tres herramientas **deben seguir funcionando de forma completamente independiente**: el enlace es
    un atajo opcional, nunca una dependencia obligatoria entre ellas.

---

## 9. Historial de mejoras

### Entrega del 2026-11-23 — Enlace entre Girar, Indexar y Foliar

**El pedido:** al trabajar en Indexar o en Foliar y notar una hoja mal girada o en blanco, el usuario tenía
que salir, abrir Girar por separado, guardar y volver a meter los archivos a mano. Pidió botones que
conectaran las tres pantallas sin perder lo que llevaba hecho.

**Diseño acordado con el usuario** (confirmado antes de programar, con un diagrama y después una pregunta de
selección múltiple): ver los puntos en la sección 8, "Enlace entre Girar, Indexar y Foliar".

**Lo que se hizo** (sin tocar la lógica propia de ninguna de las tres herramientas — solo se agregaron
botones, eventos y métodos nuevos):

- `EnviarAHerramientaRequest` (record nuevo): evento con el `destino` (TOOL_ID) y la lista de archivos.
- En `GirarPanel`: botones **"Ir a Indexar"** e **"Ir a Foliar"** (al lado de Guardar), método `enviarA`
  (arma cada PDF en una carpeta temporal, igual que al guardar, y manda la lista) y
  `onEnviarAHerramienta` (recibe desde Foliar/Indexar, limpia su lista y agrega lo que llegó).
- En `IndexarPanel` y en `FoliarPanel`: botón **"Arreglar en Girar"** y `onEnviarAHerramienta` (recibe desde
  Girar). Foliar, como solo trabaja un PDF a la vez, abre el primero de la lista que llega y avisa si venían
  más.
- **El panel de la lista de Girar se movió al lado izquierdo** de la pantalla (pedido explícito del usuario).
- Los tres paneles se registran con `eventStudio().addAnnotatedListeners(this)` al final del constructor.

**El bug que costó encontrar — diálogo modal bloqueando el enlace:** el aviso de Foliar ("trabaja un PDF a la
vez…") se mostraba con `Ui.aviso` (`showAndWait()`) **de forma sincrónica**, dentro del mismo
`onEnviarAHerramienta` que Girar estaba esperando para terminar su propio `broadcast`. Eso bloqueaba el hilo
de JavaFX a medio camino: en las pruebas (sin nadie para cerrar el diálogo) el envío con 2 PDF nunca
terminaba, sin ningún error ni excepción visible — parecía que el evento nunca llegaba. Se encontró agregando
contadores y mensajes de diagnóstico temporales en cada paso de la cadena (se quitaron después de encontrar
la causa). **La corrección:** ese aviso ahora se muestra con `Platform.runLater(...)`, después de que el
envío ya terminó. De paso, se hizo más robusto `Ui.enSegundoPlano`: si cerrar la ventanita de "progreso"
fallara, ya no se pierde el aviso de que el trabajo terminó o falló (antes se habría tragado en silencio,
igual que pasó con este bug).

**Pruebas nuevas:** `PantallasTest.enlaceIndexarConGirar()` (round-trip completo Indexar → Girar → Indexar,
con una hoja quitada en Girar) y `PantallasTest.enlaceGirarDirectoConFoliar()` (Girar con 2 PDF nunca
tocados → Foliar, que solo abre el primero). Las dos corrieron **3 veces seguidas en CI sin fallar** antes
de darse por buenas (el bug era intermitente al principio).

**Manual:** nueva sección 8, "Enlazar Girar con Foliar e Indexar", con nota en los pasos de guardar de
Foliar e Indexar, y 7 preguntas frecuentes nuevas.

### Entrega del 2026-11-23 — Girar con lista de archivos

**El problema:** Indexar trabajaba con una lista de archivos (PDF sueltos, carpetas, comprimidos) y dejaba
ver y arreglar cada PDF uno por uno. Girar no: si se le daba una carpeta o un comprimido, saltaba directo a
una ventanita que giraba todo de un solo golpe sin mostrar nada, y si se le daban varios PDF, **solo cargaba
el primero**. El usuario pidió que Girar funcionara igual que Indexar.

**Lo que se hizo, en `GirarPanel`:**

- **Lista de archivos** (`items`, `ListView<Item>`) en el panel de la derecha, con los botones **Agregar
  PDF**, **Agregar carpeta**, **Agregar ZIP / RAR / 7z** y **Limpiar**. Es el mismo patrón
  `Item(origen, relativa)` de Indexar, así que la ruta de salida de cada PDF ya viene resuelta.
- **Comprimidos** (`abiertos`, `Abierto(archivo, lote, marcada)`): se abren en segundo plano con `Lote`, sus
  PDF entran a la lista y al guardar se vuelve a armar el ZIP. La marca es `…_girado.zip` (en Indexar es
  `…_indexado.zip`).
- **Cambios recordados por archivo** (`ediciones`, clase `Edicion`): al cambiarse de PDF se guarda lo que
  llevaba hecho (hojas, deshacer y rehacer) y al volver se restaura. Si tenía hojas insertadas de otros PDF,
  esos PDF se vuelven a abrir solos.
- **Miniaturas solo del PDF elegido:** al cambiar de archivo se cierran las vistas del anterior y se limpia
  la caché de imágenes, para no llenar la memoria con 8 PDF abiertos.
- **`hayCambios`** ya no depende de tener el PDF abierto: usa `hojasOriginales` (cuántas hojas traía cada
  uno), así se puede saber qué PDF cambió sin abrirlo.
- **Guardado doble:** con **un solo** PDF suelto se sigue guardando como antes (pregunta el nombre). Con
  **varios** (o con cualquier comprimido), el botón pasa a decir **"Girar y guardar (N)"**, pregunta la
  carpeta y guarda cada PDF por separado: los de carpetas vuelven a su misma subcarpeta, los de comprimidos
  se rearman en ZIP y **los que no se tocaron se copian tal cual**.
- **"Insertar PDF" pasó a llamarse "Insertar hojas aquí"** y se quitó la pregunta "¿insertar o abrir?" que
  salía al arrastrar un segundo PDF (ahora se va a la lista).
- **El atajo se queda:** el botón "Girar una carpeta o comprimido" sigue girando todo de un solo golpe.
- **Cintitas de ayuda** en todos los botones nuevos.

**Lo que se tocó fuera de `GirarPanel`:**

- `Lote.armarZipDeSalida(destino, trabajo, lote, marcada)`: se **movió** aquí el armado del ZIP de salida que
  estaba repetido dentro de `IndexarPanel.armarZip` (que ahora solo lo llama). Una sola versión para los dos.

**Pruebas nuevas:** `PantallasTest.girarVariosPdfRecuerdaLoDeCadaUno()` (una carpeta de 3 PDF va a la lista,
se cambia de uno a otro y el cambio se recuerda) y
`NuevasTest.armarZipDeSalidaJuntaLoProcesadoYLoQueNoSeToco()`.

**Manual:** se reescribió el Paso 1 de Girar, el guardado y el atajo, y se agregaron 8 preguntas frecuentes.

### Entrega del 2026-11-14 — Mejoras 1, 2, 5 y 6

- **Mejora 1 (resto) — Comprimidos "Extraer aquí":** `Lote.preparar()` ya no descomprime en una carpeta
  temporal que se borra. Extrae **al lado** del original, con el mismo nombre (o "Nombre (2)"…), y esa
  carpeta **no se borra**. Los comprimidos de adentro se dejan como carpeta marcada `.__zip__` y
  `Lote.limpiar()` los vuelve a cerrar a ZIP dentro de esa carpeta.
- **Mejora 2 — Lista de PDF antes de guardar (Foliar y Girar):** `LoteUi.listaPdf()`, entre la vista previa
  de carpetas y "¿dónde guardo?": cada PDF con una casilla (para excluirlo) y botones Subir/Bajar.
- **Mejora 5 — "El plantillador":** clase `Plantillador`, que convierte un ejemplo de texto en las piezas de
  `OpcionesFolio`. En `FoliarPanel` hay un campo "¿O prefiere escribir un ejemplo?".
- **Mejora 6 — Tramos por leyenda (Indexar):** clase `Tramo` y campo `OpcionesIndexar.tramos`. Si la lista de
  tramos de una leyenda está vacía, esa leyenda funciona como siempre.

Las Mejoras 3 y 4 son de entregas anteriores (ver el historial de git).

---

## 10. Lo que falta

### 10.1 Guardar tramos y favoritos entre usos
`OpcionesIndexar.tramos` y los favoritos del ejemplo de Foliar no se guardan en `Config`: se pierden al
cerrar. `Config` hoy solo guarda valores simples; para listas haría falta un formato propio (una línea por
tramo con separadores, o JSON con una librería chica).

### 10.2 Lista de incluir/excluir y reordenar también en Indexar
La Mejora 2 cubrió Foliar y Girar reusando `LoteUi`. Indexar tiene su propia lista (`Item`/`EditorHojas`),
que ya deja ver y editar uno por uno, pero no tiene el incluir/excluir ni el Subir/Bajar.

### 10.3 Las 3 opciones de resultado en Indexar
Indexar guarda cada PDF suelto directo en la carpeta elegida y solo arma ZIP para lo que vino de un
comprimido. Darle las mismas 3 opciones que tienen Foliar y Girar (Solo ZIP / ZIP y carpeta / Solo carpeta)
requiere que escriba primero a una carpeta temporal, como ya hace `Lote`.

### 10.4 Amigabilidad (de entregas anteriores, sigue en pie)
Botón "Personalizar ✎" con pasos numerados, bienvenida de 3 pasos, "Volver a lo de fábrica", "¿Cómo se
usa?", errores en palabras sencillas, resumen antes de guardar, "Abrir el resultado" / "Abrir la carpeta".

### 10.5 Confirmar el texto editable en Adobe de verdad
Las marcas (`/Type /Annot`, `/Subtype /Widget`, `/FT /Tx`) ya están y las prueba
`NuevasTest.folioEditable()`, pero **falta la confirmación real en Adobe** (Acrobat o Reader), en vista
normal y en "Rellenar y firmar". No se puede probar desde aquí.

### 10.6 Funciones nuevas que el usuario quiere más adelante
Decidido el 2026-11-14: pasar estas herramientas de su otro proyecto a Auditoría PDF (y valorar si también a
PDFsam): **Comprimir** (bajar peso), **PDF a imagen**, **Imagen a PDF**, **Escanear fotos**, **Quitar
links**, **Reparar y Propiedades**, **Recortar márgenes** y **Leer en voz alta (exportar a MP3)**. Además:

- Agregar un **"Dividir por tamaño"** nuevo (más exacto, con botones rápidos de MB y vista previa por
  colores) como herramienta **separada**, sin tocar el que ya trae PDFsam: quedan los dos.
- Agregar **"Comprimir hasta un peso"** ("que quede en 5 MB") como opción nueva dentro de Comprimir, sin
  tocar baja/media/alta.
- Decidió **NO** agregar por ahora nada de OCR, Separar por texto, Comparar ni Firmas.

---

## 11. Pruebas

Están en `pdfsam-tools/pdfsam-foliado/src/test/java/org/pdfsam/tools/foliado/`:

| Archivo | Qué prueba |
|---|---|
| `MotorTest` | el motor: folios, leyendas, giros, tamaños, lotes |
| `NuevasTest` | comprimidos (RAR, 7z, contraseñas), leyendas que se mueven, texto editable, ZIP de salida |
| `PantallasTest` | las pantallas de verdad (JavaFX), con fotos; corre con `xvfb` |
| `PlantilladorTest` | el traductor de ejemplos de folio |

**51 pruebas** en total. Corren solas al hacer `push` a `main`.

### Lo que NO se puede probar desde el entorno de desarrollo

Sin Windows, sin Adobe, sin ratón real y sin Maven Central. Por eso:
- Nada se compila localmente: todo va a GitHub Actions.
- El texto editable en Adobe no está confirmado a mano (ver 10.5).
- El portable y el instalador solo se pueden armar en el runner de Windows.

**Siempre decirle al usuario con honestidad qué quedó sin probar.**

---

## 12. Licencias

- **PDFsam Basic** es de Sober Lemur S.r.l., bajo **GNU AGPL v3**. Estas dos versiones se publican bajo la
  misma licencia (ver `LICENSE`). Sin garantía de ningún tipo.
- "PDFsam" y su logo son marcas de sus dueños. **Auditoría PDF no es el programa oficial** ni está
  respaldada por sus autores: por eso tiene nombre y logo propios.
- La AGPL obliga a **publicar el código fuente** de la versión modificada. Por eso el código va en el
  repositorio y también dentro de cada entrega.
- **7-Zip-JBinding** (motor de 7-Zip, de Igor Pavlov): **LGPL 2.1** con la restricción de unRAR. El código de
  unRAR se usa **solo para abrir** archivos RAR, **nunca** para crearlos. Por eso los resultados salen en ZIP.

---

## 13. Preguntas frecuentes

**¿Por dónde empiezo si es mi primera conversación con este proyecto?**
Por las secciones 2 (cómo trabajar con el usuario), 5 (cómo se arma) y 7 (cuidados). Con eso ya no se cometen
los errores típicos.

**¿Puedo compilar o probar aquí mismo?**
No. No hay Maven Central ni Windows. Todo se compila y se prueba con GitHub Actions (`pruebas.yml`). Lo único
que se puede hacer localmente es revisar la sintaxis con `javac -proc:only` (reporta errores de símbolos que
se pueden ignorar, pero sí detecta errores de escritura).

**¿Dónde pongo una función nueva?**
Si es una herramienta nueva, un archivo `…Tool.java` + su `…Panel.java` en `org.pdfsam.tools.foliado`, y
registrarla en los tres lugares de la sección 4. Si es una opción de una herramienta que ya existe, en su
`Opciones…` y su `Panel`.

**¿Tengo que tocar las dos ramas?**
No a mano: se trabaja en `main` y se pasa con `cherry-pick` a `auditoria`. Solo si el cambio es de nombre,
logo o "Acerca de" se toca `auditoria` aparte.

**¿Por qué el resultado de un RAR sale en ZIP?**
Porque crear RAR no se puede con programas libres (restricción de unRAR). El ZIP trae exactamente las mismas
carpetas y archivos.

**¿Por qué los ZIP se arman en una carpeta temporal y no directo en el destino?**
Por OneDrive: si se arma dentro de una carpeta sincronizada, OneDrive la bloquea a medio camino y el guardado
falla con un error raro que termina en `__zip__`.

**¿Qué pasa si una prueba falla en GitHub?**
No seguir. Corregir y volver a subir. Nunca pasar a `auditoria` ni armar instaladores con el CI en rojo.

**¿Cómo sé qué versión es la última?**
Por los Releases del repositorio: tag `portable` (PDFsam) y tag `auditoria` (Auditoría PDF). Cada uno trae
portable, instalador, código fuente y un ZIP con todo junto.

**¿El usuario quiere que le explique lo técnico?**
Quiere saber **qué** se hizo y **qué cuidados** hay, en español sencillo. Los detalles de clases y métodos
van en este archivo, no en el chat.

**Un evento entre pantallas (`@EventListener`/`eventStudio()`) no llega y no hay ningún error en el log. ¿Qué
reviso primero?**
Busque un diálogo modal (`Ui.aviso`, `Ui.error`, `Ui.pregunta`) que se esté mostrando de forma sincrónica
(`showAndWait()`) **dentro** del método que escucha el evento, sobre todo si ese método a su vez fue
disparado desde el callback de `Ui.enSegundoPlano` de OTRO panel. El diálogo bloquea el hilo de JavaFX antes
de que el que mandó el evento termine, y en las pruebas automáticas (sin nadie que lo cierre) todo se queda
pegado sin ninguna excepción visible — ver la entrega del 23/11/2026, "Enlace entre Girar, Indexar y Foliar".
Para confirmarlo rápido sin adivinar: agregue un contador/campo de diagnóstico de solo-para-pruebas en cada
paso de la cadena (evento recibido → función que procesa → justo antes y después del broadcast) y mire en
cuál de esos pasos se quedó la última vez — no hace falta ver el código de la librería de eventos para
encontrarlo así. La corrección es mostrar esos diálogos con `Platform.runLater(...)` en vez de mostrarlos
directo.
