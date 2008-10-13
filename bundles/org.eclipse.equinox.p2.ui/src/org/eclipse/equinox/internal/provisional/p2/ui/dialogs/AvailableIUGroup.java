/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.provisional.p2.ui.dialogs;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.*;
import org.eclipse.equinox.internal.p2.ui.ProvUIActivator;
import org.eclipse.equinox.internal.p2.ui.ProvUIMessages;
import org.eclipse.equinox.internal.p2.ui.dialogs.*;
import org.eclipse.equinox.internal.p2.ui.viewers.DeferredQueryContentProvider;
import org.eclipse.equinox.internal.p2.ui.viewers.IUDetailsLabelProvider;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.core.repository.IRepositoryManager;
import org.eclipse.equinox.internal.provisional.p2.core.repository.RepositoryEvent;
import org.eclipse.equinox.internal.provisional.p2.engine.ProvisioningContext;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.ui.*;
import org.eclipse.equinox.internal.provisional.p2.ui.model.IRepositoryElement;
import org.eclipse.equinox.internal.provisional.p2.ui.model.MetadataRepositories;
import org.eclipse.equinox.internal.provisional.p2.ui.operations.*;
import org.eclipse.equinox.internal.provisional.p2.ui.policy.IUViewQueryContext;
import org.eclipse.equinox.internal.provisional.p2.ui.policy.Policy;
import org.eclipse.equinox.internal.provisional.p2.ui.viewers.*;
import org.eclipse.jface.action.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.statushandlers.StatusManager;

/**
 * An AvailableIUGroup is a reusable UI component that displays the
 * IU's available for installation.
 * 
 * @since 3.4
 */
public class AvailableIUGroup extends StructuredIUGroup {

	private static final int SITE_COLUMN_WIDTH_IN_DLUS = 300;
	private static final int OTHER_COLUMN_WIDTH_IN_DLUS = 200;

	ChangeViewAction viewByRepo, viewFlat, viewCategory;
	QueryableMetadataRepositoryManager queryableManager;

	private class ChangeViewAction extends Action {
		int viewType;

		ChangeViewAction(String text, int viewType) {
			super(text, IAction.AS_RADIO_BUTTON);
			this.viewType = viewType;
			setChecked(this.viewType == queryContext.getViewType());
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.jface.action.IAction#run()
		 */
		public void run() {
			if (this.viewType != queryContext.getViewType()) {
				queryContext.setViewType(viewType);
				updateAvailableViewState();
			}
		}
	}

	ProvisioningContext provisioningContext;
	IUViewQueryContext queryContext;
	// We restrict the type of the filter used because PatternFilter does
	// unnecessary accesses of children that cause problems with the deferred
	// tree.
	AvailableIUPatternFilter filter;
	private boolean useBold = false;
	private IUDetailsLabelProvider labelProvider;
	private Display display;
	DeferredFetchFilteredTree filteredTree;
	IUColumnConfig[] columnConfig;
	private int refreshRepoFlags = IRepositoryManager.REPOSITORIES_NON_SYSTEM;
	Job lastRequestedLoadJob;

	/**
	 * Create a group that represents the available IU's but does not use any of the
	 * view menu or check box capabilities.
	 * 
	 * @param parent the parent composite for the group
	 */
	public AvailableIUGroup(final Composite parent) {
		this(Policy.getDefault(), parent, parent.getFont(), null, null, ProvUI.getIUColumnConfig());
	}

	/**
	 * Create a group that represents the available IU's.
	 * 
	 * @param parent the parent composite for the group
	 * @param font The font to use for calculating pixel sizes.  This font is
	 * not managed by the receiver.
	 * @param queryContext the ProvisioningContext describing the context for provisioning,
	 * including information about which repositories should be used.
	 * @param columnConfig the description of the columns that should be shown.  If <code>null</code>, a default
	 * will be used.
	 */
	public AvailableIUGroup(Policy policy, final Composite parent, Font font, QueryableMetadataRepositoryManager queryable, IUViewQueryContext queryContext, IUColumnConfig[] columnConfig) {
		super(policy, parent, font);
		this.display = parent.getDisplay();
		if (queryable == null)
			this.queryableManager = new QueryableMetadataRepositoryManager(policy, false);
		else
			this.queryableManager = queryable;
		if (queryContext == null)
			this.queryContext = policy.getQueryContext();
		else
			this.queryContext = queryContext;
		if (columnConfig == null)
			this.columnConfig = ProvUI.getIUColumnConfig();
		else
			this.columnConfig = columnConfig;
		this.filter = new AvailableIUPatternFilter(this.columnConfig);
		createGroupComposite(parent);
	}

	protected StructuredViewer createViewer(Composite parent) {
		// Table of available IU's
		filteredTree = new DeferredFetchFilteredTree(parent, SWT.MULTI | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER, filter, new IViewMenuProvider() {

			public void fillViewMenu(IMenuManager viewMenu) {
				viewByRepo = new ChangeViewAction(ProvUIMessages.AvailableIUGroup_ViewBySite, IUViewQueryContext.AVAILABLE_VIEW_BY_REPO);
				viewMenu.add(viewByRepo);
				viewCategory = new ChangeViewAction(ProvUIMessages.AvailableIUGroup_ViewByCategory, IUViewQueryContext.AVAILABLE_VIEW_BY_CATEGORY);
				viewMenu.add(viewCategory);
				viewFlat = new ChangeViewAction(ProvUIMessages.AvailableIUGroup_ViewByName, IUViewQueryContext.AVAILABLE_VIEW_FLAT);
				viewMenu.add(viewFlat);
			}

		}, parent.getDisplay());
		final TreeViewer availableIUViewer = filteredTree.getViewer();

		// If the user expanded or collapsed anything while we were loading a repo
		// in the background, we would not want to disrupt their work by making
		// a newly loaded visible and expanding it.  Setting the load job to null 
		// will take care of this.
		availableIUViewer.getTree().addTreeListener(new TreeListener() {
			public void treeCollapsed(TreeEvent e) {
				lastRequestedLoadJob = null;
			}

			public void treeExpanded(TreeEvent e) {
				lastRequestedLoadJob = null;
			}
		});

		labelProvider = new IUDetailsLabelProvider(filteredTree, columnConfig, getShell());
		labelProvider.setUseBoldFontForFilteredItems(useBold);
		labelProvider.setToolTipProperty(IInstallableUnit.PROP_DESCRIPTION);

		// Filters and sorters before establishing content, so we don't refresh unnecessarily.
		availableIUViewer.setComparator(new IUComparator(IUComparator.IU_NAME));
		availableIUViewer.setComparer(new ProvElementComparer());

		// Now the content provider.
		DeferredQueryContentProvider contentProvider = new DeferredQueryContentProvider();
		availableIUViewer.setContentProvider(contentProvider);

		// Now the presentation, columns before label provider.
		setTreeColumns(availableIUViewer.getTree());
		availableIUViewer.setLabelProvider(labelProvider);

		// Notify the filtered tree so that it can hook listeners on the
		// content provider.  This is needed so that filtering is only allowed
		// after content has been retrieved.
		filteredTree.contentProviderSet(contentProvider);

		// Input last.
		availableIUViewer.setInput(getNewInput());

		final StructuredViewerProvisioningListener listener = new StructuredViewerProvisioningListener(availableIUViewer, StructuredViewerProvisioningListener.PROV_EVENT_METADATA_REPOSITORY) {
			protected void repositoryAdded(final RepositoryEvent event) {
				// Ignore disabled repositories
				if (!event.isRepositoryEnabled())
					return;
				makeRepositoryVisible(event.getRepositoryLocation());
			}
		};
		ProvUIActivator.getDefault().addProvisioningListener(listener);

		availableIUViewer.getControl().addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				ProvUIActivator.getDefault().removeProvisioningListener(listener);
			}
		});
		return availableIUViewer;
	}

	private void setTreeColumns(Tree tree) {
		tree.setHeaderVisible(true);

		for (int i = 0; i < columnConfig.length; i++) {
			TreeColumn tc = new TreeColumn(tree, SWT.NONE, i);
			tc.setResizable(true);
			tc.setText(columnConfig[i].columnTitle);
			tc.setWidth(convertHorizontalDLUsToPixels(columnConfig[i].defaultColumnWidth));
		}
	}

	Object getNewInput() {
		return new MetadataRepositories(queryContext, getPolicy(), queryableManager);
	}

	public void setRepositoryRefreshFlags(int flags) {
		refreshRepoFlags = flags;
	}

	/**
	 * Set a boolean indicating whether a bold font should be used when
	 * showing filtered items.  This method does not refresh the tree or 
	 * labels, so that must be done explicitly by the caller.
	 * @param useBoldFont
	 */
	public void setUseBoldFontForFilteredItems(boolean useBoldFont) {
		if (labelProvider != null)
			labelProvider.setUseBoldFontForFilteredItems(useBoldFont);
	}

	/**
	 * Return the composite that contains the controls in this group.
	 * @return the composite
	 */
	public Composite getComposite() {
		return super.getComposite();
	}

	/**
	 * Get the viewer used to represent the available IU's
	 * @return the viewer
	 */
	public StructuredViewer getStructuredViewer() {
		return super.getStructuredViewer();
	}

	/**
	 * Get the selected IU's
	 * @return the array of selected IU's
	 */
	public IInstallableUnit[] getSelectedIUs() {
		return super.getSelectedIUs();
	}

	public CheckboxTreeViewer getCheckboxTreeViewer() {
		return filteredTree.getCheckboxTreeViewer();
	}

	/**
	 * Get the selected IU's
	 * @return the array of checked IU's
	 */
	public IInstallableUnit[] getCheckedLeafIUs() {
		Object[] selections = filteredTree.getCheckboxTreeViewer().getCheckedElements();
		List leaves = new ArrayList(selections.length);
		for (int i = 0; i < selections.length; i++) {
			if (!getCheckboxTreeViewer().getGrayed(selections[i])) {
				IInstallableUnit iu = (IInstallableUnit) ProvUI.getAdapter(selections[i], IInstallableUnit.class);
				if (iu != null && !ProvisioningUtil.isCategory(iu))
					leaves.add(iu);
			}
		}
		return (IInstallableUnit[]) leaves.toArray(new IInstallableUnit[leaves.size()]);
	}

	public Tree getTree() {
		if (viewer == null)
			return null;
		return ((TreeViewer) viewer).getTree();
	}

	/**
	 * Refresh the available view completely.
	 */
	public void refresh() {
		URI[] urls = provisioningContext.getMetadataRepositories();
		ProvisioningOperation op;
		if (urls == null)
			op = new RefreshColocatedRepositoriesOperation(ProvUIMessages.AvailableIUGroup_RefreshOperationLabel, refreshRepoFlags);
		else
			op = new RefreshColocatedRepositoriesOperation(ProvUIMessages.AvailableIUGroup_RefreshOperationLabel, urls);
		ProvisioningOperationRunner.schedule(op, getShell(), StatusManager.SHOW | StatusManager.LOG);
		if (viewer != null && !viewer.getControl().isDisposed())
			viewer.setInput(getNewInput());
	}

	/*
	 * Make the repository with the specified location visible in the viewer.
	 */
	void makeRepositoryVisible(final URI location) {
		// First reset the input so that the new repo shows up
		display.asyncExec(new Runnable() {
			public void run() {
				final TreeViewer treeViewer = filteredTree.getViewer();
				final Tree tree = treeViewer.getTree();
				IWorkbench workbench = PlatformUI.getWorkbench();
				if (workbench.isClosing())
					return;
				if (tree != null && !tree.isDisposed()) {
					treeViewer.setInput(getNewInput());
				}
			}
		});

		// We don't know if loading will be a fast or slow operation.
		// We do it in a job to be safe, and when it's done, we update
		// the UI.
		Job job = new Job(NLS.bind(ProvUIMessages.AvailableIUGroup_LoadingRepository, location.toString())) {
			protected IStatus run(IProgressMonitor monitor) {
				try {
					ProvisioningUtil.loadMetadataRepository(location, monitor);
					return Status.OK_STATUS;
				} catch (ProvisionException e) {
					return e.getStatus();
				} catch (OperationCanceledException e) {
					return Status.CANCEL_STATUS;
				}
			}
		};
		job.setPriority(Job.LONG);
		job.setSystem(true);
		job.setUser(false);
		job.addJobChangeListener(new JobChangeAdapter() {
			public void done(final IJobChangeEvent event) {
				if (event.getResult().isOK())
					display.asyncExec(new Runnable() {
						public void run() {
							final TreeViewer treeViewer = filteredTree.getViewer();
							IWorkbench workbench = PlatformUI.getWorkbench();
							if (workbench.isClosing())
								return;
							// Expand only if there have been no other jobs started for other repos.
							if (event.getJob() == lastRequestedLoadJob) {
								final Tree tree = treeViewer.getTree();
								if (tree != null && !tree.isDisposed()) {
									TreeItem[] items = tree.getItems();
									for (int i = 0; i < items.length; i++) {
										if (items[i].getData() instanceof IRepositoryElement) {
											URI url = ((IRepositoryElement) items[i].getData()).getLocation();
											if (url.equals(location)) {
												treeViewer.expandToLevel(items[i].getData(), AbstractTreeViewer.ALL_LEVELS);
												tree.select(items[i]);
												return;
											}
										}
									}
								}
							}
						}
					});
			}
		});
		lastRequestedLoadJob = job;
		job.schedule();
	}

	public void updateAvailableViewState() {
		if (getTree() == null || getTree().isDisposed())
			return;
		final Composite parent = getComposite().getParent();
		setUseBoldFontForFilteredItems(queryContext.getViewType() != IUViewQueryContext.AVAILABLE_VIEW_FLAT);

		BusyIndicator.showWhile(display, new Runnable() {
			public void run() {
				parent.setRedraw(false);
				updateTreeColumns();
				getCheckboxTreeViewer().setInput(getNewInput());
				parent.layout(true);
				parent.setRedraw(true);
			}
		});
	}

	public void updateTreeColumns() {
		if (getTree() == null || getTree().isDisposed())
			return;
		TreeColumn[] columns = getTree().getColumns();
		if (columns.length > 0)
			columns[0].setWidth(convertHorizontalDLUsToPixels(queryContext.getViewType() == IUViewQueryContext.AVAILABLE_VIEW_BY_REPO ? SITE_COLUMN_WIDTH_IN_DLUS : OTHER_COLUMN_WIDTH_IN_DLUS));

	}

	public Control getDefaultFocusControl() {
		if (filteredTree != null)
			return filteredTree.getFilterControl();
		return null;
	}

	protected GridData getViewerGridData() {
		GridData data = super.getViewerGridData();
		data.heightHint = convertVerticalDLUsToPixels(240);
		return data;
	}
}
