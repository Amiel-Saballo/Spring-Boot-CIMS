package com.clinic.inventory.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.clinic.inventory.dto.ReportDtos;
import com.clinic.inventory.dto.ReportDtos.ReceivingHistoryRow;
import com.clinic.inventory.dto.ReportDtos.SupplyIssuanceHistoryRow;
import com.clinic.inventory.enums.ItemCategory;
import com.clinic.inventory.enums.ReportType;
import com.clinic.inventory.enums.TransactionType;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class ReportExportService {
    private static final List<String> MONTHS = List.of("January", "February",
            "March", "April", "May", "June", "July", "August", "September",
            "October", "November", "December");

    // Column layout per week block in the source template:
    // DEL, Pullout/Returns, W#, Dispensed, ENDING INV, Actual Inv, VAR
    private static final int COLS_PER_WEEK = 7;
    // Week 3 has no DEL column in the source template - that cell is left blank
    // in every month tab of the reference workbook.
    private static final int WEEK_WITHOUT_DEL = 3;
    // Week 1's DEL / Pullout/Returns / W1 columns get a red highlight; weeks
    // 2-5 do not.
    private static final int WEEK_WITH_RED_INPUTS = 1;
    private static final int STOCK_HEADER_ROWS = 2;

    private static final byte[] BLUE = rgb(0x00, 0x00, 0xFF);
    private static final byte[] WHITE = rgb(0xFF, 0xFF, 0xFF);
    private static final byte[] PINK = rgb(0xEA, 0xD1, 0xDC);
    private static final byte[] CYAN = rgb(0x00, 0xFF, 0xFF);
    private static final byte[] RED = rgb(0xFF, 0x00, 0x00);

    // Per-week header band colors for the top "WEEK n" row.
    private static final byte[][] WEEK_HEADER_COLORS = { rgb(0xC5, 0xE0, 0xB3), // Week
                                                                                // 1
            rgb(0x00, 0xB0, 0x50), // Week 2 - "green" (Excel standard Green)
            rgb(0xD9, 0xD2, 0xE9), // Week 3 - "light purple"
            rgb(0xFF, 0xD9, 0x66), // Week 4 - "light yellow 1"
            rgb(0x46, 0xBD, 0xC6), // Week 5
    };

    private static byte[] rgb(int r, int g, int b) {
        return new byte[] { (byte) r, (byte) g, (byte) b };
    }

    public byte[] csv(ReportDtos.GeneratedReport report) {
        StringBuilder sb = new StringBuilder();
        List<List<Object>> table = table(report);
        for (List<Object> row : table) {
            sb.append(row.stream().map(this::csvCell)
                    .collect(java.util.stream.Collectors.joining(",")))
                    .append("\r\n");
        }
        return ("\uFEFF" + sb).getBytes(StandardCharsets.UTF_8);
    }

    public byte[] excel(ReportDtos.GeneratedReport report) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet(safeSheetName(report.title()));
            List<List<Object>> table = table(report);
            boolean isStock = report
                    .reportType() == com.clinic.inventory.enums.ReportType.STOCK_BALANCE;
            int headerRows = isStock ? STOCK_HEADER_ROWS : 1;

            XSSFCellStyle plainHeader = withBorder(headerStyle(wb, null, null));
            XSSFCellStyle categoryStyle = withBorder(boldStyle(wb, null, null));

            sheet.setDefaultRowHeightInPoints(14.25f);

            for (int r = 0; r < table.size(); r++) {
                Row row = sheet.createRow(r);
                List<Object> rowData = table.get(r);
                boolean isCategoryRow = isStock && r >= headerRows
                        && isCategoryRow(rowData);
                for (int c = 0; c < rowData.size(); c++) {
                    Cell cell = row.createCell(c);
                    Object value = rowData.get(c);
                    if (value instanceof Number n)
                        cell.setCellValue(n.doubleValue());
                    else
                        cell.setCellValue(
                                value == null ? "" : value.toString());

                    if (isStock && r < headerRows) {
                        cell.setCellStyle(
                                withBorder(stockHeaderStyle(wb, c, r)));
                    } else if (isCategoryRow) {
                        cell.setCellStyle(categoryStyle);
                    } else if (isStock) {
                        cell.setCellStyle(withBorder(stockDataStyle(wb, c)));
                    } else if (r < headerRows) {
                        cell.setCellStyle(plainHeader);
                    }
                }
            }

            if (isStock) {
                // Merge WEEK 1 - WEEK 5 labels across their 7-column block on
                // the top
                // header row, matching the source template (E1:K1, L1:R1,
                // S1:Y1, Z1:AF1, AG1:AM1).
                int col = 4; // column E (0-indexed)
                for (int week = 0; week < 5; week++) {
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, col,
                            col + COLS_PER_WEEK - 1));
                    col += COLS_PER_WEEK;
                }
                // Merge each category divider row across the full row width.
                for (int r = headerRows; r < table.size(); r++) {
                    if (isCategoryRow(table.get(r))) {
                        sheet.addMergedRegion(new CellRangeAddress(r, r, 0,
                                table.get(r).size() - 1));
                    }
                }
            }

            int cols = table.isEmpty() ? 0 : table.getFirst().size();
            for (int c = 0; c < cols; c++) {
                int width = c == 0 ? 33 : (c == 1 ? 15 : 13); // tighter widths,
                                                              // matching the
                                                              // source template
                sheet.setColumnWidth(c, Math.min(60, width) * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate Excel report",
                    e);
        }
    }

    /**
     * Column role within the 41-column Stock Balance layout, by 0-based index.
     */
    private enum StockColumn {
        ITEM, RUNNING_BAL, TOTAL_MONTHLY, BEGINNING_INV, DEL, PULLOUT,
        WEEK_ISSUED, DISPENSED, ENDING_INV, ACTUAL_INV, VAR, SUMMARY_ACTUAL_INV,
        SUMMARY_VAR
    }

    private StockColumn stockColumn(int c) {
        if (c == 0)
            return StockColumn.ITEM;
        if (c == 1)
            return StockColumn.RUNNING_BAL;
        if (c == 2)
            return StockColumn.TOTAL_MONTHLY;
        if (c == 3)
            return StockColumn.BEGINNING_INV;
        if (c == 39)
            return StockColumn.SUMMARY_ACTUAL_INV;
        if (c == 40)
            return StockColumn.SUMMARY_VAR;
        int offset = (c - 4) % COLS_PER_WEEK;
        return switch (offset) {
        case 0 -> StockColumn.DEL;
        case 1 -> StockColumn.PULLOUT;
        case 2 -> StockColumn.WEEK_ISSUED;
        case 3 -> StockColumn.DISPENSED;
        case 4 -> StockColumn.ENDING_INV;
        case 5 -> StockColumn.ACTUAL_INV;
        default -> StockColumn.VAR;
        };
    }

    /**
     * Which week number (1-5) a column index falls under, or 0 if not in a week
     * block.
     */
    private int weekNumber(int c) {
        if (c < 4 || c > 38)
            return 0;
        return ((c - 4) / COLS_PER_WEEK) + 1;
    }

    /**
     * Row 0 = the merged "WEEK n" band; Row 1 = the detailed field sub-headers.
     * Both need different styling for the same column role.
     */
    private XSSFCellStyle stockHeaderStyle(XSSFWorkbook wb, int col, int row) {
        if (row == 0) {
            int week = weekNumber(col);
            if (week > 0)
                return filledStyle(wb, WEEK_HEADER_COLORS[week - 1], null, true,
                        true);
            StockColumn role = stockColumn(col);
            if (role == StockColumn.SUMMARY_ACTUAL_INV)
                return filledStyle(wb, null, BLUE, true, true);
            if (role == StockColumn.SUMMARY_VAR)
                return filledStyle(wb, BLUE, WHITE, true, true);
            return headerStyle(wb, null, null);
        }

        StockColumn role = stockColumn(col);
        int week = weekNumber(col);
        return switch (role) {
        case RUNNING_BAL, TOTAL_MONTHLY, VAR ->
            filledStyle(wb, BLUE, WHITE, true, true);
        case DISPENSED -> filledStyle(wb, PINK, null, true, true);
        case ENDING_INV -> filledStyle(wb, PINK, BLUE, true, true);
        case SUMMARY_ACTUAL_INV -> filledStyle(wb, CYAN, null, false, true);
        case SUMMARY_VAR -> filledStyle(wb, BLUE, null, false, true);
        case DEL, PULLOUT,
                WEEK_ISSUED ->
            week == WEEK_WITH_RED_INPUTS
                    ? filledStyle(wb, RED, null, true, true)
                    : headerStyle(wb, null, null);
        default -> headerStyle(wb, null, null);
        };
    }

    private XSSFCellStyle stockDataStyle(XSSFWorkbook wb, int col) {
        StockColumn role = stockColumn(col);
        int week = weekNumber(col);
        return switch (role) {
        case RUNNING_BAL, TOTAL_MONTHLY, VAR, SUMMARY_VAR ->
            filledStyle(wb, BLUE, WHITE, true, false);
        case DISPENSED -> filledStyle(wb, PINK, null, true, false);
        case ENDING_INV -> filledStyle(wb, PINK, BLUE, true, false);
        case DEL, PULLOUT,
                WEEK_ISSUED ->
            week == WEEK_WITH_RED_INPUTS
                    ? filledStyle(wb, RED, null, false, false)
                    : plainStyle(wb);
        default -> plainStyle(wb);
        };
    }

    private XSSFCellStyle filledStyle(XSSFWorkbook wb, byte[] fill,
            byte[] fontColor, boolean bold, boolean isHeaderRow) {
        XSSFCellStyle style = wb.createCellStyle();
        if (fill != null) {
            style.setFillForegroundColor(new XSSFColor(fill, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        XSSFFont font = wb.createFont();
        font.setBold(bold);
        if (fontColor != null)
            font.setColor(new XSSFColor(fontColor, null));
        style.setFont(font);
        if (isHeaderRow)
            applyHeaderAlignment(style);
        return style;
    }

    private XSSFCellStyle headerStyle(XSSFWorkbook wb, byte[] fill,
            byte[] fontColor) {
        XSSFCellStyle style = wb.createCellStyle();
        if (fill != null) {
            style.setFillForegroundColor(new XSSFColor(fill, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        XSSFFont font = wb.createFont();
        font.setBold(true);
        if (fontColor != null)
            font.setColor(new XSSFColor(fontColor, null));
        style.setFont(font);
        applyHeaderAlignment(style);
        return style;
    }

    private XSSFCellStyle boldStyle(XSSFWorkbook wb, byte[] fill,
            byte[] fontColor) {
        XSSFCellStyle style = wb.createCellStyle();
        if (fill != null) {
            style.setFillForegroundColor(new XSSFColor(fill, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        XSSFFont font = wb.createFont();
        font.setBold(true);
        if (fontColor != null)
            font.setColor(new XSSFColor(fontColor, null));
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle plainStyle(XSSFWorkbook wb) {
        return wb.createCellStyle();
    }

    private void applyHeaderAlignment(XSSFCellStyle style) {
        style.setWrapText(true);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
    }

    /**
     * Bold gridlines around every cell, per the requested "bold lines" look.
     */
    private XSSFCellStyle withBorder(XSSFCellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * The Stock Balance table has 41 columns. Giving every column equal width
     * (the old behaviour) leaves ~18pt per column, which is too narrow for
     * words like "Pullout/Returns" or "RUNNING BAL" - OpenPDF is then forced to
     * break them letter-by-letter. This instead: - gives the item-name and
     * summary columns real width, and the 35 per-week columns a narrow but
     * consistent width, - merges the WEEK 1-5 band with colspan, colored per
     * week, - keeps sub-headers horizontal and word-wrapped (matching the Excel
     * header wrapping), - reuses the same color roles (pink Dispensed/ENDING
     * INV, blue VAR, red week-1 inputs, bold category rows) as the Excel
     * export. Everything (widths, cell styling, cell construction) lives inline
     * here rather than in separate helper methods.
     */
    public byte[] pdf(ReportDtos.GeneratedReport report) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A3.rotate(), 16, 16, 28,
                    28);
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph(report.title(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
            document.add(new Paragraph(report.from() + " to " + report.to(),
                    FontFactory.getFont(FontFactory.HELVETICA, 9)));
            document.add(Chunk.NEWLINE);

            boolean isStock = report
                    .reportType() == com.clinic.inventory.enums.ReportType.STOCK_BALANCE;
            List<List<Object>> table = table(report);

            if (!table.isEmpty()) {
                int colCount = table.getFirst().size();

                java.util.function.Function<byte[], Color> toColor = rgbBytes -> new Color(
                        rgbBytes[0] & 0xFF, rgbBytes[1] & 0xFF,
                        rgbBytes[2] & 0xFF);

                interface CellBuilder {
                    PdfPCell build(String text, float fontSize, int style,
                            Color bg, Color fg, int align);
                }
                CellBuilder cellOf = (text, fontSize, style, bg, fg, align) -> {
                    com.lowagie.text.Font font = FontFactory.getFont(
                            FontFactory.HELVETICA, fontSize, style,
                            fg == null ? Color.BLACK : fg);
                    PdfPCell cell = new PdfPCell(new Phrase(text, font));
                    cell.setPadding(1.5f); // Reduced padding gives text more
                                           // horizontal room
                    cell.setHorizontalAlignment(align);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setBorderWidth(0.5f); // Clean thin borders instead of
                                               // heavy lines
                    if (bg != null)
                        cell.setBackgroundColor(bg);
                    return cell;
                };

                PdfPTable pdfTable = new PdfPTable(colCount);
                pdfTable.setWidthPercentage(100);

                if (isStock) {
                    float[] widths = new float[colCount];
                    for (int c = 0; c < colCount; c++) {
                        widths[c] = switch (c) {
                        case 0 -> 10f; // Item description
                        case 1 -> 4.2f; // RUNNING BAL (Expanded to prevent
                                        // RUNNIN G BAL)
                        case 2 -> 4.5f; // Total Monthly Dispensed
                        case 3 -> 3.8f; // BEGINNING INV
                        case 39, 40 -> 3f; // Summary Actual Inv / VAR
                        default -> 2.5f; // Weekly sub-columns
                        };
                    }
                    pdfTable.setWidths(widths);
                    pdfTable.setHeaderRows(STOCK_HEADER_ROWS);

                    for (int r = 0; r < table.size(); r++) {
                        List<Object> rowData = table.get(r);
                        boolean isCategoryRow = r >= STOCK_HEADER_ROWS
                                && isCategoryRow(rowData);

                        if (isCategoryRow) {
                            PdfPCell cell = cellOf.build(
                                    rowData.get(0).toString(), 7,
                                    com.lowagie.text.Font.BOLD, null, null,
                                    Element.ALIGN_LEFT);
                            cell.setColspan(colCount);
                            cell.setBackgroundColor(
                                    new Color(0xE8, 0xE8, 0xE8));
                            pdfTable.addCell(cell);
                            continue;
                        }

                        int c = 0;
                        while (c < colCount) {
                            if (r == 0 && weekNumber(c) > 0) {
                                int week = weekNumber(c);
                                PdfPCell cell = cellOf.build("WEEK " + week,
                                        7.5f, com.lowagie.text.Font.BOLD,
                                        toColor.apply(
                                                WEEK_HEADER_COLORS[week - 1]),
                                        week == 2 ? Color.WHITE : null,
                                        Element.ALIGN_CENTER);
                                cell.setColspan(COLS_PER_WEEK);
                                pdfTable.addCell(cell);
                                c += COLS_PER_WEEK;
                                continue;
                            }

                            Object value = rowData.get(c);
                            String text = value == null ? "" : value.toString();

                            // Clean line breaks on Row 1 subheaders
                            if (r == 1) {
                                text = formatHeaderLabel(text);
                            }

                            StockColumn role = stockColumn(c);
                            int week = weekNumber(c);
                            boolean isHeader = r < STOCK_HEADER_ROWS;
                            boolean isTopHeader = r == 0;

                            Color bg = null;
                            Color fg = null;
                            boolean bold = isHeader;
                            int align = c == 0 ? Element.ALIGN_LEFT
                                    : Element.ALIGN_CENTER;

                            switch (role) {
                            case RUNNING_BAL, TOTAL_MONTHLY, VAR -> {
                                bg = toColor.apply(BLUE);
                                fg = toColor.apply(WHITE);
                                bold = true;
                            }
                            case DISPENSED -> {
                                bg = toColor.apply(PINK);
                                bold = true;
                            }
                            case ENDING_INV -> {
                                bg = toColor.apply(PINK);
                                fg = toColor.apply(BLUE);
                                bold = true;
                            }
                            case SUMMARY_ACTUAL_INV -> {
                                if (isTopHeader) {
                                    fg = toColor.apply(BLUE);
                                    bold = true;
                                } else if (isHeader) {
                                    bg = toColor.apply(CYAN);
                                }
                            }
                            case SUMMARY_VAR -> {
                                if (isTopHeader) {
                                    bg = toColor.apply(BLUE);
                                    fg = toColor.apply(WHITE);
                                    bold = true;
                                } else if (isHeader) {
                                    bg = toColor.apply(BLUE);
                                } else {
                                    bg = toColor.apply(BLUE);
                                    fg = toColor.apply(WHITE);
                                    bold = true;
                                }
                            }
                            case DEL, PULLOUT, WEEK_ISSUED -> {
                                if (week == WEEK_WITH_RED_INPUTS) {
                                    bg = toColor.apply(RED);
                                    bold = isHeader;
                                }
                            }
                            default -> {
                            }
                            }

                            // Headers use 5.5f font size; data cells use 5.5f
                            // for high readability
                            pdfTable.addCell(cellOf.build(text,
                                    isHeader ? 5.5f : 5.5f,
                                    bold ? com.lowagie.text.Font.BOLD
                                            : com.lowagie.text.Font.NORMAL,
                                    bg, fg, align));
                            c++;
                        }
                    }
                } else {
                    pdfTable.setHeaderRows(1);
                    for (int r = 0; r < table.size(); r++) {
                        for (Object value : table.get(r)) {
                            pdfTable.addCell(cellOf.build(
                                    value == null ? "" : value.toString(),
                                    r == 0 ? 8f : 7f,
                                    r == 0 ? com.lowagie.text.Font.BOLD
                                            : com.lowagie.text.Font.NORMAL,
                                    r == 0 ? new Color(0xE8, 0xE8, 0xE8) : null,
                                    null, Element.ALIGN_LEFT));
                        }
                    }
                }

                document.add(pdfTable);
            }
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Unable to generate PDF report", e);
        }
    }

    /**
     * Utility method to insert precise manual linebreaks into narrow PDF
     * subheaders
     */
    private String formatHeaderLabel(String text) {
        return switch (text) {
        case "RUNNING BAL" -> "RUNNING\nBAL";
        case "Total Monthly Dispensed" -> "Total Monthly\nDispensed";
        case "BEGINNING INV" -> "BEGINNING\nINV";
        case "Pullout/Returns" -> "Pullout/\nReturns";
        case "ENDING INV" -> "ENDING\nINV";
        case "Actual Inv" -> "Actual\nInv";
        default -> text;
        };
    }

    private PdfPTable buildPlainPdfTable(List<List<Object>> table)
            throws DocumentException {
        PdfPTable pdfTable = new PdfPTable(table.getFirst().size());
        pdfTable.setWidthPercentage(100);
        for (int r = 0; r < table.size(); r++) {
            for (Object value : table.get(r)) {
                PdfPCell cell = new PdfPCell(new Phrase(
                        value == null ? "" : value.toString(),
                        FontFactory.getFont(FontFactory.HELVETICA,
                                r == 0 ? 7 : 6,
                                r == 0 ? com.lowagie.text.Font.BOLD
                                        : com.lowagie.text.Font.NORMAL)));
                cell.setPadding(3);
                pdfTable.addCell(cell);
            }
        }
        return pdfTable;
    }

    /**
     * Fill color, font color and bold flag for a single PDF cell — the PDF
     * analog of the XSSFCellStyle helpers.
     */
    private record CellStyle(byte[] fill, byte[] fontColor, boolean bold) {
    }

    private PdfPTable buildStockPdfTable(List<List<Object>> table)
            throws DocumentException {
        int cols = table.getFirst().size();
        PdfPTable pdfTable = new PdfPTable(cols);
        pdfTable.setWidthPercentage(100);

        float[] widths = new float[cols];
        for (int c = 0; c < cols; c++) {
            widths[c] = c == 0 ? 33f : (c == 1 ? 15f : 13f);
        }
        pdfTable.setWidths(widths);

        // Header rows (WEEK n band + field sub-headers), with the WEEK n cells
        // merged over 7 columns.
        for (int r = 0; r < STOCK_HEADER_ROWS; r++) {
            List<Object> rowData = table.get(r);
            int c = 0;
            while (c < rowData.size()) {
                int span = mergedSpan(r, c);
                PdfPCell cell = stockPdfCell(valueOf(rowData.get(c)),
                        stockPdfHeaderStyle(c, r), 7,
                        com.lowagie.text.Font.BOLD, true);
                cell.setColspan(span);
                pdfTable.addCell(cell);
                c += span;
            }
        }

        // Data rows, with category divider rows spanning the full width in
        // bold.
        for (int r = STOCK_HEADER_ROWS; r < table.size(); r++) {
            List<Object> rowData = table.get(r);
            if (isCategoryRow(rowData)) {
                PdfPCell cell = new PdfPCell(new Phrase(valueOf(rowData.get(0)),
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6)));
                cell.setColspan(rowData.size());
                cell.setPadding(3);
                pdfTable.addCell(cell);
            } else {
                for (int c = 0; c < rowData.size(); c++) {
                    PdfPCell cell = stockPdfCell(valueOf(rowData.get(c)),
                            stockPdfDataStyle(c), 6,
                            com.lowagie.text.Font.NORMAL, false);
                    pdfTable.addCell(cell);
                }
            }
        }
        return pdfTable;
    }

    /**
     * Column span for a header cell at (row, col); only row 0's WEEK-n band
     * entry points merge (over 7 cols).
     */
    private int mergedSpan(int row, int col) {
        if (row == 0) {
            int week = weekNumber(col);
            if (week > 0 && (col - 4) % COLS_PER_WEEK == 0) {
                return COLS_PER_WEEK;
            }
        }
        return 1;
    }

    private CellStyle stockPdfHeaderStyle(int col, int row) {
        if (row == 0) {
            int week = weekNumber(col);
            if (week > 0)
                return new CellStyle(WEEK_HEADER_COLORS[week - 1], null, true);
            StockColumn role = stockColumn(col);
            if (role == StockColumn.SUMMARY_ACTUAL_INV)
                return new CellStyle(null, BLUE, true);
            if (role == StockColumn.SUMMARY_VAR)
                return new CellStyle(BLUE, WHITE, true);
            return new CellStyle(null, null, true);
        }

        StockColumn role = stockColumn(col);
        int week = weekNumber(col);
        return switch (role) {
        case RUNNING_BAL, TOTAL_MONTHLY, VAR ->
            new CellStyle(BLUE, WHITE, true);
        case DISPENSED -> new CellStyle(PINK, null, true);
        case ENDING_INV -> new CellStyle(PINK, BLUE, true);
        case SUMMARY_ACTUAL_INV -> new CellStyle(CYAN, null, false);
        case SUMMARY_VAR -> new CellStyle(BLUE, null, false);
        case DEL, PULLOUT, WEEK_ISSUED ->
            week == WEEK_WITH_RED_INPUTS ? new CellStyle(RED, null, true)
                    : new CellStyle(null, null, true);
        default -> new CellStyle(null, null, true);
        };
    }

    private CellStyle stockPdfDataStyle(int col) {
        StockColumn role = stockColumn(col);
        int week = weekNumber(col);
        return switch (role) {
        case RUNNING_BAL, TOTAL_MONTHLY, VAR, SUMMARY_VAR ->
            new CellStyle(BLUE, WHITE, true);
        case DISPENSED -> new CellStyle(PINK, null, true);
        case ENDING_INV -> new CellStyle(PINK, BLUE, true);
        case DEL, PULLOUT, WEEK_ISSUED ->
            week == WEEK_WITH_RED_INPUTS ? new CellStyle(RED, null, false)
                    : new CellStyle(null, null, false);
        default -> new CellStyle(null, null, false);
        };
    }

    private PdfPCell stockPdfCell(String text, CellStyle style, int fontSize,
            int baseFontStyle, boolean centerHeader) {
        Color fontColor = style.fontColor() != null
                ? awtColor(style.fontColor())
                : Color.BLACK;
        com.lowagie.text.Font font = FontFactory.getFont(FontFactory.HELVETICA,
                fontSize,
                style.bold() ? com.lowagie.text.Font.BOLD : baseFontStyle,
                fontColor);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        if (style.fill() != null)
            cell.setBackgroundColor(awtColor(style.fill()));
        cell.setPadding(3);
        cell.setBorderWidth(0.75f);
        if (centerHeader) {
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        }
        return cell;
    }

    private Color awtColor(byte[] rgb) {
        return new Color(rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
    }

    private String valueOf(Object o) {
        return o == null ? "" : o.toString();
    }

    private List<List<Object>> table(ReportDtos.GeneratedReport report) {
        return switch (report.reportType()) {
        case STOCK_BALANCE -> stockTable(report);
        case TRANSACTION_HISTORY -> transactionTable(report);
        case EQUIPMENT_REGISTRY -> equipmentTable(report);
        };
    }

    @SuppressWarnings("unchecked")
    private List<List<Object>> stockTable(ReportDtos.GeneratedReport report) {
        List<List<Object>> rows = new ArrayList<>();

        // Row 0: WEEK 1 - WEEK 5 labels (merged over 7 columns each in the
        // .xlsx output).
        // Column A is intentionally blank - the source template has no header
        // text there.
        List<Object> headerTop = new ArrayList<>(List.of("", "", "", ""));
        for (int week = 1; week <= 5; week++) {
            headerTop.add("WEEK " + week);
            for (int i = 0; i < COLS_PER_WEEK - 1; i++)
                headerTop.add("");
        }
        headerTop.add("Actual Inv");
        headerTop.add("VAR");
        rows.add(headerTop);

        // Row 1: field sub-headers. Week 3 omits DEL, matching the source
        // template.
        List<Object> headerSub = new ArrayList<>(List.of("", "RUNNING BAL",
                "Total Monthly Dispensed", "BEGINNING INV"));
        for (int week = 1; week <= 5; week++) {
            headerSub.add(week == WEEK_WITHOUT_DEL ? "" : "DEL");
            headerSub.add("Pullout/Returns");
            headerSub.add("W" + week);
            headerSub.add("Dispensed");
            headerSub.add("ENDING INV");
            headerSub.add("Actual Inv");
            headerSub.add("VAR");
        }
        headerSub.add("");
        headerSub.add("");
        rows.add(headerSub);

        // Data rows, grouped under a bold category divider row whenever the
        // item
        // category changes, matching the source template's section breaks.
        ItemCategory currentCategory = null;
        for (ReportDtos.StockBalanceRow r : (List<ReportDtos.StockBalanceRow>) report
                .rows()) {
            if (!Objects.equals(currentCategory, r.category())) {
                currentCategory = r.category();
                List<Object> categoryRow = new ArrayList<>(
                        Collections.nCopies(headerSub.size(), ""));
                categoryRow.set(0, categoryLabel(currentCategory));
                rows.add(categoryRow);
            }

            List<Object> row = new ArrayList<>(
                    List.of(r.itemName(), r.runningBalance(),
                            r.totalMonthlyDispensed(), r.beginningInventory()));
            for (ReportDtos.Week w : r.weeks()) {
                if (w.week() == WEEK_WITHOUT_DEL) {
                    row.add(""); // no DEL tracked for week 3 in this template
                } else {
                    row.add(w.delivery());
                }
                row.add(w.pullOutReturn());
                row.add(w.dispensed() - w.pullOutReturn()); // W#: Dispensed -
                                                            // Pullout/Returns
                row.add(w.dispensed());
                row.add(w.endingInventory());
                row.add(w.actualInventory());
                row.add(w.variance());
            }

            if (!r.weeks().isEmpty()) {
                ReportDtos.Week last = r.weeks().getLast();
                row.add(last.actualInventory());
                row.add(last.variance());
            } else {
                row.add("");
                row.add("");
            }
            rows.add(row);
        }
        return rows;
    }

    private String categoryLabel(ItemCategory category) {
        return switch (category) {
        case MEDICINE -> "MEDICINE AND SUPPLIES";
        case SUPPLY -> "MEDICAL SUPPLIES";
        case EQUIPMENT -> "EQUIPMENT";
        };
    }

    private boolean isCategoryRow(List<Object> row) {
        for (int c = 1; c < row.size(); c++) {
            Object v = row.get(c);
            if (v != null && !v.toString().isEmpty())
                return false;
        }
        return row.get(0) != null && !row.get(0).toString().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<List<Object>> transactionTable(
            ReportDtos.GeneratedReport report) {

        if (isMedicineIssuanceReport(report)) {
            return medicineIssuanceTable(report);
        }

        if (isSupplyIssuanceReport(report)) {
            return supplyIssuanceTable(report);
        }

        if (isReceivingReport(report)) {
            return receivingTable(report);
        }

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Date", "Transaction Type", "Reference", "User",
                "Item", "Category", "Activity"));
        for (ReportDtos.TransactionHistoryRow r : (List<ReportDtos.TransactionHistoryRow>) report
                .rows()) {
            List<Object> row = new ArrayList<>();
            row.add(r.date());
            row.add(r.transactionType());
            row.add(r.referenceNumber());
            row.add(r.user());
            row.add(r.itemName());
            row.add(r.itemCategory());
            row.add(r.detail());
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<List<Object>> equipmentTable(
            ReportDtos.GeneratedReport report) {
        List<List<Object>> rows = new ArrayList<>();
        List<Object> header = new ArrayList<>(
                List.of("Asset Tag", "Item"));
                
        header.addAll(MONTHS);
        rows.add(header);
        for (ReportDtos.EquipmentReportRow r : (List<ReportDtos.EquipmentReportRow>) report
                .rows()) {
            List<Object> itemRow = new ArrayList<>(
                    List.of(r.assetTag(), r.itemName()));
            itemRow.addAll(r.monthlyPresence());
            rows.add(itemRow);
            List<Object> remarks = new ArrayList<>(Collections.nCopies(14, ""));
            remarks.set(0, "Remarks");
            remarks.set(1, r.remarks());
            rows.add(remarks);
        }
        return rows;
    }

    private boolean isMedicineIssuanceReport(
            ReportDtos.GeneratedReport report) {
        return report.reportType() == ReportType.TRANSACTION_HISTORY
                && report.transactionType() == TransactionType.ISSUANCE
                && report.itemCategory() == ItemCategory.MEDICINE;
    }

    private boolean isSupplyIssuanceReport(ReportDtos.GeneratedReport report) {
        return report.reportType() == ReportType.TRANSACTION_HISTORY
                && report.transactionType() == TransactionType.ISSUANCE
                && report.itemCategory() == ItemCategory.SUPPLY;
    }

    private boolean isReceivingReport(ReportDtos.GeneratedReport report) {
        return report.reportType() == ReportType.TRANSACTION_HISTORY
                && report.transactionType() == TransactionType.RECEIVING;
    }

    @SuppressWarnings("unchecked")
    private List<List<Object>> medicineIssuanceTable(
            ReportDtos.GeneratedReport report) {
        List<List<Object>> rows = new ArrayList<>();

        rows.add(List.of("Date", "Nurse-On-Duty", "Employee No.",
                "Employee Name", "Department", "Supervisor", "Chief Complaint",
                "Disposition", "Item Issued", "Quantity", "Remarks"));

        for (ReportDtos.MedicineIssuanceHistoryRow r : (List<ReportDtos.MedicineIssuanceHistoryRow>) report
                .rows()) {
            List<Object> row = new ArrayList<>();

            row.add(r.dateIssued());
            row.add(r.nurseOnDuty());
            row.add(r.employeeNumber());
            row.add(r.employeeName());
            row.add(r.department());
            row.add(r.supervisor());
            row.add(r.chiefComplaint());
            row.add(r.disposition());
            row.add(r.dateIssued());
            row.add(r.quantity());
            row.add(r.remarks());

            rows.add(row);
        }

        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<List<Object>> supplyIssuanceTable(
            ReportDtos.GeneratedReport report) {
        List<List<Object>> rows = new ArrayList<>();

        rows.add(List.of("Date", "Nurse-On-Duty", "Employee No.",
                "Employee Name", "Department", "Supervisor", "Chief Complaint",
                "Disposition", "Item Issued", "Quantity", "Remarks"));

        for (SupplyIssuanceHistoryRow r : (List<ReportDtos.SupplyIssuanceHistoryRow>) report
                .rows()) {
            List<Object> row = new ArrayList<>();

            row.add(r.dateIssued());
            row.add(r.nurseOnDuty());
            row.add(r.employeeNumber());
            row.add(r.employeeName());
            row.add(r.department());
            row.add(r.supervisor());
            row.add(r.chiefComplaint());
            row.add(r.disposition());
            row.add(r.itemIssued());
            row.add(r.quantity());
            row.add(r.remarks());

            rows.add(row);
        }

        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<List<Object>> receivingTable(
            ReportDtos.GeneratedReport report) {
        List<List<Object>> rows = new ArrayList<>();

        rows.add(List.of("Date Received", "Item Received", "Quantity",
                "Supplier", "Received By"));

        for (ReceivingHistoryRow r : (List<ReportDtos.ReceivingHistoryRow>) report
                .rows()) {
            List<Object> row = new ArrayList<>();

            row.add(r.dateReceived());
            row.add(r.itemReceived());
            row.add(r.quantity());
            row.add(r.receivedFrom());
            row.add(r.receivedBy());

            rows.add(row);
        }

        return rows;
    }

    private String csvCell(Object value) {
        String s = value == null ? "" : value.toString();
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    private String safeSheetName(String name) {
        String cleaned = name.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return cleaned.substring(0, Math.min(31, cleaned.length()));
    }
}
