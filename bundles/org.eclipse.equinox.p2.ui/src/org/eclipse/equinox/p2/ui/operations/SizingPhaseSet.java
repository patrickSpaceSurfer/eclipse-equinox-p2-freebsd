/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
/**
 * 
 */
package org.eclipse.equinox.p2.ui.operations;

import org.eclipse.equinox.internal.p2.ui.ProvUIMessages;
import org.eclipse.equinox.p2.engine.Phase;
import org.eclipse.equinox.p2.engine.PhaseSet;
import org.eclipse.equinox.p2.engine.phases.Sizing;

public class SizingPhaseSet extends PhaseSet {
	private static Sizing sizing;

	SizingPhaseSet() {
		super(new Phase[] {sizing = new Sizing(100, ProvUIMessages.SizingPhaseSet_PhaseSetName)});
	}

	Sizing getSizing() {
		return sizing;
	}
}