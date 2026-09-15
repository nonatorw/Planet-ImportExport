package com.planet.importexport.exportapi;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.planet.importexport.customerrecord.CustomerRecordDocument;
import com.planet.importexport.customerrecord.CustomerRecordRepository;
import com.planet.importexport.exportapi.exception.UnknownExportColumnException;
import com.planet.importexport.exportapi.model.ExportFormat;
import com.planet.importexport.exportapi.support.DelimitedTextExportWriter;
import com.planet.importexport.exportapi.support.ExportRowProjector;
import com.planet.importexport.exportapi.support.XlsxExportWriter;
import com.planet.importexport.exportapi.validator.ExportColumnValidator;

/**
 * Orchestrates the export flow (design.md section 4): validate the requested
 * format and columns, project the current version of every stored record, and
 * serialize per the requested format.
 *
 * <p>Column validation (task C1) runs before any storage access, per design.md
 * section 4 step 1 ("on any unrecognized column, return 400 ... without
 * touching storage").</p>
 */
@ApplicationScoped
public class ExportService {

    private final CustomerRecordRepository customerRecordRepository;

    /**
     * Creates a new export service.
     *
     * @param customerRecordRepository the repository queried for every stored
     *                                 record's current version
     */
    @Inject
    public ExportService(CustomerRecordRepository customerRecordRepository) {
        this.customerRecordRepository = customerRecordRepository;
    }

    /**
     * Validates the requested columns, projects the current version of every
     * stored customer record, and serializes the resulting rows in the
     * requested format.
     *
     * @param format           the requested output format, already parsed and
     *                         validated by the caller
     * @param requestedColumns the requested columns, in the exact order the
     *                         output must honor
     *
     * @return the serialized export content in the requested format
     *
     * @throws UnknownExportColumnException if any requested column is outside
     *                                      the recognized schema (task C1)
     */
    public byte[] exportColumns(ExportFormat format,
                                List<String> requestedColumns) {
        ExportColumnValidator.validate(requestedColumns);

        List<CustomerRecordDocument> currentVersions =
                customerRecordRepository.findAllCurrentVersions();

        List<List<String>> rows =
                currentVersions.stream()
                               .map(record -> ExportRowProjector.project(record,
                                                                         requestedColumns))
                               .toList();

        return switch (format) {
            case CSV -> DelimitedTextExportWriter.writeCsv(requestedColumns,
                                                           rows);

            case TXT -> DelimitedTextExportWriter.writeTxt(requestedColumns,
                                                           rows);

            case XLSX -> XlsxExportWriter.write(requestedColumns,
                                                rows);
        };
    }
}
