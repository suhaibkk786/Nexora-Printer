# Features and known limitations

## Implemented (source)
- Bluetooth Classic SPP, USB bulk OUT (printer class, vendor-specific fallback with warning), raw TCP.
- ESC/POS: init, alignment, bold, underline, size, font select, line spacing, feed, partial/full cut, drawer pulse, raster (banded), native QR, native barcodes (Code 128/39, EAN-13/8, UPC-A), code pages, left margin / print width, best-effort density.
- Raster fallback for QR (ZXing), barcodes (ZXing), text (Android text engine, Arabic shaping + bidi).
- Image: gallery pick with EXIF rotation, sub-sampled decode, resize, alignment, threshold and Floyd-Steinberg dithering.
- PDF: PdfRenderer, page selection, scaling, progress, cancel, per-page recycling.
- Room (printers, per-printer settings, jobs), DataStore (default id, mode, last used, auto-connect/reconnect, theme, defaults, connection priority).
- Default printer modes, ask-every-time picker, preserved failed jobs with retry, no silent rerouting.
- Auto-connect, auto-reconnect (3 attempts), ACL/USB-detach detection.
- Android PrintService (saved printers discoverable in system print dialog) and share/open-with integration.

## Known limitations
- **BLE-only printers are not supported** (no Classic SPP). They are labelled as such in the scan list.
- USB devices are identified by vendor/product id; two identical printers cannot be told apart.
- "Print speed" is not exposed: ESC/POS has no standard command for it.
- Density uses ESC 7, which many but not all printers honour; 0 leaves the printer default.
- A raw link cannot report paper-out or print completion. "Success" means bytes were written to the connection.
- Failed-job retry payloads over ~1.5 MB are not stored (SQLite cursor limit); such jobs cannot be retried.
- Retrying a job on a different printer re-sends bytes laid out for the original printer's width.
- Margins use GS L / GS W; some clone printers ignore them.
- PrintService: jobs are rasterised with threshold dithering at the printer's usable width; paper-size and orientation options from the system dialog are not applied.
- Minification is off in v1.0.0.
