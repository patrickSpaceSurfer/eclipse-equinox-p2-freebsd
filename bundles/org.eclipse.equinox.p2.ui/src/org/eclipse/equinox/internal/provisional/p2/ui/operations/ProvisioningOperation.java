/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.provisional.p2.ui.operations;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.ui.ProvUIMessages;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.osgi.util.NLS;

/**
 * Abstract class representing provisioning operations.  ProvisioningOperations
 * can be run in the foreground or in the background as a job.
 * 
 * @since 3.4
 */

public abstract class ProvisioningOperation {

	private String label;

	public ProvisioningOperation(String label) {
		this.label = label;
	}

	/**
	 * 
	 */
	public IStatus execute(IProgressMonitor monitor, final IAdaptable uiInfo) throws ExecutionException {
		IStatus status;
		try {
			status = doExecute(monitor, uiInfo);
		} catch (final ProvisionException e) {
			throw new ExecutionException(NLS.bind(ProvUIMessages.ProvisioningOperation_ExecuteErrorTitle, label, e));
		} catch (OperationCanceledException e) {
			return Status.CANCEL_STATUS;
		}
		return status;
	}

	/**
	 * Perform the specific work involved in executing this operation.
	 * 
	 * @param monitor
	 *            the progress monitor to use for the operation
	 * @param uiInfo
	 *            the IAdaptable (or <code>null</code>) provided by the
	 *            caller in order to supply UI information for prompting the
	 *            user if necessary. When this parameter is not
	 *            <code>null</code>, it contains an adapter for the
	 *            org.eclipse.swt.widgets.Shell.class
	 * @throws ProvisionException
	 *             propagates any ProvisionException thrown
	 * 
	 */
	protected abstract IStatus doExecute(IProgressMonitor monitor, IAdaptable uiInfo) throws ProvisionException;

	protected IStatus okStatus() {
		return Status.OK_STATUS;
	}

	public String getLabel() {
		return label;
	}

	/**
	 * Return a boolean indicating whether the operation can be run in the
	 * background. 
	 * 
	 * @return <code>true</code> if the operation can be run in the background, and
	 * <code>false</code> if it should be run in the UI.
	 */
	public boolean runInBackground() {
		return false;
	}

	/**
	 * Return a boolean indicating whether this operation was triggered by the
	 * user.  This value is used to determine whether any job running this operation
	 * should be considered a user job.  This can affect the way progress is shown to the user.
	 * 
	 * @return <code>true</code> if the operation was initiated by the user, 
	 * <code>false</code> if it was not.
	 */
	public boolean isUser() {
		return true;
	}

}