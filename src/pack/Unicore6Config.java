/*********************************************************************************
 * Copyright (c) 2006, 2007, 2008 Forschungszentrum Juelich GmbH 
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
package de.fzj.hila.implementation.unicore6.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;

import de.fzj.hila.Config;
import de.fzj.hila.ID;
import de.fzj.hila.Location;
import de.fzj.hila.exceptions.HiLALocationSyntaxException;
import de.fzj.hila.implementation.unicore6.Unicore6Factory;
import de.fzj.hila.implementation.unicore6.Unicore6ID;
import de.fzj.hila.implementation.unicore6.Unicore6Properties;
import de.fzj.hila.implementation.unicore6.Unicore6SecurityProperties;

/**
 * @author bjoernh
 *
 * TODO: add support for registries
 */
public class Unicore6Config implements Config
{

  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(Unicore6Config.class);

  private Map<Location, Unicore6ID> loc2id;

  /**
   * 
   */
  public Unicore6Config(Unicore6Properties u6p)
  {
    loc2id = new HashMap<Location, Unicore6ID>();
    List<String> sites = u6p.getSiteNames();
    for (String site : sites)
    {
      Location siteLoc = null;
      try
      {
        siteLoc = new Location(Unicore6Factory.SCHEME + ":/" + Location.SITES + "/" + site);
        log.debug("sitelocation as constructed from properties: " + siteLoc);
        siteLoc = siteLoc.getSiteLocation();
      }
      catch (HiLALocationSyntaxException e)
      {
        e.printStackTrace();
      }
      Unicore6ID siteSec = null;
      try
      {
        log.debug("Instantiating SecurityProperties from file: " + u6p.getSecurityPropertiesFile(site));
        siteSec = new Unicore6ID(new FileSystemResource(u6p.getSecurityPropertiesFile(site)));
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
      if ((siteLoc != null) && (siteSec != null))
      {
        log.debug("Putting SecurityProperties for location " + siteLoc + " into Config map.");
        log.debug("Properties are: " + siteSec);
        loc2id.put(siteLoc, siteSec);
      }
    }
  }

  /* (non-Javadoc)
   * @see de.fzj.hila.Config#exists(de.fzj.hila.Location)
   */
  public boolean exists(Location location)
  {
    log.debug("Does an Id exist for location " + location + ": " + loc2id.containsKey(location));
    return loc2id.containsKey(location);
  }

  /* (non-Javadoc)
   * @see de.fzj.hila.Config#findIDforLocation(de.fzj.hila.Location)
   */
  public ID findIDforLocation(Location location)
  {
    Location siteLocation = null;
    try
    {
      siteLocation = location.getSiteLocation();
      log.debug("siteLocation for location " + location + " is " + siteLocation);
    }
    catch (HiLALocationSyntaxException e)
    {
      e.printStackTrace();
    }
    return loc2id.get(siteLocation);
  }

  /* (non-Javadoc)
   * @see de.fzj.hila.Config#getAllSiteLocations()
   */
  public Set<Location> getAllSiteLocations()
  {
    return loc2id.keySet();
  }
  
  public Set<Location> getAllSiteLocations(String user)
  {
    return null;
  }

  @SuppressWarnings("unchecked")
  public Map getExtraInformationForLocation(Location location)
  {
    // TODO Auto-generated method stub
    return null;
  }

  public void refresh()
  {
    // TODO Auto-generated method stub

  }

}
