/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.treeviewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.events.TreeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

/**
 * TODO: sorting/filtering should be implemented above content viewer
 * TODO: tree editor not implemented
 * 
 * TODO: context support for adapters
 * TODO: variables viewer (dup elements)
 * TODO: selection support
 * TODO: expand support
 */
public class AsyncTreeViewer extends Viewer {

    /**
     * A map of elements to associated tree items or tree
     */
    Map fElementsToWidgets = new HashMap();

    List fPendingUpdates = new ArrayList();
    
    Map fImageCache = new HashMap();

    Tree fTree;

    Object fInput;

    /**
     * Creates an asynchronous tree viewer on a newly-created tree control under
     * the given parent. The tree control is created using the SWT style bits
     * <code>MULTI, H_SCROLL, V_SCROLL,</code> and <code>BORDER</code>. The
     * viewer has no input, no content provider, a default label provider, no
     * sorter, and no filters.
     * 
     * @param parent
     *            the parent control
     */
    public AsyncTreeViewer(Composite parent) {
        this(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
    }

    /**
     * Creates an asynchronous tree viewer on a newly-created tree control under
     * the given parent. The tree control is created using the given SWT style
     * bits. The viewer has no input.
     * 
     * @param parent
     *            the parent control
     * @param style
     *            the SWT style bits used to create the tree.
     */
    public AsyncTreeViewer(Composite parent, int style) {
        this(new Tree(parent, style));
    }

    /**
     * Creates an asynchronous tree viewer on the given tree control. The viewer
     * has no input, no content provider, a default label provider, no sorter,
     * and no filters.
     * 
     * @param tree
     *            the tree control
     */
    public AsyncTreeViewer(Tree tree) {
        super();
        fTree = tree;
        tree.addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent e) {
                dispose();
            }
        });
        tree.addTreeListener(new TreeListener() {
        
            public void treeExpanded(TreeEvent e) {
                Object source = e.item;
                if (source instanceof TreeItem) {
                    TreeItem treeItem = ((TreeItem)source);
                    treeItem.setExpanded(true);
                    refresh(treeItem.getData());
                }
            }
        
            public void treeCollapsed(TreeEvent e) {
            }
        
        });
    }
    
    protected void dispose() {
        Iterator images = fImageCache.values().iterator();
        while (images.hasNext()) {
            Image image = (Image) images.next();
            image.dispose();
        }
        fElementsToWidgets.clear();
        fPendingUpdates.clear();
    }

    /**
     * Adds the elements identified by the given path to this tree. The path is
     * an array of elements beginning with the root element of this tree. Has no
     * effect if the element path does not begin with the root element.
     * 
     * @param elementPath
     */
    public void add(Object[] elementPath) {
        // TODO:
    }

    /**
     * Removes all occurrences of the given element from this tree.
     * 
     * @param element
     */
    public void remove(Object element) {
        // TODO:
    }

    /**
     * Updates all occurrences of the given element in this tree.
     * 
     * @param element
     */
    public void update(Object element) {
        if (element == fInput) {
            return;
        }
        IPresentationAdapter adapter = getPresentationAdapter(element);
        if (adapter != null) {
            Widget[] items = getWidgets(element);
            if (items != null) {
                for (int i = 0; i < items.length; i++) {
                    TreeItem item = (TreeItem)items[i];
                    ILabelUpdate labelUpdate = new LabelUpdate(item, this);
                    schedule(labelUpdate);   
                    adapter.retrieveLabel(element, null, labelUpdate);                
                }
            }
        }
    }

    /**
     * Refreshes all occurrences of the given element in this tree, and visible
     * children.
     * 
     * @param element
     */
    public void refresh(Object element) {
        Widget[] items = getWidgets(element);
        if (items == null) {
            return;
        }
        update(element);
        for (int i = 0; i < items.length; i++) {
            Widget item = items[i];
            if (element == fInput) {
                updateChildren(element, item);
            } else if (((TreeItem)item).getExpanded()) {
                updateChildren(element, item);
            }
        }

    }

    protected void updateChildren(Object parent, Widget item) {
        IPresentationAdapter adapter = getPresentationAdapter(parent);
        if (adapter != null) {
            IChildrenUpdate updateChildren = new ChildrenUpdate(item, this);
            schedule(updateChildren);   
            adapter.retrieveChildren(parent, null, updateChildren);
        }
    }
    
    protected IPresentationAdapter getPresentationAdapter(Object element) {
        IPresentationAdapter adapter = null;
        if (element instanceof IAdaptable) {
            IAdaptable adaptable = (IAdaptable) element;
            adapter = (IPresentationAdapter) adaptable.getAdapter(IPresentationAdapter.class);
        }
        return adapter;
    }

    /**
     * Cancels any conflicting updates for children of the given item,
     * and schedules the new update.
     *  
     * @param update
     */
    protected void schedule(IPresentationUpdate update) {
        AbstractUpdate absUpdate = (AbstractUpdate) update;
        synchronized (fPendingUpdates) {
            Iterator updates = fPendingUpdates.listIterator();
            while (updates.hasNext()) {
                AbstractUpdate pendingUpdate = (AbstractUpdate) updates.next();
                if (absUpdate.contains(pendingUpdate)) {
                    pendingUpdate.setCanceled(true);
                    updates.remove();
                }
            }
            fPendingUpdates.add(update);
        }
    }

    /**
     * Returns the widgets associated with the given element or <code>null</code>.
     * 
     * @param element
     * @return
     */
    protected Widget[] getWidgets(Object element) {
        if (element == null) {
            return null;
        }
        return (Widget[]) fElementsToWidgets.get(element);
    }

    /**
     * Selects the last element in the given element path, expanding elements in
     * the path as required.
     * 
     * @param elementPath
     */
    public void select(Object[] elementPath) {
        // TODO:
    }

    /**
     * Expands all elements in the given path. The element path must begin with
     * the root element in this tree. Expands elements in the path as required.
     * 
     * @param elementPath
     */
    public void expand(Object[] elementPath) {
        // TODO:
    }

    /**
     * Removes the last element in the given path. Does not remove other
     * occurrences of the given element from this tree.
     * 
     * @param elementPath
     */
    public void remove(Object[] elementPath) {
        // TODO:
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.viewers.Viewer#getControl()
     */
    public Control getControl() {
        return fTree;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.viewers.IInputProvider#getInput()
     */
    public Object getInput() {
        return fInput;
    }

    public ISelection getSelection() {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.viewers.Viewer#refresh()
     */
    public void refresh() {
        if (fInput != null) {
            refresh(fInput);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.viewers.Viewer#setInput(java.lang.Object)
     */
    public synchronized void setInput(Object input) {
        cancelPendingUpdates();
        unmapAllElements();
        Object oldInput = fInput;
        fInput = input;
        inputChanged(fInput, oldInput);
    }

    protected synchronized void unmapAllElements() {
        Iterator iterator = fElementsToWidgets.keySet().iterator();
        while (iterator.hasNext()) {
            Object element = iterator.next();
            Widget[] widgets = getWidgets(element);
            for (int i = 0; i < widgets.length; i++) {
                Widget widget = widgets[i];
                if (widget instanceof TreeItem) {
                    TreeItem item = (TreeItem) widget;
                    item.dispose();
                }
            }
        }
        fElementsToWidgets.clear();
    }

    protected synchronized void cancelPendingUpdates() {
        Iterator updates = fPendingUpdates.iterator();
        while (updates.hasNext()) {
            IPresentationUpdate update = (IPresentationUpdate) updates.next();
            update.setCanceled(true);
        }
        fPendingUpdates.clear();
    }

    /**
     * Internal hook method called when the input to this viewer is initially
     * set or subsequently changed.
     * <p>
     * The default implementation does nothing. Subclassers may override this
     * method to do something when a viewer's input is set. A typical use is
     * populate the viewer.
     * </p>
     * 
     * @param input
     *            the new input of this viewer, or <code>null</code> if none
     * @param oldInput
     *            the old input element or <code>null</code> if there was
     *            previously no input
     */
    protected void inputChanged(Object input, Object oldInput) {
        map(input, fTree);
        refresh();
    }

    /**
     * Maps the given element to the given item.
     * 
     * @param element
     * @param item TreeItem or Tree
     */
    protected void map(Object element, Widget item) {
        item.setData(element);
        Object object = fElementsToWidgets.get(element);
        if (object == null) {
            fElementsToWidgets.put(element, new Widget[] { item });
        } else {
            Widget[] old = (Widget[]) object;
            Widget[] items = new Widget[old.length + 1];
            System.arraycopy(old, 0, items, 0, old.length);
            items[old.length] = item;
            fElementsToWidgets.put(element, items);
        }
    }

    public void setSelection(ISelection selection, boolean reveal) {
        // TODO Auto-generated method stub

    }
    

    /**
     * Removes the update from the pending updates list.
     * 
     * @param update
     */
    void updateComplete(AbstractUpdate update) {
        synchronized (fPendingUpdates) {
            fPendingUpdates.remove(update);
        }
    }

    void setChildren(Widget widget, List children, List hasChildren) {
        TreeItem[] oldItems = null;
        if (widget instanceof Tree) {
            Tree tree = (Tree) widget;
            oldItems = tree.getItems();
        } else {
            oldItems = ((TreeItem)widget).getItems();
        }
        Iterator newKids = children.iterator();
        int index = 0;
        while (newKids.hasNext()) {
            Object kid = newKids.next();
            boolean hasKids = ((Boolean) hasChildren.get(index)).booleanValue();
            if (index < oldItems.length) {
                TreeItem oldItem = oldItems[index];
                Object oldData = oldItem.getData();
                if (!kid.equals(oldData)) {
                    unmap(kid, oldItem);
                    map(kid, oldItem);
                }
                if (!hasKids && oldItem.getItemCount() > 0) {
                    // dispose children 
                    TreeItem[] items = oldItem.getItems();
                    for (int i = 0; i < items.length; i++) {
                        TreeItem oldChild = items[i];
                        unmap(oldChild.getData(), oldChild);
                        oldChild.dispose();
                    }
                } else if (hasKids && oldItem.getItemCount() == 0) {
                    // dummy to update + 
                    new TreeItem(oldItem, SWT.NONE);
                }
            } else {
                TreeItem newItem = newTreeItem(widget, index);
                map(kid, newItem);
                if (hasKids) {
                    // dummy to update +
                    new TreeItem(newItem, SWT.NONE);
                }
            }
            index++;
        }
        // remove left over old items
        while (index < oldItems.length) {
            TreeItem oldItem = oldItems[index];
            unmap(oldItem.getData(), oldItem);
            oldItem.dispose();
            index++;
        }
        // refresh the current kids
        newKids = children.iterator();
        while (newKids.hasNext()) {
            refresh(newKids.next());
        }
    }
    
    protected TreeItem newTreeItem(Widget parent, int index) {
        if (parent instanceof Tree) {
            return new TreeItem((Tree)parent, SWT.NONE, index);
        }
        return new TreeItem((TreeItem)parent, SWT.NONE, index);
    }

    /**
     * Unmaps the given item, and unmaps and disposes of all children
     * of that item. Does not dispose of the given item.
     * 
     * @param kid
     * @param oldItem
     */
    protected synchronized void unmap(Object kid, TreeItem oldItem) {
        if (kid == null) {
            // when unmapping a dummy item
            return;
        }
        Widget[] widgets = (Widget[]) fElementsToWidgets.get(kid);
        if (widgets != null) {
            for (int i = 0; i < widgets.length; i++) {
                Widget item = widgets[i];
                if (item == oldItem) {
                    if (widgets.length == 1) {
                        fElementsToWidgets.remove(kid);
                        return;
                    }
                    Widget[] newItems = new Widget[widgets.length - 1];
                    System.arraycopy(widgets, 0, newItems, 0, i);
                    if (i < newItems.length) {
                        System.arraycopy(widgets, i + 1, newItems, i, newItems.length - i);
                    }
                }
            }
        }
        TreeItem[] children = oldItem.getItems();
        for (int i = 0; i < children.length; i++) {
            TreeItem child = children[i];
            unmap(child.getData(), child);
            child.dispose();
        }
    }
    
    protected Image getImage(ImageDescriptor descriptor) {
        Image image = (Image) fImageCache.get(descriptor);
        if (image == null) {
            image = new Image(getControl().getDisplay(), descriptor.getImageData());
            fImageCache.put(descriptor, image);
        }
        return image;
    }
}
