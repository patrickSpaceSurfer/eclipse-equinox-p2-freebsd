/*******************************************************************************
 *  Copyright (c) 2008, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *      IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.touchpoint.eclipse;

import org.eclipse.equinox.internal.p2.engine.InstallableUnitOperand;

import java.io.File;
import java.util.*;
import org.eclipse.equinox.internal.p2.touchpoint.eclipse.EclipseTouchpoint;
import org.eclipse.equinox.internal.p2.touchpoint.eclipse.Util;
import org.eclipse.equinox.internal.p2.touchpoint.eclipse.actions.ActionConstants;
import org.eclipse.equinox.internal.p2.touchpoint.eclipse.actions.RemoveProgramArgumentAction;
import org.eclipse.equinox.internal.provisional.frameworkadmin.Manipulator;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.IFileArtifactRepository;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;
import org.eclipse.osgi.service.resolver.BundleDescription;

public class RemoveProgramArgumentActionTest extends AbstractProvisioningTest {

	public RemoveProgramArgumentActionTest(String name) {
		super(name);
	}

	public RemoveProgramArgumentActionTest() {
		super("");
	}

	public void testExecuteUndo() {
		Map parameters = new HashMap();
		parameters.put(ActionConstants.PARM_AGENT, getAgent());
		EclipseTouchpoint touchpoint = new EclipseTouchpoint();
		Properties profileProperties = new Properties();
		profileProperties.setProperty(IProfile.PROP_INSTALL_FOLDER, getTempFolder().toString());
		IProfile profile = createProfile("test", profileProperties);
		InstallableUnitOperand operand = new InstallableUnitOperand(null, createIU("test"));
		touchpoint.initializePhase(null, profile, "test", parameters);
		parameters.put("iu", operand.second());
		touchpoint.initializeOperand(profile, parameters);
		Manipulator manipulator = (Manipulator) parameters.get(EclipseTouchpoint.PARM_MANIPULATOR);
		assertNotNull(manipulator);

		String programArg = "-test";
		manipulator.getLauncherData().addProgramArg(programArg);
		assertTrue(Arrays.asList(manipulator.getLauncherData().getProgramArgs()).contains(programArg));

		parameters.put(ActionConstants.PARM_PROGRAM_ARG, programArg);
		parameters = Collections.unmodifiableMap(parameters);

		RemoveProgramArgumentAction action = new RemoveProgramArgumentAction();
		action.execute(parameters);
		assertFalse(Arrays.asList(manipulator.getLauncherData().getProgramArgs()).contains(programArg));
		action.undo(parameters);
		assertTrue(Arrays.asList(manipulator.getLauncherData().getProgramArgs()).contains(programArg));
	}

	public void testExecuteUndoWithArtifact() {
		Properties profileProperties = new Properties();
		File installFolder = getTempFolder();
		profileProperties.setProperty(IProfile.PROP_INSTALL_FOLDER, installFolder.toString());
		profileProperties.setProperty(IProfile.PROP_CACHE, installFolder.toString());
		IProfile profile = createProfile("test", profileProperties);

		IFileArtifactRepository bundlePool = Util.getBundlePoolRepository(getAgent(), profile);
		File osgiSource = getTestData("1.0", "/testData/eclipseTouchpoint/bundles/org.eclipse.osgi_3.4.2.R34x_v20080826-1230.jar");
		File targetPlugins = new File(installFolder, "plugins");
		assertTrue(targetPlugins.mkdir());
		File osgiTarget = new File(targetPlugins, "org.eclipse.osgi_3.4.2.R34x_v20080826-1230.jar");
		copy("2.0", osgiSource, osgiTarget);

		BundleDescription bundleDescription = BundlesAction.createBundleDescription(osgiTarget);
		IArtifactKey key = BundlesAction.createBundleArtifactKey(bundleDescription.getSymbolicName(), bundleDescription.getVersion().toString());
		IArtifactDescriptor descriptor = PublisherHelper.createArtifactDescriptor(key, osgiTarget);
		IInstallableUnit iu = createBundleIU(bundleDescription, osgiTarget.isDirectory(), key);
		bundlePool.addDescriptor(descriptor);

		Map parameters = new HashMap();
		parameters.put(ActionConstants.PARM_AGENT, getAgent());
		parameters.put(ActionConstants.PARM_PROFILE, profile);
		EclipseTouchpoint touchpoint = new EclipseTouchpoint();
		touchpoint.initializePhase(null, profile, "test", parameters);
		InstallableUnitOperand operand = new InstallableUnitOperand(null, iu);
		parameters.put("iu", operand.second());
		parameters.put("artifact", key);
		touchpoint.initializeOperand(profile, parameters);
		Manipulator manipulator = (Manipulator) parameters.get(EclipseTouchpoint.PARM_MANIPULATOR);
		assertNotNull(manipulator);

		String programArg = "-somekey";
		Map keyParameters = new HashMap(parameters);
		keyParameters.put(ActionConstants.PARM_PROGRAM_ARG, programArg);
		manipulator.getLauncherData().addProgramArg(programArg);

		programArg = "@artifact";
		String resolvedArtifact = osgiTarget.getAbsolutePath();

		manipulator.getLauncherData().addProgramArg(resolvedArtifact);
		assertTrue(Arrays.asList(manipulator.getLauncherData().getProgramArgs()).contains(resolvedArtifact));
		parameters.put(ActionConstants.PARM_PROGRAM_ARG, programArg);
		parameters = Collections.unmodifiableMap(parameters);

		RemoveProgramArgumentAction artifactAction = new RemoveProgramArgumentAction();
		RemoveProgramArgumentAction keyAction = new RemoveProgramArgumentAction();

		keyAction.execute(keyParameters);
		artifactAction.execute(parameters);
		assertFalse(Arrays.asList(manipulator.getLauncherData().getProgramArgs()).contains(resolvedArtifact));
		artifactAction.undo(parameters);
		keyAction.undo(keyParameters);
		assertTrue(Arrays.asList(manipulator.getLauncherData().getProgramArgs()).contains(resolvedArtifact));
	}

	public void testExecuteUndoWithArtifactLocation() {
		Properties profileProperties = new Properties();
		File installFolder = getTempFolder();
		profileProperties.setProperty(IProfile.PROP_INSTALL_FOLDER, installFolder.toString());
		profileProperties.setProperty(IProfile.PROP_CACHE, installFolder.toString());
		IProfile profile = createProfile("test", profileProperties);

		IFileArtifactRepository bundlePool = Util.getBundlePoolRepository(getAgent(), profile);
		File osgiSource = getTestData("1.0", "/testData/eclipseTouchpoint/bundles/org.eclipse.osgi_3.4.2.R34x_v20080826-1230.jar");
		File targetPlugins = new File(installFolder, "plugins");
		assertTrue(targetPlugins.mkdir());
		File osgiTarget = new File(targetPlugins, "org.eclipse.osgi_3.4.2.R34x_v20080826-1230.jar");
		copy("2.0", osgiSource, osgiTarget);

		BundleDescription bundleDescription = BundlesAction.createBundleDescription(osgiTarget);
		IArtifactKey key = BundlesAction.createBundleArtifactKey(bundleDescription.getSymbolicName(), bundleDescription.getVersion().toString());
		IArtifactDescriptor descriptor = PublisherHelper.createArtifactDescriptor(key, osgiTarget);
		IInstallableUnit iu = createBundleIU(bundleDescription, osgiTarget.isDirectory(), key);
		bundlePool.addDescriptor(descriptor);

		Map parameters = new HashMap();
		parameters.put(ActionConstants.PARM_AGENT, getAgent());
		parameters.put(ActionConstants.PARM_PROFILE, profile);
		EclipseTouchpoint touchpoint = new EclipseTouchpoint();
		touchpoint.initializePhase(null, profile, "test", parameters);
		InstallableUnitOperand operand = new InstallableUnitOperand(null, iu);
		parameters.put("iu", operand.second());
		parameters.put("artifact", key);
		touchpoint.initializeOperand(profile, parameters);
		Manipulator manipulator = (Manipulator) parameters.get(EclipseTouchpoint.PARM_MANIPULATOR);
		assertNotNull(manipulator);

		String programArg = "-somekey";
		Map keyParameters = new HashMap(parameters);
		keyParameters.put(ActionConstants.PARM_PROGRAM_ARG, programArg);
		manipulator.getLauncherData().addProgramArg(programArg);

		programArg = (String) parameters.get("artifact.location");
		String resolvedArtifact = osgiTarget.getAbsolutePath();

		manipulator.getLauncherData().addProgramArg(resolvedArtifact);
		assertTrue(Arrays.asList(manipulator.getLauncherData().getProgramArgs()).contains(resolvedArtifact));
		parameters.put(ActionConstants.PARM_PROGRAM_ARG, programArg);
		parameters = Collections.unmodifiableMap(parameters);

		RemoveProgramArgumentAction artifactAction = new RemoveProgramArgumentAction();
		RemoveProgramArgumentAction keyAction = new RemoveProgramArgumentAction();

		keyAction.execute(keyParameters);
		artifactAction.execute(parameters);
		assertFalse(Arrays.asList(manipulator.getLauncherData().getProgramArgs()).contains(resolvedArtifact));
		artifactAction.undo(parameters);
		keyAction.undo(keyParameters);
		assertTrue(Arrays.asList(manipulator.getLauncherData().getProgramArgs()).contains(resolvedArtifact));
	}

}