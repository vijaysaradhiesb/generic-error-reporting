package com.integ.error.generic.reporting;

import com.integ.error.generic.reporting.spring.Handler;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.osgi.ActiveMQServiceFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.options.RawUrlReference;
import org.ops4j.pax.exam.util.Filter;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.util.tracker.ServiceTracker;

import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.TransactionManager;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Random;
import java.util.UUID;

import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;


public class IntegrationTestsSupport extends Assert {
    protected final static Exception EXCEPTION = new Exception("Sorry, we are closed here!");

    protected static final String POLICY_DB_URL;
    protected static final String CLAIMS_DB_URL;

    private static final File BROKER1_XML;
    private static final File BROKER2_XML;

    private static final String HTTP_PORT1 = AvailablePortFinder.getNextAvailable(new Random().nextInt(60000) + 2000) + "";

    private static final String PORT1 = AvailablePortFinder.getNextAvailable(new Random().nextInt(60000) + 2000) + "";
    private static final String PORT2 = AvailablePortFinder.getNextAvailable(new Random().nextInt(60000) + 2000) + "";
    private static final String PORT3 = AvailablePortFinder.getNextAvailable(new Random().nextInt(60000) + 2000) + "";
    private static final String PORT4 = AvailablePortFinder.getNextAvailable(new Random().nextInt(60000) + 2000) + "";

    protected ProducerTemplate producerTemplate;

    @Inject
    protected TransactionManager transactionManager;

    @Inject
    protected BundleContext bundleContext;

    @Inject
    @Filter("(osgi.service.blueprint.compname=activeMQServiceFactory)")
    protected ManagedServiceFactory activeMQOSGiManagedServiceFactory;

    @Inject
    @Filter("(camel.context.name=generic-error-handling-camel-context)")
    protected CamelContext gerContext;

    @Inject
    @Filter("(&(integ.xads.default=true)(aries.xa.aware=true))")
    protected DataSource dataSource;

    @Inject
    @Filter("(osgi.unit.name=generic_error_handler_pu)")
    protected EntityManagerFactory entityManagerFactory;

    private ActiveMQServiceFactory mqServiceFactory = new ActiveMQServiceFactory();

    static {
        POLICY_DB_URL = "jdbc:h2:./target/paxexam/h2db/" + UUID.randomUUID().toString() + "/:testdb";
        CLAIMS_DB_URL = "jdbc:h2:./target/paxexam/h2db/" + UUID.randomUUID().toString() + "/:testdb";

        BROKER1_XML = new File("target/paxexam/broker1.xml"); //Broker from connectivity test resources
        BROKER2_XML = new File("target/paxexam/broker2.xml"); //Broker from connectivity test resources
    }

    @Configuration
    public Option[] configuration() throws IOException, URISyntaxException {
        printDebugInfo();

        //Preparing resources
        File targetClasses = new File("target/classes");
        assert(targetClasses.exists());

        FileUtils.copyURLToFile(this.getClass().getResource("/test-connectivity/broker1.xml"), BROKER1_XML); //Broker from connectivity test resources
        FileUtils.copyURLToFile(this.getClass().getResource("/test-connectivity/broker2.xml"), BROKER2_XML); //Broker from connectivity test resources

        //Describing test
        return new Option[]{
                //Karaf configuration
                karafDistributionConfiguration()
                .frameworkUrl(maven()
                        .groupId("org.apache.karaf")
                        .artifactId("apache-karaf")
                        .type("tar.gz")
                        .versionAsInProject())
                .karafVersion("2.4.0"),

                keepRuntimeFolder(),

                KarafDistributionOption.logLevel(LogLevelOption.LogLevel.ERROR),

                vmOption("-Djava.protocol.handler.pkgs=com.integ.error.generic.reporting|org.ops4j.pax.url"),
                vmOption(System.getenv("DEBUG") != null ? "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005" : "-DX_NOTHING_X=NOTHING"),

                bootClasspathLibrary(maven().groupId("org.apache.karaf.deployer").artifactId("org.apache.karaf.deployer.spring").versionAsInProject()),
                bootClasspathLibrary(streamBundle(TinyBundles.bundle().add(Handler.class).build())),
                bootClasspathLibrary(maven().groupId("org.ops4j.pax.url").artifactId("pax-url-reference").versionAsInProject()),
                bootClasspathLibrary(maven().groupId("org.ops4j.pax.url").artifactId("pax-url-assembly").versionAsInProject()),

                configureConsole().ignoreLocalConsole(),

                editConfigurationFilePut("etc/org.ops4j.pax.web.cfg", "org.osgi.service.http.port", HTTP_PORT1),
                editConfigurationFilePut("etc/org.apache.karaf.shell.cfg", "sshPort", PORT1),
                editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiRegistryPort", PORT2),
                editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiServerPort", PORT3),
                editConfigurationFilePut("etc/users.properties", "admin", "admin,admin,manager,viewer,Monitor, Operator, Maintainer, Deployer, Auditor, Administrator, SuperUser"),
                editConfigurationFilePut("etc/org.ops4j.pax.url.mvn.cfg", "org.ops4j.pax.url.mvn.repositories","http://nexus:8080/repository/integ/@id=integ"),

                editConfigurationFilePut("etc/com.integ.integration.mailer.cfg", "smtp.server", "localhost"),
                editConfigurationFilePut("etc/com.integ.integration.mailer.cfg", "smtp.user", ""),
                editConfigurationFilePut("etc/com.integ.integration.mailer.cfg", "smtp.pass", ""),
                editConfigurationFilePut("etc/com.integ.integration.mailer.cfg", "smtp.debug", ""),
                editConfigurationFilePut("etc/com.integ.integration.mailer.cfg", "smtp.auth", "false"),
                editConfigurationFilePut("etc/com.integ.integration.mailer.cfg", "override.email.address", "local@local.pax-exam"),
                editConfigurationFilePut("etc/com.integ.integration.mailer.cfg", "whitelist.email.address", "local@local.pax-exam"),
                editConfigurationFilePut("etc/com.integ.integration.mailer.cfg", "whitelist.domain.address", ""),
                editConfigurationFilePut("etc/com.integ.integration.mailer.cfg", "email.subject.prefix", ""),

                editConfigurationFilePut("etc/com.integ.error.generic.reporting.cfg", "ger.hz.group.name", "pax-exam"),
                editConfigurationFilePut("etc/com.integ.error.generic.reporting.cfg", "ger.hz.group.password", ""),
                editConfigurationFilePut("etc/com.integ.error.generic.reporting.cfg", "ger.hz.network.port", PORT4),
                editConfigurationFilePut("etc/com.integ.error.generic.reporting.cfg", "ger.hz.network.tcpip.members", "127.0.0.1"),

                //ActiveMQ configuration
                replaceConfigurationFile("etc/activemq_broker1.xml", BROKER1_XML),
                replaceConfigurationFile("etc/activemq_broker2.xml", BROKER2_XML),

                editConfigurationFilePut("etc/com.integ.connectivity.jms1.cfg", "jms1.osgi.name", "policy"),
                editConfigurationFilePut("etc/com.integ.connectivity.jms1.cfg", "jms1.osgi.default", "true"), //FIXME which default?
                editConfigurationFilePut("etc/com.integ.connectivity.jms1.cfg", "jms1.xa.uniqueResourceName", "jms1Policy"),
                editConfigurationFilePut("etc/com.integ.connectivity.jms1.cfg", "jms1.url", "failover:(vm://testBroker1?create=false)"), //Broker from connectivity test resources
                editConfigurationFilePut("etc/com.integ.connectivity.jms1.cfg", "jms1.user", "admin"),
                editConfigurationFilePut("etc/com.integ.connectivity.jms1.cfg", "jms1.password", "admin"),
                features(maven().groupId("com.integ.integration.product.connectivity").artifactId("jms-connectivity-provider").type("xml").classifier("features").versionAsInProject(),
                        "integ-connectivityJms-jms1"),

                //Configuring DB
                mavenBundle().groupId("com.h2database").artifactId("h2").versionAsInProject(),

                editConfigurationFilePut("etc/com.integ.connectivity.xads1.cfg", "xads1.osgi.name", "policy"),
                editConfigurationFilePut("etc/com.integ.connectivity.xads1.cfg", "xads1.osgi.default", "true"), //FIXME which default?
                editConfigurationFilePut("etc/com.integ.connectivity.xads1.cfg", "xads1.tm.uniqueResourceName", "xads1Policy"),
                editConfigurationFilePut("etc/com.integ.connectivity.xads1.cfg", "xads1.ds.class", "org.h2.jdbcx.JdbcDataSource"),
                editConfigurationFilePut("etc/com.integ.connectivity.xads1.cfg", "xads1.ds.minPoolSize", "15"),
                editConfigurationFilePut("etc/com.integ.connectivity.xads1.cfg", "xads1.ds.maxPoolSize", "50"),
                editConfigurationFilePut("etc/com.integ.connectivity.xads1.properties.cfg", "URL", POLICY_DB_URL),
                features(maven().groupId("com.integ.integration.product.connectivity").artifactId("xads-connectivity-provider").type("xml").classifier("features").versionAsInProject(),
                        "integ-connectivityXads-xads1"),

                //Configuring Hazelcast
                editConfigurationFilePut("etc/com.integ.connectivity.hazelcast1.cfg", "hazelcast1.osgi.name", "main"),


                //Features preparation
                features(maven().groupId("org.apache.karaf.assemblies.features").artifactId("enterprise").type("xml").classifier("features").versionAsInProject()),
                features(maven().groupId("org.apache.karaf.assemblies.features").artifactId("spring").type("xml").classifier("features").versionAsInProject()),
                features(maven().groupId("io.fabric8").artifactId("fabric8-karaf").type("xml").classifier("features").versionAsInProject()),
                features(maven().groupId("org.apache.cxf.karaf").artifactId("apache-cxf").type("xml").classifier("features").versionAsInProject()),
                features(maven().groupId("org.apache.activemq").artifactId("activemq-karaf").type("xml").classifier("features").versionAsInProject()),
                features(maven().groupId("org.apache.camel.karaf").artifactId("apache-camel").type("xml").classifier("features").versionAsInProject(), "camel-test"),


                features(new RawUrlReference("file://" + targetClasses.getAbsolutePath() + "/deployment/features.xml"),
                        "integ-genericErrorReporting-deps"),

                // Our bundle installation
                provision(bundle("assembly:" + targetClasses.getAbsolutePath())),

                editConfigurationFilePut("etc/com.integ.error.generic.reporting.cfg", "ger.config.synchronisation.delay", "1000"),
                editConfigurationFilePut("etc/com.integ.error.generic.reporting.cfg", "camel.trace.enable", "true"),
                vmOption("-Dhibernate.hbm2ddl.auto=create"),

                //Test database structure & data
                replaceConfigurationFile("etc/database.sql", new File("src/test/resources/database.sql"))
        };

    }

    @Before
    public void initBroker() throws ConfigurationException {
        mqServiceFactory.setBundleContext(((ActiveMQServiceFactory)activeMQOSGiManagedServiceFactory).getBundleContext());

        Dictionary<String, String> props1 = new Hashtable<>();
        props1.put("config", "etc/activemq_broker1.xml");
        props1.put("broker-name", "BROKER1");
        mqServiceFactory.updated("1", props1);

        Dictionary<String, String> props2 = new Hashtable<>();
        props2.put("config", "etc/activemq_broker2.xml");
        props2.put("broker-name", "BROKER2");
        mqServiceFactory.updated("2", props2);
    }

    @After
    public void cleanup() throws IOException, HeuristicRollbackException, HeuristicMixedException {
        MockEndpoint.resetMocks(gerContext);

        for (BrokerService broker : mqServiceFactory.getBrokersMap().values()) {
            broker.getPersistenceAdapter().deleteAllMessages();
        }
    }

    public void printDebugInfo() {
        System.out.println("----------------------------------------------------");
        System.out.println("----------------------------------------------------");
        System.out.println("DEBUGGING HINTS:");
        System.out.println("");
        System.out.println("For debugging purposes you can loging to started fuse instance with:");
        System.out.println("$ ssh admin@localhost -p " + PORT1 + " -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -oHostKeyAlgorithms=+ssh-dss");
        System.out.println("");
        System.out.println("To start test in remote debug mode, run:");
        System.out.println("$ DEBUG=true mvn clean install");
        System.out.println("Then you can connect to it from IntelliJ on remote debug port 5005");
        System.out.println("----------------------------------------------------");
        System.out.println("----------------------------------------------------");
    }

    protected <T> T getOsgiService(Class<T> type, long timeout) {
        return getOsgiService(type, null, timeout);
    }

    protected <T> T getOsgiService(Class<T> type, String filter, long timeout) {
        ServiceTracker tracker = null;
        try {
            String flt;
            if (filter != null) {
                if (filter.startsWith("(")) {
                    flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")" + filter + ")";
                } else {
                    flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")(" + filter + "))";
                }
            } else {
                flt = "(" + Constants.OBJECTCLASS + "=" + type.getName() + ")";
            }
            org.osgi.framework.Filter osgiFilter = FrameworkUtil.createFilter(flt);
            tracker = new ServiceTracker(bundleContext, osgiFilter, null);
            tracker.open(true);
            // Note that the tracker is not closed to keep the reference
            // This is buggy, as the service reference may change i think
            Object svc = type.cast(tracker.waitForService(timeout));
            if (svc == null) {
                throw new RuntimeException("Gave up waiting for service " + flt);
            }
            return type.cast(svc);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Invalid filter", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
