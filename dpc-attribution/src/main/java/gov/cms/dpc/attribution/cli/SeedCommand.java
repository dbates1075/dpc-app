package gov.cms.dpc.attribution.cli;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.dpc.attribution.DPCAttributionConfiguration;
import gov.cms.dpc.attribution.dao.tables.OrganizationEndpoints;
import gov.cms.dpc.attribution.dao.tables.Organizations;
import gov.cms.dpc.attribution.dao.tables.Patients;
import gov.cms.dpc.attribution.dao.tables.Providers;
import gov.cms.dpc.attribution.dao.tables.records.OrganizationEndpointsRecord;
import gov.cms.dpc.attribution.dao.tables.records.OrganizationsRecord;
import gov.cms.dpc.attribution.dao.tables.records.PatientsRecord;
import gov.cms.dpc.attribution.dao.tables.records.ProvidersRecord;
import gov.cms.dpc.attribution.jdbi.RosterUtils;
import gov.cms.dpc.attribution.utils.DBUtils;
import gov.cms.dpc.common.entities.*;
import gov.cms.dpc.common.utils.SeedProcessor;
import gov.cms.dpc.fhir.converters.EndpointConverter;
import io.dropwizard.Application;
import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.db.PooledDataSourceFactory;
import io.dropwizard.setup.Environment;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.hl7.fhir.dstu3.model.*;
import org.jooq.DSLContext;
import org.jooq.conf.RenderNameStyle;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class SeedCommand extends EnvironmentCommand<DPCAttributionConfiguration> {

    private static Logger logger = LoggerFactory.getLogger(SeedCommand.class);
    private static final String CSV = "test_associations.csv";
    private static final String ORGANIZATION_BUNDLE = "organization_bundle.json";
    private static final String PROVIDER_BUNDLE = "provider_bundle.json";
    private static final String PATIENT_BUNDLE = "patient_bundle.json";
    private static final UUID ORGANIZATION_ID = UUID.fromString("46ac7ad6-7487-4dd0-baa0-6e2c8cae76a0");

    private final Settings settings;

    public SeedCommand(Application<DPCAttributionConfiguration> application) {
        super(application, "seed", "Seed the attribution roster");
        this.settings = new Settings().withRenderNameStyle(RenderNameStyle.AS_IS);
    }

    @Override
    public void configure(Subparser subparser) {
        subparser
                .addArgument("-t", "--timestamp")
                .dest("timestamp")
                .type(String.class)
                .required(false)
                .help("Custom timestamp to use when adding attributed relationships.");
    }

    @Override
    protected void run(Environment environment, Namespace namespace, DPCAttributionConfiguration configuration) throws Exception {
        // Get the db factory
        final PooledDataSourceFactory dataSourceFactory = configuration.getDatabase();
        final ManagedDataSource dataSource = dataSourceFactory.build(environment.metrics(), "attribution-seeder");

        final OffsetDateTime creationTimestamp = generateTimestamp(namespace);

        // Read in the seeds file and write things
        logger.info("Seeding attributions at time {}", creationTimestamp.toLocalDateTime());

        try (final Connection connection = dataSource.getConnection();
             DSLContext context = DSL.using(connection, this.settings)) {

            // Truncate everything
            DBUtils.truncateAllTables(context, "public");

            final FhirContext ctx = FhirContext.forDstu3();
            final IParser parser = ctx.newJsonParser();
            // Start with the Organizations and their endpoints
            seedOrganizationBundle(context, parser);

            // Providers next
            seedProviderBundle(context, parser, ORGANIZATION_ID);

            // Add the patients, saving the references
            final Map<String, Reference> patientReferences = seedPatientBundle(context, parser, ORGANIZATION_ID);

            // Get the test attribution seeds
            seedAttributions(context, ORGANIZATION_ID, creationTimestamp, patientReferences);

            logger.info("Finished loading seeds");
        }
    }

    private void seedOrganizationBundle(DSLContext context, IParser parser) throws IOException {
        try (final InputStream orgBundleStream = SeedCommand.class.getClassLoader().getResourceAsStream(ORGANIZATION_BUNDLE)) {
            if (orgBundleStream == null) {
                throw new MissingResourceException("Can not find seeds file", this.getClass().getName(), CSV);
            }
            final OrganizationEntity orgEntity = new OrganizationEntity();
            final Bundle bundle = parser.parseResource(Bundle.class, orgBundleStream);
            final List<EndpointEntity> endpointEntities = BundleParser.parse(Endpoint.class, bundle, EndpointConverter::convert, ORGANIZATION_ID);
            final List<OrganizationEntity> organizationEntities = BundleParser.parse(Organization.class, bundle, orgEntity::fromFHIR, ORGANIZATION_ID);

            organizationEntities
                    .stream()
                    .map(entity -> organizationEntityToRecord(context, entity))
                    .forEach(context::executeInsert);
            endpointEntities
                    .stream()
                    .map(entity -> endpointsEntityToRecord(context, entity))
                    .forEach(context::executeInsert);
        }
    }

    private void seedProviderBundle(DSLContext context, IParser parser, UUID organizationID) throws IOException {
        try (final InputStream providerBundleStream = SeedCommand.class.getClassLoader().getResourceAsStream(PROVIDER_BUNDLE)) {
            final Bundle providerBundle = parser.parseResource(Bundle.class, providerBundleStream);
            final List<ProviderEntity> providers = BundleParser.parse(Practitioner.class, providerBundle, ProviderEntity::fromFHIR, organizationID);

            providers
                    .stream()
                    .map(entity -> providersEntityToRecord(context, entity))
                    .forEach(context::executeInsert);
        }
    }

    private void seedAttributions(DSLContext context, UUID organizationID, OffsetDateTime creationTimestamp, Map<String, Reference> patientReferences) throws IOException {
        try (InputStream resource = SeedCommand.class.getClassLoader().getResourceAsStream(CSV)) {
            if (resource == null) {
                throw new MissingResourceException("Can not find seeds file", this.getClass().getName(), CSV);
            }
            SeedProcessor
                    .extractProviderMap(resource)
                    .entrySet()
                    .stream()
                    .map(entry -> SeedProcessor.generateAttributionGroup(entry, organizationID, patientReferences))
                    .forEach(bundle -> RosterUtils.submitAttributionGroup(bundle, context, organizationID, creationTimestamp));
        }
    }

    private Map<String, Reference> seedPatientBundle(DSLContext context, IParser parser, UUID organizationID) throws IOException {
        try (final InputStream providerBundleStream = SeedCommand.class.getClassLoader().getResourceAsStream(PATIENT_BUNDLE)) {
            final Bundle patientBundle = parser.parseResource(Bundle.class, providerBundleStream);
            final List<PatientEntity> patients = BundleParser.parse(Patient.class, patientBundle, PatientEntity::fromFHIR, organizationID);

            Map<String, Reference> patientReferences = new HashMap<>();

            patients
                    .stream()
                    // Add the managing organization
                    .peek(entity -> {
                        final OrganizationEntity organization = new OrganizationEntity();
                        organization.setId(organizationID);
                        entity.setOrganization(organization);
                    })
                    .map(entity -> patientEntityToRecord(context, entity))
                    .peek(context::executeInsert)
                    .forEach(record -> {
                        final Reference ref = new Reference(new IdType("Patient", record.getId().toString()));
                        patientReferences.put(record.getBeneficiaryId(), ref);
                    });

            return patientReferences;
        }
    }

    private static OffsetDateTime generateTimestamp(Namespace namespace) {
        final String timestamp = namespace.getString("timestamp");
        if (timestamp == null) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(timestamp.trim());
    }

    private static OrganizationsRecord organizationEntityToRecord(DSLContext context, OrganizationEntity entity) {
        // We have to manually map the embedded fields
        final OrganizationsRecord record = context.newRecord(Organizations.ORGANIZATIONS, entity);
        record.setIdSystem(entity.getOrganizationID().getSystem().ordinal());
        record.setIdValue(entity.getOrganizationID().getValue());

        final AddressEntity address = entity.getOrganizationAddress();
        record.setAddressType(address.getType().ordinal());
        record.setAddressUse(address.getUse().ordinal());
        record.setLine1(address.getLine1());
        record.setLine2(address.getLine2());
        record.setCity(address.getCity());
        record.setDistrict(address.getDistrict());
        record.setState(address.getState());
        record.setPostalCode(address.getPostalCode());
        record.setCountry(address.getCountry());
        return record;
    }

    private static OrganizationEndpointsRecord endpointsEntityToRecord(DSLContext context, EndpointEntity entity) {
        final OrganizationEndpointsRecord record = context.newRecord(OrganizationEndpoints.ORGANIZATION_ENDPOINTS, entity);

        final EndpointEntity.ConnectionType connectionType = entity.getConnectionType();
        record.setOrganizationId(entity.getOrganization().getId());
        record.setSystem(connectionType.getSystem());
        record.setCode(connectionType.getCode());

        // Not sure why we have to manually set these values
        record.setStatus(entity.getStatus().ordinal());
        record.setName(entity.getName());
        record.setAddress(entity.getAddress());
        record.setValidationStatus(entity.getValidationStatus().ordinal());
        record.setValidationMessage(entity.getValidationMessage());

        return record;
    }

    private static ProvidersRecord providersEntityToRecord(DSLContext context, ProviderEntity entity) {
        final ProvidersRecord record = context.newRecord(Providers.PROVIDERS, entity);
        record.setOrganizationId(entity.getOrganization().getId());
        record.setId(UUID.randomUUID());

        return record;
    }

    private static PatientsRecord patientEntityToRecord(DSLContext context, PatientEntity entity) {
        // Generate a temporary ID
        final PatientsRecord record = context.newRecord(Patients.PATIENTS, entity);
        record.setOrganizationId(entity.getOrganization().getId());
        record.setId(UUID.randomUUID());

        return record;
    }
}
