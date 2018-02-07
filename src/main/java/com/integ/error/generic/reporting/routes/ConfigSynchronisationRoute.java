package com.integ.error.generic.reporting.routes;

import com.integ.error.generic.reporting.domain.GenericErrorReportingPolicy;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hazelcast.HazelcastConstants;

import javax.persistence.EntityManager;


public class ConfigSynchronisationRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        /**
         * Consumes original messages from DLQAlerts and get correlated Failures Details from Hazelcast
         * Implements steps 7, 8, 9 and 10
         */
        from("jpa:" + GenericErrorReportingPolicy.class.getCanonicalName() +
                "?maxMessagesPerPoll=99999" +
                "&persistenceUnit=generic_error_handler_pu" +
                "&consumer.delay={{ger.config.synchronisation.delay}}" +
                "&consumeDelete=false" +
                "&consumeLockEntity=false")
                .routeId("gerConfigSynchronizationRoute")

                .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.PUT_OPERATION))
                .setHeader(HazelcastConstants.OBJECT_ID, simple("${body.destination}"))
                .to("hazelcastGER:map:{{ger.hz.map.GER_MAP.name}}-config")
                .log(LoggingLevel.DEBUG, "Error Reporting configuration updated for policy: \"${body.getId()}\", destination: \"${body.getDestination()}\"")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        //This is due to I£$&!!!!&$%£!!!!!! bug in Camel JPA!!!
                        exchange.getIn().getHeader("CamelEntityManager", EntityManager.class).clear();
                    }
                });
    }
}