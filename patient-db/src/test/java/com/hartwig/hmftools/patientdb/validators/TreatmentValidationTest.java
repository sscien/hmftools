package com.hartwig.hmftools.patientdb.validators;

import static com.hartwig.hmftools.patientdb.data.TestDatamodelFactory.biopsyTreatmentBuilder;
import static com.hartwig.hmftools.patientdb.data.TestDatamodelFactory.drugBuilder;
import static com.hartwig.hmftools.patientdb.readers.BiopsyTreatmentReader.FIELD_DRUG;
import static com.hartwig.hmftools.patientdb.readers.BiopsyTreatmentReader.FIELD_DRUG_END;
import static com.hartwig.hmftools.patientdb.readers.BiopsyTreatmentReader.FIELD_DRUG_OTHER;
import static com.hartwig.hmftools.patientdb.readers.BiopsyTreatmentReader.FIELD_DRUG_START;
import static com.hartwig.hmftools.patientdb.readers.BiopsyTreatmentReader.FIELD_TREATMENT_GIVEN;
import static com.hartwig.hmftools.patientdb.readers.BiopsyTreatmentReader.FORM_TREATMENT;
import static com.hartwig.hmftools.patientdb.readers.CpctPatientReader.FIELD_DEATH_DATE;
import static com.hartwig.hmftools.patientdb.validators.PatientValidator.fields;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.ecrf.datamodel.ValidationFinding;
import com.hartwig.hmftools.common.ecrf.formstatus.FormStatusState;
import com.hartwig.hmftools.patientdb.data.BiopsyTreatmentData;
import com.hartwig.hmftools.patientdb.data.CuratedTreatment;
import com.hartwig.hmftools.patientdb.data.DrugData;
import com.hartwig.hmftools.patientdb.data.ImmutableBiopsyTreatmentData;
import com.hartwig.hmftools.patientdb.data.ImmutableCuratedTreatment;
import com.hartwig.hmftools.patientdb.data.ImmutableDrugData;
import com.hartwig.hmftools.patientdb.data.ImmutablePatientData;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

public class TreatmentValidationTest {
    private final String CPCT_ID = "CPCT01020000";
    private final static LocalDate JAN2015 = LocalDate.parse("2015-01-01");
    private final static LocalDate FEB2015 = LocalDate.parse("2015-02-01");
    private final static LocalDate MAR2015 = LocalDate.parse("2015-03-01");

    private final static DrugData DRUG_NULL = create(null, null, null);
    private final static DrugData DRUG_WRONG = create(null, FEB2015, JAN2015);
    private final static DrugData DRUG_JAN_JAN = create("Drug1", JAN2015, JAN2015);
    private final static DrugData DRUG_JAN_ONGOING = create("Drug1", JAN2015, null);
    private final static DrugData DRUG_JAN_FEB = create("Drug1", JAN2015, FEB2015);
    private final static DrugData DRUG_FEB_ONGOING = create("Drug1", FEB2015, null);
    private final static DrugData DRUG_JAN_MAR = create("Drug1", JAN2015, MAR2015);

    private final static DrugData DRUG_WITH_PARTIAL_CURATED_ENTRY = ImmutableDrugData.of("Drug1 Drug2 Drug3", JAN2015, JAN2015, null,
            Lists.newArrayList(ImmutableCuratedTreatment.of("Drug1", "Type1", "Drug1")));
    private final static DrugData DRUG_MISSING_CURATED_ENTRY = ImmutableDrugData.of("Drug1", JAN2015, JAN2015, null, Lists.newArrayList());

    private final static BiopsyTreatmentData TREATMENT_GIVEN_NULL = biopsyTreatmentBuilder().treatmentGiven(null).build();
    private final static BiopsyTreatmentData TREATMENT_GIVEN_EMPTY = biopsyTreatmentBuilder().build();
    private final static BiopsyTreatmentData TREATMENT_NOT_GIVEN_DATA =
            biopsyTreatmentBuilder().treatmentGiven("No").addDrugs(DRUG_JAN_FEB).build();
    private final static BiopsyTreatmentData TREATMENT_GIVEN_GIBBERISH = biopsyTreatmentBuilder().treatmentGiven("mmm").build();
    private final static BiopsyTreatmentData TREATMENT_WRONG_DRUG_DATA = biopsyTreatmentBuilder().addDrugs(DRUG_NULL, DRUG_WRONG).build();

    private final static BiopsyTreatmentData TREATMENT_JAN_JAN = biopsyTreatmentBuilder().addDrugs(DRUG_JAN_JAN).build();
    private final static BiopsyTreatmentData TREATMENT_JAN_FEB = biopsyTreatmentBuilder().addDrugs(DRUG_JAN_FEB).build();
    private final static BiopsyTreatmentData TREATMENT_JAN_MAR = biopsyTreatmentBuilder().addDrugs(DRUG_JAN_MAR).build();
    private final static BiopsyTreatmentData TREATMENT_JAN_ONGOING = biopsyTreatmentBuilder().addDrugs(DRUG_JAN_ONGOING).build();
    private final static BiopsyTreatmentData TREATMENT_FEB_ONGOING = biopsyTreatmentBuilder().addDrugs(DRUG_FEB_ONGOING).build();

    @Test
    public void reportsMissingDrugData() {
        final List<ValidationFinding> findings = PatientValidator.validateDrugData(CPCT_ID, DRUG_NULL, FormStatusState.SAVED, true);
        assertEquals(2, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::ecrfItem).collect(Collectors.toList());
        assertTrue(findingsFields.contains(FIELD_DRUG_START));
        assertTrue(findingsFields.contains(fields(FIELD_DRUG, FIELD_DRUG_OTHER)));
    }

    @Test
    public void reportsIncorrectDrugData() {
        final List<ValidationFinding> findings = PatientValidator.validateDrugData(CPCT_ID, DRUG_WRONG, FormStatusState.SAVED, true);
        assertEquals(2, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::ecrfItem).collect(Collectors.toList());
        assertTrue(findingsFields.contains(fields(FIELD_DRUG_START, FIELD_DRUG_END)));
        assertTrue(findingsFields.contains(fields(FIELD_DRUG, FIELD_DRUG_OTHER)));
    }

    @Test
    public void reportsMissingTreatmentGiven() {
        final List<ValidationFinding> findings = PatientValidator.validateTreatmentData(CPCT_ID, TREATMENT_GIVEN_NULL);
        assertEquals(1, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::ecrfItem).collect(Collectors.toList());
        assertTrue(findingsFields.contains(FIELD_TREATMENT_GIVEN));
    }

    @Test
    public void reportsMissingTreatmentData() {
        final List<ValidationFinding> findings = PatientValidator.validateTreatmentData(CPCT_ID, TREATMENT_GIVEN_EMPTY);
        assertEquals(1, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::ecrfItem).collect(Collectors.toList());
        assertTrue(findingsFields.contains(FORM_TREATMENT));
    }

    @Test
    public void reportsTreatmentGivenNoWithData() {
        final List<ValidationFinding> findings = PatientValidator.validateTreatmentData(CPCT_ID, TREATMENT_NOT_GIVEN_DATA);
        assertEquals(1, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::ecrfItem).collect(Collectors.toList());
        assertTrue(findingsFields.contains(FIELD_TREATMENT_GIVEN));
    }

    @Test
    public void reportsTreatmentGivenGibberish() {
        final List<ValidationFinding> findings = PatientValidator.validateTreatmentData(CPCT_ID, TREATMENT_GIVEN_GIBBERISH);
        assertEquals(1, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::ecrfItem).collect(Collectors.toList());
        assertTrue(findingsFields.contains(FIELD_TREATMENT_GIVEN));
    }

    @Test
    public void reportsDrugFindingsForTreatment() {
        final List<ValidationFinding> findings = PatientValidator.validateTreatmentData(CPCT_ID, TREATMENT_WRONG_DRUG_DATA);
        assertEquals(4, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::ecrfItem).collect(Collectors.toList());
        assertTrue(findingsFields.contains(FIELD_DRUG_START));
        assertTrue(findingsFields.contains(fields(FIELD_DRUG, FIELD_DRUG_OTHER)));
    }

    @Test
    public void reportsWrongTreatmentTimeline() {
        final List<ValidationFinding> findings =
                PatientValidator.validateTreatments(CPCT_ID, Lists.newArrayList(TREATMENT_JAN_MAR, TREATMENT_JAN_JAN, TREATMENT_JAN_FEB));
        assertEquals(1, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::ecrfItem).collect(Collectors.toList());
        assertTrue(findingsFields.contains(FORM_TREATMENT));
    }

    @Test
    public void reportsWrongTreatmentTimelineOngoing() {
        final List<ValidationFinding> findings =
                PatientValidator.validateTreatments(CPCT_ID, Lists.newArrayList(TREATMENT_JAN_FEB, TREATMENT_JAN_ONGOING));
        assertEquals(1, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::ecrfItem).collect(Collectors.toList());
        assertTrue(findingsFields.contains(FORM_TREATMENT));
    }

    @Test
    public void reportsTwoOngoingTreatments() {
        final List<ValidationFinding> findings =
                PatientValidator.validateTreatments(CPCT_ID, Lists.newArrayList(TREATMENT_JAN_ONGOING, TREATMENT_FEB_ONGOING));
        assertEquals(3, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::ecrfItem).collect(Collectors.toList());
        assertTrue(findingsFields.contains(FORM_TREATMENT));
    }

    @Test
    public void reportsMissingCuratedTreatment() {
        String curationName = "testTreatmentCuration";
        final List<ValidationFinding> findings = PatientValidator.validateTreatmentCuration(CPCT_ID,
                curationName,
                "",
                Lists.newArrayList(ImmutableBiopsyTreatmentData.of("Yes", "Yes",
                        Lists.newArrayList(DRUG_MISSING_CURATED_ENTRY),
                        FormStatusState.UNKNOWN,
                        false)));
        assertEquals(1, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::level).collect(Collectors.toList());
        assertTrue(findingsFields.get(0).equals(curationName));
    }

    @Test
    public void reportsPartiallyCuratedTreatment() {
        String curationName = "testTreatmentCuration";
        final List<ValidationFinding> findings = PatientValidator.validateTreatmentCuration(CPCT_ID,
                curationName,
                "",
                Lists.newArrayList(ImmutableBiopsyTreatmentData.of("Yes", "Yes",
                        Lists.newArrayList(DRUG_WITH_PARTIAL_CURATED_ENTRY),
                        FormStatusState.UNKNOWN,
                        false)));
        assertEquals(1, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::level).collect(Collectors.toList());
        assertTrue(findingsFields.get(0).equals(curationName));
    }

    @Test
    public void doesNotReportCorrectDeathTimeline() {
        final List<ValidationFinding> findings = PatientValidator.validateDeathDate(CPCT_ID,
                ImmutablePatientData.builder().cpctId(CPCT_ID).deathDate(MAR2015).build(),
                Lists.newArrayList(TREATMENT_JAN_JAN, TREATMENT_JAN_FEB));
        assertEquals(0, findings.size());
    }

    @Test
    public void reportsDeathDateBeforeEndOfTreatment() {
        final List<ValidationFinding> findings = PatientValidator.validateDeathDate(CPCT_ID,
                ImmutablePatientData.builder().cpctId(CPCT_ID).deathDate(MAR2015).build(),
                Lists.newArrayList(TREATMENT_JAN_ONGOING, TREATMENT_JAN_FEB));
        assertEquals(1, findings.size());
        findings.stream().map(ValidationFinding::patientId).forEach(id -> assertEquals(CPCT_ID, id));
        final List<String> findingsFields = findings.stream().map(ValidationFinding::ecrfItem).collect(Collectors.toList());
        assertTrue(findingsFields.contains(fields(FIELD_DEATH_DATE, FORM_TREATMENT)));
    }

    @NotNull
    private static DrugData create(@Nullable String name, @Nullable LocalDate startDate, @Nullable LocalDate endDate) {
        List<CuratedTreatment> curation =
                name != null ? Lists.newArrayList(ImmutableCuratedTreatment.of(name, "Type1", name)) : Lists.newArrayList();
        return drugBuilder().name(name).startDate(startDate).endDate(endDate).addAllCuratedTreatments(curation).build();
    }
}
