package de.fzj.unicore.uas.security;

import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import junit.framework.TestCase;

import org.codehaus.xfire.service.Service;
import org.oasisOpen.docs.wsrf.rp2.GetResourcePropertyDocument;

import de.fzj.unicore.uas.UAS;
import de.fzj.unicore.uas.client.UASClientFactory;
import de.fzj.unicore.uas.security.testservices.ExampleService;
import de.fzj.unicore.uas.security.testservices.IExample;
import de.fzj.unicore.uas.util.LogUtil;
import de.fzj.unicore.wsrflite.Kernel;
import de.fzj.unicore.wsrflite.xfire.XFireKernel;

public class TestPlainWSAuthZ extends TestCase implements Observer {
	private static boolean initDone=false;
	
	protected void setUp()throws Exception{
		//System.setProperty("javax.net.debug", "ssl,handshake");
		Logger.getLogger("de").setLevel(Level.ALL);
		Logger.getLogger("org").setLevel(Level.SEVERE);
		Logger.getLogger("com").setLevel(Level.SEVERE);
		UAS uas=new UAS("src/test/resources/secure/uas.config.simple");
		uas.addObserver(this);
		uas.start();
		while(!initDone){
			Thread.sleep(500);
		}
		addServices();
	}
	
	
	protected void addServices()throws Exception{
		Service s = XFireKernel.exposeAsService("test", 
				IExample.class, ExampleService.class, false);
		
		s.addInHandler(new PlainWSAuthZHandler());
	}
	
	public void test(){
		doTestACL();
		doTestNoACL();
	}
	
	private void doTestACL(){
		try{
			UAS.setProperty(UASSecurityProperties.UAS_CHECKACCESS+".test", "true");
			
			String address=getBaseurl()+"/test";
			IExample service=new UASClientFactory(UAS.getSecurityProperties()).createPlainWSProxy(IExample.class, address, UAS.getSecurityProperties());
			GetResourcePropertyDocument in=GetResourcePropertyDocument.Factory.newInstance();
			in.setGetResourceProperty(new QName("Hello","World"));
			service.getTime(in);
			fail("Expected exception due to 'Access denied'");
		}catch(Exception e){
			//OK
			System.out.println("OK: got "+LogUtil.createFaultMessage("", e));
		}
		
	}
	
	
	private void doTestNoACL(){
		try{
			UAS.setProperty(UASSecurityProperties.UAS_CHECKACCESS+".test", "false");
			
			String address=getBaseurl()+"/test";
			IExample service=new UASClientFactory(UAS.getSecurityProperties()).createPlainWSProxy(IExample.class, address, UAS.getSecurityProperties());
			GetResourcePropertyDocument in=GetResourcePropertyDocument.Factory.newInstance();
			in.setGetResourceProperty(new QName("Hello","World"));
			service.getTime(in);
		}catch(Exception e){
			e.printStackTrace();
			fail();
		}
		
	}
	
	private String getBaseurl(){
		return Kernel.getKernel().getProperty(Kernel.WSRF_BASEURL);
	}
	
	public void update(Observable o, Object arg) {
		initDone=true;
	}
	
	
}
