package com.ebee.importer;

import com.ebee.common.security.SecurityService;
import com.ebee.importer.service.ImportApplier;
import com.ebee.importer.service.ImportRow;
import com.ebee.importer.service.ImportService;
import com.ebee.importer.service.ImportType;
import com.ebee.importer.service.RowError;
import com.ebee.importer.service.RowImporter;
import com.ebee.importer.entity.ImportJob;
import com.ebee.importer.repository.ImportJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportServiceTest {

    private final RowImporter importer = mock(RowImporter.class);
    private final ImportApplier applier = mock(ImportApplier.class);
    private final ImportJobRepository jobs = mock(ImportJobRepository.class);
    private ImportService service;

    @BeforeEach
    void setUp() {
        when(importer.type()).thenReturn(ImportType.SUPPLIERS);
        when(jobs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new ImportService(List.of(importer), applier, jobs, mock(SecurityService.class), new ObjectMapper());
    }

    private static MockMultipartFile file(String csv) {
        return new MockMultipartFile("file", "suppliers.csv", "text/csv", csv.getBytes());
    }

    private static final String GOOD = "name,address_line1,city,colour\nAcme,1 Road,Colombo,blue\n";

    @Test
    void missingRequiredColumn_isInvalid_andNothingIsValidatedOrApplied() throws Exception {
        var r = service.run(ImportType.SUPPLIERS, file("name,city\nAcme,Colombo\n"), false);

        assertThat(r.status()).isEqualTo(ImportJob.Status.INVALID);
        assertThat(r.errors()).extracting(RowError::column).containsExactly("address_line1");
        verify(importer, never()).validate(anyList(), anyList(), anyList());
        verify(applier, never()).apply(any(), anyList());
    }

    @Test
    void dryRun_validatesButNeverApplies_andReportsUnknownColumns() throws Exception {
        when(importer.validate(anyList(), anyList(), anyList())).thenReturn(new RowImporter.Counts(0, 1, 0));

        var r = service.run(ImportType.SUPPLIERS, file(GOOD), true);

        assertThat(r.status()).isEqualTo(ImportJob.Status.VALID);
        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.ignoredColumns()).containsExactly("colour");
        verify(importer).validate(anyList(), anyList(), anyList());
        verify(applier, never()).apply(any(), anyList());
    }

    @Test
    void anyRowError_blocksTheWholeImport() throws Exception {
        doAnswer(inv -> {
            List<RowError> errors = inv.getArgument(1);
            errors.add(new RowError(2, "city", "is required"));
            return new RowImporter.Counts(0, 0, 0);
        }).when(importer).validate(anyList(), anyList(), anyList());

        var r = service.run(ImportType.SUPPLIERS, file(GOOD), false);

        assertThat(r.status()).isEqualTo(ImportJob.Status.INVALID);
        assertThat(r.errorCount()).isEqualTo(1);
        verify(applier, never()).apply(any(), anyList());
    }

    @Test
    void validFile_isAppliedOnce_andCountsReturned() throws Exception {
        when(applier.apply(any(), anyList())).thenReturn(new RowImporter.Counts(1, 0, 0));

        var r = service.run(ImportType.SUPPLIERS, file(GOOD), false);

        assertThat(r.status()).isEqualTo(ImportJob.Status.APPLIED);
        assertThat(r.created()).isEqualTo(1);
    }

    @Test
    void failureWhileApplying_isReportedAsFailed_nothingImported() throws Exception {
        when(applier.apply(any(), anyList())).thenThrow(new IllegalStateException("database down"));

        var r = service.run(ImportType.SUPPLIERS, file(GOOD), false);

        assertThat(r.status()).isEqualTo(ImportJob.Status.FAILED);
        assertThat(r.errors().get(0).message()).contains("Nothing was imported").contains("database down");
        verify(jobs).save(any());
    }

    @Test
    void skippingInvalidRows_appliesTheGoodOnes_andSaysWhichWereLeftOut() throws Exception {
        doAnswer(inv -> {
            List<ImportRow> rows = inv.getArgument(0);
            List<RowError> errors = inv.getArgument(1);
            rows.stream().filter(r -> r.text("city") == null)
                    .forEach(r -> errors.add(new RowError(r.rowNumber(), "city", "is required")));
            return new RowImporter.Counts(rows.size() - errors.size(), 0, 0);
        }).when(importer).validate(anyList(), anyList(), anyList());
        when(applier.apply(any(), anyList())).thenReturn(new RowImporter.Counts(1, 0, 0));
        var csv = file("name,address_line1,city\nAcme,1 Road,Jaffna\nBad,2 Road,\n");

        var check = service.run(ImportType.SUPPLIERS, csv, true, false);
        assertThat(check.status()).isEqualTo(ImportJob.Status.INVALID);
        assertThat(check.invalidRows()).isEqualTo(1);
        assertThat(check.created()).isEqualTo(1);
        assertThat(check.rows()).extracting(r -> r.values().get("name")).containsExactly("Acme", "Bad");

        var applied = service.run(ImportType.SUPPLIERS, csv, false, true);
        assertThat(applied.status()).isEqualTo(ImportJob.Status.APPLIED);
        assertThat(applied.invalidRows()).isEqualTo(1);
        assertThat(applied.errors()).extracting(RowError::row).containsExactly(3);
        verify(applier).apply(any(), org.mockito.ArgumentMatchers.argThat((List<ImportRow> r) -> r.size() == 1));
    }

    @Test
    void skippingCannotRescueAFileWithAMissingColumn() throws Exception {
        var r = service.run(ImportType.SUPPLIERS, file("name,city\nAcme,Jaffna\n"), false, true);

        assertThat(r.status()).isEqualTo(ImportJob.Status.INVALID);
        verify(applier, never()).apply(any(), anyList());
    }
}
