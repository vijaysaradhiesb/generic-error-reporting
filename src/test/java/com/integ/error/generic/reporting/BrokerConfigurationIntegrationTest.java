package com.integ.error.generic.reporting;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/**
 *
 * This class should be considered as complete example of how to test services with Pax Exam.
 * Most of this java file, should be moved to common tooling class. Subject to priority.
 *
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class BrokerConfigurationIntegrationTest extends IntegrationTestsSupport {
    private static boolean INITIALISED = false;

    @Before
    public void setupContext() throws Exception {
        producerTemplate = new DefaultProducerTemplate(gerContext);
        producerTemplate.start();

        if (!INITIALISED) {
            INITIALISED = true;

            gerContext.setTracing(true);
            gerContext.setUseMDCLogging(true);

            //For test purpose we need to shutdown this route to be able to consume this message
            gerContext.getRoute("gerDLQAlertsProcessRoute").getConsumer().stop();

            gerContext.addRoutes(new RouteBuilder() {
                public void configure() throws Exception {
                    from("direct:start") //Starting test here and producing to neverland1 in transaction
                            .transacted("PROPAGATION_REQUIRES_NEW")
                            .setExchangePattern(ExchangePattern.InOnly)
                            .to("jmstx:queue:neverland1");

                    from("jmstx:queue:neverland1") //Consumed in transaction and throwing exception to force rollback (with 1 retry)
                            .transacted("PROPAGATION_MANDATORY")
                            .setExchangePattern(ExchangePattern.InOnly)
                            .throwException(new Exception("Sorry, we are closed here!"))
                            .to("log:test");

                    from("jmstx:queue:DLQAlerts") //Due to broker config we expect to receive mirror message here, so consuming and throwing to simulate GER Error (with 1 retry)
                            .transacted("PROPAGATION_MANDATORY")
                            .setExchangePattern(ExchangePattern.InOnly)
                            .throwException(new Exception("Sorry, we are closed here too!"))
                            .to("log:test");

                    from("jmstx:queue:DLQUndelivered.DLQAlerts") //As DLQAlerts retries are exceeded, we are expecting to receive message here as configured in broker
                            .transacted("PROPAGATION_MANDATORY")
                            .setExchangePattern(ExchangePattern.InOnly)
                            .to("mock:result");

                    from("jmstx:DLQ.>")
                            .transacted("PROPAGATION_MANDATORY")
                            .to("mock:dlq");
                }
            });
        }
    }

    @Test
    public void testSingleUser() throws Exception {
        MockEndpoint dlq = MockEndpoint.resolve(gerContext, "mock:dlq");
        MockEndpoint result = MockEndpoint.resolve(gerContext, "mock:result");

        dlq.expectedMessageCount(1); //We are expecting the message we are sending will go to DLQ.* and DLQAlerts
        result.expectedMessageCount(1);
        result.setResultWaitTime(20000);

        Thread.sleep(5000);

        producerTemplate.sendBody("direct:start", "Single ticket to neverland please!");

        result.assertIsSatisfied();
        dlq.assertIsSatisfied();
    }
}
