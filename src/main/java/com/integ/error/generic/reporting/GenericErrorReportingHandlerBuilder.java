package com.integ.error.generic.reporting;

import com.integ.error.generic.reporting.routes.core.FailureDetailsRoute;
import org.apache.camel.ExchangePattern;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.processor.RedeliveryErrorHandler;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 *
 * This is a hybrid error handler of DeadLetterChannel and TransactionalErrorHandler.
 * In fact this error handler is executed by TransactionalErrorHandler.
 * What is happening here is that error handler is defining deadLetter processor,
 * but is not overriding isDeadLetterChannel() { return true; } like DeadLetterChannel
 * do making TransactionErorrHandler thinking it have to be roll backed.
 *
 */
public class GenericErrorReportingHandlerBuilder extends DefaultErrorHandlerBuilder {
    public GenericErrorReportingHandlerBuilder() {
        setDeadLetterUri("direct-vm:" + FailureDetailsRoute.INTEG_GENERIC_ERROR_REPORTING);
    }

    public GenericErrorReportingHandlerBuilder(String nonDefaultFailureDetailsRecorderEndpoint) {
        setDeadLetterUri(nonDefaultFailureDetailsRecorderEndpoint);
    }

    @Override
    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        //Configure dead letter processor
        if (deadLetter == null) {
            ObjectHelper.notEmpty(deadLetterUri, "deadLetterUri", this);
            deadLetter = routeContext.getCamelContext().getEndpoint(deadLetterUri);
            if (deadLetter == null) {
                throw new NoSuchEndpointException(deadLetterUri);
            }
        }

        RedeliveryErrorHandler answer = new RedeliveryErrorHandler(routeContext.getCamelContext(), processor, getLogger(), getOnRedelivery(),
                getRedeliveryPolicy(), getFailureProcessor(), getDeadLetterUri(), false, isUseOriginalMessage(),
                getRetryWhilePolicy(routeContext.getCamelContext()), getExecutorService(routeContext.getCamelContext()), getOnPrepareFailure(),getOnExceptionOccurred()) {
        };

        // configure error handler before we can use it
        configure(routeContext, answer);
        return answer;
    }

    @Override
    public Processor getFailureProcessor() {
        if (failureProcessor == null) {
            // force MEP to be InOnly so when sending to DLQ we would not expect a reply if the MEP was InOut
            failureProcessor = new SendProcessor(deadLetter, ExchangePattern.InOnly);
        }
        return failureProcessor;
    }
}