/**
 *
 */
package com.mixblendr.util;

import java.awt.Component;
import javax.swing.JOptionPane;
import org.tritonus.share.sampled.AudioUtils;

/**
 * Miscellaneous utility functions
 * 
 * @author Florian Bomers
 */
public class Utils {

	/**
	 * return the base name of the given file path, e.g. /temp/file.wav will
	 * return file.wav
	 */
	public final static String getBaseName(String path) {
		int pos = path.lastIndexOf('/');
		if (pos >= 0) {
			return path.substring(pos + 1);
		}
		return path;
	}

	/**
	 * returns if this string is defined, which is true if the string is not
	 * null, and its length is larger than 0.
	 */
	public static final boolean isDefined(String s) {
		return (s != null) && (s.length() > 0);
	}

	/**
	 * convert the linear level 0..1 to a logarithmic level, also from 0..1
	 */
	public static final double convertLinearToLog(double linear) {
		double ret = AudioUtils.linear2decibel(linear);
		// convert back to range 0..1
		double silence = -70.0; // AudioUtils.SILENCE_DECIBEL
		ret = (ret - silence) / (-silence);
		if (ret < 0) ret = 0;
		if (ret > 1) ret = 1;
		return ret;
	}

	/**
	 * ask the user with a confirmation dialog if the text is OK.
	 * 
	 * @param text the text to be acknowledged by the user
	 * @return true if the user clicked yes, false otherwise.
	 */
	public static boolean confirm(Component parent, String text) {
		return JOptionPane.showInternalConfirmDialog(parent, text, "confirm",
				JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
	}

	/**
	 * Display the text in a dialog box and asks the user to press OK.
	 * 
	 * @param text the text to be acknowledged by the user
	 */
	public static void FYI(Component parent, String text) {
		JOptionPane.showInternalConfirmDialog(parent, text, "Info",
				JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
	}

	/** return the minimum value of all provided parameters */
	public final static int min(int x1, int x2, int x3) {
		if (x1 > x2) {
			x1 = x2;
		}
		if (x1 > x3) {
			return x3;
		}
		return x1;
	}

	/** return the maximum value of all provided parameters */
	public final static int max(int x1, int x2, int x3) {
		if (x1 < x2) {
			x1 = x2;
		}
		if (x1 < x3) {
			return x3;
		}
		return x1;
	}

	/** return the minimum value of all provided parameters */
	public final static int min(int x1, int x2, int x3, int x4) {
		if (x1 > x2) {
			x1 = x2;
		}
		if (x1 > x3) {
			x1 = x3;
		}
		if (x1 > x4) {
			return x4;
		}
		return x1;
	}

	/** return the maximum value of all provided parameters */
	public final static int max(int x1, int x2, int x3, int x4) {
		if (x1 < x2) {
			x1 = x2;
		}
		if (x1 < x3) {
			x1 = x3;
		}
		if (x1 < x4) {
			return x4;
		}
		return x1;
	}
}
