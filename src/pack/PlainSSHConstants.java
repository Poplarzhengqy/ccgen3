package de.fzj.unicore.rcp.terminal.ssh.plain;

import de.fzj.unicore.rcp.terminal.TerminalConstants;

public interface PlainSSHConstants extends TerminalConstants {
	
	public static final String PLAIN_HOST = "plain_host", PLAIN_PORT = "plain_port", 
		PLAIN_LOGIN = "plain_login", PLAIN_TYPE = "plain_type", PLAIN_KEY = "plain_key" ,
		PLAIN_TYPE_PASS = "password", PLAIN_TYPE_PUBKEY = "public-key", 
		PLAIN_TYPE_INTERACTIVE = "keyboard-interactive";

	public static final String SSH_INFO_PREFIX = "SSHInfo-";
	
	public static final String PLAIN_SUPPORTED = "plain_supported";
	
	public static final String CONNECTION_TYPE_ID_PLAIN = "PLAIN";

}
