/**
 *
 */
package com.mixblendr.test;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import com.mixblendr.skin.GUIBuilder;
import com.mixblendr.skin.MPanel;
import com.mixblendr.skin.MSlider;

import static com.mixblendr.util.Debug.*;

/**
 * A test program to just display a non-functional GUI.
 * 
 * @author Florian Bomers
 */
public class TestSkin {
	private static final long serialVersionUID = 0;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// Schedule a job for the event-dispatching thread:
		// creating and showing this application's GUI.
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					createAndShowGUI();
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(1);
				}
			}
		});
	}

	/**
	 * Create the GUI and show it. For thread safety, this method should be
	 * invoked from the event-dispatching thread.
	 */
	protected static void createAndShowGUI() throws Exception {
		// Create and set up the window.
		final JFrame frame = new JFrame("Skin Test");
		WindowAdapter windowAdapter = new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent we) {
				frame.dispose();
			}
		};
		frame.addWindowListener(windowAdapter);

		debug("Loading skin from '/skins/test'");
		GUIBuilder builder = new GUIBuilder(TestSkin.class, "/skins/test");
		// modify some controls
		MSlider pan = (MSlider) builder.getControlExc("trough.pan");
		pan.setSnapToCenter(true);
		pan.setValue(0.5);
		final MSlider volume = (MSlider) builder.getControlExc("trough.volume");
		volume.setValue(1.0);
		pan.addListener(new MSlider.Listener() {
			public void sliderValueChanged(MSlider source) {
				// nothing
			}

			public void sliderValueTracked(MSlider source) {
				volume.setProgress(source.getValue());
			}

			public void sliderTrackingEnd(MSlider source) {
				// nothing
			}

			public void sliderTrackingStart(MSlider source) {
				// nothing
			}

		});

		MPanel newContentPane = builder.getMasterPanel();
		newContentPane.setOpaque(true); // content panes must be opaque
		frame.setContentPane(newContentPane);

		// Display the window.
		frame.pack();
		frame.setVisible(true);
	}
}
