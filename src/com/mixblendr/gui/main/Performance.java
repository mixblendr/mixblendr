/**
 *
 */
package com.mixblendr.gui.main;

import static com.mixblendr.util.Debug.debug;

import java.awt.Toolkit;

/**
 * Various methods for performance tuning.
 * 
 * @author Florian Bomers
 */
class Performance {

	/** preload this class */
	private static void preload(String clazz) {
		try {
			Class.forName(clazz);
		} catch (Throwable t) {
			debug(t);
		}
	}

	/** preload a number of classes in different jars used in this applet */
	static void preload() {
		// preload some classes
		preload("org.tritonus.share.sampled.FloatSampleBuffer");
		preload("org.tritonus.sampled.file.jorbis.JorbisAudioFileReader");
		preload("org.tritonus.sampled.convert.jorbis.JorbisFormatConversionProvider");
		preload("org.tritonus.sampled.convert.SampleRateConversionProvider");
		preload("com.jcraft.jogg.Page");
		preload("com.jcraft.jorbis.Block");
		preload("org.tritonus.sampled.convert.javalayer.MpegFormatConversionProvider");
		preload("org.tritonus.sampled.file.mpeg.MpegAudioFileReader");
		preload("javazoom.jl.decoder.Decoder");
	}

	/** set the OS's default UI */
	static void setDefaultUI() {
		try {
			// Set System L&F
			javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
			Toolkit.getDefaultToolkit().setDynamicLayout(true);
			System.setProperty("sun.awt.noerasebackground", "true");
		} catch (Exception e) {
			// debug(e);
		}
	}
}
