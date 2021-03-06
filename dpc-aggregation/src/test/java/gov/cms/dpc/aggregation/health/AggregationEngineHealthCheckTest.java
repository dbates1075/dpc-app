package gov.cms.dpc.aggregation.health;

import ca.uhn.fhir.context.FhirContext;
import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.ConfigFactory;
import gov.cms.dpc.aggregation.engine.AggregationEngine;
import gov.cms.dpc.aggregation.engine.OperationsConfig;
import gov.cms.dpc.bluebutton.client.BlueButtonClient;
import gov.cms.dpc.bluebutton.client.MockBlueButtonClient;
import gov.cms.dpc.fhir.hapi.ContextUtils;
import gov.cms.dpc.queue.IJobQueue;
import gov.cms.dpc.queue.MemoryBatchQueue;
import gov.cms.dpc.queue.models.JobQueueBatch;
import gov.cms.dpc.testing.BufferedLoggerHandler;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * These tests are here to make sure the engine is still running/polling in situations where errors are recoverable.
 * A test to check if the engine exits out of the loop correctly when an error occurs
 * in AggregationEngineTest#testUnhealthyIfProcessJobBatchThrowsException
 */
@ExtendWith(BufferedLoggerHandler.class)
public class AggregationEngineHealthCheckTest {
    private static final String TEST_PROVIDER_ID = "1";
    private static final UUID aggregatorID = UUID.randomUUID();

    private IJobQueue queue;
    private BlueButtonClient bbclient;
    private AggregationEngine engine;

    static private FhirContext fhirContext = FhirContext.forDstu3();
    static private MetricRegistry metricRegistry = new MetricRegistry();
    static private String exportPath;


    @BeforeAll
    static void setupAll() {
        final var config = ConfigFactory.load("testing.conf").getConfig("dpc.aggregation");
        exportPath = config.getString("exportPath");
        AggregationEngine.setGlobalErrorHandler();
        ContextUtils.prefetchResourceModels(fhirContext, JobQueueBatch.validResourceTypes);
    }

    @BeforeEach
    void setupEach() {
        queue = Mockito.spy(new MemoryBatchQueue(10));
        bbclient = Mockito.spy(new MockBlueButtonClient(fhirContext));
        var operationalConfig = new OperationsConfig(1000, exportPath, 500);
        engine = Mockito.spy(new AggregationEngine(aggregatorID, bbclient, queue, fhirContext, metricRegistry, operationalConfig));
        AggregationEngine.setGlobalErrorHandler();
    }

    @Test
    public void testHealthyEngine() throws InterruptedException {

        final var orgID = UUID.randomUUID();

        queue.createJob(
                orgID,
                TEST_PROVIDER_ID,
                Collections.singletonList("1"),
                Collections.singletonList(ResourceType.Patient)
        );

        AggregationEngineHealthCheck healthCheck = new AggregationEngineHealthCheck(engine);
        Assert.assertTrue(healthCheck.check().isHealthy());

        ExecutorService executor = Executors.newCachedThreadPool();
        executor.execute(engine);
        executor.awaitTermination(2, TimeUnit.SECONDS);

        Assert.assertTrue(healthCheck.check().isHealthy());

    }

    @Test
    public void testHealthyEngineWhenJobBatchErrors() throws InterruptedException {

        Mockito.doThrow(new RuntimeException("Error")).when(bbclient).requestPatientFromServer(Mockito.anyString());

        final var orgID = UUID.randomUUID();

        queue.createJob(
                orgID,
                TEST_PROVIDER_ID,
                Collections.singletonList("1"),
                Collections.singletonList(ResourceType.Patient)
        );

        AggregationEngineHealthCheck healthCheck = new AggregationEngineHealthCheck(engine);
        Assert.assertTrue(healthCheck.check().isHealthy());

        ExecutorService executor = Executors.newCachedThreadPool();
        executor.execute(engine);
        executor.awaitTermination(2, TimeUnit.SECONDS);

        Assert.assertTrue(healthCheck.check().isHealthy());

    }

    @Test
    public void testHealthyEngineWhenClaimBatchErrors() throws InterruptedException {

        final var orgID = UUID.randomUUID();

        queue.createJob(
                orgID,
                TEST_PROVIDER_ID,
                Collections.singletonList("1"),
                Collections.singletonList(ResourceType.Patient)
        );

        Mockito.doThrow(new RuntimeException("Error")).when(queue).claimBatch(Mockito.any(UUID.class));

        AggregationEngineHealthCheck healthCheck = new AggregationEngineHealthCheck(engine);
        Assert.assertTrue(healthCheck.check().isHealthy());

        ExecutorService executor = Executors.newCachedThreadPool();
        executor.execute(engine);
        executor.awaitTermination(2, TimeUnit.SECONDS);

        Assert.assertTrue(healthCheck.check().isHealthy());

    }

    @Test
    public void testHealthyEngineWhenQueueOperationsError() throws InterruptedException {
        Mockito.doThrow(new RuntimeException("Error")).when(queue).completePartialBatch(Mockito.any(JobQueueBatch.class), Mockito.any(UUID.class));

        final var orgID = UUID.randomUUID();

        queue.createJob(
                orgID,
                TEST_PROVIDER_ID,
                Collections.singletonList("1"),
                Collections.singletonList(ResourceType.Patient)
        );

        AggregationEngineHealthCheck healthCheck = new AggregationEngineHealthCheck(engine);
        Assert.assertTrue(healthCheck.check().isHealthy());

        ExecutorService executor = Executors.newCachedThreadPool();
        executor.execute(engine);
        executor.awaitTermination(2, TimeUnit.SECONDS);

        Assert.assertTrue(healthCheck.check().isHealthy());
    }
}
