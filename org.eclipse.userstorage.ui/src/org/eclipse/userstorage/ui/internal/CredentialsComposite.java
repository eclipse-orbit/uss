/*
 * Copyright (c) 2015 Eike Stepper (Berlin, Germany) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Eike Stepper - initial API and implementation
 */
package org.eclipse.userstorage.ui.internal;

import org.eclipse.userstorage.IStorageService;
import org.eclipse.userstorage.internal.Credentials;
import org.eclipse.userstorage.internal.StorageService;
import org.eclipse.userstorage.internal.util.StringUtil;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;

import java.net.URI;
import java.util.concurrent.Callable;

/**
 * @author Eike Stepper
 */
public class CredentialsComposite extends Composite
{
  private final Callable<URI> createAccountURIProvider = new Callable<URI>()
  {
    @Override
    public URI call() throws Exception
    {
      return service.getCreateAccountURI();
    }
  };

  private final Callable<URI> editAccountURIProvider = new Callable<URI>()
  {
    @Override
    public URI call() throws Exception
    {
      return service.getEditAccountURI();
    }
  };

  private final Callable<URI> recoverPasswordURIProvider = new Callable<URI>()
  {
    @Override
    public URI call() throws Exception
    {
      return service.getRecoverPasswordURI();
    }
  };

  private final ModifyListener modifyListener = new ModifyListener()
  {
    @Override
    public void modifyText(ModifyEvent e)
    {
      credentials = new Credentials(usernameText.getText(), passwordText.getText());
      validate();
    }
  };

  private final boolean showServiceCredentials;

  private IStorageService service;

  private Credentials credentials;

  private Label usernameLabel;

  private Text usernameText;

  private Label passwordLabel;

  private Text passwordText;

  private Link createAccountLink;

  private Link editAccountLink;

  private Link recoverPasswordLink;

  public CredentialsComposite(Composite parent, int style, int marginWidth, int marginHeight, boolean showServiceCredentials)
  {
    super(parent, style);
    this.showServiceCredentials = showServiceCredentials;

    GridLayout layout = UIUtil.createGridLayout(getGridColumns());
    layout.marginWidth = marginWidth;
    layout.marginHeight = marginHeight;
    setLayout(layout);

    createUI(this, layout.numColumns);
    setCredentials(null);
  }

  public IStorageService getService()
  {
    return service;
  }

  public void setService(IStorageService service)
  {
    this.service = service;
    if (service != null)
    {
      usernameLabel.setEnabled(true);
      usernameText.setEnabled(true);
      passwordLabel.setEnabled(true);
      passwordText.setEnabled(true);

      enableLink(createAccountLink, createAccountURIProvider);
      enableLink(editAccountLink, editAccountURIProvider);
      enableLink(recoverPasswordLink, recoverPasswordURIProvider);

      if (showServiceCredentials)
      {
        setCredentials(((StorageService)service).getCredentials());
      }
    }
    else
    {
      usernameLabel.setEnabled(false);
      usernameText.setEnabled(false);
      passwordLabel.setEnabled(false);
      passwordText.setEnabled(false);

      createAccountLink.setEnabled(false);
      editAccountLink.setEnabled(false);
      recoverPasswordLink.setEnabled(false);

      if (showServiceCredentials)
      {
        setCredentials(null);
      }
    }
  }

  public Credentials getCredentials()
  {
    return credentials;
  }

  public void setCredentials(Credentials credentials)
  {
    this.credentials = credentials;
    if (credentials != null)
    {
      usernameText.setText(StringUtil.safe(credentials.getUsername()));
      passwordText.setText(StringUtil.safe(credentials.getPassword()));
    }
    else
    {
      usernameText.setText("");
      passwordText.setText("");
    }
  }

  public int getGridColumns()
  {
    return 2;
  }

  @Override
  public void setEnabled(boolean enabled)
  {
    usernameLabel.setEnabled(enabled);
    usernameText.setEnabled(enabled);
    passwordLabel.setEnabled(enabled);
    passwordText.setEnabled(enabled);
    createAccountLink.setEnabled(enabled);
  }

  protected void createUI(Composite parent, int columns)
  {
    usernameLabel = new Label(parent, SWT.NONE);
    usernameLabel.setText("User name:");

    usernameText = new Text(parent, SWT.BORDER);
    usernameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, columns - 1, 1));
    usernameText.addModifyListener(modifyListener);

    passwordLabel = new Label(parent, SWT.NONE);
    passwordLabel.setText("Password:");

    passwordText = new Text(parent, SWT.BORDER | SWT.PASSWORD);
    passwordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, columns - 1, 1));
    passwordText.addModifyListener(modifyListener);

    createAccountLink = createLink(parent, columns, "Create an account", createAccountURIProvider);
    editAccountLink = createLink(parent, columns, "Edit your account", editAccountURIProvider);
    recoverPasswordLink = createLink(parent, columns, "Recover your password", recoverPasswordURIProvider);
  }

  protected void validate()
  {
  }

  private Link createLink(Composite parent, int columns, final String label, final Callable<URI> uriProvider)
  {
    new Label(parent, SWT.NONE); // Skip first column.

    final Link link = new Link(parent, SWT.NONE);
    link.setText("<a>" + label + "</a>");
    link.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, columns - 1, 1));
    link.addSelectionListener(new SelectionAdapter()
    {
      @Override
      public void widgetSelected(SelectionEvent e)
      {
        if (link.isEnabled())
        {
          try
          {
            String uri = uriProvider.call().toString();
            if (!SystemBrowser.open(uri))
            {
              MessageDialog.openInformation(getShell(), "System Browser Not Found", "Go to " + uri + " to " + label.toLowerCase() + ".");
            }
          }
          catch (Exception ex)
          {
            Activator.log(ex);
          }
        }
      }
    });

    return link;
  }

  private void enableLink(Link link, Callable<URI> uriProvider)
  {
    try
    {
      URI uri = uriProvider.call();
      link.setEnabled(uri != null);
    }
    catch (Exception ex)
    {
      //$FALL-THROUGH$
    }
  }
}
