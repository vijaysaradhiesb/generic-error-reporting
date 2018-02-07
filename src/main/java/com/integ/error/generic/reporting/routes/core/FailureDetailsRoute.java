package com.integ.error.generic.reporting.routes.core;

import com.integ.error.generic.reporting.FailureDetails;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailureDetailsRoute extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(FailureDetailsRoute.class);
    public static final String INTEG_GENERIC_ERROR_REPORTING = "integGenericErrorReportingEndpoint";

    @Override
    public void configure() throws Exception {

        /**
         * Puts an exception in Hazelcast for failed exhange.
         * Steps 3,4,5 from architecture picture
         */
        //@formatter:off
        from("direct-vm:" + INTEG_GENERIC_ERROR_REPORTING).routeId("gerFailureDetailsProcessRoute")
                .log(LoggingLevel.INFO, "Started recording failure details for destination \"${header.JMSDestination}\", msg. id: \"${header.JMSMessageID}\"")
                .setHeader("CURRENT_KEY", simple("${header.JMSMessageID}-${bean:uuid.randomUUID()}"))
                .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.GET_OPERATION))
                .choice()
                    .when(header("JMSMessageID").isNotNull())
                        .setHeader(HazelcastConstants.OBJECT_ID, header("JMSMessageID"))
                        .to("hazelcastGER:map:{{ger.hz.map.GER_MAP.name}}")

                        .setBody(simple("${body},${header.CURRENT_KEY}"))
                        .setHeader(HazelcastConstants.OBJECT_ID, header("JMSMessageID"))
                        .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.PUT_OPERATION))
                        .to("hazelcastGER:map:{{ger.hz.map.GER_MAP.name}}")

                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                Exception e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                                if (e == null) {
                                    e = exchange.getProperty(Exchange.EXCEPTION_HANDLED, Exception.class);
                                }

                                if (e != null) {
                                    exchange.getIn().setBody(new FailureDetails(e));
                                } else {
                                    LOG.warn("Exception nout found in exchange...");
                                }
                            }
                        })
                        .setHeader(HazelcastConstants.OBJECT_ID, header("CURRENT_KEY"))
                        .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.PUT_OPERATION))
                        .to("hazelcastGER:map:{{ger.hz.map.GER_MAP.name}}-values")

                        .log(LoggingLevel.INFO, "Failure details has been recorded for destination \"${header.JMSDestination}\", msg. id: \"${header.JMSMessageID}\"")
                    .otherwise()
                        .log(LoggingLevel.INFO, "Non JMS route, exception details will be ignored")
                    .end()
                .end();
        //@formatter:on
    }
}
