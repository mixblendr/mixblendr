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
 * A flow layout that lays out the components horizontally with their preferred
 * width one after another in one row. The width is determined by the combined
 * width of all components. If a component's preferred height is negative, its
 * height is set to the parent's height.
 * 
 * @author Florian Bomers
 */
public class RigidFlowLayout implements LayoutManager {

	int fixedHeight = -1;

	/**
	 * Create a RigidFlowLayout.
	 */
	public RigidFlowLayout() {
		super();
	}

	/**
	 * Create a RigidFlowLayout with the given fixed height.
	 */
	public RigidFlowLayout(int fixedHeight) {
		super();
		this.fixedHeight = fixedHeight;
	}

	private int getDefaultHeight(Container parent, Insets parentInsets) {
		int h = parent.getHeight() - parentInsets.top - parentInsets.bottom;
		if (h < 0) {
			return 0;
		}
		return h;
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

			// any components with width<0 will share the remaining width
			int fixedWidth = 0;
			int nonFixedWidthCount = 0;
			for (int i = 0; i < count; i++) {
				Component m = parent.getComponent(i);
				if (m.isVisible()) {
					Dimension d = m.getPreferredSize();
					if (d.width >= 0) {
						fixedWidth += d.width;
					} else {
						nonFixedWidthCount += 1.0;
					}
				}
			}

			int remainingNonFixedWidth = parent.getWidth() - insets.left
					- insets.right - fixedWidth;
			double averageNonFixedWidth = (nonFixedWidthCount > 0) ? ((double) remainingNonFixedWidth)
					/ nonFixedWidthCount
					: 0.0;
			double nonFixedWidthRemainder = 0.0;
			for (int i = 0; i < count; i++) {
				Component m = parent.getComponent(i);
				if (m.isVisible()) {
					Dimension d = m.getPreferredSize();
					if (fixedHeight > 0) {
						d.height = fixedHeight - insets.top - insets.bottom;
					} else if (d.height < 0) {
						d.height = getDefaultHeight(parent, insets);
					}
					if (d.width < 0) {
						d.width = (int) (nonFixedWidthRemainder + averageNonFixedWidth);
						nonFixedWidthRemainder = (nonFixedWidthRemainder + averageNonFixedWidth)
								- d.width;
					}
					m.setBounds(x, y, d.width, d.height);
					x += d.width;
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
					dim.height = Math.max(dim.height, d.height);
					dim.width += d.width;
				}
			}
			Insets insets = parent.getInsets();
			dim.width += insets.left + insets.right;
			if (fixedHeight > 0) {
				dim.height = fixedHeight;
			} else {
				dim.height += insets.top + insets.bottom;
			}
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
			Dimension dim = new Dimension(0, 0); // getDefaultHeight(parent,
			// insets));
			int count = parent.getComponentCount();

			for (int i = 0; i < count; i++) {
				Component m = parent.getComponent(i);
				if (m.isVisible()) {
					Dimension d = m.getPreferredSize();
					dim.height = Math.max(dim.height, d.height);
					// if (d.height < 0) {
					// d.height = getDefaultHeight(parent, insets);
					// }
					dim.width += d.width;
				}
			}
			dim.width += insets.left + insets.right;
			if (fixedHeight > 0) {
				dim.height = fixedHeight;
			} else {
				dim.height += insets.top + insets.bottom;
			}
			// Debug.debug("RigidFlowLayout:
			// prefSize="+dim.width+"x"+dim.height+" - "+parent);
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
