package gov.cms.dpc.attribution;

import ca.mestevens.java.configuration.TypesafeConfiguration;
import com.fasterxml.jackson.annotation.JsonProperty;
import gov.cms.dpc.common.hibernate.attribution.IDPCDatabase;
import gov.cms.dpc.common.hibernate.queue.IDPCQueueDatabase;
import gov.cms.dpc.fhir.configuration.DPCFHIRConfiguration;
import gov.cms.dpc.fhir.configuration.IDPCFHIRConfiguration;
import io.dropwizard.db.DataSourceFactory;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import org.hibernate.validator.constraints.NotEmpty;
import org.knowm.dropwizard.sundial.SundialConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.Duration;

public class DPCAttributionConfiguration extends TypesafeConfiguration implements IDPCDatabase, IDPCFHIRConfiguration, IDPCQueueDatabase {

    @Valid
    private Duration expirationThreshold;

    private Boolean migrationEnabled;

    @Valid
    @NotNull
    @JsonProperty("database")
    private DataSourceFactory database = new DataSourceFactory();

    @Valid
    @NotNull
    @JsonProperty("queuedb")
    private DataSourceFactory queueDatabase = new DataSourceFactory();

    @Valid
    @NotNull
    @JsonProperty("sundial")
    private SundialConfiguration sundial = new SundialConfiguration();

    @NotEmpty
    private String publicServerURL;

    @Valid
    @NotNull
    @JsonProperty("fhir")
    private DPCFHIRConfiguration fhirConfig;

    @Valid
    @JsonProperty("swagger")
    private SwaggerBundleConfiguration swaggerBundleConfiguration;

    private Integer providerLimit;

    @NotEmpty
    private String exportPath;

    public String getExportPath() {
        return exportPath;
    }

    public void setExportPath(String exportPath) {
        this.exportPath = exportPath;
    }

    @Override
    public DataSourceFactory getDatabase() {
        return database;
    }

    public SundialConfiguration getSundial() {
        return sundial;
    }

    public Duration getExpirationThreshold() {
        return expirationThreshold;
    }

    public void setExpirationThreshold(int expirationThreshold) {
        this.expirationThreshold = Duration.ofDays(expirationThreshold);
    }

    public String getPublicServerURL() {
        return publicServerURL;
    }

    public void setPublicServerURL(String publicServerURL) {
        this.publicServerURL = publicServerURL;
    }

    @Override
    public DPCFHIRConfiguration getFHIRConfiguration() {
        return this.fhirConfig;
    }

    @Override
    public void setFHIRConfiguration(DPCFHIRConfiguration config) {
        this.fhirConfig = config;
    }

    public Boolean getMigrationEnabled() {
        return migrationEnabled;
    }

    public void setMigrationEnabled(Boolean migrationEnabled) {
        this.migrationEnabled = migrationEnabled;
    }

    public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
        return swaggerBundleConfiguration;
    }

    public void setSwaggerBundleConfiguration(SwaggerBundleConfiguration swaggerBundleConfiguration) {
        this.swaggerBundleConfiguration = swaggerBundleConfiguration;
    }

    public Integer getProviderLimit() {
        return providerLimit;
    }

    public void setProviderLimit(Integer providerLimit) {
        this.providerLimit = providerLimit;
    }

    @Override
    public DataSourceFactory getQueueDatabase() {
        return queueDatabase;
    }
}
