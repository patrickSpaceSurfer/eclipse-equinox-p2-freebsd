/*******************************************************************************
 *  Copyright (c) 2008, 2018 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *      IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.touchpoint.eclipse.actions;

import java.util.Map;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.frameworkadmin.BundleInfo;
import org.eclipse.equinox.internal.p2.touchpoint.eclipse.EclipseTouchpoint;
import org.eclipse.equinox.internal.p2.touchpoint.eclipse.Util;
import org.eclipse.equinox.internal.provisional.frameworkadmin.Manipulator;
import org.eclipse.equinox.p2.engine.spi.ProvisioningAction;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.osgi.util.NLS;

public class MarkStartedAction extends ProvisioningAction {
	public static final String ID = "markStarted"; //$NON-NLS-1$

	public IStatus execute(Map<String, Object> parameters) {
		Manipulator manipulator = (Manipulator) parameters.get(EclipseTouchpoint.PARM_MANIPULATOR);
		IInstallableUnit iu = (IInstallableUnit) parameters.get(EclipseTouchpoint.PARM_IU);
		String started = (String) parameters.get(ActionConstants.PARM_STARTED);
		if (started == null) {
			return Util.createError(NLS.bind(Messages.parameter_not_set, ActionConstants.PARM_STARTED, ID));
		}

		// Changes to this object will be reflected in the backing runtime configuration store
		BundleInfo bundleInfo = Util.findBundleInfo(manipulator.getConfigData(), iu);
		if (bundleInfo == null) {
			return Util.createWarning(NLS.bind(Messages.failed_find_bundleinfo, iu));
		}

		// Bundle fragments are not started
		if (bundleInfo.getFragmentHost() != null) {
			return Status.OK_STATUS;
		}

		getMemento().put(ActionConstants.PARM_PREVIOUS_STARTED, Boolean.valueOf(bundleInfo.isMarkedAsStarted()));
		bundleInfo.setMarkedAsStarted(Boolean.valueOf(started).booleanValue());
		return Status.OK_STATUS;
	}

	public IStatus undo(Map<String, Object> parameters) {
		Boolean previousStarted = (Boolean) getMemento().get(ActionConstants.PARM_PREVIOUS_STARTED);
		if (previousStarted == null) {
			return Status.OK_STATUS;
		}

		Manipulator manipulator = (Manipulator) parameters.get(EclipseTouchpoint.PARM_MANIPULATOR);
		IInstallableUnit iu = (IInstallableUnit) parameters.get(EclipseTouchpoint.PARM_IU);

		// Changes to this object will be reflected in the backing runtime configuration store
		BundleInfo bundleInfo = Util.findBundleInfo(manipulator.getConfigData(), iu);
		if (bundleInfo == null) {
			return Util.createWarning(NLS.bind(Messages.failed_find_bundleinfo, iu));
		}

		bundleInfo.setMarkedAsStarted(previousStarted.booleanValue());
		return Status.OK_STATUS;
	}
}
