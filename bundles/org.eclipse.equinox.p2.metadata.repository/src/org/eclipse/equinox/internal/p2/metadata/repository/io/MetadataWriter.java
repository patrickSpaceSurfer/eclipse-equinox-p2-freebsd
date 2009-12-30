/*******************************************************************************
 *  Copyright (c) 2007, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *     Genuitec, LLC - added license support
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.metadata.repository.io;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.Map.Entry;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.core.helpers.LogHelper;
import org.eclipse.equinox.internal.p2.metadata.IRequiredCapability;
import org.eclipse.equinox.internal.p2.metadata.LDAPQuery;
import org.eclipse.equinox.internal.p2.metadata.repository.Activator;
import org.eclipse.equinox.internal.p2.persistence.XMLWriter;
import org.eclipse.equinox.internal.provisional.p2.metadata.*;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.metadata.query.FragmentQuery;

public abstract class MetadataWriter extends XMLWriter implements XMLConstants {

	public MetadataWriter(OutputStream output, ProcessingInstruction[] piElements) throws UnsupportedEncodingException {
		super(output, piElements);
		// TODO: add a processing instruction for the metadata version
	}

	/**
	 * Writes a list of {@link IInstallableUnit}.
	 * @param units An Iterator of {@link IInstallableUnit}.
	 * @param size The number of units to write
	 */
	protected void writeInstallableUnits(Iterator<IInstallableUnit> units, int size) {
		if (size == 0)
			return;
		start(INSTALLABLE_UNITS_ELEMENT);

		// The size is a bummer. Is it really needed? It forces the use of a collect
		attribute(COLLECTION_SIZE_ATTRIBUTE, size);
		while (units.hasNext())
			writeInstallableUnit(units.next());
		end(INSTALLABLE_UNITS_ELEMENT);
	}

	protected void writeInstallableUnit(IInstallableUnit resolvedIU) {
		IInstallableUnit iu = resolvedIU.unresolved();
		start(INSTALLABLE_UNIT_ELEMENT);
		attribute(ID_ATTRIBUTE, iu.getId());
		attribute(VERSION_ATTRIBUTE, iu.getVersion());
		attribute(SINGLETON_ATTRIBUTE, iu.isSingleton(), true);
		//		attribute(FRAGMENT_ATTRIBUTE, iu.isFragment(), false);

		if (FragmentQuery.isFragment(iu) && iu instanceof IInstallableUnitFragment) {
			IInstallableUnitFragment fragment = (IInstallableUnitFragment) iu;
			writeHostRequiredCapabilities(fragment.getHost());
		}

		if (iu instanceof IInstallableUnitPatch) {
			IInstallableUnitPatch patch = (IInstallableUnitPatch) iu;
			writeApplicabilityScope(patch.getApplicabilityScope());
			writeRequirementsChange(patch.getRequirementsChange());
			writeLifeCycle(patch.getLifeCycle());
		}

		writeUpdateDescriptor(resolvedIU, resolvedIU.getUpdateDescriptor());
		writeProperties(iu.getProperties());
		writeMetaRequiredCapabilities(iu.getMetaRequiredCapabilities());
		writeProvidedCapabilities(iu.getProvidedCapabilities());
		writeRequiredCapabilities(iu.getRequiredCapabilities());
		writeTrimmedCdata(IU_FILTER_ELEMENT, iu.getFilter() == null ? null : ((LDAPQuery) iu.getFilter()).getFilter());

		writeArtifactKeys(iu.getArtifacts());
		writeTouchpointType(iu.getTouchpointType());
		writeTouchpointData(iu.getTouchpointData());
		writeLicenses(iu.getLicenses());
		writeCopyright(iu.getCopyright());

		end(INSTALLABLE_UNIT_ELEMENT);
	}

	protected void writeLifeCycle(IRequirement capability) {
		if (capability == null)
			return;
		start(LIFECYCLE);
		writeRequiredCapability(capability);
		end(LIFECYCLE);
	}

	protected void writeHostRequiredCapabilities(IRequirement[] capabilities) {
		if (capabilities != null && capabilities.length > 0) {
			start(HOST_REQUIRED_CAPABILITIES_ELEMENT);
			attribute(COLLECTION_SIZE_ATTRIBUTE, capabilities.length);
			for (int i = 0; i < capabilities.length; i++) {
				writeRequiredCapability(capabilities[i]);
			}
			end(HOST_REQUIRED_CAPABILITIES_ELEMENT);
		}
	}

	protected void writeProvidedCapabilities(Collection<IProvidedCapability> capabilities) {
		if (capabilities != null && capabilities.size() > 0) {
			start(PROVIDED_CAPABILITIES_ELEMENT);
			attribute(COLLECTION_SIZE_ATTRIBUTE, capabilities.size());
			for (IProvidedCapability capability : capabilities) {
				start(PROVIDED_CAPABILITY_ELEMENT);
				attribute(NAMESPACE_ATTRIBUTE, capability.getNamespace());
				attribute(NAME_ATTRIBUTE, capability.getName());
				attribute(VERSION_ATTRIBUTE, capability.getVersion());
				end(PROVIDED_CAPABILITY_ELEMENT);
			}
			end(PROVIDED_CAPABILITIES_ELEMENT);
		}
	}

	protected void writeMetaRequiredCapabilities(Collection<IRequirement> metaRequirements) {
		if (metaRequirements != null && metaRequirements.size() > 0) {
			start(META_REQUIRED_CAPABILITIES_ELEMENT);
			attribute(COLLECTION_SIZE_ATTRIBUTE, metaRequirements.size());
			for (IRequirement req : metaRequirements) {
				writeRequiredCapability(req);
			}
			end(META_REQUIRED_CAPABILITIES_ELEMENT);
		}
	}

	protected void writeRequiredCapabilities(Collection<IRequirement> requirements) {
		if (requirements != null && requirements.size() > 0) {
			start(REQUIRED_CAPABILITIES_ELEMENT);
			attribute(COLLECTION_SIZE_ATTRIBUTE, requirements.size());
			for (IRequirement req : requirements) {
				writeRequiredCapability(req);
			}
			end(REQUIRED_CAPABILITIES_ELEMENT);
		}
	}

	protected void writeUpdateDescriptor(IInstallableUnit iu, IUpdateDescriptor descriptor) {
		if (descriptor == null)
			return;

		start(UPDATE_DESCRIPTOR_ELEMENT);
		attribute(ID_ATTRIBUTE, descriptor.getId());
		attribute(VERSION_RANGE_ATTRIBUTE, descriptor.getRange());
		attribute(UPDATE_DESCRIPTOR_SEVERITY, descriptor.getSeverity());
		attribute(DESCRIPTION_ATTRIBUTE, descriptor.getDescription());
		end(UPDATE_DESCRIPTOR_ELEMENT);
	}

	protected void writeApplicabilityScope(IRequirement[][] capabilities) {
		start(APPLICABILITY_SCOPE);
		for (int i = 0; i < capabilities.length; i++) {
			start(APPLY_ON);
			writeRequiredCapabilities(Arrays.asList(capabilities[i]));
			end(APPLY_ON);
		}
		end(APPLICABILITY_SCOPE);
	}

	protected void writeRequirementsChange(List<IRequirementChange> changes) {
		start(REQUIREMENT_CHANGES);
		for (int i = 0; i < changes.size(); i++) {
			writeRequirementChange(changes.get(i));
		}
		end(REQUIREMENT_CHANGES);
	}

	protected void writeRequirementChange(IRequirementChange change) {
		start(REQUIREMENT_CHANGE);
		if (change.applyOn() != null) {
			start(REQUIREMENT_FROM);
			writeRequiredCapability(change.applyOn());
			end(REQUIREMENT_FROM);
		}
		if (change.newValue() != null) {
			start(REQUIREMENT_TO);
			writeRequiredCapability(change.newValue());
			end(REQUIREMENT_TO);
		}
		end(REQUIREMENT_CHANGE);
	}

	protected void writeRequiredCapability(IRequirement requirement) {
		if (requirement instanceof IRequiredCapability) {
			IRequiredCapability reqCapability = (IRequiredCapability) requirement;
			start(REQUIRED_CAPABILITY_ELEMENT);
			attribute(NAMESPACE_ATTRIBUTE, reqCapability.getNamespace());
			attribute(NAME_ATTRIBUTE, reqCapability.getName());
			attribute(VERSION_RANGE_ATTRIBUTE, reqCapability.getRange());
			attribute(CAPABILITY_OPTIONAL_ATTRIBUTE, requirement.getMin() == 0, false);
			attribute(CAPABILITY_GREED_ATTRIBUTE, requirement.isGreedy(), true);
			if (requirement.getFilter() != null)
				writeTrimmedCdata(CAPABILITY_FILTER_ELEMENT, ((LDAPQuery) requirement.getFilter()).getFilter());
			end(REQUIRED_CAPABILITY_ELEMENT);
		} else {
			throw new IllegalStateException();
		}

	}

	protected void writeArtifactKeys(Collection<IArtifactKey> artifactKeys) {
		if (artifactKeys != null && artifactKeys.size() > 0) {
			start(ARTIFACT_KEYS_ELEMENT);
			attribute(COLLECTION_SIZE_ATTRIBUTE, artifactKeys.size());
			for (IArtifactKey artifactKey : artifactKeys) {
				start(ARTIFACT_KEY_ELEMENT);
				attribute(ARTIFACT_KEY_CLASSIFIER_ATTRIBUTE, artifactKey.getClassifier());
				attribute(ID_ATTRIBUTE, artifactKey.getId());
				attribute(VERSION_ATTRIBUTE, artifactKey.getVersion());
				end(ARTIFACT_KEY_ELEMENT);
			}
			end(ARTIFACT_KEYS_ELEMENT);
		}
	}

	protected void writeTouchpointType(ITouchpointType touchpointType) {
		start(TOUCHPOINT_TYPE_ELEMENT);
		attribute(ID_ATTRIBUTE, touchpointType.getId());
		attribute(VERSION_ATTRIBUTE, touchpointType.getVersion());
		end(TOUCHPOINT_TYPE_ELEMENT);
	}

	protected void writeTouchpointData(List<ITouchpointData> touchpointData) {
		if (touchpointData != null && touchpointData.size() > 0) {
			start(TOUCHPOINT_DATA_ELEMENT);
			attribute(COLLECTION_SIZE_ATTRIBUTE, touchpointData.size());
			for (int i = 0; i < touchpointData.size(); i++) {
				ITouchpointData nextData = touchpointData.get(i);
				Map<String, ITouchpointInstruction> instructions = nextData.getInstructions();
				if (instructions.size() > 0) {
					start(TOUCHPOINT_DATA_INSTRUCTIONS_ELEMENT);
					attribute(COLLECTION_SIZE_ATTRIBUTE, instructions.size());
					for (Entry<String, ITouchpointInstruction> entry : instructions.entrySet()) {
						start(TOUCHPOINT_DATA_INSTRUCTION_ELEMENT);
						attribute(TOUCHPOINT_DATA_INSTRUCTION_KEY_ATTRIBUTE, entry.getKey());
						ITouchpointInstruction instruction = entry.getValue();
						if (instruction.getImportAttribute() != null)
							attribute(TOUCHPOINT_DATA_INSTRUCTION_IMPORT_ATTRIBUTE, instruction.getImportAttribute());
						cdata(instruction.getBody(), true);
						end(TOUCHPOINT_DATA_INSTRUCTION_ELEMENT);
					}
					end(TOUCHPOINT_DATA_INSTRUCTIONS_ELEMENT);
				}
			}
			end(TOUCHPOINT_DATA_ELEMENT);
		}
	}

	private void writeTrimmedCdata(String element, String filter) {
		String trimmed;
		if (filter != null && (trimmed = filter.trim()).length() > 0) {
			start(element);
			cdata(trimmed);
			end(element);
		}
	}

	private void writeLicenses(List<ILicense> licenses) {
		if (licenses != null && licenses.size() > 0) {
			// In the future there may be more than one license, so we write this 
			// as a collection of one.
			// See bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=216911
			start(LICENSES_ELEMENT);
			attribute(COLLECTION_SIZE_ATTRIBUTE, licenses.size());
			for (int i = 0; i < licenses.size(); i++) {
				ILicense license = licenses.get(i);
				if (license == null)
					continue;
				start(LICENSE_ELEMENT);
				if (license.getLocation() != null) {
					attribute(URI_ATTRIBUTE, license.getLocation().toString());

					try {
						// we write the URL attribute for backwards compatibility with 3.4.x
						// this attribute should be removed if we make a breaking format change.
						attribute(URL_ATTRIBUTE, URIUtil.toURL(license.getLocation()).toExternalForm());
					} catch (MalformedURLException e) {
						attribute(URL_ATTRIBUTE, license.getLocation().toString());
					}
				}
				cdata(license.getBody(), true);
				end(LICENSE_ELEMENT);
			}
			end(LICENSES_ELEMENT);
		}
	}

	private void writeCopyright(ICopyright copyright) {
		if (copyright != null) {
			start(COPYRIGHT_ELEMENT);
			try {
				if (copyright.getLocation() != null) {
					attribute(URI_ATTRIBUTE, copyright.getLocation().toString());
					try {
						// we write the URL attribute for backwards compatibility with 3.4.x
						// this attribute should be removed if we make a breaking format change.
						attribute(URL_ATTRIBUTE, URIUtil.toURL(copyright.getLocation()).toExternalForm());
					} catch (MalformedURLException e) {
						attribute(URL_ATTRIBUTE, copyright.getLocation().toString());
					}
				}
			} catch (IllegalStateException ise) {
				LogHelper.log(new Status(IStatus.INFO, Activator.ID, "Error writing the copyright URL: " + copyright.getLocation())); //$NON-NLS-1$
			}
			cdata(copyright.getBody(), true);
			end(COPYRIGHT_ELEMENT);
		}
	}
}
