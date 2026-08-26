package com.clinic.inventory.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.clinic.inventory.dto.ReportDtos;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
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
        try (Workbook wb = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(safeSheetName(report.title()));
            List<List<Object>> table = table(report);
            CellStyle header = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font font = wb.createFont();
            font.setBold(true);
            header.setFont(font);
            header.setWrapText(true);
            for (int r = 0; r < table.size(); r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < table.get(r).size(); c++) {
                    Cell cell = row.createCell(c);
                    Object value = table.get(r).get(c);
                    if (value instanceof Number n)
                        cell.setCellValue(n.doubleValue());
                    else
                        cell.setCellValue(
                                value == null ? "" : value.toString());
                    if (r == 0)
                        cell.setCellStyle(header);
                }
            }
            int cols = table.isEmpty() ? 0 : table.getFirst().size();
            for (int c = 0; c < cols; c++)
                sheet.setColumnWidth(c, Math.min(60, c == 1 ? 30 : 16) * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate Excel report",
                    e);
        }
    }

    public byte[] pdf(ReportDtos.GeneratedReport report) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 20, 20, 28,
                    28);
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph(report.title(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
            document.add(new Paragraph(report.from() + " to " + report.to(),
                    FontFactory.getFont(FontFactory.HELVETICA, 9)));
            document.add(Chunk.NEWLINE);
            List<List<Object>> table = table(report);
            if (!table.isEmpty()) {
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
                document.add(pdfTable);
            }
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Unable to generate PDF report", e);
        }
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
        List<Object> header = new ArrayList<>(List.of("Item", "Running Bal",
                "Total Monthly Dispensed", "Beginning Inv"));
        for (int week = 1; week <= 5; week++)
            header.addAll(List.of("W" + week + " DEL",
                    "W" + week + " Pullout/Returns", "W" + week + " Issued",
                    "W" + week + " Dispensed", "W" + week + " Ending Inv",
                    "W" + week + " Actual Inv", "W" + week + " VAR"));
        header.addAll(List.of("Actual Inv", "VAR"));
        rows.add(header);
        for (ReportDtos.StockBalanceRow r : (List<ReportDtos.StockBalanceRow>) report
                .rows()) {
            List<Object> row = new ArrayList<>(
                    List.of(r.itemName(), r.runningBalance(),
                            r.totalMonthlyDispensed(), r.beginningInventory()));
            for (ReportDtos.Week w : r.weeks())
                row.addAll(List.of(w.delivery(), 0, w.dispensed(),
                        w.dispensed(), w.endingInventory(), w.actualInventory(),
                        w.variance()));
            ReportDtos.Week last = r.weeks().getLast();
            row.add(last.actualInventory());
            row.add(last.variance());
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<List<Object>> transactionTable(
            ReportDtos.GeneratedReport report) {
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
                List.of("Property Number", "Item"));
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

    private String csvCell(Object value) {
        String s = value == null ? "" : value.toString();
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    private String safeSheetName(String name) {
        String cleaned = name.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return cleaned.substring(0, Math.min(31, cleaned.length()));
    }
}
