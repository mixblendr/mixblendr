/**
 *
 */
package com.mixblendr.util;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

/**
 * Collection of useful methods for GUI creation.
 * 
 * @author Florian Bomers
 */
public class GUIUtils {

	/**
	 * Restrict this component to the specified width. The height will be
	 * adjusted to fit its container.
	 */
	public static void setFixedWidth(JComponent c, int width) {
		if (width > 0) {
			c.setMinimumSize(new Dimension(width, c.getHeight()));
			c.setMaximumSize(new Dimension(width, 100000));
			c.setPreferredSize(new Dimension(width, c.getHeight()));
		}
	}

	/** Restrict this component to the specified width and height. */
	public static void setFixedSize(JComponent c, int width, int height) {
		if (width > 0 && height > 0) {
			c.setMinimumSize(new Dimension(width, height));
			c.setMaximumSize(new Dimension(width, height));
			c.setPreferredSize(new Dimension(width, height));
		}
	}

	/**
	 * Create a new label with the given caption and the alignment (see
	 * SwingConstants).
	 */
	public static JLabel createLabel(String caption, int alignment) {
		return new JLabel(caption, alignment);
	}

	/**
	 * Create a new label with the given caption, the alignment (see
	 * SwingConstants), and the specified width.
	 */
	public static JLabel createLabel(String caption, int alignment, int width) {
		JLabel res = createLabel(caption, alignment);
		setFixedWidth(res, width);
		return res;
	}

	/**
	 * Create a new label with the given caption, the alignment (see
	 * SwingConstants). The label will fix its size to fit the caption and it
	 * will not resize itself when the caption changes.
	 */
	public static JLabel createLabelFixedWidth(String caption, int alignment) {
		JLabel res = createLabel(caption, alignment);
		setFixedWidth(res, res.getSize().width);
		return res;
	}

	/**
	 * Create a new horizontal progress bar with 0 minimum and the given maximum
	 * value.
	 */
	public static JProgressBar createProgressBar(int max) {
		JProgressBar res = new JProgressBar();
		res.setMinimum(0);
		res.setMaximum(max);
		return res;
	}

	/**
	 * Create a new button with the given caption which sends its events to the
	 * given action listener
	 */
	public static JButton createButton(String caption, ActionListener al) {
		JButton res = new JButton(caption);
		res.addActionListener(al);
		return res;
	}

	/**
	 * Create a new check box with the given caption and which sends its events
	 * to the given item listener
	 */
	public static JCheckBox createCheckbox(String caption, ItemListener il) {
		JCheckBox res = new JCheckBox(caption);
		res.addItemListener(il);
		res.setBorder(new EmptyBorder(0, 0, 0, 0));
		return res;
	}

	/**
	 * Create a new slider with the given min and max values, and the given
	 * default position. It reports its events to the given ChangeListener.
	 */
	public static JSlider createSlider(int min, int max, int def,
			ChangeListener cl) {
		JSlider res = new JSlider(SwingConstants.HORIZONTAL, min, max, def);
		res.addChangeListener(cl);
		return res;
	}

	/** Create a new combo box. It reports its events to the given ItemListener. */
	public static JComboBox createComboBox(ItemListener il) {
		JComboBox res = new JComboBox();
		res.addItemListener(il);
		return res;
	}

}
