package de.fzj.unicore.rcp.terminal.ssh.plain;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import de.fzj.unicore.rcp.terminal.BasicConfigUIBuilder;
import de.fzj.unicore.rcp.terminal.tableviewer.model.TerminalSite;

public class PlainSSHConfigUIBuilder extends BasicConfigUIBuilder implements PlainSSHConstants {

	String path = "";
	String type = "";
	
	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	@Override
	protected String[] getRelevantConfigKeys() {
		return new String[]{PLAIN_HOST,PLAIN_PORT,PLAIN_LOGIN};
	}
	
	protected String[] getRelevantKeyConfigKeys() {
		return new String[]{PLAIN_TYPE,PLAIN_KEY};
	}
	

	@Override
	protected String getLabelForKey(String key) {
		if(key.equals(PLAIN_HOST)){
			return "Host";
		}
		else if(key.equals(PLAIN_PORT)){
			return "Port";
		}
		else if(key.equals(PLAIN_LOGIN)){
			return "Login";
		}
		else if(key.equals(PLAIN_TYPE)){
			return "Auth-Type";
		}
		else if(key.equals(PLAIN_KEY)){
			return "Private-Key-File";
		}
		else if(key.equals(PLAIN_TYPE_PASS)){
			return "Password";
		}
		else if(key.equals(PLAIN_TYPE_PUBKEY)){
			return "Public-Key";
		}
		else if(key.equals(PLAIN_TYPE_INTERACTIVE)){
			return "Keyboard-Interactive";
		}
		return key;
	}

	@Override
	public String validateInput(Composite parent, TerminalSite site)
	{
		//super.validateInput(parent, site);
		for(String key : getTextFields().keySet()){
			boolean dirty = true;
			Text text = getTextFields().get(key);
			if(key.equals(PLAIN_KEY)){// && type.equals(PLAIN_TYPE_PASS)){
				dirty=false;
			}
			if (dirty && text.getText().length() == 0 && type.equals(PLAIN_TYPE_PUBKEY)) {
				return "Please check input at field "+getLabelForKey(key);
			}
			if (key.equals(PLAIN_PORT)) {
				try {
					Integer.parseInt(text.getText());
				} catch (Exception e) {
					return "Input is not an integer number at field " +getLabelForKey(key);
				}
			}
		}
		return null;
	}
	
	@Override
	public Map<String,String> getTooltipInfo(TerminalSite site) {

		Map<String,String> config = site.getConnectionTypeConfigs();
		
		Map<String,String> attributes = new HashMap<String,String>();
		for(String key : getRelevantConfigKeys())
		{
			String value = config.get(key) == null ? "" : config.get(key);
			attributes.put(getLabelForKey(key), value);
		}
		
		for(String key : getRelevantKeyConfigKeys())
		{
			String value = config.get(key) == null ? "" : config.get(key);
			attributes.put(getLabelForKey(key), value);
		}

		return attributes;

	}

	@Override
	public void buildUI(Composite parent, TerminalSite site) {

		Map<String,String> config = site.getConnectionTypeConfigs();

		for(String key : getRelevantConfigKeys())
		{
			Label label = new Label(parent, SWT.NONE);
			label.setSize(64, 32);
			label.setText(getLabelForKey(key));
			GridData gridData = new GridData();
			//gridData.widthHint = 150;
			//gridData.horizontalAlignment = SWT.FILL;
			//gridData.grabExcessHorizontalSpace = true;

			Text text = new Text(parent, SWT.BORDER);
			text.setSize(64, 32);
			String value = config.get(key) == null ? "" : config.get(key);
			text.setText(value);
			text.setLayoutData(gridData);
			textFields.put(key, text);
		}
		
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		parent.setLayout(layout);
		
		GridData gridData = new GridData();
		gridData.horizontalSpan = 2;
		
		
		Label labelspace = new Label(parent, SWT.NONE);
		labelspace.setSize(64, 10);
		labelspace.setText(" ");
		
		labelspace = new Label(parent, SWT.NONE);
		labelspace.setSize(64, 10);
		labelspace.setText(" ");
	    
	    final Group group = new Group(parent, SWT.SHADOW_ETCHED_IN);
	    group.setText("Authentication");
	    // Assuming parent is grid layout
	    //group.setLayoutData(new GridData(GridData.FILL_BOTH));
	    
	    group.setLayout(new GridLayout(3, false));
	    group.setLayoutData(gridData);
	    //gridData.horizontalSpan = 4;

	    Listener radioGroup = new Listener () {
	        public void handleEvent (Event event) {		          
	            Control [] children = group.getChildren ();
	            for (int j=0; j<children.length; j++) {
	              Control child = children [j];
	              if (child instanceof Button) {
	                Button button = (Button) child;
	                if ((button.getStyle () & SWT.RADIO) != 0) button.setSelection (false);
	              }
	            }
	          
	          Button button = (Button) event.widget;
	          button.setSelection (true);
	          if(button.getText().equals(getLabelForKey(PlainSSHConstants.PLAIN_TYPE_PASS))){
	        	  //type.getConnectionTypeConfigs().put(PLAIN_TYPE, button.getText());
	        	  type = PlainSSHConstants.PLAIN_TYPE_PASS;
	        	  Control[] controls = group.getChildren();
	        	  enableControls(controls, false, false, true);
	          }
	          else if(button.getText().equals(getLabelForKey(PlainSSHConstants.PLAIN_TYPE_INTERACTIVE))){
	        	  //type.getConnectionTypeConfigs().put(PLAIN_TYPE, button.getText());
	        	  type = PlainSSHConstants.PLAIN_TYPE_INTERACTIVE;
	        	  Control[] controls = group.getChildren();
	        	  enableControls(controls, false, false, true);
	          }
	          else {
	        	  type = PlainSSHConstants.PLAIN_TYPE_PUBKEY;
	        	  Control[] controls = group.getChildren();
	        	  enableControls(controls, true, false, true);
	          }
	        }
	      };
	    
	    gridData = new GridData();
	    final Button passwordButton = new Button(group, SWT.RADIO);
	    passwordButton.setText(getLabelForKey(PlainSSHConstants.PLAIN_TYPE_PASS));
	    passwordButton.setBounds(10, 5, 75, 30);
	    passwordButton.addListener(SWT.Selection, radioGroup);
	    gridData.horizontalSpan = 3;
	    gridData.horizontalAlignment = SWT.FILL;
	    passwordButton.setLayoutData(gridData);
	    
	    final Button interactiveButton = new Button(group, SWT.RADIO);
	    interactiveButton.setText(getLabelForKey(PlainSSHConstants.PLAIN_TYPE_INTERACTIVE));
	    interactiveButton.setBounds(10, 5, 75, 30);
	    interactiveButton.addListener(SWT.Selection, radioGroup);
	    interactiveButton.setLayoutData(gridData);
	    
	    final Button publickeyButton = new Button(group, SWT.RADIO);
	    publickeyButton.setText(getLabelForKey(PlainSSHConstants.PLAIN_TYPE_PUBKEY));
	    publickeyButton.setBounds(10, 5, 75, 30);
	    publickeyButton.addListener(SWT.Selection, radioGroup);
	    publickeyButton.setLayoutData(gridData);

//	    Label filllabel = new Label(group, SWT.NONE);
//	    filllabel.setSize(64, 32);
//	    filllabel.setText(" ");
//	    gridData = new GridData();
//	    gridData.horizontalSpan = 1;
//	    //filllabel.setLayoutData(gridData);

	    boolean dirty = false;
	    if(config.get(PLAIN_TYPE) == null || config.get(PLAIN_TYPE).equals("")){
	    	type = "";
	    }
	    else if(config.get(PLAIN_TYPE).equals(PLAIN_TYPE_PASS)){
	    	type = PLAIN_TYPE_PASS;
	    	passwordButton.setSelection(true);
	    }
	    else if(config.get(PLAIN_TYPE).equals(PLAIN_TYPE_INTERACTIVE)){
	    	type = PLAIN_TYPE_INTERACTIVE;
	    	interactiveButton.setSelection(true);
	    }
	    else if(config.get(PLAIN_TYPE).equals(PLAIN_TYPE_PUBKEY)){
	    	type = PLAIN_TYPE_PUBKEY;
	    	publickeyButton.setSelection(true);
	    	dirty = true;
	    }
	    else type = "";
	    	    
	    Label label = new Label(group, SWT.NONE);
		label.setSize(64, 32);
		label.setText(getLabelForKey(PLAIN_KEY));
		label.setEnabled(dirty);
		final Text text = new Text(group, SWT.BORDER);
		text.setSize(128, 32);
		String path = config.get(PLAIN_KEY) == null ? "" : config.get(PLAIN_KEY);
		text.setText(path);
		text.setToolTipText(text.getText());
		text.setEnabled(dirty);
		gridData = new GridData();
		//gridData.horizontalAlignment = SWT.FILL;
		//gridData.grabExcessHorizontalSpace = true;

		//text.setLayoutData(gridData);
		
		textFields.put(PLAIN_KEY, text);
		
		final Button fileDialogButton = new Button(group, SWT.PUSH);
		fileDialogButton.setText("...");
		fileDialogButton.setBounds(10, 5, 45, 30);
		fileDialogButton.setEnabled(dirty);
		fileDialogButton.addSelectionListener(new SelectionAdapter() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				if(promptPrivateKey(text.getText())){
					text.setText(getPath());
				}
			}		
		});
	    
	}
	
	private void enableControls(Control[] controls, boolean enable, boolean all, boolean radios){
		for(Control c : controls){
  		  if(!(c instanceof Button)){
  			  c.setEnabled(enable);
  		  }
  		  else{
  			  Button cbutton = (Button) c;
  			  if (!((cbutton.getStyle () & SWT.RADIO) != 0)){
  				  c.setEnabled(enable);
  			  }
  			  else{
  				  if(all)c.setEnabled(radios);				  
  			  }
  		  }	        		  
  	  }
	}
	
	
	
	public boolean promptPrivateKey( final String message ) {
		Display.getDefault().syncExec( new Runnable() {
			public void run() {
				FileDialog dialog = new FileDialog(new Shell(), SWT.OPEN);
				if(!message.equals("")) {
					File file = new File(message);
					if(file.exists()){
						dialog.setFilterPath(message);
					}
				}
				dialog.setText("Choose your Private Key(e.g. ~/.ssh/id_dsa)");
				String path = dialog.open();
				if (path != null && path.length()>0){
					setPath(path);
				}
				else setPath("");
			}
		} );
	return !isEmpty(getPath());
	}
	
	private boolean isEmpty(String s)
	{
		return s == null || s.trim().length() == 0;
	}
	
	public void applyConfigurationChanges(Composite parent, TerminalSite site) {
		for(String key : textFields.keySet())
		{
			String value = textFields.get(key).getText();
			site.getConnectionTypeConfigs().put(key, value);
		}
		site.getConnectionTypeConfigs().put(PLAIN_TYPE, type);
		String value = textFields.get(PLAIN_KEY).getText();
		site.getConnectionTypeConfigs().put(PLAIN_KEY, value);
		
		textFields.clear();

	}
	
	public void setEnabled(Composite parent, TerminalSite site, boolean _enabled)
	{
		boolean enabled = _enabled;
		Control[] controls = parent.getChildren();
		for(Control c : controls){
			c.setEnabled(enabled);
			if(c instanceof Group){
				Group group = (Group)c;
				if(enabled) {
					group.setEnabled(true);
					group.setText("Authentication");
				}
				else{
					group.setEnabled(false) ;
					group.setText("");
				}
				Control[] controls2 = group.getChildren();
				if(type.equals(PLAIN_TYPE_PASS) || type.equals(PLAIN_TYPE_INTERACTIVE)){
					enabled = false;
					this.enableControls(controls2, enabled, true, _enabled);
				}
				else{
					this.enableControls(controls2, enabled, true, _enabled);
				}
			}
		}

	}
}
