package gov.cms.dpc.aggregation;

import ca.uhn.fhir.context.FhirContext;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.Binder;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.hubspot.dropwizard.guicier.DropwizardAwareModule;
import com.typesafe.config.Config;
import gov.cms.dpc.aggregation.client.ClientModule;
import gov.cms.dpc.aggregation.engine.AggregationEngine;
import gov.cms.dpc.aggregation.engine.OperationsConfig;
import gov.cms.dpc.aggregation.engine.suppression.SuppressionEngine;
import gov.cms.dpc.aggregation.engine.suppression.SuppressionEngineImpl;
import gov.cms.dpc.common.annotations.ExportPath;
import gov.cms.dpc.fhir.hapi.ContextUtils;
import gov.cms.dpc.queue.models.JobQueueBatch;

import javax.inject.Singleton;

public class AggregationAppModule extends DropwizardAwareModule<DPCAggregationConfiguration> {

    private final FhirContext ctx;

    AggregationAppModule() {
        this.ctx = FhirContext.forDstu3();

        // Setup the context with model scans (avoids doing this on the fetch threads and perhaps multithreaded bug)
        ContextUtils.prefetchResourceModels(ctx, JobQueueBatch.validResourceTypes);
    }

    @Override
    public void configure(Binder binder) {
        // Install the client module to abstract away the setup of the various client integrations
        binder.install(new ClientModule(getConfiguration(), this.ctx));

        binder.bind(SuppressionEngine.class).to(SuppressionEngineImpl.class).in(Scopes.SINGLETON);
        binder.bind(AggregationEngine.class);
        binder.bind(AggregationManager.class).asEagerSingleton();

        // Healthchecks
        // Additional health-checks can be added here
        // By default, Dropwizard adds a check for Hibernate and each additonal database (e.g. auth, queue, etc)
        // We also have JobQueueHealthy which ensures the queue is operation correctly
        // We have the BlueButton Client healthcheck as well
    }

    @Provides
    @Singleton
    public FhirContext provideSTU3Context() {
        return this.ctx;
    }

    @Provides
    @Singleton
    MetricRegistry provideMetricRegistry() {
        return getEnvironment().metrics();
    }

    @Provides
    public Config provideConfig() {
        return getConfiguration().getConfig();
    }

    @Provides
    @ExportPath
    public String provideExportPath() {
        return getConfiguration().getExportPath();
    }

    @Provides
    OperationsConfig provideOperationsConfig() {
        final var config = getConfiguration();

        return new OperationsConfig(
                config.getResourcesPerFileCount(),
                config.getExportPath(),
                config.getRetryCount(),
                config.getPollingFrequency()
        );
    }
}
