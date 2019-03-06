package com.hartwig.hmftools.patientdb;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.context.RunContext;
import com.hartwig.hmftools.common.ecrf.EcrfModel;
import com.hartwig.hmftools.common.ecrf.datamodel.EcrfPatient;
import com.hartwig.hmftools.common.ecrf.datamodel.ValidationFinding;
import com.hartwig.hmftools.common.ecrf.formstatus.FormStatusModel;
import com.hartwig.hmftools.common.ecrf.formstatus.FormStatusReader;
import com.hartwig.hmftools.common.lims.Lims;
import com.hartwig.hmftools.common.lims.LimsFactory;
import com.hartwig.hmftools.common.lims.LimsSampleType;
import com.hartwig.hmftools.patientdb.curators.BiopsySiteCurator;
import com.hartwig.hmftools.patientdb.curators.TreatmentCurator;
import com.hartwig.hmftools.patientdb.curators.TumorLocationCurator;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;
import com.hartwig.hmftools.patientdb.data.ImmutableCuratedTumorLocation;
import com.hartwig.hmftools.patientdb.data.ImmutableTumorTypeLims;
import com.hartwig.hmftools.patientdb.data.Patient;
import com.hartwig.hmftools.patientdb.data.SampleData;
import com.hartwig.hmftools.patientdb.data.TumorTypeLims;
import com.hartwig.hmftools.patientdb.readers.LimsSampleReader;
import com.hartwig.hmftools.patientdb.readers.PatientReader;
import com.hartwig.hmftools.patientdb.readers.RunsFolderReader;
import com.hartwig.hmftools.patientdb.readers.TumorLocationCurationLims;
import com.hartwig.hmftools.patientdb.readers.cpct.CpctPatientReader;
import com.hartwig.hmftools.patientdb.readers.cpct.CpctUtil;
import com.hartwig.hmftools.patientdb.readers.drup.DrupPatientReader;
import com.hartwig.hmftools.patientdb.validators.CurationValidator;
import com.hartwig.hmftools.patientdb.validators.PatientValidator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public final class LoadClinicalData {
    private static final Logger LOGGER = LogManager.getLogger(LoadClinicalData.class);
    private static final String VERSION = LoadClinicalData.class.getPackage().getImplementationVersion();

    private static final String RUNS_DIR_CPCT_DRUP = "runs_dir_cpct_drup";
    private static final String RUNS_DIR_CORE = "runs_dir_core";
    private static final String RUNS_DIR_COLO = "runs_dir_colo";
    private static final String CPCT_ECRF_FILE = "cpct_ecrf";
    private static final String CPCT_FORM_STATUS_CSV = "cpct_form_status_csv";
    private static final String DRUP_ECRF_FILE = "drup_ecrf";
    private static final String DO_LOAD_RAW_ECRF = "do_load_raw_ecrf";

    private static final String DB_USER = "db_user";
    private static final String DB_PASS = "db_pass";
    private static final String DB_URL = "db_url";

    private static final String LIMS_DIRECTORY = "lims";

    private static final String CSV_OUT_DIR = "csv_out_dir";
    private static final String TUMOR_LOCATION_SYMLINK = "tumor_location_symlink";
    private static final String PORTAL_DATA_LINK = "portal_data_symlink";

    public static void main(@NotNull final String[] args) throws ParseException, IOException, XMLStreamException, SQLException {
        LOGGER.info("Running patient-db v{}", VERSION);
        final Options options = createOptions();
        final CommandLine cmd = createCommandLine(args, options);

        if (checkInputs(cmd)) {
            LOGGER.info("Running clinical data import.");

            final DatabaseAccess dbWriter = createDbWriter(cmd);

            Lims limsData = readingLims(cmd);

            String patientIdCOLO = loadSamplesCOLO(cmd);
            patientIdCOLO = patientIdCOLO.substring(1, patientIdCOLO.length() - 1);
            final Map<String, List<SampleData>> samplesPerPatient = loadSamplesPerPatient(cmd, limsData);
            final EcrfModels ecrfModels = loadEcrfModels(cmd);

            if (cmd.hasOption(DO_LOAD_RAW_ECRF)) {
                writeRawEcrf(dbWriter, samplesPerPatient.keySet(), ecrfModels);
            }

            writeClinicalData(dbWriter,
                    samplesPerPatient,
                    patientIdCOLO,
                    ecrfModels,
                    cmd.getOptionValue(CSV_OUT_DIR),
                    Optional.ofNullable(cmd.getOptionValue(TUMOR_LOCATION_SYMLINK)),
                    Optional.ofNullable(cmd.getOptionValue(PORTAL_DATA_LINK)),
                    limsData);
        } else {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("patient-db", options);
        }
    }

    private static Lims readingLims(@NotNull CommandLine cmd) throws IOException {
        final String limsDirectory = cmd.getOptionValue(LIMS_DIRECTORY);
        LOGGER.info(String.format("Loading samples from LIMS on %s.", limsDirectory));
        return LimsFactory.fromLimsDirectory(limsDirectory);
    }

    private static Map<String, TumorTypeLims> loadAndInterpretPatientsFromLims(@NotNull Map<String, List<SampleData>> samplesPerPatient,
            @NotNull TumorLocationCurator tumorLocationCurator, @NotNull Lims lims, @NotNull String patientIdCOLO) {
        List<String> corePatients = Lists.newArrayList();
        List<String> widePatients = Lists.newArrayList();
        List<String> coloPatient = Lists.newArrayList();

        TumorLocationCurationLims tumorLocationCurationLims = new TumorLocationCurationLims(lims, tumorLocationCurator);

        for (Map.Entry<String, List<SampleData>> sampleData : samplesPerPatient.entrySet()) {
            String patient = sampleData.getKey();
            LimsSampleType type = LimsSampleType.fromSampleId(patient);
            LimsSampleType typeColo = LimsSampleType.fromSampleId(patientIdCOLO);

            if (type == LimsSampleType.CORE) {
                corePatients.add(patient);
            } else if (type == LimsSampleType.WIDE) {
                widePatients.add(patient);
            } else if (typeColo == LimsSampleType.COLO) {
                coloPatient.add(patientIdCOLO);
            }
        }
        LOGGER.info(String.format("Interpreting and curating data for %s CORE patients.", corePatients.size()));
        Map<String, TumorTypeLims> corePatientsData = readLimsPatients(tumorLocationCurationLims, corePatients, samplesPerPatient, lims);
        LOGGER.info(String.format("Finished curation of %s CORE patients.", corePatientsData.size()));

        LOGGER.info(String.format("Interpreting and curating data for %s WIDE patients.", widePatients.size()));
        Map<String, TumorTypeLims> WIDEPatientsData = readLimsPatients(tumorLocationCurationLims, widePatients, samplesPerPatient, lims);
        LOGGER.info(String.format("Finished curation of %s WIDE patients.", WIDEPatientsData.size()));

        LOGGER.info(String.format("Interpreting and curating data for %s COLO patients.", coloPatient));
        Map<String, TumorTypeLims> COLOPatientsData = Maps.newHashMap();
        COLOPatientsData.put(patientIdCOLO,
                ImmutableTumorTypeLims.of(patientIdCOLO, ImmutableCuratedTumorLocation.of("Skin", "Melanoma", "Melanoma")));
        LOGGER.info(String.format("Finished curation of %s COLO patients.", COLOPatientsData.size()));

        Map<String, TumorTypeLims> mergedPatients = Maps.newHashMap();
        mergedPatients.putAll(corePatientsData);
        mergedPatients.putAll(WIDEPatientsData);
        mergedPatients.putAll(COLOPatientsData);

        return mergedPatients;
    }

    @NotNull
    private static Map<String, TumorTypeLims> readLimsPatients(@NotNull final TumorLocationCurationLims tumorLocationCurationLims,
            @NotNull List<String> sampleIdsFromPatients, @NotNull final Map<String, List<SampleData>> samplesPerPatient,
            @NotNull final Lims lims) {
        final Map<String, TumorTypeLims> patientMap = Maps.newHashMap();
        for (int i = 0; i < sampleIdsFromPatients.size(); i++) {
            List<SampleData> samples = samplesPerPatient.get(sampleIdsFromPatients.get(i));
            //create sampleID
            String rawSampleId = samples.toString().split(" ")[1];
            String sampleId = rawSampleId.substring(1, rawSampleId.length() - 2);
            patientMap.put(lims.patientId(sampleId), tumorLocationCurationLims.read(sampleId, lims.patientId(sampleId)));

        }
        return patientMap;
    }

    @NotNull
    private static Map<String, Patient> readEcrfPatients(@NotNull final PatientReader reader, @NotNull final Iterable<EcrfPatient> patients,
            @NotNull final Map<String, List<SampleData>> samplesPerPatient) {
        final Map<String, Patient> patientMap = Maps.newHashMap();
        for (final EcrfPatient ecrfPatient : patients) {
            List<SampleData> samples = samplesPerPatient.get(ecrfPatient.patientId());
            Patient patient = reader.read(ecrfPatient, samples != null ? samples : Lists.newArrayList());
            patientMap.put(patient.patientIdentifier(), patient);
        }
        return patientMap;
    }

    private static void writeClinicalData(@NotNull final DatabaseAccess dbAccess, @NotNull Map<String, List<SampleData>> samplesPerPatient,
            @NotNull String patientIdCOLO, @NotNull EcrfModels ecrfModels, @NotNull String csvOutputDir,
            @NotNull Optional<String> tumorLocationSymlink, @NotNull Optional<String> portalDataLink, @NotNull Lims lims)
            throws IOException {
        TumorLocationCurator tumorLocationCurator = TumorLocationCurator.fromProductionResource();
        BiopsySiteCurator biopsySiteCurator = BiopsySiteCurator.fromProductionResource();
        TreatmentCurator treatmentCurator = TreatmentCurator.fromProductionResource();

        Map<String, Patient> patients =
                loadAndInterpretAllPatients(samplesPerPatient, ecrfModels, tumorLocationCurator, treatmentCurator, biopsySiteCurator);

        Map<String, TumorTypeLims> patientsMergedLims =
                loadAndInterpretPatientsFromLims(samplesPerPatient, tumorLocationCurator, lims, patientIdCOLO);

        DumpClinicalData.writeClinicalDumps(csvOutputDir,
                patients.values(),
                tumorLocationSymlink,
                portalDataLink,
                patientsMergedLims.values());

        LOGGER.info("Clearing interpreted clinical tables in database.");
        dbAccess.clearClinicalTables();

        Set<String> sequencedPatientIdentifiers = samplesPerPatient.keySet();
        int missingPatients = 0;
        int missingSamples = 0;
        LOGGER.info(String.format("Writing clinical data for %s sequenced patients.", sequencedPatientIdentifiers.size()));
        for (final String patientIdentifier : sequencedPatientIdentifiers) {
            if (patientIdentifier.contains(LimsSampleType.CPCT.toString()) || patientIdentifier.contains(LimsSampleType.DRUP.toString())
                    || patientIdentifier.contains(LimsSampleType.WIDE.toString())) {
                Patient patient = patients.get(patientIdentifier);
                if (patient == null) {
                    missingPatients++;
                    missingSamples += samplesPerPatient.get(patientIdentifier).size();
                    dbAccess.writeSampleClinicalData(patientIdentifier, samplesPerPatient.get(patientIdentifier));
                } else {
                    dbAccess.writeFullClinicalData(patient);
                    List<ValidationFinding> findings = PatientValidator.validatePatient(patient);

                    dbAccess.writeValidationFindings(findings);
                    dbAccess.writeValidationFindings(patient.matchFindings());
                }
            }
        }
        if (missingPatients > 0) {
            LOGGER.warn(String.format("Could not load %s patients (%s samples)!", missingPatients, missingSamples));
        }
        dbAccess.writeValidationFindings(CurationValidator.validateTreatmentCurator(treatmentCurator));
        dbAccess.writeValidationFindings(CurationValidator.validateTumorLocationCurator(tumorLocationCurator));

        LOGGER.info("Finished!");
    }

    @NotNull
    private static Map<String, Patient> loadAndInterpretAllPatients(@NotNull Map<String, List<SampleData>> samplesPerPatient,
            @NotNull EcrfModels ecrfModels, @NotNull TumorLocationCurator tumorLocationCurator, @NotNull TreatmentCurator treatmentCurator,
            @NotNull BiopsySiteCurator biopsySiteCurator) {
        final EcrfModel cpctEcrfModel = ecrfModels.cpctModel();
        LOGGER.info(String.format("Interpreting and curating data for %s CPCT patients.", cpctEcrfModel.patientCount()));
        PatientReader cpctPatientReader = new CpctPatientReader(tumorLocationCurator,
                CpctUtil.extractHospitalMap(cpctEcrfModel),
                biopsySiteCurator,
                treatmentCurator);

        Map<String, Patient> cpctPatients = readEcrfPatients(cpctPatientReader, cpctEcrfModel.patients(), samplesPerPatient);
        LOGGER.info(String.format("Finished curation of %s CPCT patients.", cpctPatients.size()));

        final EcrfModel drupEcrfModel = ecrfModels.drupModel();
        LOGGER.info(String.format("Interpreting and curating data for %s DRUP patients.", drupEcrfModel.patientCount()));
        PatientReader drupPatientReader = new DrupPatientReader(tumorLocationCurator, biopsySiteCurator);

        Map<String, Patient> drupPatients = readEcrfPatients(drupPatientReader, drupEcrfModel.patients(), samplesPerPatient);
        LOGGER.info(String.format("Finished curation of %s DRUP patients.", drupPatients.size()));

        Map<String, Patient> mergedPatients = Maps.newHashMap();
        mergedPatients.putAll(cpctPatients);
        mergedPatients.putAll(drupPatients);
        return mergedPatients;
    }

    private static void writeRawEcrf(@NotNull DatabaseAccess dbWriter, @NotNull Set<String> sequencedPatients,
            @NotNull EcrfModels ecrfModels) {
        final EcrfModel cpctEcrfModel = ecrfModels.cpctModel();
        LOGGER.info(String.format("Writing raw cpct ecrf data for %s patients", cpctEcrfModel.patientCount()));
        dbWriter.clearCpctEcrf();
        dbWriter.writeCpctEcrf(cpctEcrfModel, sequencedPatients);
        LOGGER.info(String.format("Finished writing raw cpct ecrf data for %s patients.", cpctEcrfModel.patientCount()));

        final EcrfModel drupEcrfModel = ecrfModels.drupModel();
        LOGGER.info(String.format("Writing raw drup ecrf data for %s patients", drupEcrfModel.patientCount()));
        dbWriter.clearDrupEcrf();
        dbWriter.writeDrupEcrf(drupEcrfModel, sequencedPatients);
        LOGGER.info(String.format("Finished writing raw drup ecrf data for %s patients.", drupEcrfModel.patientCount()));
    }

    private static boolean checkInputs(@NotNull CommandLine cmd) {
        final String runsFolderPathCPCTandDRUP = cmd.getOptionValue(RUNS_DIR_CPCT_DRUP);
        final String runsFolderPathCore = cmd.getOptionValue(RUNS_DIR_CORE);
        final String runsFolderPathColo = cmd.getOptionValue(RUNS_DIR_COLO);

        boolean allParamsPresent = !Utils.anyNull(runsFolderPathCPCTandDRUP,
                runsFolderPathCore,
                runsFolderPathColo,
                cmd.getOptionValue(DB_USER),
                cmd.getOptionValue(DB_PASS),
                cmd.getOptionValue(DB_URL),
                cmd.getOptionValue(CPCT_ECRF_FILE),
                cmd.getOptionValue(CPCT_FORM_STATUS_CSV),
                cmd.getOptionValue(DRUP_ECRF_FILE),
                cmd.getOptionValue(LIMS_DIRECTORY),
                cmd.getOptionValue(CSV_OUT_DIR));

        boolean validRunDirectory = true;
        if (allParamsPresent) {
            final File runDirectoryCPCTandDRUP = new File(runsFolderPathCPCTandDRUP);
            final File runDirectoryCORE = new File(runsFolderPathCore);
            final File runDirectoryCOLO = new File(runsFolderPathColo);

            if (!runDirectoryCPCTandDRUP.isDirectory()) {
                validRunDirectory = false;
                if (!runDirectoryCPCTandDRUP.exists()) {
                    LOGGER.warn("dir " + runDirectoryCPCTandDRUP + " does not exist.");
                } else if (!runDirectoryCORE.isDirectory()) {
                    LOGGER.warn("dir " + runDirectoryCORE + " does not exist.");
                } else if (!runDirectoryCOLO.isDirectory()) {
                    LOGGER.warn("dir " + runDirectoryCOLO + " does not exist.");
                }
            }
        }

        return allParamsPresent && validRunDirectory;
    }

    @NotNull
    private static String loadSamplesCOLO(@NotNull CommandLine cmd) throws IOException {
        final String runsFolderPathCOLO = cmd.getOptionValue(RUNS_DIR_COLO);
        final List<RunContext> runContextsCOLO = RunsFolderReader.getRunContexts(new File(runsFolderPathCOLO));
        LOGGER.info(String.format("Loading run contexts from %s", runsFolderPathCOLO) + " (" + runContextsCOLO.size() + " sets).");
        Set<String> COLOpatient = Utils.sequencedPatientIdentifiers(runContextsCOLO);
        return COLOpatient.toString();
    }

    @NotNull
    private static Map<String, List<SampleData>> loadSamplesPerPatient(@NotNull CommandLine cmd, @NotNull Lims lims) throws IOException {
        final String runsFolderPathCPCTandDRUP = cmd.getOptionValue(RUNS_DIR_CPCT_DRUP);
        final String runsFolderPathCORE = cmd.getOptionValue(RUNS_DIR_CORE);

        List<RunContext> runContextsAll = new ArrayList<>();

        final List<RunContext> runContextsCPCTandDRUP = RunsFolderReader.getRunContexts(new File(runsFolderPathCPCTandDRUP));
        LOGGER.info(String.format("Loading run contexts from %s", runsFolderPathCPCTandDRUP) + " (" + runContextsCPCTandDRUP.size()
                + " sets).");

        final List<RunContext> runContextsCORE = RunsFolderReader.getRunContexts(new File(runsFolderPathCORE));
        LOGGER.info(String.format("Loading run contexts from %s", runsFolderPathCORE) + " (" + runContextsCORE.size() + " sets).");

        runContextsAll.addAll(runContextsCPCTandDRUP);
        runContextsAll.addAll(runContextsCORE);

        LOGGER.info(String.format("Finished loading %s run contexts.", runContextsAll.size()));

        Map<String, List<SampleData>> samplesPerPatient = readSamplesPerPatient(lims, runContextsAll);
        LOGGER.info(String.format("Loaded samples for %s patients from LIMS", samplesPerPatient.keySet().size()));

        return samplesPerPatient;
    }

    @NotNull
    private static Map<String, List<SampleData>> readSamplesPerPatient(@NotNull Lims lims, @NotNull List<RunContext> runContexts) {
        LimsSampleReader sampleReader = new LimsSampleReader(lims);

        final Set<String> sequencedPatientIdentifiers = Utils.sequencedPatientIdentifiers(runContexts);

        Map<String, List<SampleData>> samplesPerPatient = Maps.newHashMap();
        for (String patientIdentifier : sequencedPatientIdentifiers) {
            Set<String> sampleIds = extractTumorSampleIdsForPatient(patientIdentifier, runContexts);
            samplesPerPatient.put(patientIdentifier, sampleReader.read(sampleIds));
        }

        return samplesPerPatient;
    }

    @NotNull
    private static Set<String> extractTumorSampleIdsForPatient(@NotNull final String patientIdentifier,
            @NotNull final List<RunContext> runContexts) {
        final Set<String> sampleIdsForPatient = Sets.newHashSet();
        runContexts.forEach(runContext -> {
            final String sampleId = runContext.tumorSample();
            if (sampleId.startsWith(patientIdentifier)) {
                sampleIdsForPatient.add(sampleId);
            }
        });
        return sampleIdsForPatient;
    }

    @NotNull
    private static EcrfModels loadEcrfModels(@NotNull CommandLine cmd) throws IOException, XMLStreamException {
        final String cpctEcrfFilePath = cmd.getOptionValue(CPCT_ECRF_FILE);
        final String cpctFormStatusCsv = cmd.getOptionValue(CPCT_FORM_STATUS_CSV);
        LOGGER.info(String.format("Loading CPCT eCRF from %s.", cpctEcrfFilePath));
        final FormStatusModel cpctFormStatusModel = FormStatusReader.buildModelFromCsv(cpctFormStatusCsv);
        final EcrfModel cpctEcrfModel = EcrfModel.loadFromXMLWithFormStates(cpctEcrfFilePath, cpctFormStatusModel);
        LOGGER.info(String.format("Finished loading CPCT eCRF. Read %s patients.", cpctEcrfModel.patientCount()));

        final String drupEcrfFilePath = cmd.getOptionValue(DRUP_ECRF_FILE);
        LOGGER.info(String.format("Loading DRUP eCRF from %s.", drupEcrfFilePath));
        final EcrfModel drupEcrfModel = EcrfModel.loadFromXMLNoFormStates(drupEcrfFilePath);
        LOGGER.info(String.format("Finished loading DRUP eCRF. Read %s patients.", drupEcrfModel.patientCount()));

        return ImmutableEcrfModels.of(cpctEcrfModel, drupEcrfModel);
    }

    @NotNull
    private static DatabaseAccess createDbWriter(@NotNull CommandLine cmd) throws SQLException {
        final String jdbcUrl = "jdbc:" + cmd.getOptionValue(DB_URL);
        return new DatabaseAccess(cmd.getOptionValue(DB_USER), cmd.getOptionValue(DB_PASS), jdbcUrl);
    }

    @NotNull
    private static Options createOptions() {
        final Options options = new Options();
        options.addOption(RUNS_DIR_CPCT_DRUP, true, "Path towards the folder containing cpct and drup patient runs.");
        options.addOption(RUNS_DIR_CORE, true, "Path towards the folder containing core patient runs.");
        options.addOption(RUNS_DIR_COLO, true, "Path towards the folder containing colo run.");

        options.addOption(DB_USER, true, "Database user name.");
        options.addOption(DB_PASS, true, "Database password.");
        options.addOption(DB_URL, true, "Database url.");

        options.addOption(CPCT_ECRF_FILE, true, "Path towards the cpct ecrf file.");
        options.addOption(CPCT_FORM_STATUS_CSV, true, "Path towards the cpct form status csv file.");
        options.addOption(DRUP_ECRF_FILE, true, "Path towards the drup ecrf file.");
        options.addOption(DO_LOAD_RAW_ECRF, false, "Also write raw ecrf data to database?");

        options.addOption(CSV_OUT_DIR, true, "Path towards the output directory for csv data dumps.");
        options.addOption(TUMOR_LOCATION_SYMLINK, true, "Name of cancer type csv symlink.");
        options.addOption(PORTAL_DATA_LINK, true, "Name of portal data csv symlink.");

        options.addOption(LIMS_DIRECTORY, true, "Path towards the LIMS directory.");
        return options;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final String[] args, @NotNull final Options options) throws ParseException {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }
}
