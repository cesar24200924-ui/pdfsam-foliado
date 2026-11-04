# PDFsam Basic + Foliar e Indexar PDF

Versión modificada de [PDFsam Basic](https://github.com/torakiki/pdfsam) con estas herramientas nuevas:

- **Foliar**: pone el folio (Folio 001, 002…) en cada hoja, con vista previa real, posición en cualquier esquina o libre (arrastrando con el ratón), folio en letras, color y letras de la computadora, personalizar hojas, y foliado de carpetas o ZIP completos con numeración continua.
- **Girar** (reemplaza al de PDFsam): con miniaturas, girar una, varias o todas las hojas, arrastrar para cambiar de lugar, ver en grande, deshacer/rehacer y carpetas o ZIP completos.
- **Indexar PDF**: pone leyendas en cada hoja de muchos PDF a la vez (DPOT arriba a la izquierda, parte del nombre del archivo con 1/10 arriba a la derecha, pie de página, numeración abajo), respetando las subcarpetas.

El código de las herramientas nuevas está en `pdfsam-tools/pdfsam-foliado`. El manual está en `docs/Manual-Foliar-Indexar.html`.

## Programa portable para Windows

En **Releases** está `PDFsam-Foliar-Indexar-portable.zip`: se descomprime y se abre `PDFsam-Foliar-Indexar\pdfsam.exe`, sin instalar nada.
Se arma solo con GitHub Actions (flujo `portable`) cada vez que se crea una etiqueta `v…`.

## Licencia

PDFsam Basic es software libre bajo la licencia GNU Affero General Public License v3 (ver `LICENSE`). Esta versión modificada se publica bajo la misma licencia.
