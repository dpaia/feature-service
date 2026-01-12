package com.sivalabs.ft.features.domain;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.opencsv.CSVWriter;
import com.sivalabs.ft.features.api.models.RoadmapItem;
import com.sivalabs.ft.features.api.models.RoadmapResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ReportingService {

    private final RoadmapService roadmapService;

    public ReportingService(RoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    public ResponseEntity<ByteArrayResource> exportRoadmap(
            String format,
            String[] productCodes,
            String[] statuses,
            java.time.LocalDate dateFrom,
            java.time.LocalDate dateTo,
            String groupBy,
            String owner)
            throws IOException {

        if (format == null || format.trim().isEmpty()) {
            throw new IllegalArgumentException("Format parameter is mandatory");
        }

        String formatUpper = format.toUpperCase();
        if (!formatUpper.equals("CSV") && !formatUpper.equals("PDF")) {
            throw new IllegalArgumentException("Invalid format. Allowed values are CSV and PDF");
        }

        RoadmapResponse roadmapData =
                roadmapService.getRoadmap(productCodes, statuses, dateFrom, dateTo, groupBy, owner);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String fileName = "Roadmap_" + timestamp;

        switch (formatUpper) {
            case "CSV":
                return generateCsvExport(roadmapData, fileName);
            case "PDF":
                return generatePdfExport(roadmapData, fileName);
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    private ResponseEntity<ByteArrayResource> generateCsvExport(RoadmapResponse roadmapData, String fileName)
            throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);

        try (CSVWriter csvWriter = new CSVWriter(
                outputStreamWriter,
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {
            // CSV Headers
            String[] headers = {
                "Product Code",
                "Product Name",
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
            csvWriter.writeNext(headers);

            // CSV Data
            for (RoadmapItem item : roadmapData.roadmapItems()) {
                String productCode = item.release().product() != null
                        ? item.release().product().code()
                        : "";
                String productName = item.release().product() != null
                        ? item.release().product().name()
                        : "";

                String[] row = {
                    productCode,
                    productName,
                    item.release().code(),
                    item.release().description(),
                    item.release().status(),
                    formatInstantForCSV(item.release().releasedAt()),
                    formatInstantForCSV(item.release().plannedStartDate()),
                    formatInstantForCSV(item.release().plannedReleaseDate()),
                    formatInstantForCSV(item.release().actualReleaseDate()),
                    item.release().owner() != null ? item.release().owner() : "",
                    String.valueOf(item.progressMetrics().totalFeatures()),
                    String.valueOf(item.progressMetrics().completedFeatures()),
                    String.valueOf(item.progressMetrics().inProgressFeatures()),
                    String.valueOf(item.progressMetrics().newFeatures()),
                    String.valueOf(item.progressMetrics().onHoldFeatures()),
                    String.valueOf(item.progressMetrics().completionPercentage()),
                    item.healthIndicators().timelineAdherence() != null
                            ? item.healthIndicators().timelineAdherence()
                            : "",
                    item.healthIndicators().riskLevel() != null
                            ? item.healthIndicators().riskLevel()
                            : ""
                };
                csvWriter.writeNext(row);
            }

            csvWriter.flush();
        }

        byte[] csvBytes = outputStream.toByteArray();
        ByteArrayResource resource = new ByteArrayResource(csvBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(csvBytes.length)
                .body(resource);
    }

    private ResponseEntity<ByteArrayResource> generatePdfExport(RoadmapResponse roadmapData, String fileName)
            throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDocument = new PdfDocument(writer);
        Document document = new Document(pdfDocument);

        // Title
        document.add(new Paragraph("Product Roadmap Report")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(18)
                .setBold());

        document.add(new Paragraph(
                        "Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(12));

        document.add(new Paragraph(" ")); // Spacing

        // Summary section
        if (roadmapData.summary() != null) {
            document.add(new Paragraph("Summary").setFontSize(14).setBold());

            document.add(new Paragraph(String.format(
                    "Total Releases: %d, Completed: %d, Draft: %d, Total Features: %d, Overall Completion: %.2f%%",
                    roadmapData.summary().totalReleases(),
                    roadmapData.summary().completedReleases(),
                    roadmapData.summary().draftReleases(),
                    roadmapData.summary().totalFeatures(),
                    roadmapData.summary().overallCompletionPercentage())));

            document.add(new Paragraph(" ")); // Spacing
        }

        // Main data table
        if (!roadmapData.roadmapItems().isEmpty()) {
            document.add(new Paragraph("Roadmap Details").setFontSize(14).setBold());

            Table table = new Table(new float[] {2, 3, 2, 2, 2, 2, 2, 2});
            table.setWidth(UnitValue.createPercentValue(100));

            // Headers
            table.addHeaderCell(new Cell().add(new Paragraph("Product").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Release").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Status").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Owner").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Total Features").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Completed").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Completion %").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Risk Level").setBold()));

            // Data rows
            for (RoadmapItem item : roadmapData.roadmapItems()) {
                String productCode = item.release().product() != null
                        ? item.release().product().code()
                        : "";

                table.addCell(new Cell().add(new Paragraph(productCode)));
                table.addCell(new Cell().add(new Paragraph(item.release().code())));
                table.addCell(new Cell().add(new Paragraph(item.release().status())));
                table.addCell(new Cell()
                        .add(new Paragraph(
                                item.release().owner() != null ? item.release().owner() : "")));
                table.addCell(new Cell()
                        .add(new Paragraph(String.valueOf(item.progressMetrics().totalFeatures()))));
                table.addCell(new Cell()
                        .add(new Paragraph(String.valueOf(item.progressMetrics().completedFeatures()))));
                table.addCell(new Cell()
                        .add(new Paragraph(
                                String.format("%.2f%%", item.progressMetrics().completionPercentage()))));
                table.addCell(new Cell()
                        .add(new Paragraph(
                                item.healthIndicators().riskLevel() != null
                                        ? item.healthIndicators().riskLevel()
                                        : "")));
            }

            document.add(table);
        }

        document.close();

        byte[] pdfBytes = outputStream.toByteArray();
        ByteArrayResource resource = new ByteArrayResource(pdfBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(resource);
    }

    private String formatInstantForCSV(java.time.Instant instant) {
        if (instant == null) {
            return "";
        }
        return instant.toString();
    }
}
