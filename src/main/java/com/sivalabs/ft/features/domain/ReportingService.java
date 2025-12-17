package com.sivalabs.ft.features.domain;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.opencsv.CSVWriter;
import com.sivalabs.ft.features.domain.dtos.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReportingService {
    private static final Logger log = LoggerFactory.getLogger(ReportingService.class);

    private static final String[] CSV_HEADERS = {
        "Product Code",
        "Product Name",
        "Release Code",
        "Release Description",
        "Release Status",
        "Released At",
        "Total Features",
        "Completed Features",
        "In Progress Features",
        "New Features",
        "On Hold Features",
        "Completion Percentage",
        "Timeline Adherence",
        "Risk Level",
        "Blocked Features",
        "Feature Code",
        "Feature Title",
        "Feature Status",
        "Assigned To",
        "Created At"
    };

    public byte[] exportRoadmapToCsv(RoadmapResponseDto roadmapData) {
        log.debug(
                "Exporting roadmap to CSV with {} items",
                roadmapData.roadmapItems().size());

        try (StringWriter stringWriter = new StringWriter();
                CSVWriter csvWriter = new CSVWriter(
                        stringWriter,
                        CSVWriter.DEFAULT_SEPARATOR,
                        CSVWriter.NO_QUOTE_CHARACTER,
                        CSVWriter.NO_ESCAPE_CHARACTER,
                        CSVWriter.RFC4180_LINE_END)) {

            // Write headers
            csvWriter.writeNext(CSV_HEADERS);

            // Write data rows
            for (RoadmapItemDto item : roadmapData.roadmapItems()) {
                writeRoadmapItemToCsv(csvWriter, item);
            }

            return stringWriter.toString().getBytes();

        } catch (IOException e) {
            log.error("Error generating CSV export", e);
            throw new RuntimeException("Failed to generate CSV export", e);
        }
    }

    public byte[] exportRoadmapToPdf(RoadmapResponseDto roadmapData) {
        log.debug(
                "Exporting roadmap to PDF with {} items",
                roadmapData.roadmapItems().size());

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, outputStream);

            document.open();

            // Add title
            addPdfTitle(document, roadmapData);

            // Add summary section
            addPdfSummary(document, roadmapData.summary());

            // Add roadmap items
            addPdfRoadmapItems(document, roadmapData.roadmapItems());

            document.close();

            return outputStream.toByteArray();

        } catch (DocumentException e) {
            log.error("Error generating PDF export", e);
            throw new RuntimeException("Failed to generate PDF export", e);
        }
    }

    public String generateExportFilename(String format) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return String.format("Roadmap_%s.%s", timestamp, format.toLowerCase());
    }

    private void writeRoadmapItemToCsv(CSVWriter csvWriter, RoadmapItemDto item) {
        ReleaseDto release = item.release();
        ProgressMetricsDto progress = item.progressMetrics();
        HealthIndicatorsDto health = item.healthIndicators();

        if (item.features().isEmpty()) {
            // Write release info without features
            String[] row = createCsvRow(release, progress, health, null);
            csvWriter.writeNext(row);
        } else {
            // Write one row per feature
            for (FeatureDto feature : item.features()) {
                String[] row = createCsvRow(release, progress, health, feature);
                csvWriter.writeNext(row);
            }
        }
    }

    private String[] createCsvRow(
            ReleaseDto release, ProgressMetricsDto progress, HealthIndicatorsDto health, FeatureDto feature) {
        List<String> row = new ArrayList<>();

        // Release information
        row.add(release.code().split("-")[0]); // Product code from release code
        row.add(release.productName() != null ? release.productName() : ""); // Product name
        row.add(release.code());
        row.add(release.description() != null ? release.description() : "");
        row.add(release.status().toString());
        row.add(release.releasedAt() != null ? release.releasedAt().toString() : "");

        // Progress metrics
        row.add(String.valueOf(progress.totalFeatures()));
        row.add(String.valueOf(progress.completedFeatures()));
        row.add(String.valueOf(progress.inProgressFeatures()));
        row.add(String.valueOf(progress.newFeatures()));
        row.add(String.valueOf(progress.onHoldFeatures()));
        row.add(String.format("%.2f", progress.completionPercentage()));

        // Health indicators
        row.add(health.timelineAdherence().toString());
        row.add(health.riskLevel().toString());
        row.add(String.valueOf(health.blockedFeatures()));

        // Feature information (if provided)
        if (feature != null) {
            row.add(feature.code());
            row.add(feature.title());
            row.add(feature.status().toString());
            row.add(feature.assignedTo() != null ? feature.assignedTo() : "");
            row.add(feature.createdAt().toString());
        } else {
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add("");
        }

        return row.toArray(new String[0]);
    }

    private void addPdfTitle(Document document, RoadmapResponseDto roadmapData) throws DocumentException {
        Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
        Paragraph title = new Paragraph("Product Roadmap Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        // Add generation timestamp
        Font timestampFont = new Font(Font.FontFamily.HELVETICA, 10, Font.ITALIC);
        Paragraph timestamp = new Paragraph(
                "Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                timestampFont);
        timestamp.setAlignment(Element.ALIGN_RIGHT);
        timestamp.setSpacingAfter(20);
        document.add(timestamp);
    }

    private void addPdfSummary(Document document, RoadmapSummaryDto summary) throws DocumentException {
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
        Paragraph summaryTitle = new Paragraph("Executive Summary", headerFont);
        summaryTitle.setSpacingAfter(10);
        document.add(summaryTitle);

        // Create summary table
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingAfter(20);

        addTableRow(summaryTable, "Total Releases", String.valueOf(summary.totalReleases()));
        addTableRow(summaryTable, "Completed Releases", String.valueOf(summary.completedReleases()));
        addTableRow(summaryTable, "Draft Releases", String.valueOf(summary.draftReleases()));
        addTableRow(summaryTable, "Total Features", String.valueOf(summary.totalFeatures()));
        addTableRow(summaryTable, "Overall Completion", String.format("%.1f%%", summary.overallCompletionPercentage()));

        document.add(summaryTable);
    }

    private void addPdfRoadmapItems(Document document, List<RoadmapItemDto> roadmapItems) throws DocumentException {
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
        Paragraph itemsTitle = new Paragraph("Roadmap Details", headerFont);
        itemsTitle.setSpacingAfter(15);
        document.add(itemsTitle);

        for (RoadmapItemDto item : roadmapItems) {
            addPdfRoadmapItem(document, item);
        }
    }

    private void addPdfRoadmapItem(Document document, RoadmapItemDto item) throws DocumentException {
        ReleaseDto release = item.release();
        ProgressMetricsDto progress = item.progressMetrics();
        HealthIndicatorsDto health = item.healthIndicators();

        // Release header
        Font releaseFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        String productInfo = release.productName() != null ? " (" + release.productName() + ")" : "";
        Paragraph releaseHeader = new Paragraph(
                release.code() + " - " + (release.description() != null ? release.description() : "") + productInfo,
                releaseFont);
        releaseHeader.setSpacingAfter(5);
        document.add(releaseHeader);

        // Release details table
        PdfPTable releaseTable = new PdfPTable(4);
        releaseTable.setWidthPercentage(100);
        releaseTable.setWidths(new float[] {1, 1, 1, 1});

        // Header row
        Font cellHeaderFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD);
        PdfPCell headerCell1 = new PdfPCell(new Phrase("Status", cellHeaderFont));
        PdfPCell headerCell2 = new PdfPCell(new Phrase("Completion", cellHeaderFont));
        PdfPCell headerCell3 = new PdfPCell(new Phrase("Timeline", cellHeaderFont));
        PdfPCell headerCell4 = new PdfPCell(new Phrase("Risk Level", cellHeaderFont));

        headerCell1.setBackgroundColor(BaseColor.LIGHT_GRAY);
        headerCell2.setBackgroundColor(BaseColor.LIGHT_GRAY);
        headerCell3.setBackgroundColor(BaseColor.LIGHT_GRAY);
        headerCell4.setBackgroundColor(BaseColor.LIGHT_GRAY);

        releaseTable.addCell(headerCell1);
        releaseTable.addCell(headerCell2);
        releaseTable.addCell(headerCell3);
        releaseTable.addCell(headerCell4);

        // Data row
        releaseTable.addCell(release.status().toString());
        releaseTable.addCell(String.format(
                "%.1f%% (%d/%d)",
                progress.completionPercentage(), progress.completedFeatures(), progress.totalFeatures()));
        releaseTable.addCell(health.timelineAdherence().toString());
        releaseTable.addCell(health.riskLevel().toString());

        releaseTable.setSpacingAfter(10);
        document.add(releaseTable);

        // Features summary if any features exist
        if (!item.features().isEmpty()) {
            Font featureFont = new Font(Font.FontFamily.HELVETICA, 9);
            Paragraph featuresSummary = new Paragraph(
                    String.format(
                            "Features: %d total (%d completed, %d in progress, %d new, %d on hold)",
                            progress.totalFeatures(),
                            progress.completedFeatures(),
                            progress.inProgressFeatures(),
                            progress.newFeatures(),
                            progress.onHoldFeatures()),
                    featureFont);
            featuresSummary.setSpacingAfter(15);
            document.add(featuresSummary);
        }
    }

    private void addTableRow(PdfPTable table, String label, String value) {
        Font labelFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
        Font valueFont = new Font(Font.FontFamily.HELVETICA, 10);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));

        labelCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setBorder(Rectangle.NO_BORDER);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}
