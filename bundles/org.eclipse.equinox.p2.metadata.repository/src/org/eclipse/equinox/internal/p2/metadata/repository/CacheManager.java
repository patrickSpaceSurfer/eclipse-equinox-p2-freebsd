/*******************************************************************************
 * Copyright (c) 2008, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Cloudsmith Inc - additional implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.metadata.repository;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.ecf.filetransfer.UserCancelledException;
import org.eclipse.equinox.internal.p2.core.helpers.*;
import org.eclipse.equinox.internal.p2.repository.*;
import org.eclipse.equinox.internal.p2.repository.Activator;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.core.eventbus.IProvisioningEventBus;
import org.eclipse.equinox.internal.provisional.p2.core.eventbus.SynchronousProvisioningListener;
import org.eclipse.equinox.internal.provisional.p2.core.location.AgentLocation;
import org.eclipse.equinox.internal.provisional.p2.repository.*;
import org.eclipse.osgi.util.NLS;

/**
 * A class to manage metadata cache files. Creating the cache files will place
 * the file in the AgentData location in a cache directory.
 * 
 * Using the bus listeners will allow the manager to listen for repository
 * events. When a repository is removed, it will remove the cache file if one
 * was created for the repository.
 */
public class CacheManager {
	/**
	 * IStateful implementation of BufferedOutputStream. Class is used to get the status from
	 * a download operation.
	 */
	private static class StatefulStream extends BufferedOutputStream implements IStateful {
		private IStatus status;

		public StatefulStream(OutputStream stream) {
			super(stream);
		}

		public IStatus getStatus() {

			return status;
		}

		public void setStatus(IStatus aStatus) {
			status = aStatus;
		}

	}

	private static SynchronousProvisioningListener busListener;
	private static final String DOWNLOADING = "downloading"; //$NON-NLS-1$
	private static final String JAR_EXTENSION = ".jar"; //$NON-NLS-1$
	private static final String PROP_RESUMABLE = "org.eclipse.equinox.p2.metadata.repository.resumable"; //$NON-NLS-1$
	private static final String RESUME_DEFAULT = "true"; //$NON-NLS-1$
	private static final String XML_EXTENSION = ".xml"; //$NON-NLS-1$

	private final HashSet knownPrefixes = new HashSet(5);

	/**
	 * Returns a hash of the repository location.
	 */
	private int computeHash(URI repositoryLocation) {
		return repositoryLocation.hashCode();
	}

	/**
	 * Returns a local cache file with the contents of the given remote location,
	 * or <code>null</code> if a local cache could not be created.
	 * 
	 * @param repositoryLocation The remote location to be cached
	 * @param prefix The prefix to use when creating the cache file
	 * @param monitor a progress monitor
	 * @return A {@link File} object pointing to the cache file or <code>null</code>
	 * if the location is not a repository.
	 * @throws FileNotFoundException if neither jar nor xml index file exists at given location 
	 * @throws AuthenticationFailedException if jar not available and xml causes authentication fail
	 * @throws IOException on general IO errors
	 * @throws ProvisionException on any error (e.g. user cancellation, unknown host, malformed address, connection refused, etc.)
	 */
	public File createCache(URI repositoryLocation, String prefix, IProgressMonitor monitor) throws IOException, ProvisionException {

		SubMonitor submonitor = SubMonitor.convert(monitor, 1000);
		try {
			knownPrefixes.add(prefix);
			File cacheFile = getCache(repositoryLocation, prefix);
			URI jarLocation = URIUtil.append(repositoryLocation, prefix + JAR_EXTENSION);
			URI xmlLocation = URIUtil.append(repositoryLocation, prefix + XML_EXTENSION);
			int hashCode = computeHash(repositoryLocation);

			// Knowing if cache is stale is complicated by the fact that a jar could have been 
			// produced after an xml index (and vice versa), and by the need to capture any
			// errors, as these needs to be reported to the user as something meaningful - instead of
			// just a general "can't read repository".
			// (Previous impl of stale checking ignored errors, and caused multiple round-trips)
			boolean stale = true;
			long lastModified = 0L;
			String name = null;
			String useExtension = JAR_EXTENSION;
			URI remoteFile = jarLocation;

			if (cacheFile != null) {
				lastModified = cacheFile.lastModified();
				name = cacheFile.getName();
			}
			// get last modified on jar
			long lastModifiedRemote = 0L;
			// bug 269588 - server may return 0 when file exists, so extra flag is needed
			boolean useJar = true;
			try {
				lastModifiedRemote = getTransport().getLastModified(jarLocation, submonitor.newChild(1));
				if (lastModifiedRemote <= 0)
					LogHelper.log(new Status(IStatus.WARNING, Activator.ID, "Server returned lastModified <= 0 for " + jarLocation)); //$NON-NLS-1$

			} catch (Exception e) {
				// not ideal, just skip the jar on error, and try the xml instead - report errors for
				// the xml.
				useJar = false;
			}
			if (submonitor.isCanceled())
				throw new OperationCanceledException();

			if (useJar) {
				// There is a jar, and it should be used - cache is stale if it is xml based or
				// if older (irrespective of jar or xml).
				// Bug 269588 - also stale if remote reports 0
				stale = lastModifiedRemote > lastModified || (name != null && name.endsWith(XML_EXTENSION) || lastModifiedRemote <= 0);
			} else {
				// Also need to check remote XML file, and handle cancel, and errors
				// (Status is reported based on finding the XML file as giving up on certain errors
				// when checking for the jar may not be correct).
				try {
					lastModifiedRemote = getTransport().getLastModified(xmlLocation, submonitor.newChild(1));
					// if lastModifiedRemote is 0 - something is wrong in the communication stack, as 
					// a FileNotFound exception should have been thrown.
					// bug 269588 - server may return 0 when file exists - site is not correctly configured
					if (lastModifiedRemote <= 0)
						LogHelper.log(new Status(IStatus.WARNING, Activator.ID, "Server returned lastModified <= 0 for " + xmlLocation)); //$NON-NLS-1$

				} catch (UserCancelledException e) {
					throw new OperationCanceledException();
				} catch (FileNotFoundException e) {
					throw new FileNotFoundException(NLS.bind(Messages.CacheManager_Neither_0_nor_1_found, jarLocation, xmlLocation));
				} catch (AuthenticationFailedException e) {
					throw new ProvisionException(new Status(IStatus.ERROR, Activator.ID, ProvisionException.REPOSITORY_NOT_FOUND, NLS.bind(Messages.CacheManager_AuthenticationFaileFor_0, repositoryLocation), e));
				} catch (CoreException e) {
					throw new ProvisionException(new Status(IStatus.ERROR, Activator.ID, ProvisionException.REPOSITORY_NOT_FOUND, NLS.bind(Messages.CacheManager_FailedCommunicationWithRepo_0, repositoryLocation), e));

				}
				// There is an xml, and it should be used - cache is stale if it is jar based or
				// if older (irrespective of jar or xml).
				// bug 269588 - server may return 0 when file exists - assume it is stale
				stale = lastModifiedRemote > lastModified || (name != null && name.endsWith(JAR_EXTENSION) || lastModifiedRemote <= 0);
				useExtension = XML_EXTENSION;
				remoteFile = xmlLocation;
			}

			if (!stale)
				return cacheFile;

			// The cache is stale or missing, so we need to update it from the remote location
			cacheFile = new File(getCacheDirectory(), prefix + hashCode + useExtension);
			updateCache(cacheFile, remoteFile, lastModifiedRemote, submonitor);
			return cacheFile;
		} finally {
			submonitor.done();
		}
	}

	/**
	 * Deletes the local cache file(s) for the given repository
	 * @param repositoryLocation
	 */
	void deleteCache(URI repositoryLocation) {
		for (Iterator it = knownPrefixes.iterator(); it.hasNext();) {
			String prefix = (String) it.next();
			File[] cacheFiles = getCacheFiles(repositoryLocation, prefix);
			for (int i = 0; i < cacheFiles.length; i++) {
				// delete the cache file if it exists
				safeDelete(cacheFiles[i]);
				// delete a resumable download if it exists
				safeDelete(new File(new File(cacheFiles[i].getParentFile(), DOWNLOADING), cacheFiles[i].getName()));
			}
		}
	}

	/**
	 * Determines the local file path of the repository's cache file.
	 * @param repositoryLocation The location to compute the cache for
	 * @param prefix The prefix to use for this location
	 * @return A {@link File} pointing to the cache file or <code>null</code> if
	 * the cache file does not exist.
	 */
	private File getCache(URI repositoryLocation, String prefix) {
		File[] files = getCacheFiles(repositoryLocation, prefix);
		if (files[0].exists())
			return files[0];
		return files[1].exists() ? files[1] : null;
	}

	/**
	 * Returns the file corresponding to the data area to be used by the cache manager.
	 */
	private File getCacheDirectory() {
		AgentLocation agentLocation = (AgentLocation) ServiceHelper.getService(Activator.getContext(), AgentLocation.class.getName());
		URL dataArea = agentLocation.getDataArea(Activator.ID + "/cache/"); //$NON-NLS-1$
		return URLUtil.toFile(dataArea);
	}

	/**
	 * Determines the local file paths of the repository's potential cache files.
	 * @param repositoryLocation The location to compute the cache for
	 * @param prefix The prefix to use for this location
	 * @return A {@link File} array with the cache files for JAR and XML extensions.
	 */
	private File[] getCacheFiles(URI repositoryLocation, String prefix) {
		File[] files = new File[2];
		File dataAreaFile = getCacheDirectory();
		int hashCode = computeHash(repositoryLocation);
		files[0] = new File(dataAreaFile, prefix + hashCode + JAR_EXTENSION);
		files[1] = new File(dataAreaFile, prefix + hashCode + XML_EXTENSION);
		return files;
	}

	private RepositoryTransport getTransport() {
		return RepositoryTransport.getInstance();
	}

	public boolean isResumeEnabled() {
		String resumeProp = System.getProperty(PROP_RESUMABLE, RESUME_DEFAULT);
		return Boolean.valueOf(resumeProp).booleanValue();
	}

	/**
	 * Make cacheFile resumable and return true if it was possible.
	 * @param cacheFile - the partially downloaded file to make resumeable
	 * @param remoteFile The remote file being cached
	 * @param status - the download status reported for the partial download
	 * @return true if the file was made resumable, false otherwise
	 */
	private boolean makeResumeable(File cacheFile, URI remoteFile, IStatus status) {
		if (status == null || status.isOK() || cacheFile == null || !(status instanceof DownloadStatus))
			return false;
		// check if resume feature is turned off
		if (!isResumeEnabled())
			return false;
		DownloadStatus downloadStatus = (DownloadStatus) status;
		long currentLength = cacheFile.length();
		// if cache file does not exist, or nothing was written to it, there is nothing to resume
		if (currentLength == 0L)
			return false;

		long reportedSize = downloadStatus.getFileSize();
		long reportedModified = downloadStatus.getLastModified();

		if (reportedSize == DownloadStatus.UNKNOWN_SIZE || reportedSize == 0L) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.ID, NLS.bind("Download of {0} not resumable because filesize not reported.", remoteFile))); //$NON-NLS-1$
			return false;
		}
		if (reportedModified <= 0) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.ID, NLS.bind("Download of {0} not resumable because last-modified not reported.", remoteFile))); //$NON-NLS-1$
			return false;
		}

		// if more than what was reported has been written something odd is going on, and we can't
		// trust the reported size. 
		// There is a small chance that user canceled in the time window after the full download is seen, and the result is returned. In this
		// case the reported and current lengths will be equal.
		if (reportedSize < currentLength) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.ID, NLS.bind("Download of {0} not resumable because more was read then reported size.", remoteFile))); //$NON-NLS-1$
			return false;
		}
		File resumeDir = new File(cacheFile.getParentFile(), DOWNLOADING);
		if (!resumeDir.mkdir()) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, NLS.bind("Could not create directory {0} for resumable download of {1}", resumeDir, remoteFile))); //$NON-NLS-1$
			return false;
		}
		// move partial cache file to "downloading" directory
		File resumeFile = new File(resumeDir, cacheFile.getName());
		if (!cacheFile.renameTo(resumeFile)) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, NLS.bind("Could not move {0} to {1} for resumed download", cacheFile, resumeFile))); //$NON-NLS-1$
			return false;
		}
		// touch the file with remote modified time
		if (!resumeFile.setLastModified(reportedModified)) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, NLS.bind("Could not set last modified time on {0} for resumed download", resumeFile))); //$NON-NLS-1$
			return false;
		}
		return true;
	}

	/**
	 * Adds a {@link SynchronousProvisioningListener} to the event bus for
	 * deleting cache files when the corresponding repository is deleted.
	 */
	public void registerRepoEventListener() {
		IProvisioningEventBus eventBus = (IProvisioningEventBus) ServiceHelper.getService(Activator.getContext(), IProvisioningEventBus.SERVICE_NAME);
		if (eventBus == null) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, "ProvisioningEventBus could not be obtained. Metadata caches may not be cleaned up properly.")); //$NON-NLS-1$
			return;
		}
		if (busListener == null) {
			busListener = new SynchronousProvisioningListener() {
				public void notify(EventObject o) {
					if (o instanceof RepositoryEvent) {
						RepositoryEvent event = (RepositoryEvent) o;
						if (RepositoryEvent.REMOVED == event.getKind() && IRepository.TYPE_METADATA == event.getRepositoryType()) {
							deleteCache(event.getRepositoryLocation());
						}
					}
				}
			};
		}
		// the bus could have disappeared and is now back again - so do this every time
		eventBus.addListener(busListener);
	}

	private boolean safeDelete(File file) {
		if (file.exists()) {
			if (!file.delete()) {
				file.deleteOnExit();
				return true;
			}
		}
		return false;
	}

	/**
	 * Removes the {@link SynchronousProvisioningListener} that cleans up the
	 * cache file from the event bus.
	 */
	public void unregisterRepoEventListener() {
		IProvisioningEventBus eventBus = (IProvisioningEventBus) ServiceHelper.getService(Activator.getContext(), IProvisioningEventBus.SERVICE_NAME);
		if (eventBus != null && busListener != null) {
			eventBus.removeListener(busListener);
			busListener = null;
		}
	}

	private void updateCache(File cacheFile, URI remoteFile, long lastModifiedRemote, SubMonitor submonitor) throws FileNotFoundException, IOException, ProvisionException {
		cacheFile.getParentFile().mkdirs();
		File resumeFile = new File(new File(cacheFile.getParentFile(), DOWNLOADING), cacheFile.getName());
		// if append should be performed or not
		boolean append = false;
		if (resumeFile.exists()) {
			// the resume file can be too old
			if (lastModifiedRemote != resumeFile.lastModified() || lastModifiedRemote <= 0)
				safeDelete(resumeFile);
			else {
				if (resumeFile.renameTo(cacheFile))
					append = true;
				else
					LogHelper.log(new Status(IStatus.ERROR, Activator.ID, NLS.bind("Could not move resumable file {0} into cache", resumeFile))); //$NON-NLS-1$
			}
		}

		StatefulStream metadata = new StatefulStream(new FileOutputStream(cacheFile, append));
		IStatus result = null;
		try {
			submonitor.setWorkRemaining(1000);
			// resume from cache file's length if in append mode
			result = getTransport().download(remoteFile, metadata, append ? cacheFile.length() : -1, submonitor.newChild(1000));
		} catch (OperationCanceledException e) {
			// need to pick up the status - a new operation canceled exception is thrown at the end
			// as status will be CANCEL.
			result = metadata.getStatus();
		} finally {
			metadata.close();
			// result is null if a runtime error (other than OperationCanceledException) 
			// occurred, just delete the cache file (or a later attempt could fail 
			// with "premature end of file").
			if (result == null)
				cacheFile.delete();
		}
		if (result.isOK())
			return;

		// if possible, keep a partial download to be resumed.
		if (!makeResumeable(cacheFile, remoteFile, result))
			cacheFile.delete();

		if (result.getSeverity() == IStatus.CANCEL || submonitor.isCanceled())
			throw new OperationCanceledException();
		throw new ProvisionException(result);
	}
}
