-- CREATE TABLE OPENJPA_SEQUENCE_TABLE (ID TINYINT NOT NULL, SEQUENCE_VALUE BIGINT, PRIMARY KEY (ID));
-- CREATE TABLE QGER_HANDLER (ID BIGINT NOT NULL, HANDLER_TYPE VARCHAR(31), PRIMARY KEY (ID));
-- CREATE TABLE QGER_MAIL_HANDLER (ID BIGINT NOT NULL, CONTENT_TYPE VARCHAR(64), SENDER VARCHAR(64), BODY_VELOCITY_TEMPLATE VARCHAR(32000), REPLY_TO VARCHAR(64), FROM_ VARCHAR(64), SUBJECT VARCHAR(256), PRIMARY KEY (ID));
-- CREATE TABLE QGER_MAIL_HANDLER_RCPTS (ECID BIGINT, MAIL_HANDLER_ID BIGINT, RECIPIENT VARCHAR(64));
-- CREATE TABLE QGER_POLICY (ID BIGINT NOT NULL, SEVERITY VARCHAR(64), VERSION_ BIGINT, DESTINATION VARCHAR(64), PRIMARY KEY (ID));
-- CREATE TABLE QGER_POLICY_TO_HANDLER (POLICY_ID BIGINT, HANDLER_ID BIGINT);


INSERT INTO QGER_POLICY(ID, DESTINATION, SEVERITY, VERSION_) VALUES (1, 'neverland1', 'CRITICAL', 1);
INSERT INTO QGER_POLICY(ID, DESTINATION, SEVERITY, VERSION_) VALUES (2, '*', 'CRITICAL', 1);
INSERT INTO QGER_POLICY(ID, DESTINATION, SEVERITY, VERSION_) VALUES (3, 'neverland2', 'IGNORE', 1);

INSERT INTO QGER_HANDLER(ID, HANDLER_TYPE) VALUES (1, 'MAIL');
INSERT INTO QGER_HANDLER(ID, HANDLER_TYPE) VALUES (2, 'MAIL');

INSERT INTO QGER_POLICY_TO_HANDLER(POLICY_ID, HANDLER_ID) VALUES (1, 1);
INSERT INTO QGER_POLICY_TO_HANDLER(POLICY_ID, HANDLER_ID) VALUES (2, 2);




INSERT INTO QGER_MAIL_HANDLER(ID, CONTENT_TYPE, SENDER, BODY_VELOCITY_TEMPLATE, FROM_, REPLY_TO, SUBJECT) VALUES (1, 'text/plain', 'Generic Error Reporting System', '
#if (${headers.FAILURES_DETAILS.size()} > 0)
    #set ($retriesRecorded = ${headers.FAILURES_DETAILS.size()})
#else
    #set ($retriesRecorded = 1)
#end

Failure occurred during processing your message consumed from "${headers.ORIGINAL_DESTINATION}" with id: "${headers.JMSMessageID}".

Your message was consumed at least ${retriesRecorded} times but failed processing.
#if (${headers.FAILURES_DETAILS.size()} > 0)
Basing on our records we found below failure causes:

    #foreach ($ex in ${headers.FAILURES_DETAILS})
        #set ($exNo = $foreach.index + 1)

-------------- Failure ${exNo} occurred on host: $ex.getHostname() ($ex.getIp()) at ${ex.getRecordTime()}
${ex.getMessage()}
#end

-------------- End failures list

Please note that the amount of failures recorded might be lower than retries count as depending of failure type, not all failure details may survive.
#end

You can find your original message body in attachment as well as list of exceptions recorded.


Attached original body is for review purpose only and should not be used for recovery.

To recover, please go to ActiveMQ`s WebConsole/Hawtio.
You should find there destination named "DLQ.${headers.ORIGINAL_DESTINATION}" containing your original message with JMSMessageID: "${headers.JMSMessageID}".
Your original message can be safely recovered there to it`s original destination: "${headers.ORIGINAL_DESTINATION}"

', 'abc@integ.com', 'abc@integ.com', 'HURRAY! Error occured when consuming from queue "${headers.ORIGINAL_DESTINATION}"');







INSERT INTO QGER_MAIL_HANDLER(ID, CONTENT_TYPE, SENDER, BODY_VELOCITY_TEMPLATE, FROM_, REPLY_TO, SUBJECT) VALUES (2, 'text/plain', 'Generic Error Reporting System', '
#if (${headers.FAILURES_DETAILS.size()} > 0)
    #set ($retriesRecorded = ${headers.FAILURES_DETAILS.size()})
#else
    #set ($retriesRecorded = 1)
#end

Failure occurred during processing your message consumed from "${headers.ORIGINAL_DESTINATION}" with id: "${headers.JMSMessageID}".

Your message was consumed at least ${retriesRecorded} times but failed processing.
#if (${headers.FAILURES_DETAILS.size()} > 0)
Basing on our records we found below failure causes:

    #foreach ($ex in ${headers.FAILURES_DETAILS})
        #set ($exNo = $foreach.index + 1)

-------------- Failure ${exNo} occurred on host: $ex.getHostname() ($ex.getIp()) at ${ex.getRecordTime()}
${ex.getMessage()}
#end

-------------- End failures list

Please note that the amount of failures recorded might be lower than retries count as depending of failure type, not all failure details may survive.
#end

You can find your original message body in attachment as well as list of exceptions recorded.


Attached original body is for review purpose only and should not be used for recovery.

To recover, please go to ActiveMQ`s WebConsole/Hawtio.
You should find there destination named "DLQ.${headers.ORIGINAL_DESTINATION}" containing your original message with JMSMessageID: "${headers.JMSMessageID}".
Your original message can be safely recovered there to it`s original destination: "${headers.ORIGINAL_DESTINATION}"

', 'abc@integ.com', 'abc@integ.com', 'Error occurred when consuming from queue "${headers.ORIGINAL_DESTINATION}"');




INSERT INTO QGER_MAIL_HANDLER_RCPTS(ECID, MAIL_HANDLER_ID, RECIPIENT) VALUES (0, 1, 'abc@integ.com');

INSERT INTO QGER_MAIL_HANDLER_RCPTS(ECID, MAIL_HANDLER_ID, RECIPIENT) VALUES (0, 2, 'abc@integ.com');