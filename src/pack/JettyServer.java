/*
 * Copyright (c) 2007, 2008 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE file for licencing information.
 *
 * Created on Aug 8, 2007
 * Author: K. Benedyczak <golbi@mat.umk.pl>
 */

package eu.unicore.security.xfireutil;

import org.codehaus.xfire.transport.http.XFireServlet;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.jetty.handler.RequestLogHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.SslSelectChannelConnector;
import org.mortbay.jetty.servlet.Context;



/**
 * Test Jetty server implementation. Here appropriate connectors are configured. 
 * @author K. Benedyczak
 */
public class JettyServer
{
	public static final int PORT = 65344;
	public static final String KS = "conf/server.jks";
	public static final String KS_PWD = "the!server";
	private Server jettyServer;
	
	public JettyServer() throws Exception
	{
		//System.setProperty("DEBUG", "true");
		jettyServer = new Server();
		jettyServer.setConnectors(new Connector[] {getSSLConnector(),
				getPlainConnector()});
		
		ContextHandlerCollection contexts = new ContextHandlerCollection();
		RequestLogHandler requestLogHandler = new RequestLogHandler();

		
		
		Context root=new Context(jettyServer, "/", Context.SESSIONS);
		root.addServlet(XFireServlet.class, "/services/*");
		
		HandlerCollection handlers = new HandlerCollection();
		handlers.setHandlers(new Handler[]{
				contexts,
				root,
				new DefaultHandler(),
				requestLogHandler});
		jettyServer.setHandler(handlers);
	}
	
	private Connector getSSLConnector()
	{
		SslSelectChannelConnector connector = new SslSelectChannelConnector();
		connector.setPort(PORT);
		connector.setHost("localhost");
		connector.setWantClientAuth(true);
		connector.setNeedClientAuth(false);

		connector.setTrustPassword(KS_PWD);
		connector.setTruststore(KS);
		connector.setTruststoreType("JKS");
		
		connector.setPassword(KS_PWD);
		connector.setKeystore(KS);
		connector.setKeystoreType("JKS");
		connector.setKeyPassword(KS_PWD);
		return connector;
	}

	private Connector getPlainConnector()
	{
		SelectChannelConnector connector = new SelectChannelConnector();
		connector.setPort(PORT+1);
		connector.setHost("localhost");
		return connector;
	}

	
	public void start() throws Exception 
	{
		jettyServer.start();
	}
	
	public void stop() throws Exception 
	{
		jettyServer.stop();
	}
}
