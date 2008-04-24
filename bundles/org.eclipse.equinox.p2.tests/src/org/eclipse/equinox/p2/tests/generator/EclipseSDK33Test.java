/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Code 9 - Ongoing development
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.generator;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.publisher.*;
import org.eclipse.equinox.internal.p2.publisher.actions.FeaturesAction;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.IArtifactRepository;
import org.eclipse.equinox.internal.provisional.p2.metadata.IArtifactKey;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.tests.*;
import org.osgi.framework.Bundle;

/**
 * Tests running the metadata generator against Eclipse 3.3 features.
 */
public class EclipseSDK33Test extends AbstractProvisioningTest {
	public static Test suite() {
		return new TestSuite(EclipseSDK33Test.class);
	}

	public EclipseSDK33Test() {
		super("");
	}

	public EclipseSDK33Test(String name) {
		super(name);
	}

	public void testGeneration() {
		IPublisherInfo info = new TestPublisherInfo();
		IPublishingAction[] actions = createActions();
		Publisher publisher = new Publisher(info);
		IStatus result = publisher.publish(actions);
		assertTrue(result.isOK());

		TestMetadataRepository repo = (TestMetadataRepository) info.getMetadataRepository();
		IInstallableUnit unit = repo.find("org.eclipse.cvs.source.feature.group", "1.0.0.v20070606-7C79_79EI99g_Y9e");
		assertNotNull(unit);
		assertGroup(unit);
		unit = repo.find("org.eclipse.rcp.feature.group", "3.3.0.v20070607-8y8eE8NEbsN3X_fjWS8HPNG");
		assertNotNull(unit);
		assertGroup(unit);
		unit = repo.find("org.eclipse.jdt.feature.group", "3.3.0.v20070606-0010-7o7jCHEFpPoqQYvnXqejeR");
		assertNotNull(unit);
		assertGroup(unit);
		unit = repo.find("org.eclipse.cvs.feature.group", "1.0.0.v20070606-7C79_79EI99g_Y9e");
		assertNotNull(unit);
		assertGroup(unit);
		unit = repo.find("org.eclipse.pde.source.feature.group", "3.3.0.v20070607-7N7M-DUUEF6Ez0H46IcCC");
		assertNotNull(unit);
		assertGroup(unit);
		unit = repo.find("org.eclipse.sdk.feature.group", "3.3.0.v20070607-7M7J-BIolz-OcxWxvWAPSfLPqevO");
		assertNotNull(unit);
		assertGroup(unit);
		unit = repo.find("org.eclipse.platform.feature.group", "3.3.0.v20070612-_19UEkLEzwsdF9jSqQ-G");
		assertNotNull(unit);
		assertGroup(unit);
		unit = repo.find("org.eclipse.platform.source.feature.group", "3.3.0.v20070612-_19UEkLEzwsdF9jSqQ-G");
		assertNotNull(unit);
		assertGroup(unit);
		unit = repo.find("org.eclipse.jdt.source.feature.group", "3.3.0.v20070606-0010-7o7jCHEFpPoqQYvnXqejeR");
		assertNotNull(unit);
		assertGroup(unit);
		unit = repo.find("org.eclipse.pde.feature.group", "3.3.0.v20070607-7N7M-DUUEF6Ez0H46IcCC");
		assertNotNull(unit);
		assertGroup(unit);
		unit = repo.find("org.eclipse.rcp.source.feature.group", "3.3.0.v20070607-8y8eE8NEbsN3X_fjWS8HPNG");
		assertNotNull(unit);
		assertGroup(unit);

		IArtifactRepository artifactRepo = (TestArtifactRepository) info.getArtifactRepository();
		IArtifactKey[] keys = artifactRepo.getArtifactKeys();
		assertTrue(keys.length == 11);
	}

	private IPublishingAction[] createActions() {
		File source = getSource();
		ArrayList actions = new ArrayList();
		actions.add(new FeaturesAction(new File[] {source}));
		return (IPublishingAction[]) actions.toArray(new IPublishingAction[actions.size()]);
	}

	/**
	 * Asserts that the given IU represents a group.
	 */
	private void assertGroup(IInstallableUnit unit) {
		assertEquals("IU is not a group", Boolean.TRUE.toString(), unit.getProperty(IInstallableUnit.PROP_TYPE_GROUP));
	}

	private File getSource() {
		Bundle myBundle = TestActivator.getContext().getBundle();
		URL root = FileLocator.find(myBundle, new Path("testData/generator/eclipse3.3"), null);
		File rootFile = null;
		try {
			root = FileLocator.toFileURL(root);
			return new File(root.getPath());
		} catch (IOException e) {
			fail("4.99", e);
		}
		return null;
	}

}
