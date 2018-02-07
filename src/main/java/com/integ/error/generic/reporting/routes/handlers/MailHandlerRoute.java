package com.integ.error.generic.reporting.routes.handlers;

import com.integ.error.generic.reporting.FailureDetails;
import com.integ.error.generic.reporting.domain.GenericErrorReportingPolicy;
import com.integ.error.generic.reporting.domain.MailHandler;
import com.integ.error.generic.reporting.routes.core.DLQAlertsRoute;
import com.integ.error.generic.reporting.routes.core.FailureDetailsRoute;
import com.integ.mailer.Attachment;
import com.integ.mailer.MailMessage;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

public class MailHandlerRoute extends RouteBuilder {
    public static final String VELOCITY_SUBJECT_RESULT = "VELOCITY_SUBJECT_RESULT";

    private String mailEndpoint;

    @Override
    public void configure() throws Exception {

        /**
         * Mail handler which uses camel velocity to handle original message and exceptions occured
         *
         * Headers available:
         * ORIGINAL_JMS_MESSAGE- JmsMessage
         * ORIGINAL_DESTINATION- String
         * ORIGINAL_TIMESTAMP- Original message sent time (unix timestamp)
         * ORIGINAL_SIZE- Original message size
         * EXTRACTED_BODY- ExtractedBody object, with possibly truncated body and with isTruncated, isEmpty methods
         * FAILURES_DETAILS- List<Exception>
         * REPORTING_CONFIG- GenericErrorReportingPolicy matching destination containing configuration
         * HANDLER_CONFIG- Handler, in this case MailHandler configuration
         * body- Handler, in this case MailHandler configuration
         *
         */
        //@formatter:off
        from("direct-vm:" + FailureDetailsRoute.INTEG_GENERIC_ERROR_REPORTING + ".handle.MAIL")
                        .routeId("gerHandlerMail")
                        .transacted("PROPAGATION_MANDATORY")
                        .setExchangePattern(ExchangePattern.InOnly)

                //Processing Subject template
                .setHeader("CamelVelocityTemplate", simple("${header." + DLQAlertsRoute.HANDLER_CONFIG + ".subject}"))
                .to("velocity:dummy")
                .setHeader(VELOCITY_SUBJECT_RESULT, body())

                //Processing body template
                .setHeader("CamelVelocityTemplate", simple("${header." + DLQAlertsRoute.HANDLER_CONFIG + ".bodyVelocityTemplate}"))
                .to("velocity:dummy")

                //Composing email body
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        org.apache.camel.Message in = exchange.getIn();
                        GenericErrorReportingPolicy policy = in.getHeader(DLQAlertsRoute.REPORTING_CONFIG, GenericErrorReportingPolicy.class);
                        MailHandler config = in.getHeader(DLQAlertsRoute.HANDLER_CONFIG, MailHandler.class);
                        List failuresDetails = in.getHeader(DLQAlertsRoute.FAILURES_DETAILS, List.class);
                        DLQAlertsRoute.ExtractedBody body = in.getHeader(DLQAlertsRoute.EXTRACTED_BODY, DLQAlertsRoute.ExtractedBody.class);

                        // construct email message
                        MailMessage mailMessage = new MailMessage();
                        mailMessage.getTos().addAll(config.getTo());
                        mailMessage.setSender(config.getSender());
                        mailMessage.setFrom(config.getFrom());
                        mailMessage.setReplyTo(config.getReplyTo());
                        mailMessage.setSubject(in.getHeader(VELOCITY_SUBJECT_RESULT, String.class));
                        mailMessage.setContentType(config.getContentType());
                        mailMessage.setBody(in.getBody(String.class));

                        // add attachments

                        // Original body
                        if (!body.isEmpty() && !body.isNulled()) {
                            Attachment bodyAttachment = new Attachment();
                            bodyAttachment.setContent(body.getBodyBytes());
                            bodyAttachment.setAttachmentName(!body.isTruncated() ? "original_message.data" : "truncated_message.data");
                            bodyAttachment.setMimeType("application/octet-stream");
                            mailMessage.getAttachments().add(bodyAttachment);
                        }

                        //Exceptions
                        if (failuresDetails != null) {
                            int c = 1;
                            for (Object o : failuresDetails) {
                                FailureDetails failureDetails = (FailureDetails) o;
                                Attachment exAttachment = new Attachment();
                                exAttachment.setAttachmentName("exception" + c++ +".txt");
                                exAttachment.setContent(failureDetails.toString().getBytes());
                                exAttachment.setMimeType("text/plain");
                                mailMessage.getAttachments().add(exAttachment);
                            }
                        }

                        in.setBody(mailMessage);
                    }
                })
                .log(LoggingLevel.INFO, "Sending Mail error report to for \"${header." + DLQAlertsRoute.ORIGINAL_DESTINATION + "}\", msg. id: \"${header.JMSMessageID}\"")
                .convertBodyTo(String.class)
                .to(mailEndpoint);

    }//@formatter:on

    @Value("${ger.xmlMailerEndpoint}")
    public void setMailEndpoint(String mailEndpoint) {
        this.mailEndpoint = mailEndpoint;
    }
}
