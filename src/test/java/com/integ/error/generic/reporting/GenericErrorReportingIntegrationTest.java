package com.integ.error.generic.reporting;

import com.integ.mailer.MailMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.h2.tools.RunScript;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import javax.xml.bind.JAXBException;
import java.io.FileReader;
import java.io.IOException;

/**
 *
 * This class should be considered as complete example of how to test services with Pax Exam.
 * Most of this java file, should be moved to common tooling class. Subject to priority.
 *
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class GenericErrorReportingIntegrationTest extends IntegrationTestsSupport {
    private static boolean INITIALISED = false;

    @Before
    public void setupContext() throws Exception {
        producerTemplate = new DefaultProducerTemplate(gerContext);
        producerTemplate.start();

        if (!INITIALISED) {
            INITIALISED = true;

            RunScript.execute(dataSource.getConnection(), new FileReader("etc/database.sql"));

            CamelContext mailerContext = getOsgiService(CamelContext.class, "(camel.context.name=com.integ.integration.mailer)", 20000L);
            mailerContext.stop();

            gerContext.setTracing(true);
            gerContext.setUseMDCLogging(true);

            RouteBuilder testRoutes = new RouteBuilder() {
                public void configure() throws Exception {
                    from("direct:start1")
                            .transacted("PROPAGATION_REQUIRES_NEW")
                            .setExchangePattern(ExchangePattern.InOnly)
                            .to("jmstx:queue:neverland1");
                    from("direct:start2")
                            .transacted("PROPAGATION_REQUIRES_NEW")
                            .setExchangePattern(ExchangePattern.InOnly)
                            .to("jmstx:queue:neverland2");
                    from("direct:startX")
                            .transacted("PROPAGATION_REQUIRES_NEW")
                            .setExchangePattern(ExchangePattern.InOnly)
                            .to("jmstx:queue:neverlandX");

                    from("jmstx:queue:neverland1")
                            .transacted("PROPAGATION_MANDATORY")
                            .setExchangePattern(ExchangePattern.InOnly)
                            .throwException(EXCEPTION)
                            .to("log:test");
                    from("jmstx:queue:neverland2")
                            .transacted("PROPAGATION_MANDATORY")
                            .setExchangePattern(ExchangePattern.InOnly)
                            .throwException(EXCEPTION)
                            .to("log:test");
                    from("jmstx:queue:neverlandX")
                            .transacted("PROPAGATION_MANDATORY")
                            .setExchangePattern(ExchangePattern.InOnly)
                            .throwException(EXCEPTION)
                            .to("log:test");
                    from("direct:start3")
                            .transacted("PROPAGATION_REQUIRES_NEW")
                            .throwException(EXCEPTION)
                            .to("log:test");

                    from("jmstx:queue:esb.mailer.xmlMessage")
                            .transacted("PROPAGATION_MANDATORY")
                            .setExchangePattern(ExchangePattern.InOnly)
                            .to("mock:result");

                    from("jmstx:DLQ.>")
                            .transacted("PROPAGATION_MANDATORY")
                            .to("mock:dlq");
                }
            };
            testRoutes.setErrorHandlerBuilder(new GenericErrorReportingHandlerBuilder());

            gerContext.addRoutes(testRoutes);
        }
    }

    @Test
    public void testGenericErrorReportingMailHandlerNeverland1Policy() throws IOException, InterruptedException, JAXBException {
        MockEndpoint dlq = MockEndpoint.resolve(gerContext, "mock:dlq");
        MockEndpoint result = MockEndpoint.resolve(gerContext, "mock:result");

        dlq.expectedMessageCount(1); //We are expecting the message we are sending will got to DLQ.* and DLQAlerts
        result.expectedMessageCount(1);
        result.setResultWaitTime(30000);

        Thread.sleep(5000);

        producerTemplate.sendBody("direct:start1", "Single ticket to neverland please 1!");

        Thread.sleep(30000);

        result.assertIsSatisfied();
        dlq.assertIsSatisfied();

        MailMessage mailMessage = result.getExchanges().get(0).getIn().getBody(MailMessage.class);
        assertEquals("Attachments received is not equal to expected 3", 3, mailMessage.getAttachments().size());
        assertTrue("Subject differs, probably wrong policy was picked up", mailMessage.getSubject().contains("HURRAY"));
    }

    @Test
    public void testGenericErrorReportingMailHandlerNeverland1SubjectEvaluation() throws IOException, InterruptedException, JAXBException {
        MockEndpoint dlq = MockEndpoint.resolve(gerContext, "mock:dlq");
        MockEndpoint result = MockEndpoint.resolve(gerContext, "mock:result");

        dlq.expectedMessageCount(1); //We are expecting the message we are sending will got to DLQ.* and DLQAlerts
        result.expectedMessageCount(1);
        result.setResultWaitTime(30000);

        Thread.sleep(5000);

        producerTemplate.sendBody("direct:start1", "Single ticket to neverland please 2!");

        result.assertIsSatisfied();
        dlq.assertIsSatisfied();

        MailMessage mailMessage = result.getExchanges().get(0).getIn().getBody(MailMessage.class);
        assertEquals("Attachments received is not equal to expected 3", 3, mailMessage.getAttachments().size());
        assertTrue("Subject differs, prbably was not transformerd", mailMessage.getSubject().contains("neverland1"));
    }

    @Test
    public void testGenericErrorReportingBlindHandler_IgnoreReport() throws IOException, InterruptedException, JAXBException {
        MockEndpoint dlq = MockEndpoint.resolve(gerContext, "mock:dlq");
        MockEndpoint result = MockEndpoint.resolve(gerContext, "mock:result");

        dlq.expectedMessageCount(1);
        result.expectedMessageCount(0);
        result.setResultWaitTime(30000);

        Thread.sleep(5000);

        producerTemplate.sendBody("direct:start2", "Single ticket to neverland please 3!");

        Thread.sleep(40000);

        result.assertIsSatisfied();
        dlq.assertIsSatisfied();
    }

    @Test
    public void testGenericErrorReportingMailHandlerDefaultPolicy() throws IOException, InterruptedException, JAXBException {
        MockEndpoint dlq = MockEndpoint.resolve(gerContext, "mock:dlq");
        MockEndpoint result = MockEndpoint.resolve(gerContext, "mock:result");

        dlq.expectedMessageCount(1); //We are expecting the message we are sending will got to DLQ.* and DLQAlerts
        result.expectedMessageCount(1);
        result.setResultWaitTime(30000);

        Thread.sleep(5000);

        producerTemplate.sendBody("direct:startX", "Single ticket to neverland please 4!");

        result.assertIsSatisfied();
        dlq.assertIsSatisfied();

        MailMessage mailMessage = result.getExchanges().get(0).getIn().getBody(MailMessage.class);
        assertEquals("Attachments received is not equal to expected 3", 3, mailMessage.getAttachments().size());
        assertTrue("Subject differs, probably wrong policy was picked up", mailMessage.getSubject().contains("neverlandX"));
        assertTrue("Subject differs, probably wrong policy was picked up", !mailMessage.getSubject().contains("HURRAY"));
    }

    @Test
    public void testHugeMessageTruncated() throws IOException, InterruptedException, JAXBException {
        MockEndpoint dlq = MockEndpoint.resolve(gerContext, "mock:dlq");
        MockEndpoint result = MockEndpoint.resolve(gerContext, "mock:result");

        dlq.expectedMessageCount(1); //We are expecting the message we are sending will got to DLQ.* and DLQAlerts
        result.expectedMessageCount(1);
        result.setResultWaitTime(30000);

        Thread.sleep(5000);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1024*1024; i ++) {
            sb.append("1234567890");
        }

        producerTemplate.sendBody("direct:startX", sb.toString());

        result.assertIsSatisfied();
        dlq.assertIsSatisfied();

        MailMessage mailMessage = result.getExchanges().get(0).getIn().getBody(MailMessage.class);
        assertEquals("Attachments received is not equal to expected 3", 3, mailMessage.getAttachments().size());
        assertTrue("Body is not truncated", mailMessage.getAttachments().get(0).getAttachmentName().contains("truncated"));
    }

    @Test
    public void testEmptyBody() throws IOException, InterruptedException, JAXBException {
        MockEndpoint dlq = MockEndpoint.resolve(gerContext, "mock:dlq");
        MockEndpoint result = MockEndpoint.resolve(gerContext, "mock:result");

        dlq.expectedMessageCount(1); //We are expecting the message we are sending will got to DLQ.* and DLQAlerts
        result.expectedMessageCount(1);
        result.setResultWaitTime(30000);

        Thread.sleep(5000);

        producerTemplate.sendBody("direct:startX", "");

        result.assertIsSatisfied();
        dlq.assertIsSatisfied();

        MailMessage mailMessage = result.getExchanges().get(0).getIn().getBody(MailMessage.class);
        assertEquals("Attachments received is not equal to expected 2", 2, mailMessage.getAttachments().size());
    }

    @Test
    public void testNullBody() throws IOException, InterruptedException, JAXBException {
        MockEndpoint dlq = MockEndpoint.resolve(gerContext, "mock:dlq");
        MockEndpoint result = MockEndpoint.resolve(gerContext, "mock:result");

        dlq.expectedMessageCount(1); //We are expecting the message we are sending will got to DLQ.* and DLQAlerts
        result.expectedMessageCount(1);
        result.setResultWaitTime(30000);

        Thread.sleep(5000);

        producerTemplate.sendBody("direct:startX", null);

        result.assertIsSatisfied();
        dlq.assertIsSatisfied();

        MailMessage mailMessage = result.getExchanges().get(0).getIn().getBody(MailMessage.class);
        assertEquals("Attachments received is not equal to expected 2", 2, mailMessage.getAttachments().size());
    }

    @Test
    public void testNonJmsRouteNPEBug() throws Exception {
        try {
            producerTemplate.sendBody("direct:start3", null);
        } catch (Exception ex) {
            if (ex.getCause().getCause() != EXCEPTION) {
                throw ex;
            }
        }
    }
}
