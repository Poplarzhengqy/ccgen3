/*
 * Copyright (c) 2007, 2008 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE file for licencing information.
 *
 * Created on Aug 8, 2007
 * Author: K. Benedyczak <golbi@mat.umk.pl>
 */

package pl.edu.icm.unicore.uvos.util;

/**
 * Generally options are defined here: ids and corresponding default values.
 * @author K. Benedyczak
 */
public class ConfDefaults
{
	private static final String P = "uvos.server.";

	public static final String KEYSTORE = P + "keystore";
	public static final String keystore = "conf/certs/dummyServer.jks";
	public static final String KEYSTORE_PASSWD = P + "keystorePasswd";
	public static final String keystorePasswd = "the!server";
	public static final String KEYSTORE_KEY_PASSWD = P + "keyPasswd";
	public static final String keyPasswd = keystorePasswd;
	public static final String KEYSTORE_TYPE = P + "keystoreType";
	public static final String keystoreType = "JKS";
	public static final String KEYSTORE_ALIAS = P + "keystoreAlias";
	public static final String keystoreAlias = "mykey";
	
	
	public static final String HTTPS_ENABLE = P + "https.enable";
	public static final boolean httpsEnable = true;
	public static final String HTTPS_PORT = P + "https.port";
	public static final int httpsPort = 2443;
	public static final String HTTPS_HOST = P + "https.host";
	public static final String httpsHost = "localhost";
	public static final String HTTPS_ANON = P + "https.allowAnonymous";
	public static final boolean httpsAnon = true;
	
	public static final String TRUSTSTORE = P + "https.truststore";
	public static final String truststore = "conf/certs/dummyServerTruststore.jks";
	public static final String TRUSTSTORE_PASSWD = P + "https.truststorePasswd";
	public static final String truststorePasswd = "the!server";
	public static final String TRUSTSTORE_TYPE = P + "https.truststoreType";
	public static final String truststoreType = "JKS";

	public static final String HTTP_ENABLE = P + "http.enable";
	public static final boolean httpEnable = true;
	public static final String HTTP_PORT = P + "http.port";
	public static final int httpPort = 2020;
	public static final String HTTP_HOST = P + "http.host";
	public static final String httpHost = "localhost";
	
	public static final String AUTHN_ORDER = P + "authn.order";
	public static final String authnOrder = "TLS HTTP";
	public static final String AUTHN_FAIL = P + "authn.failOnError";
	public static final boolean authnFail = true;
	public static final String AUTHN_TLS_DN_FIRST = P + "authn.mapTLSCertToDNFirst";
	public static final boolean authnTlsDNFirst = false;
	public static final String ETD = P + "authn.enableETD";
	public static final boolean etd = true;
	
	public static final String SIGN_RESPONSE = P + "saml.signResponses";
	public static final String signResponse = "asRequest";
	public static final String SIGN_ASSERTION = P + "saml.signAssertions";
	public static final String signAssertion = "always";

	public static final String DEF_ATTR_ASSERTION_VALIDITY = P + "saml.validityPeriod";
	public static final int defAttrAssertionValidity = 14400;
	
	public static final String SAML_REQUEST_VALIDITY = P + "saml.requestValidityPeriod";
	public static final int samlRequestValidity = 120;
	
	public static final String ISSUER_URI = P + "saml.issuerURI";
	public static final String defIssuerURI = "I will try to autogenerate one.";
	
	public static final String MAP_CERT_TO_DN = P + "saml.allowToUseCertificateAsDN";
	public static final boolean defMapCertToDN = true;	
	
	public static final String MAIL_CONF = P + "mailConfig";
	public static final String mailConf = "conf/mail.properties";

	public static final String MAIL_TEMPLATES = P + "mailTemplates";
	public static final String mailTemplates = "conf/mailTemplates.properties";
	
	public static final String WEBAPPS_DIR = P + "webappsDir";
	public static final String webappsDir = "./webapps";
	
	public static final String ATTRIBUTE_TYPE_FILES = P + "attributeTypeFiles";
	public static final String attributeTypeFiles = "conf/attributeTypes/uvosCore.at, conf/attributeTypes/ldap.at";

	public static final String ATTRIBUTE_TYPES_UPDATE = P + "attributeTypeUpdate";
	public static final boolean attributeTypeUpdate = false;

	public  static final String REGISTER = P + "useExternalRegistry";
	public  static final boolean register = false;

	public  static final String REGISTRY_BASE = P + "externalRegistryUrl";
	
	public static final String GENERATE_ATTRIBUTES_FROM_DN = P + "generateAttributesFromDN";
	public static final boolean generateAttributesFromDN = false;
	
	//properties of DB config 
	public static final String P_DBCONFIG_FILE = "db.mapconfigFile";
	public static final String DBCONFIG_FILE_DEF = "conf/db/mapconfig.xml";
	public static final String DBPROPERTIES_FILE_DEF = "conf/datamap.properties";
	public static final String P_DBPROPERTIES_FILE = "db.datamapPropertiesFile";
	public static final String DBUPDATE_PROPERTIES_FILE_DEF = "conf/db/dbUpdate/update.properties";
	public static final String P_DBUPDATE_PROPERTIES_FILE = "db.dbUpdatePropertiesFile";
}
