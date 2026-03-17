package com.sivalabs.ft.features.domain;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sivalabs.ft.features.domain.dtos.ProgressMetrics;
import com.sivalabs.ft.features.domain.dtos.RoadmapItem;
import com.sivalabs.ft.features.domain.dtos.RoadmapResponse;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReportingService {

    private static final List<String> VALID_FORMATS = List.of("csv", "pdf");
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String[] CSV_HEADERS = {
        "Product Code",
        "Release Code",
        "Release Description",
        "Release Status",
        "Released At",
        "Planned Start Date",
        "Planned Release Date",
        "Actual Release Date",
        "Owner",
        "Total Features",
        "Completed Features",
        "In Progress Features",
        "New Features",
        "On Hold Features",
        "Completion Percentage",
        "Timeline Adherence",
        "Risk Level"
    };

    private final RoadmapService roadmapService;

    ReportingService(RoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    public String generateFilename(String format) {
        String timestamp = TIMESTAMP_FMT.format(Instant.now().atZone(ZoneOffset.UTC));
        return "Roadmap_" + timestamp + "." + format.toLowerCase();
    }

    public byte[] export(RoadmapFilter filter, String format) {
        if (format == null || !VALID_FORMATS.contains(format.toLowerCase())) {
            throw new BadRequestException("Invalid format: " + format + ". Allowed values: CSV, PDF");
        }
        RoadmapResponse roadmap = roadmapService.getRoadmap(filter);
        return switch (format.toLowerCase()) {
            case "csv" -> generateCsv(roadmap.roadmapItems());
            case "pdf" -> generatePdf(roadmap.roadmapItems());
            default -> throw new BadRequestException("Unsupported format: " + format);
        };
    }

    private byte[] generateCsv(List<RoadmapItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", CSV_HEADERS)).append("\n");
        for (RoadmapItem item : items) {
            ProgressMetrics m = item.progressMetrics();
            sb.append(csvRow(
                    item.product().code(),
                    item.release().code(),
                    item.release().description(),
                    item.release().status() != null ? item.release().status().name() : "",
                    formatInstant(item.release().releasedAt()),
                    formatInstant(item.release().plannedStartDate()),
                    formatInstant(item.release().plannedReleaseDate()),
                    formatInstant(item.release().actualReleaseDate()),
                    item.release().owner(),
                    String.valueOf(m.totalFeatures()),
                    String.valueOf(m.completedFeatures()),
                    String.valueOf(m.inProgressFeatures()),
                    String.valueOf(m.newFeatures()),
                    String.valueOf(m.onHoldFeatures()),
                    String.format("%.2f", m.completionPercentage()),
                    item.healthIndicators().timelineAdherence() != null
                            ? item.healthIndicators().timelineAdherence().name()
                            : "",
                    item.healthIndicators().riskLevel() != null
                            ? item.healthIndicators().riskLevel().name()
                            : ""));
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String csvRow(String... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(",");
            String v = values[i] != null ? values[i] : "";
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                sb.append("\"").append(v.replace("\"", "\"\"")).append("\"");
            } else {
                sb.append(v);
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "";
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    private byte[] generatePdf(List<RoadmapItem> items) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 6, Color.BLACK);

            PdfPTable table = new PdfPTable(CSV_HEADERS.length);
            table.setWidthPercentage(100);

            for (String header : CSV_HEADERS) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setBackgroundColor(new Color(70, 130, 180));
                cell.setPadding(3);
                table.addCell(cell);
            }

            for (RoadmapItem item : items) {
                ProgressMetrics m = item.progressMetrics();
                String[] row = {
                    item.product().code(),
                    item.release().code(),
                    item.release().description() != null ? item.release().description() : "",
                    item.release().status() != null ? item.release().status().name() : "",
                    formatInstant(item.release().releasedAt()),
                    formatInstant(item.release().plannedStartDate()),
                    formatInstant(item.release().plannedReleaseDate()),
                    formatInstant(item.release().actualReleaseDate()),
                    item.release().owner() != null ? item.release().owner() : "",
                    String.valueOf(m.totalFeatures()),
                    String.valueOf(m.completedFeatures()),
                    String.valueOf(m.inProgressFeatures()),
                    String.valueOf(m.newFeatures()),
                    String.valueOf(m.onHoldFeatures()),
                    String.format("%.2f", m.completionPercentage()),
                    item.healthIndicators().timelineAdherence() != null
                            ? item.healthIndicators().timelineAdherence().name()
                            : "",
                    item.healthIndicators().riskLevel() != null
                            ? item.healthIndicators().riskLevel().name()
                            : ""
                };
                for (String value : row) {
                    PdfPCell cell = new PdfPCell(new Phrase(value, cellFont));
                    cell.setPadding(2);
                    table.addCell(cell);
                }
            }

            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
}
