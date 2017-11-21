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
import org.eclipse.swt.widgets.Display;

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



	public IBidirectionalConnection createConnection(final SSHConnectionInfo sshConnectionInfo) {
		try {
			IJSchService service = UnicoreTerminalPlainSSHPlugin.getDefault().getJSchService();
			if (service == null) {
				Display.getDefault().asyncExec( new Runnable() {
					public void run() {

					}
				} );
			} else {


				this.userInfo = sshConnectionInfo;
				Session session;

				String keypath = this.userInfo.getPrivateKeyPath();
				if(keypath != null && keypath.trim().length() > 0) service.getJSch().addIdentity(keypath);
				session = service.createSession( this.userInfo.getHostname(),
						this.userInfo.getPort(),
						this.userInfo.getUsername() );

				String authMethods = session.getConfig(PREFERRED_AUTHENTICATION);
				session.setUserInfo( this.userInfo );
				if(authMethods.contains(AUTHENTICATION_PUBLIC_KEY))
				{
					// first try to connect via public key only
					// if this fails, ask the user for a different public/private key
					// if the second try fails or the user clicks cancel, fallback to other auth methods
					session.setConfig(PREFERRED_AUTHENTICATION, AUTHENTICATION_PUBLIC_KEY);
					try {
						session.connect();
					} catch (Exception e) {
						
						if(userInfo.promptPrivateKey("")) // TODO boolean aus config abfragen
						{
							keypath = userInfo.getPrivateKeyPath();
							service.getJSch().addIdentity(keypath);
						}
						else
						{
							// TODO boolean in config setzen
						}
						session = service.createSession( userInfo.getHostname(),
								userInfo.getPort(),
								userInfo.getUsername() );
						session.setUserInfo(userInfo );
					}
					
				}
				session.setConfig(PREFERRED_AUTHENTICATION, authMethods);
				//        if ( forwards != null ) {
					//          for ( IForward forward : forwards ) {
						//            if (forward.getType() == ForwardType.LOCAL ) {
				//              session.setPortForwardingL( forward.getBindPort(),
				//                                          forward.getHostname(),
				//                                          forward.getPort() );
				//            } else {
				//              session.setPortForwardingR( forward.getBindPort(),
				//                                          forward.getHostname(),
				//                                          forward.getPort() );
				//            }
				//          }
				//        }


				if(!session.isConnected()) session.connect();
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
