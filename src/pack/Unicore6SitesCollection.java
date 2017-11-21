/*********************************************************************************
 * Copyright (c) 2009 Forschungszentrum Juelich GmbH 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * (1) Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the disclaimer at the end. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * 
 * (2) Neither the name of Forschungszentrum Juelich GmbH nor the names of its 
 * contributors may be used to endorse or promote products derived from this 
 * software without specific prior written permission.
 * 
 * DISCLAIMER
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ********************************************************************************/
package eu.unicore.hila.grid.unicore6;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import eu.unicore.hila.Location;
import eu.unicore.hila.Resource;
import eu.unicore.hila.annotations.ResourceType;
import eu.unicore.hila.common.BaseResource;
import eu.unicore.hila.exceptions.HiLAException;

/**
 * @author bjoernh
 * 
 *         13.08.2009 15:25:37
 * 
 */
@ResourceType(locationStructure = { "unicore6:/sites/?",
	"unicore6:/{user}@sites/?" })
public class Unicore6SitesCollection extends BaseResource implements Resource {
    private static final Logger log = Logger
	    .getLogger(Unicore6SitesCollection.class);

    private static final Map<Location, Unicore6SitesCollection> sitesCollections = new HashMap<Location, Unicore6SitesCollection>();

    private List<Resource> sites;
    private long sitesTimestamp = 0;

    /**
     * @param location
     */
    private Unicore6SitesCollection(Location location) {
	super(location);

	sites = new ArrayList<Resource>();
    }

    public static Unicore6SitesCollection locate(Location _location,
	    Object... _extraInformation) {
	if (sitesCollections.containsKey(_location)) {
	    return sitesCollections.get(_location);
	}
	Unicore6SitesCollection sitesCollection = new Unicore6SitesCollection(
		_location);
	sitesCollections.put(_location, sitesCollection);
	return sitesCollection;
    }

    /**
     * @throws HiLAException
     * @see eu.unicore.hila.Resource#getChildren()
     */
    public List<Resource> getChildren() throws HiLAException {
	final Unicore6Grid parent = (Unicore6Grid) getParent();
	final Unicore6Properties props = parent.getProperties();

	List<String> registryURLs = props.getRegistryUrls();

	if ((System.currentTimeMillis() - sitesTimestamp) > 60000) {
	    if (log.isDebugEnabled()) {
		log.debug("Updating sites list.");
	    }
	    sites.clear();
	    sites.addAll(SiteLocator.getInstance().getAllSites(registryURLs,
		    props, location, 5000L));
	    
	    sitesTimestamp = System.currentTimeMillis();
	}

	return sites;
    }

}
