package com.planet.importexport.exportapi;

import java.util.ArrayList;
import java.util.List;

import com.planet.importexport.customerrecord.CustomerRecordDocument;
import com.planet.importexport.customerrecord.CustomerRecordRepository;
import com.planet.importexport.exportapi.exception.UnknownExportColumnException;
import com.planet.importexport.exportapi.exception.UnsupportedExportFormatException;
import com.planet.importexport.exportapi.model.ExportFormat;
import com.planet.importexport.exportapi.support.DelimitedTextExportWriter;
import com.planet.importexport.exportapi.support.ExportRowProjector;
import com.planet.importexport.exportapi.support.XlsxExportWriter;
import com.planet.importexport.exportapi.validator.ExportColumnValidator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Orchestrates the export flow (design.md section 4): validate the requested
 * format and columns, project the current version of every stored record, and
 * serialize per the requested format.
 *
 * <p>Column validation (task C1) runs before any storage access, per design.md
 * section 4 step 1 ("on any unrecognized column, return 400 ... without
 * touching storage").
 */
@ApplicationScoped
public class ExportService {

    private final CustomerRecordRepository customerRecordRepository;

    /**
     * @param customerRecordRepository the repository queried for every stored
     *                                 record's current version
     */
    @Inject
    public ExportService(CustomerRecordRepository customerRecordRepository) {
        this.customerRecordRepository = customerRecordRepository;
    }

    /**
     * @param format           the requested output format (raw request value;
     *                         parsed/validated here)
     * @param requestedColumns the requested columns, in the exact order the
     *                         output must honor
     * @return the serialized export content in the requested format
     * @throws UnsupportedExportFormatException if {@code format} is not CSV,
     *                                          TXT, or XLSX (task C5: legacy
     *                                          XLS and any other unknown
     *                                          format are rejected explicitly)
     * @throws UnknownExportColumnException     if any requested column is
     *                                          outside the recognized schema
     *                                          (task C1)
     */
    public byte[] export(String format,
                         List<String> requestedColumns) {
        ExportFormat exportFormat = ExportFormat.fromRequestValue(format);

        ExportColumnValidator.validate(requestedColumns);

        List<CustomerRecordDocument> currentVersions =
                customerRecordRepository.findAllCurrentVersions();

        List<List<String>> rows = new ArrayList<>(currentVersions.size());

        for (CustomerRecordDocument record : currentVersions) {
            rows.add(ExportRowProjector.project(record,
                                                requestedColumns));
        }

        return switch (exportFormat) {
            case CSV -> DelimitedTextExportWriter.writeCsv(requestedColumns,
                                                           rows);

            case TXT -> DelimitedTextExportWriter.writeTxt(requestedColumns,
                                                           rows);

            case XLSX -> XlsxExportWriter.write(requestedColumns,
                                                rows);
        };
    }
}
