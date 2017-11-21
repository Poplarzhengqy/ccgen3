package de.fzj.unicore.rcp.terminal.ssh.plain;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;

import de.fzj.unicore.rcp.terminal.IBidirectionalConnection;
import de.fzj.unicore.rcp.terminal.TerminalConnection;
import de.fzj.unicore.rcp.terminal.extensionpoints.ICommunicationProvider;

public class PlainSSHCommunicationProvider implements
ICommunicationProvider, PlainSSHConstants {


	private Map<String,SSHConnectionInfo> cachedConnectionInfos = new HashMap<String, SSHConnectionInfo>(); // store connection infos for single sign on

	public TerminalConnection establishConnection(Map<String,String> config, IProgressMonitor progress) {
		TerminalConnection result = new TerminalConnection();

		//SSHConnectionInfo info = readConnectionInfo(serverAddress);

		SSHConnectionInfo info = readConnectionInfo(config);
		String host = info.getHostname();
		if(host == null) 
		{
			boolean okPressed = info.promptHost();
			if(!okPressed) return null;
		}
		Integer port = info.getPort();
		if(port == null) 
		{
			boolean okPressed = info.promptPort();
			if(!okPressed) return null;
		}

		boolean dirty = false;
		String userName = info.getUsername();
		if(userName == null) 
		{
			boolean okPressed = info.promptUsername();
			if(!okPressed) return null;
			config.put(PlainSSHConstants.PLAIN_LOGIN, info.getUsername());
			dirty = true;
		}


//		if(config.get(PlainSSHConstants.PLAIN_TYPE).equals("")){
//			boolean okPressed = info.promptPlainType();
//			if(!okPressed) return null;
//			config.put(PlainSSHConstants.PLAIN_TYPE, info.getAuthType());
//			dirty = true;
//		}


		String keypath = info.getPrivateKeyPath();
		if(keypath == null || keypath.equalsIgnoreCase("")) 
		{
			config.put(PlainSSHConstants.PLAIN_KEY, keypath);
			
		}


		//to update config with new information from prompts
		if(dirty){
			config = info.getConfig();
			info = this.readConnectionInfo(config);
		}

		SshShell shell = new SshShell();
		IBidirectionalConnection connection = shell.createConnection(info);
		if(connection == null)
		{
			// authentication seemingly failed 
			info.setPassword(null); // reset password
			info.setPassphrase(null); // reset private key file passphrase
		}
		result.setConnection(connection);
		result.setListener(shell);
		return result;
	}




	protected SSHConnectionInfo readConnectionInfo(Map<String,String> config)
	{

		try
		{
			String id = config.get(ID);
			SSHConnectionInfo result = cachedConnectionInfos.get(id);
			if(result == null) result = new SSHConnectionInfo(config);
			result.setConfig(config);
			cachedConnectionInfos.put(id, result);
			return result;
			//			return new SSHConnectionInfo(user,hostname,null,null,port,serverAddress);

		}catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	
}
