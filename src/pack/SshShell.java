/*****************************************************************************
 * Copyright (c) 2006, 2007 g-Eclipse Consortium 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Initial development of the original code was made for the
 * g-Eclipse project founded by European Union
 * project number: FP6-IST-034327  http://www.geclipse.eu/
 *
 * Contributors:
 *    Thomas Koeckerbauer GUP, JKU - initial API and implementation
 *****************************************************************************/

package de.fzj.unicore.rcp.terminal.ssh.plain;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.core.runtime.Status;
import org.eclipse.jsch.core.IJSchService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;

import de.fzj.unicore.rcp.terminal.IBidirectionalConnection;
import de.fzj.unicore.rcp.terminal.ITerminalListener;


/**
 * A terminal factory which allows to open SSH connected terminals.
 */
public class SshShell implements  ITerminalListener {
	private static final String PREFERRED_AUTHENTICATION = "PreferredAuthentications";
	private static final String AUTHENTICATION_PUBLIC_KEY = "publickey";
	private static final String AUTHENTICATION_PASSWORD = "password";
	private static final String AUTHENTICATION_KEYBOARD = "keyboard-interactive";
	
	ChannelShell channel;

	SSHConnectionInfo userInfo;
	private int preConnectCols = -1;
	private int preConnectLines;
	private int preConnectXPix;
	private int preConnectYPix;

	public void windowSizeChanged( final int cols, final int lines, final int xPixels, final int yPixels ) {
		if ( this.channel.isConnected() ) {
			this.channel.setPtySize( cols, lines, xPixels, yPixels );
		} else {
			this.preConnectCols = cols;
			this.preConnectLines = lines;
			this.preConnectXPix = xPixels;
			this.preConnectYPix = yPixels;
		}
	}



	public IBidirectionalConnection createConnection(final SSHConnectionInfo sshConnectionInfo, boolean publickey) {
		try {
			IJSchService service = UnicoreTerminalPlainSSHPlugin.getDefault().getJSchService();
			if (service == null) {
				Display.getDefault().asyncExec( new Runnable() {
					public void run() {

					}
				} );
			} 
			else {
				this.userInfo = sshConnectionInfo;
				Session session;
				session = service.createSession( this.userInfo.getHostname(),
						this.userInfo.getPort(),
						this.userInfo.getUsername() );

				String authtype = userInfo.getAuthType();
				String authMethods = session.getConfig(PREFERRED_AUTHENTICATION);
				session.setUserInfo( this.userInfo );

				if(authtype.equals(PlainSSHConstants.PLAIN_TYPE_PUBKEY) || authtype.equals("") || authtype==null )
				{
					// first try to connect via public key only
					// if this fails, ask the user for a different public/private key
					// if the second try fails or the user clicks cancel, fallback to other auth methods
					session.setConfig(PREFERRED_AUTHENTICATION, AUTHENTICATION_PUBLIC_KEY);
					try {
						session.connect();
						if(!userInfo.getAuthType().equals(PlainSSHConstants.PLAIN_TYPE_PUBKEY)){
							authtype = PlainSSHConstants.PLAIN_TYPE_PUBKEY;
						}					
					} 
					catch (Exception e) {
						if(userInfo.promptPrivateKey(""))
						{
							String keypath = this.userInfo.getPrivateKeyPath();
							if(keypath != null && keypath.trim().length() > 0) service.getJSch().addIdentity(keypath);
							service.getJSch().addIdentity(keypath);

							session = service.createSession( userInfo.getHostname(),
									userInfo.getPort(),
									userInfo.getUsername() );
							session.setUserInfo(userInfo );
							session.setConfig(PREFERRED_AUTHENTICATION, AUTHENTICATION_PUBLIC_KEY);
							try {
								session.connect();
								if(!userInfo.getAuthType().equals(PlainSSHConstants.PLAIN_TYPE_PUBKEY)){
									authtype = PlainSSHConstants.PLAIN_TYPE_PUBKEY;
								}	
							} catch (Exception e1) {
								authtype = PlainSSHConstants.PLAIN_TYPE_PASS;
								session = service.createSession( userInfo.getHostname(),
										userInfo.getPort(),
										userInfo.getUsername() );
								session.setUserInfo(userInfo );
								session.setConfig(PREFERRED_AUTHENTICATION, AUTHENTICATION_PASSWORD);
								
								try {
									session.connect();
									if(!userInfo.getAuthType().equals(PlainSSHConstants.PLAIN_TYPE_PASS)){
										authtype = PlainSSHConstants.PLAIN_TYPE_PASS;
									}	
								} catch (Exception e2){
									userInfo.setPasswordInteractiveUsed(true);
									authtype = PlainSSHConstants.PLAIN_TYPE_INTERACTIVE;
									session = service.createSession( userInfo.getHostname(),
											userInfo.getPort(),
											userInfo.getUsername() );
									session.setUserInfo(userInfo );
									session.setConfig(PREFERRED_AUTHENTICATION, AUTHENTICATION_KEYBOARD);
									try {
										session.connect();
										if(!userInfo.getAuthType().equals(PlainSSHConstants.PLAIN_TYPE_INTERACTIVE)){
											authtype = PlainSSHConstants.PLAIN_TYPE_INTERACTIVE;
										}	
									} catch (Exception e3){
										authtype = "";
//										Display.getDefault().syncExec( new Runnable() {
//											public void run() {
//												Shell shell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
//												
//												if (shell == null) shell = new Shell();
//												MessageBox messageBox = new MessageBox(shell, SWT.ICON_CANCEL | SWT.OK);										        
//										        messageBox.setText("Authentication cancelled");
//										        messageBox.setMessage("No authentication method was successful!");
//											}
//										} );									
									}
								}
							}
						}
						else {
							authtype = PlainSSHConstants.PLAIN_TYPE_PASS;
							session = service.createSession( userInfo.getHostname(),
									userInfo.getPort(),
									userInfo.getUsername() );
							session.setUserInfo(userInfo );
							session.setConfig(PREFERRED_AUTHENTICATION, AUTHENTICATION_PASSWORD);
							
							try {
								session.connect();
								if(!userInfo.getAuthType().equals(PlainSSHConstants.PLAIN_TYPE_PASS)){
									authtype = PlainSSHConstants.PLAIN_TYPE_PASS;
								}	
							} catch (Exception e2){
								userInfo.setPasswordInteractiveUsed(true);
								authtype = PlainSSHConstants.PLAIN_TYPE_INTERACTIVE;
								session = service.createSession( userInfo.getHostname(),
										userInfo.getPort(),
										userInfo.getUsername() );
								session.setUserInfo(userInfo );
								session.setConfig(PREFERRED_AUTHENTICATION, AUTHENTICATION_KEYBOARD);
								try {
									session.connect();
									if(!userInfo.getAuthType().equals(PlainSSHConstants.PLAIN_TYPE_INTERACTIVE)){
										authtype = PlainSSHConstants.PLAIN_TYPE_INTERACTIVE;
									}	
								} catch (Exception e3){
									authtype = "";
									Display.getDefault().syncExec( new Runnable() {
										public void run() {
											Shell shell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
											
											if (shell == null) shell = new Shell();
											MessageBox messageBox = new MessageBox(shell, SWT.ICON_CANCEL | SWT.OK);										        
									        messageBox.setText("Authentication cancelled");
									        messageBox.setMessage("No authentication method was successful!");
										}
									} );									
								}
							}
						}
					}
				}
				else if(authtype.equals(PlainSSHConstants.PLAIN_TYPE_PASS)){
					session = service.createSession( userInfo.getHostname(),
							userInfo.getPort(),
							userInfo.getUsername() );
					session.setUserInfo(userInfo );
					session.setConfig(PREFERRED_AUTHENTICATION, AUTHENTICATION_PASSWORD);					
				}
				else{
					userInfo.setPasswordInteractiveUsed(true);
					session = service.createSession( userInfo.getHostname(),
							userInfo.getPort(),
							userInfo.getUsername() );
					session.setUserInfo(userInfo );
					session.setConfig(PREFERRED_AUTHENTICATION, AUTHENTICATION_KEYBOARD);
				}

				if(!session.isConnected()) session.connect();	
				
				userInfo.setAuthType(authtype);
				session.setConfig(PREFERRED_AUTHENTICATION, authMethods);
				
				final Session s = session;
				final IBidirectionalConnection connection = new IBidirectionalConnection() {
					public void close() {
						SshShell.this.channel.disconnect();
						s.disconnect();
					}
					public InputStream getInputStream() throws IOException {
						return SshShell.this.channel.getInputStream();
					}
					public OutputStream getOutputStream() throws IOException {
						return SshShell.this.channel.getOutputStream();
					}
				};

				this.channel = (ChannelShell) session.openChannel( "shell" ); //$NON-NLS-1$
				this.channel.connect();
				if ( this.preConnectCols != -1 ) {
					windowSizeChanged( this.preConnectCols, this.preConnectLines,
							this.preConnectXPix, this.preConnectYPix );
				}
				return connection;
			}
		} catch ( final Exception exception ) {
			UnicoreTerminalPlainSSHPlugin.log(Status.ERROR,"Unable to open a connection: "+exception.getMessage(), exception );

		}
		return null;   
	}

	public void windowTitleChanged( final String windowTitle ) {
		//    this.terminal.setDescription( Messages.formatMessage( "SshShell.descriptionWithWinTitle", //$NON-NLS-1$
		//                                  this.userInfo.getUsername(),
		//                                  this.userInfo.getHostname(),
		//                                  windowTitle ) );
	}

	/* (non-Javadoc)
	 * @see de.fzj.unicore.rcp.terminal.ITerminalListener#terminated()
	 */
	public void terminated() {
		// not needed
	}
	
	
}
