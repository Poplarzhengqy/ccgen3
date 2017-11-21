/*********************************************************************************
 * Copyright (c) 2007, 2008 Forschungszentrum Juelich GmbH 
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3.x2005.x08.addressing.EndpointReferenceType;

import de.fzj.hila.Location;
import de.fzj.hila.Site;
import de.fzj.hila.common.config.MapBackedConfig;
import de.fzj.hila.exceptions.HiLAException;
import de.fzj.hila.implementation.unicore6.Unicore6Grid;
import de.fzj.hila.implementation.unicore6.Unicore6ID;
import de.fzj.hila.implementation.unicore6.Unicore6SecurityProperties;
import de.fzj.hila.implementation.unicore6.Unicore6Site;
import de.fzj.unicore.uas.TargetSystemFactory;
import de.fzj.unicore.uas.client.BaseUASClient;
import de.fzj.unicore.uas.client.RegistryClient;

/**
 * @org.apache.xbean.XBean element="registryconfig"
 * @author bjoernh
 *
 */
public class RegistryConfig extends MapBackedConfig
{

  private String registryURL = null;

  private RegistryClient regClient = null;

  private Unicore6SecurityProperties securityProperties = null;

  private Unicore6Grid grid = null;

  List<Site> registrySites = null;

  private static final Logger log = LoggerFactory.getLogger(RegistryConfig.class);

  @SuppressWarnings("unchecked")
  public Map getExtraInformationForLocation(Location location)
  {
    // TODO Auto-generated method stub
    return null;
  }

  public void refresh() throws HiLAException
  {
    log.debug("refreshing config...");
    if (regClient == null)
    {
      try
      {
        createRegistryClient();
      }
      catch (Exception e)
      {
        throw new HiLAException("Couldn't create RegistryClient");
      }
    }
    try
    {
      getSitesFromRegistry();
    }
    catch (Exception e)
    {
      throw new HiLAException("Couldn't get sites from registry.");
    }
  }

  private void createRegistryClient() throws Exception
  {
    if (regClient == null)
    {
      EndpointReferenceType regEPR = EndpointReferenceType.Factory.newInstance();
      regEPR.addNewAddress().setStringValue(registryURL);
      regClient = new RegistryClient(registryURL, regEPR, (Unicore6SecurityProperties) securityProperties.clone());
    }
  }

  private List<Site> getSitesFromRegistry() throws Exception
  {
    registrySites = new ArrayList<Site>();
    if (regClient == null)
    {
      createRegistryClient();
    }
    try
    {
      log.debug("About to query registry for available TSFs.");
      List<EndpointReferenceType> siteEPRs = regClient.listAccessibleServices(TargetSystemFactory.TSF_PORT);
      log.debug("Accessible TSF_PORTs: " + siteEPRs.size());
      for (EndpointReferenceType siteEPR : siteEPRs)
      {
        try
        {
          log.debug("Testing accessibility of TSF");
          new BaseUASClient(siteEPR.getAddress().getStringValue(), siteEPR, (Unicore6SecurityProperties) regClient.getSecurityProperties().clone()).getCurrentTime();
          log.debug("TSF accessible");
          // Chicken or egg?
          // Can't get site's name w/o credentials
          // Can't create site w/o site's name
          Unicore6Site u6Site = new Unicore6Site(null, siteEPR, this.grid, (Unicore6ID) regClient.getSecurityProperties().clone());
          registrySites.add(u6Site);
          grid.setCachedSite(u6Site.getLocation().getSiteLocation(), u6Site);
          getConfig().put(u6Site.getLocation().getSiteLocation(), u6Site.getSecurityProperties());
        }
        catch (Exception e)
        {
          // don't add to avail tss
        }
      }
    }
    catch (Exception e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return registrySites;
  }

  public Unicore6Grid getGrid()
  {
    return grid;
  }

  public void setGrid(Unicore6Grid grid)
  {
    this.grid = grid;
  }

  public void setRegistryURL(String registryURL)
  {
    this.registryURL = registryURL;
  }

  public void setSecurityProperties(Unicore6SecurityProperties securityProperties)
  {
    this.securityProperties = securityProperties;
  }

  /* (non-Javadoc)
   * @see de.fzj.hila.Config#getAllSiteLocations(java.lang.String)
   */
  public Set<Location> getAllSiteLocations(String user)
  {
    // TODO Auto-generated method stub
    return null;
  }

}
