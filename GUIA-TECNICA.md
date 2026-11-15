# Guía técnica — PDFsam / Auditoría PDF

Este archivo va dentro del código fuente a propósito: se actualiza en cada entrega, para que
cualquier conversación nueva (con Claude o con quien sea) sepa de qué trata el programa, qué se
hizo y qué falta, sin tener que repetir todo desde cero ni preguntarle nada al usuario que ya
esté contestado aquí.

## 0. Quién es el usuario y cómo trabajar con él (leer primero)

- Auditor tributario en la SAR (Honduras), **no es programador**. No usar jerga técnica sin
  explicarla; instrucciones simples, paso a paso.
- Responder/avisar avances **siempre en español** (también los mensajes de "voy a hacer tal
  cosa" mientras se trabaja), informal, puede escribir en mayúsculas.
- Prefiere que se le pregunte antes de hacer algo si hay dudas de alcance (con opciones para
  elegir). Si dice "no hagas nada" o "solo mirá", solo analizar y explicar, no tocar código.
- Cambios **quirúrgicos**: nunca reescribir código que ya funciona sin necesidad.
- Quiere código completo y listo para usar, no fragmentos a medio hacer.
- **No reportarle avances intermedios si pidió juntar varias mejoras**: solo entregar cuando
  TODO lo pedido esté listo y probado (a menos que él pregunte por el estado mientras tanto).
- Entrega estándar: **dos ZIP juntos**, uno de PDFsam (rama `main`) y otro de Auditoría PDF
  (rama `auditoria`), cada uno con 3 carpetas: `1 - Portable`, `2 - Instalador`,
  `3 - Código fuente`. Si solo pide el código fuente, un solo ZIP (o uno por programa) con el
  código, sin portable ni instalador.
- Antes de avisar que algo está listo: revisar que no quede la palabra "Curiosity" en la
  versión Auditoría PDF, revisar que los colores se vean bien en tema claro y oscuro, probar
  todo lo que se pueda (ver sección 6) y decir con honestidad qué no se pudo probar.
- Repositorio de trabajo actual: cuenta de GitHub `cesar24200924-ui` (la anterior,
  `cesar242009-blip`, se quedó sin espacio; está invitada como colaboradora).

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

### 2.1 Comandos exactos (para no repetir los mismos errores)

**Trampa de nombres repetidos:** además de las ramas `main` y `auditoria`, el repo tiene
**etiquetas (tags)** llamadas `auditoria` y `portable` (son los anclajes de los releases). Git
no distingue bien entre rama y tag con el mismo nombre:

- `git fetch origin auditoria` puede traer el TAG en vez de la rama (deja `origin/auditoria`
  apuntando a un commit viejo). Usar siempre la ruta completa:
  `git fetch origin refs/heads/auditoria:refs/remotes/origin/auditoria --force`
- `git push origin auditoria` falla con "src refspec auditoria matches more than one" si hay
  tags locales con ese nombre. Antes de subir: `git tag -d auditoria portable` (borra solo las
  etiquetas locales, no las del servidor) y subir con la ruta completa:
  `git push origin refs/heads/auditoria:refs/heads/auditoria`

**Pasos completos de una entrega nueva:**
1. Hacer los cambios de código/pruebas en `main`, commit, `git push origin main`.
2. Esperar `pruebas.yml` (se dispara solo con el push) y revisar con
   `gh run view <id> --log` que diga "BUILD SUCCESS" y "Failures: 0, Errors: 0". No seguir si
   falla — corregir y repetir este paso.
3. `git fetch origin refs/heads/auditoria:refs/remotes/origin/auditoria --force`, luego
   `git checkout auditoria` (o `git switch -c auditoria origin/auditoria` si no existe local).
4. `git cherry-pick <commits de main>` uno por uno o en rango. Si choca el manual o la pantalla
   "Acerca de" (son los únicos archivos distintos entre ramas), quedarse con la versión de
   `auditoria` en esos archivos y reaplicar a mano solo los cambios de código de ese commit.
5. `git tag -d auditoria portable` (si existen localmente) y
   `git push origin refs/heads/auditoria:refs/heads/auditoria`.
6. Lanzar los builds finales:
   `gh api -X POST repos/cesar24200924-ui/pdfsam-foliado/actions/workflows/portable.yml/dispatches -f ref=main`
   `gh api -X POST repos/cesar24200924-ui/pdfsam-foliado/actions/workflows/auditoria.yml/dispatches -f ref=auditoria`
7. Esperar a que los dos terminen (`gh api repos/.../actions/runs/<id>` → `status`/`conclusion`
   = `completed`/`success`). Tardan más que `pruebas.yml` (jpackage + WiX).
8. Los archivos quedan en los Releases de GitHub con esos tags:
   - Tag **`portable`** (de `main`, programa "PDFsam"): assets
     `PDFsam-Foliar-Indexar-instalador.exe`, `PDFsam-Foliar-Indexar-portable.zip`,
     `PDFsam-Foliar-Indexar-codigo-fuente.zip`, `PDFsam-Foliar-Indexar-TODO.zip` (los tres
     anteriores juntos, ya organizados en `1 - Portable/`, `2 - Instalador/`,
     `3 - Codigo fuente/`).
   - Tag **`auditoria`** (de `auditoria`, programa "Auditoría PDF"): mismos 4 assets con el
     nombre `Auditoria-PDF-...`.
   Para bajarlos: pedir la URL del asset con
   `gh api repos/cesar24200924-ui/pdfsam-foliado/releases/tags/<tag> --jq '.assets[]|.name+" "+.url'`
   y descargar cada uno con
   `curl -L -H "Accept: application/octet-stream" -H "Authorization: Bearer $(gh auth token)" <url> -o <archivo>`
   (`gh release download` no funciona en este tipo de sesión — da error de GraphQL — por eso se
   usa `gh api` + `curl` a la URL del asset).
9. Si el usuario solo pidió el código fuente (sin portable ni instalador), no hace falta
   lanzar `portable.yml`/`auditoria.yml`: basta con bajar el asset `*-codigo-fuente.zip` ya
   armado del último release, o armar uno nuevo a mano con `git archive` sobre la rama que
   corresponda si hubo cambios después del último build.

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

### 4.6 Funciones nuevas pendientes (futuras, el usuario pidió guardarlas y avisa cuándo hacerlas)
Decidido el 2026-11-14: pasar estas herramientas de su otro proyecto "Curiosity PDF" a
Auditoría PDF (y valorar si también a PDFsam): **Comprimir** (bajar peso del PDF), **PDF a
imagen**, **Imagen a PDF**, **Escanear fotos**, **Quitar links**, **Reparar y Propiedades**,
**Recortar márgenes** y **Leer en voz alta (exportar a MP3)**. Además:
- Agregar "Dividir por tamaño" de Curiosity (más exacto, con botones rápidos de MB y vista
  previa por colores) como herramienta **nueva y separada**, sin tocar ni reemplazar el
  "Dividir por tamaño" que ya trae PDFsam de fábrica — quedan los dos, cada uno en su pantalla.
- Agregar "Comprimir hasta un peso" (ej. "que quede en 5 MB") como opción nueva dentro de
  Comprimir, sin tocar las opciones baja/media/alta que ya existen.
- Decidió **NO** agregar por ahora nada de "trabajo fiscal" (OCR, Separar por texto, Comparar,
  Firmas) — queda fuera de esta versión.

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
