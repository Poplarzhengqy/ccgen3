/**
 * Copyright (c) 2005, Forschungszentrum Juelich
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met: * Redistributions of source
 * code must retain the above copyright notice, this list of conditions and the following
 * disclaimer. * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. * Neither the name of the Forschungszentrum
 * Juelich nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.fzj.hila.implementation.unicore6.config;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.xmlbeans.XmlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.FileSystemResource;
import org.w3.x2005.x08.addressing.EndpointReferenceType;

import xmlbeans.org.oasis.saml2.assertion.AssertionDocument;

import com.thoughtworks.xstream.XStream;

import de.fzj.hila.Config;
import de.fzj.hila.ID;
import de.fzj.hila.Location;
import de.fzj.hila.exceptions.HiLAException;
import de.fzj.hila.exceptions.HiLAIdentityException;
import de.fzj.hila.exceptions.HiLALocationSyntaxException;
import de.fzj.hila.implementation.unicore6.Unicore6ID;
import de.fzj.hila.implementation.unicore6.Unicore6SecurityProperties;

import eu.unicore.samly2.exceptions.SAMLParseException;
import eu.unicore.security.etd.TrustDelegation;

/**
 * MegaConfig is implementation of HiLA Config which ...
 * 
 * @org.apache.xbean.XBean element="megaconfig"
 * 
 * @author roger
 */
public class MegaConfig implements Config, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(MegaConfig.class);

    private Registry[] registries;

    private int checkeveryhours;

    private File root;

    private File[] vss;

    public static final String TSF_URL = "TSF_URL";

    public static final XStream XS = new XStream();

    public MegaConfig() throws Exception {
	this.root = new File(System.getProperty("user.home"),
		".hila/megaconfig");
	root.mkdir();
    }

    public void afterPropertiesSet() throws Exception {
	File ts = new File(root, ".timestamp");
	boolean update = true;
	if (ts.exists()) {
	    Date d1 = (Date) XS.fromXML(new FileReader(ts));
	    Date d2 = new Date(d1.getTime()
		    + (1000 * 60 * 60 * this.getCheckeveryhours()));
	    update = d2.before(new Date());
	}
	if (update) {
	    reload();
	    XS.toXML(new Date(), new FileWriter(ts));
	}
	this.vss = this.root.listFiles(new FileFilter() {

	    public boolean accept(File f) {
		return f.isDirectory();
	    }
	});
	createAssertions();
    }

    public void addRegistry(Registry r) throws Exception {
	log.info("..... LOOKING at registry :: " + r.getRegistryUrl());
	String ru = r.getRegistryUrl();
	Unicore6ID[] ids = r.getIds();

	ExecutorService executor = Executors.newCachedThreadPool();

	List<Future<?>> pendingTasks = new ArrayList<Future<?>>();

	for (Unicore6ID id : ids) {
	    EndpointReferenceType _registryEPR = EndpointReferenceType.Factory
		    .newInstance();
	    _registryEPR.addNewAddress().setStringValue(ru);
	    GetUserTSFsFromRegistry gutsf = new GetUserTSFsFromRegistry(
		    _registryEPR, id, this.root);

	    pendingTasks.add(executor.submit(gutsf));
	}

	// wait for at most 4 seconds for all queries to be finished
	long time1 = System.currentTimeMillis();
	boolean allDone;
	do {
	    allDone = true;
	    for (Future<?> future : pendingTasks) {
		allDone &= future.isDone();
	    }
	    log.debug("Remaining time: " + Long.toString(5000 - System.currentTimeMillis() + time1));
	} while (!allDone && (System.currentTimeMillis() - time1 < 5000));
	
	if(!allDone) {
	    log.warn("Unfinished site discoveries pending.");
	}
	executor.shutdown();
    }

    public Registry[] getRegistries() {
	return registries;
    }

    public void setRegistries(Registry[] registries) {
	this.registries = registries;
    }

    public int getCheckeveryhours() {
	return checkeveryhours;
    }

    public void setCheckeveryhours(int checkeveryhours) {
	this.checkeveryhours = checkeveryhours;
    }

    public boolean exists(Location location) {
	try {
	    return new File(this.root, location.getSiteLocation().getName())
		    .exists();
	} catch (HiLALocationSyntaxException e) {
	    return false;
	}
    }

    public ID findIDforLocation(Location location) throws HiLAException {
	log.debug("findIDforLocation ::: " + location);

	String sn = location.getSiteLocation().getName();
	File vx = new File(this.root, sn);

	Unicore6ID u6id = null;
	Unicore6SecurityProperties u6sp;
	try {
	    File c = (File) XS.fromXML(new FileReader(new File(vx, "1")));
	    u6id = new Unicore6ID(new FileSystemResource(c));
	    u6sp = u6id.getUnicore6SecurityProperties();
	} catch (FileNotFoundException e) {
	    throw new HiLAIdentityException(
		    "Couldn't create primary identity.", e);
	} catch (Exception e) {
	    throw new HiLAIdentityException(
		    "Couldn't create primary identity.", e);
	}
	if (!location.isDefaultUser()) {
	    // acting as agent. get saml assertion and add to
	    // SecurityProperties.
	    AssertionDocument ad = new SAMLFileRetriever(new File(vx, "users"))
		    .getSAMLAssertion(location.getUser());
	    if (ad == null) {
		throw new HiLAIdentityException("No SAML assertion for user <"
			+ location.getUser() + ">.");
	    }
	    TrustDelegation td;
	    try {
		td = new TrustDelegation(ad);
	    } catch (SAMLParseException e) {
		throw new HiLAIdentityException("Invalid SAML assertion: "
			+ ad.xmlText(), e);
	    } catch (XmlException e) {
		throw new HiLAIdentityException(
			"Invalid XML in SAML assertion: " + ad.xmlText(), e);
	    } catch (IOException e) {
		throw new HiLAIdentityException(
			"Couldn't create trust delegation from given SAML assertion.",
			e);
	    }
	    List<TrustDelegation> tdList = new ArrayList<TrustDelegation>();
	    tdList.add(td);
	    u6sp.setTrustDelegationTokens(tdList);
	}
	return u6id;
    }

    public Set<Location> getAllSiteLocations() {
	return this.getAllSiteLocations(Location.DEFAULT_USER);
    }

    public Set<Location> getAllSiteLocations(String user) {
	HashSet<Location> l = new HashSet<Location>();
	for (File v : this.vss) {
	    try {
		l.add(new Location("unicore6:/" + user + "/sites/"
			+ v.getName()));
	    } catch (HiLALocationSyntaxException e) {
		e.printStackTrace();
	    }
	}
	return l;
    }

    public Map<String, Object> getExtraInformationForLocation(Location location) {
	HashMap<String, Object> xi = new HashMap<String, Object>();
	try {
	    String u = (String) XS.fromXML(new FileReader(new File(this.root,
		    location.getSiteLocation().getName() + "/url")));
	    xi.put(TSF_URL, u);
	} catch (FileNotFoundException e) {
	    e.printStackTrace();
	} catch (HiLALocationSyntaxException e) {
	    e.printStackTrace();
	}
	return xi;
    }

    public void refresh() throws HiLAException {
	try {
	    // this.reload();
	} catch (Exception e) {
	    throw new HiLAException(e);
	}
    }

    public void reload() {
	log.debug("reloading ... ");
	for (Registry r : getRegistries()) {
	    try {
		addRegistry(r);

	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    public void createAssertions() throws Exception {
	for (File f : this.vss) {
	    new SAMLFileRetriever(new File(f, "users"))
		    .generateSAMLifNeccessary();
	}
    }

    public void lodgeAssertion(Location siteLoc, AssertionDocument assertion)
	    throws HiLALocationSyntaxException, IOException {
	File ud = new File(this.root, siteLoc.getSiteLocation().getName()
		+ File.separator + "users" + File.separator + siteLoc.getUser());
	ud.mkdirs();
	File assertionFile = new File(ud, siteLoc.getUser() + ".saml");
	assertion.save(assertionFile);
    }

}
