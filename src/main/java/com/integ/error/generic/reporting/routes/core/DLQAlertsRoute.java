package com.integ.error.generic.reporting.routes.core;

import com.integ.error.generic.reporting.FailureDetails;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.command.Message;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.springframework.beans.factory.annotation.Value;

import javax.jms.JMSException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DLQAlertsRoute extends RouteBuilder {

    public static final String HANDLER_CONFIG = "HANDLER_CONFIG";
    public static final String REPORTING_CONFIG = "REPORTING_CONFIG";
    public static final String FAILURES_DETAILS = "FAILURES_DETAILS";
    public static final String ORIGINAL_JMS_MESSAGE = "ORIGINAL_JMS_MESSAGE";
    public static final String ORIGINAL_DESTINATION = "ORIGINAL_DESTINATION";
    public static final String ORIGINAL_TIMESTAMP = "ORIGINAL_TIMESTAMP";
    public static final String ORIGINAL_SIZE = "ORIGINAL_SIZE";
    public static final String EXTRACTED_BODY = "EXTRACTED_BODY";
    public static final String GER_DLQ_ALERTS_PROCESS_ROUTE_ID = "gerDLQAlertsProcessRoute";

    private long waitForHazelcastInMs;

    //@formatter:off
    @Override
    public void configure() throws Exception {

        /**
         * Consumes original messages from DLQAlerts and get correlated Failures Details from Hazelcast
         */
        from("jmstx:queue:DLQAlerts").routeId(GER_DLQ_ALERTS_PROCESS_ROUTE_ID)
                        .transacted("PROPAGATION_MANDATORY")
                        .setExchangePattern(ExchangePattern.InOnly)
                //Unboxing body
                .setBody(simple("${bodyAs(" + JmsMessage.class.getCanonicalName() + ")}"))
                .setBody(simple("${body.getJmsMessage()}"))

                //Setting common headers
                .setHeader(ORIGINAL_JMS_MESSAGE, body())
                .setHeader(ORIGINAL_TIMESTAMP, simple("${body.timestamp}"))
                .setHeader(ORIGINAL_SIZE, simple("${body.timestamp}"))
                .setHeader(ORIGINAL_DESTINATION, simple("${body.getOriginalDestination().physicalName}"))
                .setHeader(EXTRACTED_BODY, method(this, "extractBody"))

                //let's wait some time for Failure Details
                .delay(waitForHazelcastInMs)

                //Getting comma separated list of keys with exceptions
                .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.GET_OPERATION))
                .setHeader(HazelcastConstants.OBJECT_ID, header("JMSMessageID"))
                .to("hazelcastGER:map:{{ger.hz.map.GER_MAP.name}}")
                .split(body().tokenize(","), new ExceptionsAggregator()).shareUnitOfWork().stopOnException()
                    .choice().when(simple("${body.length} > 1"))

                        //Getting exception for each key
                        .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.GET_OPERATION))
                        .setHeader(HazelcastConstants.OBJECT_ID, body())
                        .to("hazelcastGER:map:{{ger.hz.map.GER_MAP.name}}-values")
                    .end()
                .end()
                .setHeader(FAILURES_DETAILS, body())

                //Getting handler configuration
                .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.GET_OPERATION))
                .setHeader(HazelcastConstants.OBJECT_ID, header(ORIGINAL_DESTINATION))
                .to("hazelcastGER:map:{{ger.hz.map.GER_MAP.name}}-config")
                .choice().when(body().isNull()) // If policy for this destination was not found, then applying default.
                    .log(LoggingLevel.DEBUG, "Dedicated error handler not found, using default one for destination \"${header." + DLQAlertsRoute.ORIGINAL_DESTINATION + "}\", msg. id: \"${header.JMSMessageID}\"")
                    .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.GET_OPERATION))
                    .setHeader(HazelcastConstants.OBJECT_ID, constant("*"))
                    .to("hazelcastGER:map:{{ger.hz.map.GER_MAP.name}}-config")
                .end()
                .log(LoggingLevel.INFO, "Handling error for destination \"${header." + DLQAlertsRoute.ORIGINAL_DESTINATION + "}\", msg. id: \"${header.JMSMessageID}\", using handler id \"${body.getId()}\"")
                .setHeader(REPORTING_CONFIG, body())

                //Deliver to handlers
                //Handlers have to reuse existing transaction!!!
                //Handlers should not change existing headers!!!
                .choice().when(simple("${body.getHandlers().size()} > 0"))
                    .log(LoggingLevel.INFO, "Error Reporting found \"${body.getHandlers().size()}\" handlers for \"${header." + DLQAlertsRoute.ORIGINAL_DESTINATION + "}\", msg. id:\" ${header.JMSMessageID}\"")
                    .split(simple("${body.getHandlers()}")).shareUnitOfWork().stopOnException()
                        .log(LoggingLevel.INFO, "Error Report for \"${header." + DLQAlertsRoute.ORIGINAL_DESTINATION + "}\", msg. id: \"${header.JMSMessageID}\" will be processed using handler id: \"${body.getId()}\"")
                        .setHeader(HANDLER_CONFIG, body())
                        .recipientList(simple("direct-vm:" + FailureDetailsRoute.INTEG_GENERIC_ERROR_REPORTING + ".handle.${body.handlerType}"))
                        .end()
                    .endChoice()
                .otherwise()
                    .log(LoggingLevel.INFO, "Blind handler found for \"${header." + DLQAlertsRoute.ORIGINAL_DESTINATION + "}\", msg. id: \"${header.JMSMessageID}\", report will be ignored!")
                .end();

    }//@formatter:on

    public static class ExceptionsAggregator implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange aggregated, Exchange newMessage) {
            FailureDetails fd = newMessage.getIn().getBody(FailureDetails.class);
            if (aggregated == null) {
                aggregated = newMessage;
                aggregated.getIn().setBody(new ArrayList<FailureDetails>());
            }
            List failuresList = aggregated.getIn().getBody(List.class);
            failuresList.add(fd);

            return aggregated;
        }

    }

    public ExtractedBody extractBody(Exchange ex) throws JMSException {
        Message message = ex.getIn().getHeader(DLQAlertsRoute.ORIGINAL_JMS_MESSAGE, Message.class);
        return new ExtractedBody(message);
    }

    public class ExtractedBody {
        public static final int SIZE_2MB = 1024 * 1024 * 2;
        private Message message;
        private byte[] bodyBytes;
        private boolean empty;
        private boolean nulled;
        private boolean truncated;

        public ExtractedBody(Message message) throws JMSException {
            this.message = message;
            extract();
        }

        public byte[] getBodyBytes() {
            return bodyBytes;
        }

        public boolean isEmpty() {
            return empty;
        }

        public boolean isTruncated() {
            return truncated;
        }

        public boolean isNulled() {
            return nulled;
        }

        public void extract() throws JMSException {
            if (message instanceof ActiveMQTextMessage && ((ActiveMQTextMessage) message).getText() != null) {
                bodyBytes = ((ActiveMQTextMessage) message).getText().getBytes();
            } else if (message instanceof Message && message.getContent() != null) {
                bodyBytes = message.getContent().getData();
            }

            if (bodyBytes == null) {
                nulled = true;
                return;
            }

            if (bodyBytes.length == 0) {
                empty = true;
            } else if (bodyBytes.length > SIZE_2MB) {
                bodyBytes = Arrays.copyOf(bodyBytes, SIZE_2MB);
                truncated = true;
            }
        }
    }

    @Value("${ger.dlqAlertsWaitForHazelcastInMs}")
    public void setWaitForHazelcastInMs(long waitForHazelcastInMs) {
        this.waitForHazelcastInMs = waitForHazelcastInMs;
    }
}
