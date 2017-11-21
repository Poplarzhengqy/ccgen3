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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import de.fzj.unicore.rcp.terminal.TerminalConstants;
import de.fzj.unicore.rcp.terminal.UnicoreTerminalPlugin;
import de.fzj.unicore.rcp.terminal.views.TerminalConfigView;


class SSHConnectionInfo implements UserInfo, UIKeyboardInteractive, PlainSSHConstants {
	
	private String siteId;
	private String siteName;
	private String defaultMethod;
	private Map<String,String> config;
	
	
	public Map<String, String> getConfig() {
		return config;
	}

	private String privateKeyPath;
	
	
	public String getAuthType() {
		return config.get(PLAIN_TYPE);
	}


	public void setAuthType(String authType) {
		Map<String,String> newConfig = new HashMap<String, String>(this.config);
		if(authType == null) newConfig.remove(PLAIN_TYPE);
		else newConfig.put(PLAIN_TYPE, authType);
		setConfig(newConfig);
	}


	public String getPrivateKeyPath() {
		return config.get(PLAIN_KEY);
	}


	public void setPrivateKeyPath(String privateKeyPath) {
		Map<String,String> newConfig = new HashMap<String, String>(this.config);
		if(privateKeyPath == null) newConfig.remove(PLAIN_KEY);
		else newConfig.put(PLAIN_KEY, privateKeyPath);
		setConfig(newConfig);
	}

	String passphrase;
	String password;
	private boolean passwordInteractiveUsed;

	public boolean isPasswordInteractiveUsed() {
		return passwordInteractiveUsed;
	}


	public void setPasswordInteractiveUsed(boolean passwordInteractiveUsed) {
		this.passwordInteractiveUsed = passwordInteractiveUsed;
	}

	private String[] keyboardInteractiveResult;
	SSHConnectionInfo(Map<String,String> config) {
		this.siteId = config.get(TerminalConstants.ID);
		this.siteName = config.get(TerminalConstants.NAME);
		this.defaultMethod = config.get(TerminalConstants.CONNECTION_TYPE);
		this.config = config;
	}
	
	
	protected boolean configChanged(Map<String,String> oldConfig, Map<String,String> newConfig)
	{
		if(config.size() != this.config.size()) return true;
		for(String key : newConfig.keySet())
		{
			String oldValue = oldConfig.get(key);
			String newValue = newConfig.get(key);
			boolean equal = newValue == null ? oldValue == null : newValue.equals(oldValue);
			if(!equal) return true;
		}
		return false;
	}

	public String getHostname() {
		return config.get(PLAIN_HOST);
	}

	public String getPassphrase() {
		return this.passphrase;
	}
	
	public String getPassword() {
		return this.password;
	}

	Integer getPort() {
		String s = config.get(PLAIN_PORT);
		if(s == null) return null;
		else return Integer.parseInt(s);
		
	}

	public String getUsername() {
		return config.get(PLAIN_LOGIN);
	}

	private boolean isEmpty(String s)
	{
		return s == null || s.trim().length() == 0;
	}

	public boolean promptPlainType( ) {
		Display.getDefault().syncExec( new Runnable() {
			public void run() {
				String configured = config.get(PLAIN_HOST);
				Shell shell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
				
				if (shell == null) shell = new Shell();
				PlainTypeDialog dlg = new PlainTypeDialog(shell,
						"Plain SSH Authentication","Please, select the authentication method for host " + configured,"",null); 
				dlg.open();
				String value = dlg.getValue();
				if (value != null && value.length()>0){
					if(value.equals(PLAIN_TYPE_PASS)){
						setAuthType(PLAIN_TYPE_PASS);
					}
					else if(value.equals(PLAIN_TYPE_PUBKEY)){
						setAuthType(PLAIN_TYPE_PUBKEY);
					}
					else {
						setAuthType(null);
					}
				}			
			}
		} );
	return !isEmpty(config.get(PLAIN_TYPE));
}
	
	public boolean promptHost( ) {
			Display.getDefault().syncExec( new Runnable() {
				public void run() {
					String configured = config.get(PLAIN_HOST);
					String hostname = configured == null ? "hostname" : configured;
					InputDialog dlg = new InputDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
							"Host name","Please enter the host name:",hostname,null); 
					dlg.open();
					if ( dlg.getReturnCode() == InputDialog.OK )
						setHostname(dlg.getValue());
					else setHostname(null);
					
				}
			} );
		return !isEmpty(config.get(PLAIN_HOST));
	}
	
	public String[] promptKeyboardInteractive( final String destination,
			final String name,
			final String instruction,
			final String[] prompt,
			final boolean[] echo ) {
		String[] result;
		
		if ( this.passwordInteractiveUsed ) {
			Display.getDefault().syncExec( new Runnable() {
				public void run() {
					HiddenInputDialog dlg = new HiddenInputDialog(PlatformUI.getWorkbench().getDisplay().
							getActiveShell(),"Keyboard-Interactive Password",
							"Please enter your password for user "  + getUsername(),"321", null);
			          dlg.open();
					keyboardInteractiveResult = new String[]{dlg.getValue()};
				}
			} );
			result = this.keyboardInteractiveResult;
		} else {
			this.passwordInteractiveUsed = true;
			
			result = new String[]{this.password};
		}
		return result;
	}
	

	public boolean promptPrivateKey( final String message ) {
		if(this.privateKeyPath != null && !this.privateKeyPath.equals("")) {
			this.setPrivateKeyPath(privateKeyPath);
			return true;
		}
		Display.getDefault().syncExec( new Runnable() {
			public void run() {
				FileDialog dialog = new FileDialog(new Shell(), SWT.OPEN);
				dialog.setText("Choose your Private Key(e.g. ~/.ssh/id_dsa)");
				privateKeyPath = dialog.open();
				if (privateKeyPath != null && privateKeyPath.length()>0){
					setPrivateKeyPath(privateKeyPath);
				}
				else privateKeyPath = "";
			}
		} );
	return !isEmpty(privateKeyPath);
	}
	
	public boolean promptPassphrase( final String message ) {
		if(passphrase != null) return true;

		Display.getDefault().syncExec( new Runnable() {
			public void run() {
				String msg = "Please, enter passphrase for your private key";
				
				HiddenInputDialog dlg = new HiddenInputDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(),"Passphrase",msg,"", null);
				dlg.open();
				if ( dlg.getReturnCode() == InputDialog.OK )
					SSHConnectionInfo.this.passphrase = dlg.getValue();
				else
					SSHConnectionInfo.this.passphrase = null;
			}
		} );
	return !isEmpty(passphrase);
	}
	

	public boolean promptPassword( final String message ) {
		if(password != null) return true;

			Display.getDefault().syncExec( new Runnable() {
				public void run() {
					String msg = "Please, enter your password for user "  + getUsername();
//					String errorMessage = "";
					if(getConfig().get(PLAIN_TYPE).equals(PLAIN_TYPE_PUBKEY)){
						msg = "Public-key authentication failed! Trying with password. Please enter your password";
					}

					String username = getUsername();
					if(username != null) 
					{
						msg += " for username "+username;
					}
					HiddenInputDialog dlg = new HiddenInputDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(),"Password",msg,"", null);
					dlg.setErrorMessage(msg);
					dlg.open();
					if ( dlg.getReturnCode() == InputDialog.OK )
						SSHConnectionInfo.this.password = dlg.getValue();
					else
						SSHConnectionInfo.this.password = null;
				}
			} );
		
		return !isEmpty(password);
	}


	public boolean promptPort( ) {
		Display.getDefault().syncExec( new Runnable() {
			public void run() {
				String configured = config.get(PLAIN_PORT);
				String port = configured == null ? "22" : configured;
				InputDialog dlg = new InputDialog(new Shell(),"Port","Please enter the port:",port,new IInputValidator() {
					
					public String isValid(String newText) {
						try {
							Integer.parseInt(newText);
							return null;
						} catch (Exception e) {
							return "Input is not an integer number";
						}
					}
				}); 
				dlg.open();
				if ( dlg.getReturnCode() == InputDialog.OK )
					setPort(Integer.parseInt(dlg.getValue()));
				else setPort(null);
				
			}
		} );
	return !isEmpty(config.get(PLAIN_HOST));
}

	public boolean promptUsername( ) {
			Display.getDefault().syncExec( new Runnable() {
				public void run() {
					String configured = config.get(PLAIN_LOGIN);
					String login = configured == null ? "login" : configured;
					Shell shell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
					if (shell == null) shell = new Shell();
					InputDialog dlg = new InputDialog(shell,"User name","Please enter the user name:",login,null); 
					dlg.open();
					String result = dlg.getValue();
					if ( dlg.getReturnCode() == InputDialog.OK && result.trim().length() > 0){
						setUsername(result);
					}						
					else setUsername(null);
				}
			} );
		return !isEmpty(config.get(PLAIN_LOGIN));
	}

	public boolean promptYesNo( final String str ) {
		final boolean[] result = { false };
		Display.getDefault().syncExec( new Runnable() {
			public void run() {
				result[0] = MessageDialog.openQuestion( null,
						Messages.getString( "SshShell.sshTerminal" ), //$NON-NLS-1$
						str );
			}
		} );
		return result[0];
		//return false;
	}
	
	public void setConfig(Map<String, String> config) {
		if(configChanged(this.config, config))
		{
			password = null;
			passphrase = null;
			PlainSSHConfigPersistence.getInstance().writeConfig(siteId, siteName, defaultMethod, config);
			
			PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
			{
				public void run() {
					try {
						TerminalConfigView sshConfig = (TerminalConfigView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(TerminalConfigView.ID);
						if(sshConfig != null) sshConfig.refresh();
					} catch (Exception e) {
						UnicoreTerminalPlugin.log(Status.ERROR,"Unable to refresh terminal config view: "+e.getMessage(), e );
					}
				}
			});
		
		}
		this.config = config;
		
	}

	public void setHostname(String hostname) {
		Map<String,String> newConfig = new HashMap<String, String>(this.config);
		if(hostname == null) newConfig.remove(PLAIN_HOST);
		else newConfig.put(PLAIN_HOST, hostname);
		setConfig(newConfig);
	}

	public void setPassphrase(String passphrase) {
		this.passphrase = passphrase;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	

	public void setPort(Integer port) {
		Map<String,String> newConfig = new HashMap<String, String>(this.config);
		if(port == null) newConfig.remove(PLAIN_PORT);
		else newConfig.put(PLAIN_PORT, port.toString());
		setConfig(newConfig);
	}
	
	public void setUsername(String username) {
		Map<String,String> newConfig = new HashMap<String, String>(this.config);
		if(username == null) newConfig.remove(PLAIN_LOGIN);
		else newConfig.put(PLAIN_LOGIN, username);
		setConfig(newConfig);
	}
	
	public void showMessage( final String message ) {
		if (message != null && message.trim().length() != 0) {
			Display.getDefault().syncExec( new Runnable() {
				public void run() {
					MessageDialog.openInformation( null,
							Messages.getString( "SshShell.sshTerminal" ), //$NON-NLS-1$
							message );
				}
			} );
		}
	}
	
	class PlainTypeDialog extends Dialog {
		  String hostname;
		  String title;
		  
		  /**
		     * The message to display, or <code>null</code> if none.
		     */
		    private String message;

		    /**
		     * The input value; the empty string by default.
		     */
		    private String value = "";//$NON-NLS-1$

		    /**
		     * The input validator, or <code>null</code> if none.
		     */
		    private IInputValidator validator;

		    /**
		     * Ok button widget.
		     */
		    private Button okButton;

		    /**
		     * Password button widget.
		     */
		    private Button passwordButton;
		    
		    /**
		     * Public Key button widget.
		     */
		    private Button publickeyButton;
		    
		    /**
		     * Error message label widget.
		     */
		    private Label errorMessageLabel;
		    
		    /**
		     * Error message string.
		     */
		    private String errorMessage;
		  /**
		   * @param parent
		   */
		  public PlainTypeDialog(Shell parentshell, String hostname) {
		    super(parentshell);
		    this.hostname = hostname;
		  }

		/**
		   * @param parent
		   * @param style
		   */
		  public PlainTypeDialog(Shell parentShell, String dialogTitle,
		            String dialogMessage, String initialValue, IInputValidator validator) {
		        super(parentShell);
		        this.title = dialogTitle;
		        message = dialogMessage;
		        if (initialValue == null) {
					value = "";//$NON-NLS-1$
				} else {
					value = initialValue;
				}
		        this.validator = validator;;
		    //this.hostname = hostname;
		  }
		  
		  /*
		     * (non-Javadoc) Method declared on Dialog.
		     */
		    protected void buttonPressed(int buttonId) {
		        if (buttonId == IDialogConstants.OK_ID) {
		            //value = text.getText();
		        } 
		        else {
		            value = null;
		        }
		        super.buttonPressed(buttonId);
		    }
		    
		    /*
		     * (non-Javadoc)
		     * 
		     * @see org.eclipse.jface.dialogs.Dialog#createButtonsForButtonBar(org.eclipse.swt.widgets.Composite)
		     */
		    protected void createButtonsForButtonBar(Composite parent) {
		        // create OK and Cancel buttons by default
		        okButton = createButton(parent, IDialogConstants.OK_ID,
		                IDialogConstants.OK_LABEL, true);
		        createButton(parent, IDialogConstants.CANCEL_ID,
		                IDialogConstants.CANCEL_LABEL, false);
		        //do this here because setting the text will set enablement on the ok
		        // button
		        //passwordButton.setFocus();
		    }
		    
		    /*
		     * (non-Javadoc) Method declared on Dialog.
		     */
		    protected Control createDialogArea(Composite parent) {
		        // create composite
		        final Composite composite = (Composite) super.createDialogArea(parent);
		        // create message
			    //label.setText("Please, select the Plain-SSH authentication method for " +  hostname);

		        if (message != null) {
		            Label label = new Label(composite, SWT.WRAP);
		            label.setText(message);
		            GridData data = new GridData(GridData.GRAB_HORIZONTAL
		                    | GridData.GRAB_VERTICAL | GridData.HORIZONTAL_ALIGN_FILL
		                    | GridData.VERTICAL_ALIGN_CENTER);
		            data.widthHint = convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH);
		            label.setLayoutData(data);
		            label.setFont(parent.getFont());
		        }
		        
		        Listener radioGroup = new Listener () {
			        public void handleEvent (Event event) {		          
			            Control [] children = composite.getChildren ();
			            for (int j=0; j<children.length; j++) {
			              Control child = children [j];
			              if (child instanceof Button) {
			                Button button = (Button) child;
			                if ((button.getStyle () & SWT.RADIO) != 0) button.setSelection (false);
			              }
			            }
			          
			          Button button = (Button) event.widget;
			          button.setSelection (true);
			          value = button.getText();
			        }
			      };
			    
			    passwordButton = new Button(composite, SWT.RADIO);
			    passwordButton.setText(PlainSSHConstants.PLAIN_TYPE_PASS);
			    passwordButton.setBounds(10, 5, 75, 30);
			    passwordButton.addListener(SWT.Selection, radioGroup);
			    
			    publickeyButton = new Button(composite, SWT.RADIO);
			    publickeyButton.setText(PlainSSHConstants.PLAIN_TYPE_PUBKEY);
			    publickeyButton.setBounds(10, 5, 75, 30);
			    publickeyButton.addListener(SWT.Selection, radioGroup);
		        
		        errorMessageLabel = new Label(composite,SWT.NONE);
		        errorMessageLabel.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL
		                | GridData.HORIZONTAL_ALIGN_FILL));
		        errorMessageLabel.setBackground(errorMessageLabel.getDisplay()
		                .getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		        errorMessageLabel.setForeground(errorMessageLabel.getDisplay()
		                .getSystemColor(SWT.COLOR_RED));
		        // Set the error message text
		        // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=66292
		        setErrorMessage(errorMessage);

		        applyDialogFont(composite);
		        return composite;
		    }

		    /**
		     * Returns the error message label.
		     * 
		     * @return the error message label
		     */
		    protected Label getErrorMessageLabel() {
		        return errorMessageLabel;
		    }
		  
		  /*
		     * (non-Javadoc)
		     * 
		     * @see org.eclipse.jface.window.Window#configureShell(org.eclipse.swt.widgets.Shell)
		     */
		    protected void configureShell(Shell shell) {
		        super.configureShell(shell);
		        if (title != null) {
					shell.setText(title);
				}
		    }
		    
		    /**
		     * Returns the ok button.
		     * 
		     * @return the ok button
		     */
		    protected Button getOkButton() {
		        return okButton;
		    }


		    /**
		     * Returns the validator.
		     * 
		     * @return the validator
		     */
		    protected IInputValidator getValidator() {
		        return validator;
		    }

		    /**
		     * Returns the string typed into this input dialog.
		     * 
		     * @return the input string
		     */
		    public String getValue() {
		        return value;
		    }

		    /**
		     * Validates the input.
		     * <p>
		     * The default implementation of this framework method delegates the request
		     * to the supplied input validator object; if it finds the input invalid,
		     * the error message is displayed in the dialog's message line. This hook
		     * method is called whenever the text changes in the input field.
		     * </p>
		     */
		    protected void validateInput() {
		        String errorMessage = null;
		        if (validator != null) {
		            errorMessage = validator.isValid(value);
		        }
		        // Bug 16256: important not to treat "" (blank error) the same as null
		        // (no error)
		        setErrorMessage(errorMessage);
		    }

		    /**
		     * Sets or clears the error message.
		     * If not <code>null</code>, the OK button is disabled.
		     * 
		     * @param errorMessage
		     *            the error message, or <code>null</code> to clear
		     * @since 3.0
		     */
		    public void setErrorMessage(String errorMessage) {
		    	this.errorMessage = errorMessage;
		    	if (errorMessageLabel != null && !errorMessageLabel.isDisposed()) {
		    		errorMessageLabel.setText(errorMessage == null ? "" : errorMessage); //$NON-NLS-1$
		    		errorMessageLabel.getParent().update();
		    		// Access the ok button by id, in case clients have overridden button creation.
		    		// See https://bugs.eclipse.org/bugs/show_bug.cgi?id=113643
		    		Control button = getButton(IDialogConstants.OK_ID);
		    		if (button != null) {
		    			button.setEnabled(errorMessage == null);
		    		}
		    	}
		    }

		  /**
		   * Makes the dialog visible.
		   * 
		   * @return
		   */
//		  public String open() {
//		    Shell parent = getParent();
//		    final Shell shell =
//		      new Shell(parent, SWT.TITLE | SWT.BORDER | SWT.APPLICATION_MODAL);
//		    shell.setText("Plain SSH Authentication");
//
//		    shell.setLayout(new GridLayout(1, true));
//		    
//		    
//		    
//		      
//		    Label label = new Label(shell, SWT.NULL);
//		    label.setText("Please, select the Plain-SSH authentication method for " +  hostname);
//
//		    
//		    final Composite composite = new Composite(shell, SWT.NONE);
//		    composite.setLayout(new GridLayout(4, false));
//		    
//		    Listener radioGroup = new Listener () {
//		        public void handleEvent (Event event) {		          
//		            Control [] children = composite.getChildren ();
//		            for (int j=0; j<children.length; j++) {
//		              Control child = children [j];
//		              if (child instanceof Button) {
//		                Button button = (Button) child;
//		                if ((button.getStyle () & SWT.RADIO) != 0) button.setSelection (false);
//		              }
//		            }
//		          
//		          Button button = (Button) event.widget;
//		          button.setSelection (true);
//		        }
//		      };
//		    
//		    final Button passwordButton = new Button(composite, SWT.RADIO);
//		    passwordButton.setText(PlainSSHConstants.PLAIN_TYPE_PASS);
//		    passwordButton.setBounds(10, 5, 75, 30);
//		    passwordButton.addListener(SWT.Selection, radioGroup);
//		    
//		    final Button publickeyButton = new Button(composite, SWT.RADIO);
//		    publickeyButton.setText(PlainSSHConstants.PLAIN_TYPE_PUBKEY);
//		    publickeyButton.setBounds(10, 5, 75, 30);
//		    publickeyButton.addListener(SWT.Selection, radioGroup);
//
//		    final Button buttonType = new Button(composite, SWT.PUSH);
//		    buttonType.setText("   Ok   ");
//		    buttonType.setBounds(10, 5, 75, 30);
//		    
//		    Button buttonCancel = new Button(composite, SWT.PUSH);
//		    buttonCancel.setText("Cancel");
//		    buttonCancel.setBounds(10, 5, 75, 30);
//
//		    buttonType.addListener(SWT.Selection, new Listener() {
//		      public void handleEvent(Event event) {
//		    	if(passwordButton.getSelection())value = PlainSSHConstants.PLAIN_TYPE_PASS;
//		    	else if(publickeyButton.getSelection()) value = PlainSSHConstants.PLAIN_TYPE_PUBKEY;
//		    	else{
//		    		value = "";
//		    	}
//		        shell.dispose();
//		      }
//		    });
//
//		    buttonCancel.addListener(SWT.Selection, new Listener() {
//		      public void handleEvent(Event event) {
//		        value = null;
//		        shell.dispose();
//		      }
//		    });
//		    
//		    shell.addListener(SWT.Traverse, new Listener() {
//		      public void handleEvent(Event event) {
//		        if(event.detail == SWT.TRAVERSE_ESCAPE)
//		          event.doit = false;
//		      }
//		    });
//
//		    shell.pack();
//		    shell.open();
//
//		    Display display = parent.getDisplay();
//		    while (!shell.isDisposed()) {
//		      if (!display.readAndDispatch())
//		        display.sleep();
//		    }
//
//		    return value;
//		  }
	}
}
