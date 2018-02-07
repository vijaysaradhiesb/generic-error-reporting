package com.integ.error.generic.reporting.spring;

import org.apache.karaf.deployer.spring.SpringURLHandler;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;


//FIXME- to be moved to tooling class.
public class Handler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return new SpringURLHandler().openConnection(u);
    }
}
