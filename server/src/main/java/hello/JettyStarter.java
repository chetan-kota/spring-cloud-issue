/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package hello;

import java.io.File;
import java.io.FilenameFilter;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;


/**
 * @author ActiveEon Team
 * @since 13/06/2017
 */
public class JettyStarter {

    protected static final String FOLDER_TO_DEPLOY = "/dist/war/";

    protected static final String HTTP_CONNECTOR_NAME = "http";

    private static final Logger logger = Logger.getLogger(JettyStarter.class);

    /**
     * To run Jetty in standalone mode
     */
    public static void main(String[] args) {
        BasicConfigurator.configure();

        String path = args[0];
        new JettyStarter().deployWebApplications(path);
    }

    public List<String> deployWebApplications(String path) {

        logger.info("Starting the web applications...");

        String[] defaultVirtualHost;
        String[] httpVirtualHost = new String[] { "@" + HTTP_CONNECTOR_NAME };

        defaultVirtualHost = httpVirtualHost;

        Server server = createHttpServer();

        server.setStopAtShutdown(true);

        HandlerList handlerList = new HandlerList();

        addWarsToHandlerList(handlerList, defaultVirtualHost, path);
        server.setHandler(handlerList);

        return startServer(server, "http");
    }

    protected Server createHttpServer() {

        int maxThreads = 100;

        QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads);
        Server server = new Server(threadPool);

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendDateHeader(false);
        httpConfiguration.setSendServerVersion(false);

        ServerConnector httpConnector = createHttpConnector(server, httpConfiguration, 8080);
        Connector[] connectors = new Connector[] { httpConnector };

        server.setConnectors(connectors);

        return server;
    }

    private ServerConnector createHttpConnector(Server server, HttpConfiguration httpConfiguration, int httpPort) {
        ServerConnector httpConnector = new ServerConnector(server);
        httpConnector.addConnectionFactory(new HttpConnectionFactory(httpConfiguration));
        httpConnector.setName(HTTP_CONNECTOR_NAME);
        httpConnector.setPort(httpPort);
        return httpConnector;
    }

    private List<String> startServer(Server server, String httpProtocol) {
        try {
            if (server.getHandler() == null) {
                logger.info("$HOME/dist/war folder is empty, nothing is deployed");
            } else {
                server.start();
                if (server.isStarted()) {
                    return printDeployedApplications(server, httpProtocol);
                } else {
                    logger.error("Failed to start web applications");
                    System.exit(1);
                }
            }
        } catch (BindException bindException) {
            logger.error("Failed to start web applications. Port 8080 is already used", bindException);
            System.exit(2);
        } catch (Exception e) {
            logger.error("Failed to start web applications", e);
            System.exit(3);
        }
        return new ArrayList<>();
    }

    private String getApplicationUrl(String httpProtocol, WebAppContext webAppContext) {
        return httpProtocol + "://localhost:8080"  + webAppContext.getContextPath();
    }

    private List<String> printDeployedApplications(Server server, String httpProtocol) {
        HandlerList handlerList = (HandlerList) server.getHandler();
        ArrayList<String> applicationsUrls = new ArrayList<>();
        if (handlerList.getHandlers() != null) {
            for (Handler handler : handlerList.getHandlers()) {
                if (!(handler instanceof WebAppContext)) {
                    continue;
                }

                WebAppContext webAppContext = (WebAppContext) handler;
                Throwable startException = webAppContext.getUnavailableException();
                if (startException == null) {
                    if (!"/".equals(webAppContext.getContextPath())) {
                        String applicationUrl = getApplicationUrl(httpProtocol, webAppContext);
                        applicationsUrls.add(applicationUrl);
                        logger.info("The web application " + webAppContext.getContextPath() + " created on " +
                                    applicationUrl);
                    }
                } else {
                    logger.warn("Failed to start context " + webAppContext.getContextPath(), startException);
                }
            }
            logger.info("*** Get started at " + httpProtocol + "://localhost:8080 ***");
        }
        return applicationsUrls;
    }

    private void addWarsToHandlerList(HandlerList handlerList, String[] virtualHost, String path) {
        File warFolder = new File(path + FOLDER_TO_DEPLOY);
        File[] warFolderContent = warFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return !"getstarted".equals(name);
            }
        });

        if (warFolderContent != null) {
            for (File fileToDeploy : warFolderContent) {
                if (isExplodedWebApp(fileToDeploy)) {
                    addExplodedWebApp(handlerList, fileToDeploy, virtualHost);
                } else if (isWarFile(fileToDeploy)) {
                    addWarFile(handlerList, fileToDeploy, virtualHost);
                } else if (isStaticFolder(fileToDeploy)) {
                    addStaticFolder(handlerList, fileToDeploy, virtualHost);
                }
            }
        }

        addGetStartedApplication(handlerList, new File(warFolder, "getstarted"), virtualHost);
    }

    private void addWarFile(HandlerList handlerList, File file, String[] virtualHost) {
        String contextPath = "/" + FilenameUtils.getBaseName(file.getName());
        WebAppContext webApp = createWebAppContext(contextPath, virtualHost);
        webApp.setWar(file.getAbsolutePath());
        handlerList.addHandler(webApp);
        logger.debug("Deploying " + contextPath + " using war file " + file);
    }

    private void addExplodedWebApp(HandlerList handlerList, File file, String[] virtualHost) {
        String contextPath = "/" + file.getName();
        WebAppContext webApp = createWebAppContext(contextPath, virtualHost);

        // Don't scan classes for annotations. Saves 1 second at startup.
        webApp.setAttribute("org.eclipse.jetty.server.webapp.WebInfIncludeJarPattern", "^$");
        webApp.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", "^$");

        webApp.setDescriptor(new File(file, "/WEB-INF/web.xml").getAbsolutePath());
        webApp.setResourceBase(file.getAbsolutePath());
        handlerList.addHandler(webApp);
        logger.debug("Deploying " + contextPath + " using exploded war " + file);
    }

    private void addStaticFolder(HandlerList handlerList, File file, String[] virtualHost) {
        String contextPath = "/" + file.getName();
        WebAppContext webApp = createWebAppContext(contextPath, virtualHost);
        webApp.setWar(file.getAbsolutePath());
        handlerList.addHandler(webApp);
        logger.debug("Deploying " + contextPath + " using folder " + file);
    }

    private void addGetStartedApplication(HandlerList handlerList, File file, String[] virtualHost) {
        if (file.exists()) {
            String contextPath = "/";
            WebAppContext webApp = createWebAppContext(contextPath, virtualHost);
            webApp.setWar(file.getAbsolutePath());
            handlerList.addHandler(webApp);
        }
    }

    private WebAppContext createWebAppContext(String contextPath, String[] virtualHost) {
        WebAppContext webApp = new WebAppContext();
        webApp.setParentLoaderPriority(true);
        webApp.setContextPath(contextPath);
        webApp.setVirtualHosts(virtualHost);
        return webApp;
    }

    private boolean isWarFile(File file) {
        return "war".equals(FilenameUtils.getExtension(file.getName()));
    }

    private boolean isExplodedWebApp(File file) {
        return file.isDirectory() && new File(file, "WEB-INF").exists();
    }

    private boolean isStaticFolder(File file) {
        return file.isDirectory();
    }

}
