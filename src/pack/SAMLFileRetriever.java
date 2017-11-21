package de.fzj.hila.implementation.unicore6.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Properties;

import org.apache.xmlbeans.XmlException;

import xmlbeans.org.oasis.saml2.assertion.AssertionDocument;
import eu.unicore.security.UnicoreSecurityFactory;
import eu.unicore.security.etd.DelegationRestrictions;
import eu.unicore.security.etd.ETDApi;
import eu.unicore.security.etd.TrustDelegation;

public class SAMLFileRetriever
{

  private static final String PASSWORD_PROP = "unicore.wsrflite.ssl.keypass";

  private static final String ALIAS_PROP = "unicore.wsrflite.ssl.keyalias";

  private static final String JKS_PROP = "unicore.wsrflite.ssl.keystore";

  private File root;
  
  public SAMLFileRetriever(File root) 
  {
    this.root = root;
  }
  
  public AssertionDocument getSAMLAssertion(String userId)
  {
    AssertionDocument ad = null;
    File assertion = new File( root, userId + "/" + userId + ".saml");
    if (assertion.exists())
    {
      try
      {
        ad = AssertionDocument.Factory.parse(assertion);
      }
      catch (XmlException e)
      {
        e.printStackTrace();
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }
    }

    if (ad != null)
    {
      Calendar currentTime = Calendar.getInstance();
      // check period of issuance
      Calendar notBefore = ad.getAssertion().getConditions().getNotBefore();
      Calendar notAfter = ad.getAssertion().getConditions().getNotOnOrAfter();

      if (currentTime.before(notBefore) || currentTime.after(notAfter))
      { // quick hack to invalidate outdated assertions.
        ad = null;
      }
    }

    if (ad == null)
    {
      ad = createSAMLAssertion(userId);
    }
    return ad;
  }

  private AssertionDocument createSAMLAssertion(String userId)
  {
    AssertionDocument ad = null;
    File userKeystore = new File(root, userId + "/" + userId + ".jks");
    File userSecurity = new File(root, userId + "/" + userId + ".security");
 
    if (userKeystore.exists() && userSecurity.exists())
    {
      try
      {
        KeyStore userJKS = KeyStore.getInstance(KeyStore.getDefaultType());
        Properties userSecProps = new Properties();
        userSecProps.load(new FileInputStream(userSecurity));

        userJKS.load(new FileInputStream(userKeystore), userSecProps.getProperty(PASSWORD_PROP).toCharArray());

        Certificate userCert = userJKS.getCertificate(userSecProps.getProperty(ALIAS_PROP));
        X509Certificate userX509 = null;
        String userDN = null;
        
        if (userCert instanceof X509Certificate)
        {
          userX509 = (X509Certificate) userCert;
          userDN = userX509.getSubjectDN().getName();
        }

        KeyStore agentjks = KeyStore.getInstance(KeyStore.getDefaultType());
        Properties agentsecprops = new Properties();
        File agentsecfile = new File(this.root.getParentFile(), "1" );
        File agentsec = (File) MegaConfig.XS.fromXML(new FileReader(agentsecfile));
        agentsecprops.load(new FileInputStream(agentsec));

        agentjks.load(new FileInputStream(new File(agentsecprops.getProperty(JKS_PROP))), agentsecprops.getProperty(PASSWORD_PROP).toCharArray());
        String agentdn = null;
        Certificate agentcert = agentjks.getCertificate(agentsecprops.getProperty(ALIAS_PROP));
        X509Certificate agentX509 = null;
        if (agentcert instanceof X509Certificate)
        {
          agentX509 = (X509Certificate) agentcert;
          agentdn = agentX509.getSubjectDN().getName();
        }

        ETDApi engine = UnicoreSecurityFactory.getETDEngine();
        Calendar until = Calendar.getInstance();
        until.add(Calendar.DATE, 14);
        DelegationRestrictions dr = new DelegationRestrictions(Calendar.getInstance().getTime(), until.getTime(), 10);
        
        TrustDelegation td = engine.generateTD( 
            userDN, 
            new X509Certificate[] { userX509 }, 
            (PrivateKey) userJKS.getKey(userSecProps.getProperty(ALIAS_PROP), userSecProps.getProperty(PASSWORD_PROP).toCharArray()), 
            agentdn, 
            dr
        );

        ad = td.getXML();
        ad.save(new File(root, userId + "/" + userId + ".saml"));
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
    }
    return ad;
  }

  public void generateSAMLifNeccessary() throws Exception
  {
    for (String s : this.root.list())
    {
      if (!new File(this.root, s+"/"+s+".saml").exists()) 
      {
        createSAMLAssertion(s);
      }
    }
  }
  
}
