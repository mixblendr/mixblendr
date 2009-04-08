/**
 *
 */
package com.mixblendr.skin;

import java.awt.Font;

import javax.swing.JComponent;

/**
 * Various utility methods for MControls.
 * 
 * @author Florian Bomers
 */
public class SkinUtils {

	/**
	 * Set this control's font by reading the definition from thr delegate.
	 * 
	 * @param control the control of which to set the font
	 * @param delegate the delegate to get the font info from
	 * @param maxSize the maximum font size to set
	 */
	public static void setFont(JComponent control, ControlDelegate delegate,
			int maxSize) {
		int fontSize = delegate.getCtrlDef().getTargetHeight() - 1;
		if (maxSize > 0 && fontSize > maxSize) {
			fontSize = maxSize;
		}
		String fontname = delegate.getCtrlDef().fontname;
		if (fontSize > 0) {
			Font oldFont = control.getFont();
			int style = 0;
			if (oldFont != null) {
				style = oldFont.getStyle();
			}
			if (oldFont == null || (fontname != null && fontname.length() > 0)) {
				if (oldFont == null) {
					// choose a default font
					fontname = "Helvetica";
				}
				control.setFont(new Font(fontname, style, fontSize));
			} else {
				control.setFont(oldFont.deriveFont((float) fontSize));
			}
		}
	}

	/**
	 * Set this control's font size
	 * 
	 * @param control the control of which to set the font
	 * @param fontName the name of the font, or null to not change the font name
	 * @param fontSize the new font size in pixels
	 */
	public static void setFont(JComponent control, String fontName, float fontSize) {
		if (fontSize > 0) {
			Font oldFont = control.getFont();
			int style = 0;
			if (oldFont != null) {
				style = oldFont.getStyle();
			}
			if (oldFont == null || (fontName != null && fontName.length() > 0)) {
				if (oldFont == null) {
					// choose a default font
					fontName = "Helvetica";
				}
				control.setFont(new Font(fontName, style, (int) fontSize));
			} else {
				control.setFont(oldFont.deriveFont(fontSize));
			}
		}
	}

}
