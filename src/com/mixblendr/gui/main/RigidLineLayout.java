/**
 *
 */
package com.mixblendr.gui.main;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * A line layout that lays out the components vertically with their preferred
 * height one after another in lines. The width is determined by the maximum
 * width of the components, the height is determined by the sum of preferred
 * height of the components. If a component's preferred width is -1, it is set
 * to the width of the parent container.
 * 
 * @author Florian Bomers
 */
public class RigidLineLayout implements LayoutManager {

	/**
	 * Create a RigidLineLayout.
	 */
	public RigidLineLayout() {
		super();
	}

	private int getDefaultWidth(Container parent, Insets parentInsets) {
		int w = parent.getWidth() - parentInsets.left - parentInsets.right;
		if (w < 0) {
			return 0;
		}
		return w;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.LayoutManager#layoutContainer(java.awt.Container)
	 */
	public void layoutContainer(Container parent) {
		synchronized (parent.getTreeLock()) {
			Insets insets = parent.getInsets();
			int count = parent.getComponentCount();
			int x = insets.left;
			int y = insets.top;

			for (int i = 0; i < count; i++) {
				Component m = parent.getComponent(i);
				if (m.isVisible()) {
					Dimension d = m.getPreferredSize();
					// if (d.width < 0) {
					d.width = getDefaultWidth(parent, insets);
					// }
					m.setBounds(x, y, d.width, d.height);
					y += d.height;
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.LayoutManager#minimumLayoutSize(java.awt.Container)
	 */
	public Dimension minimumLayoutSize(Container parent) {
		synchronized (parent.getTreeLock()) {
			Dimension dim = new Dimension(0, 0);
			int count = parent.getComponentCount();

			for (int i = 0; i < count; i++) {
				Component m = parent.getComponent(i);
				if (m.isVisible()) {
					Dimension d = m.getMinimumSize();
					dim.height += d.height;
					dim.width = Math.max(dim.width, d.width);
				}
			}
			Insets insets = parent.getInsets();
			dim.width += insets.left + insets.right;
			dim.height += insets.top + insets.bottom;
			return dim;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.LayoutManager#preferredLayoutSize(java.awt.Container)
	 */
	public Dimension preferredLayoutSize(Container parent) {
		synchronized (parent.getTreeLock()) {
			Insets insets = parent.getInsets();
			Dimension dim = new Dimension(0, 0); // getDefaultWidth(parent,
			// insets), 0);
			int count = parent.getComponentCount();

			for (int i = 0; i < count; i++) {
				Component m = parent.getComponent(i);
				if (m.isVisible()) {
					Dimension d = m.getPreferredSize();
					dim.height += d.height;
					// if (d.width < 0) {
					// d.width = getDefaultWidth(parent, insets);
					// }
					dim.width = Math.max(dim.width, d.width);
				}
			}
			dim.width += insets.left + insets.right;
			dim.height += insets.top + insets.bottom;
			return dim;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.LayoutManager#addLayoutComponent(java.lang.String,
	 *      java.awt.Component)
	 */
	public void addLayoutComponent(String name, Component comp) {
		// not used
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.LayoutManager#removeLayoutComponent(java.awt.Component)
	 */
	public void removeLayoutComponent(Component comp) {
		// not used
	}

}
