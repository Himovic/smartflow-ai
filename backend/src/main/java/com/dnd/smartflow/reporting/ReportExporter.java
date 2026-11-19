package com.dnd.smartflow.reporting;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Tabular exports to Excel (XLSX) and printable PDF, branded SmartFlow AI + QHSE. */
@Component
public class ReportExporter {
    private static final Color NAVY = new Color(11, 31, 58);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public record Column(String key, String label) {
    }

    public ResponseEntity<byte[]> export(String format, String title, List<Column> columns, List<Map<String, Object>> rows) {
        boolean pdf = "pdf".equalsIgnoreCase(format);
        byte[] data = pdf ? pdf(title, columns, rows) : xlsx(title, columns, rows);
        String file = title.replaceAll("[^A-Za-z0-9]+", "_") + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                + (pdf ? ".pdf" : ".xlsx");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file).build().toString())
                .contentType(pdf ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    public byte[] xlsx(String title, List<Column> columns, List<Map<String, Object>> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(title.length() > 30 ? title.substring(0, 30) : title);
            CellStyle header = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font font = wb.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(font);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("SmartFlow AI + QHSE — " + title + " (" + LocalDateTime.now().format(TS) + ")");
            org.apache.poi.ss.usermodel.Row h = sheet.createRow(2);
            for (int i = 0; i < columns.size(); i++) {
                org.apache.poi.ss.usermodel.Cell c = h.createCell(i);
                c.setCellValue(columns.get(i).label());
                c.setCellStyle(header);
            }
            int r = 3;
            for (Map<String, Object> row : rows) {
                org.apache.poi.ss.usermodel.Row x = sheet.createRow(r++);
                for (int i = 0; i < columns.size(); i++) {
                    Object v = row.get(columns.get(i).key());
                    org.apache.poi.ss.usermodel.Cell c = x.createCell(i);
                    if (v instanceof Number n) c.setCellValue(n.doubleValue());
                    else if (v instanceof Boolean b) c.setCellValue(b);
                    else c.setCellValue(v == null ? "" : String.valueOf(v));
                }
            }
            for (int i = 0; i < columns.size(); i++) sheet.setColumnWidth(i, 22 * 256);
            sheet.createFreezePane(0, 3);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Excel export failed", e);
        }
    }

    public byte[] pdf(String title, List<Column> columns, List<Map<String, Object>> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(columns.size() > 6 ? PageSize.A4.rotate() : PageSize.A4, 24, 24, 28, 28);
            PdfWriter.getInstance(doc, out);
            doc.open();
            writeHeader(doc, title);
            PdfPTable table = new PdfPTable(columns.size());
            table.setWidthPercentage(100);
            Font hf = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
            Font cf = new Font(Font.HELVETICA, 7.5f);
            for (Column c : columns) {
                PdfPCell cell = new PdfPCell(new Phrase(c.label(), hf));
                cell.setBackgroundColor(NAVY);
                cell.setPadding(4);
                table.addCell(cell);
            }
            table.setHeaderRows(1);
            boolean alt = false;
            for (Map<String, Object> row : rows) {
                for (Column c : columns) {
                    Object v = row.get(c.key());
                    PdfPCell cell = new PdfPCell(new Phrase(v == null ? "" : String.valueOf(v), cf));
                    cell.setPadding(3);
                    if (alt) cell.setBackgroundColor(new Color(242, 246, 250));
                    table.addCell(cell);
                }
                alt = !alt;
            }
            doc.add(table);
            doc.add(new Paragraph(rows.size() + " record(s)", new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY)));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF export failed", e);
        }
    }

    /** Free-form PDF report made of titled sections of key/value pairs and tables. */
    public byte[] sectionsPdf(String title, List<Section> sections) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 32, 32, 32, 32);
            PdfWriter.getInstance(doc, out);
            doc.open();
            writeHeader(doc, title);
            Font sf = new Font(Font.HELVETICA, 11, Font.BOLD, NAVY);
            Font kf = new Font(Font.HELVETICA, 8.5f, Font.BOLD);
            Font vf = new Font(Font.HELVETICA, 8.5f);
            for (Section s : sections) {
                Paragraph p = new Paragraph(s.title(), sf);
                p.setSpacingBefore(10);
                p.setSpacingAfter(4);
                doc.add(p);
                if (s.fields() != null && !s.fields().isEmpty()) {
                    PdfPTable t = new PdfPTable(new float[]{1, 2.4f});
                    t.setWidthPercentage(100);
                    s.fields().forEach((k, v) -> {
                        PdfPCell kc = new PdfPCell(new Phrase(k, kf));
                        kc.setBackgroundColor(new Color(236, 242, 248));
                        kc.setPadding(3);
                        t.addCell(kc);
                        PdfPCell vc = new PdfPCell(new Phrase(v == null ? "—" : String.valueOf(v), vf));
                        vc.setPadding(3);
                        t.addCell(vc);
                    });
                    doc.add(t);
                }
                if (s.columns() != null && !s.columns().isEmpty()) {
                    PdfPTable t = new PdfPTable(s.columns().size());
                    t.setWidthPercentage(100);
                    Font hf = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
                    for (Column c : s.columns()) {
                        PdfPCell cell = new PdfPCell(new Phrase(c.label(), hf));
                        cell.setBackgroundColor(NAVY);
                        cell.setPadding(3);
                        t.addCell(cell);
                    }
                    for (Map<String, Object> row : s.rows()) {
                        for (Column c : s.columns()) {
                            Object v = row.get(c.key());
                            PdfPCell cell = new PdfPCell(new Phrase(v == null ? "" : String.valueOf(v), vf));
                            cell.setPadding(3);
                            t.addCell(cell);
                        }
                    }
                    if (s.rows().isEmpty()) {
                        PdfPCell cell = new PdfPCell(new Phrase("No records", vf));
                        cell.setColspan(s.columns().size());
                        t.addCell(cell);
                    }
                    doc.add(t);
                }
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF report failed", e);
        }
    }

    public record Section(String title, Map<String, Object> fields, List<Column> columns, List<Map<String, Object>> rows) {
        public static Section fields(String title, Map<String, Object> fields) {
            return new Section(title, fields, List.of(), List.of());
        }

        public static Section table(String title, List<Column> columns, List<Map<String, Object>> rows) {
            return new Section(title, Map.of(), columns, rows);
        }
    }

    private void writeHeader(Document doc, String title) throws DocumentException {
        Font brand = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(0, 168, 204));
        doc.add(new Paragraph("SmartFlow AI + QHSE  ·  D&D | Data&Development", brand));
        Font tf = new Font(Font.HELVETICA, 15, Font.BOLD, NAVY);
        Paragraph p = new Paragraph(title, tf);
        p.setSpacingAfter(2);
        doc.add(p);
        doc.add(new Paragraph("Generated " + LocalDateTime.now().format(TS), new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY)));
        doc.add(new Paragraph(" "));
    }
}
