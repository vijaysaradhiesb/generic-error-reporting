package com.integ.error.generic.reporting;

import org.apache.commons.lang.exception.ExceptionUtils;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

public class FailureDetails implements Serializable {
    private String exceptionClass;
    private String message;
    private String stackTrace;
    private String hostname;
    private String ip;
    private String recordTime;

    public FailureDetails(Throwable e) {
        exceptionClass = e.getClass().getCanonicalName();
        message = e.getMessage();
        stackTrace = ExceptionUtils.getFullStackTrace(e);
        recordTime = new Date().toString();

        try {
            InetAddress localhost = InetAddress.getLocalHost();
            hostname = localhost.getHostName();
            ip = localhost.getHostAddress();
        } catch (UnknownHostException ignored) {
            //no-op
        }
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public String getMessage() {
        return message;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public String getHostname() {
        return hostname;
    }

    public String getIp() {
        return ip;
    }

    public String getRecordTime() {
        return recordTime;
    }

    @Override
    public String toString() {
        return "FailureDetails{" +
                "exceptionClass='" + exceptionClass + '\'' +
                ", hostname='" + hostname + '\'' +
                ", ip='" + ip + '\'' +
                ", recordTime='" + recordTime + '\'' +
                ", message='" + message + '\'' +
                ", stackTrace='" + stackTrace + '\'' +
                '}';
    }
}
