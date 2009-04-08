/**
 *
 */
package com.mixblendr.effects;

import java.awt.event.*;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.tritonus.share.sampled.FloatSampleBuffer;
import com.mixblendr.audio.*;

import static com.mixblendr.util.GUIUtils.*;

/**
 * Example base class for effects showing a GUI.
 * 
 * @author Florian Bomers
 */
public abstract class GUIEffectsBase extends JFrame implements AudioEffect,
		MouseListener, ChangeListener {

	// engine
	protected AudioState state;
	protected AudioPlayer player;
	protected AudioTrack track;

	// GUI
	private boolean guiInited = false;

	/**
	 * synchronization object: never synchronize on <code>this</code>, will
	 * interfere with JFrame's synchronization
	 */
	protected Object lock = new Object();

	/** private default constructor to prevent instanciation without name */
	@SuppressWarnings("unused")
	private GUIEffectsBase() {
		this("");
	}

	/** create a new instance of the Delay effect */
	protected GUIEffectsBase(String name) {
		super(name);
	}

	// --------------------------------- interface AudioEffect

	/**
	 * Initialize the member fields state, track, and player. Then call
	 * initImpl().
	 * 
	 * @see com.mixblendr.audio.AudioEffect#init(com.mixblendr.audio.AudioState,
	 *      com.mixblendr.audio.AudioPlayer, com.mixblendr.audio.AudioTrack)
	 */
	public final void init(AudioState aState, AudioPlayer aPlayer,
			AudioTrack aTrack) {
		synchronized (lock) {
			this.state = aState;
			this.player = aPlayer;
			this.track = aTrack;
			initImpl();
		}
	}

	/**
	 * Implementors should override this method. It's called from the init()
	 * method after initializing state, player, and track. You can use it to
	 * initialize default values.
	 */
	public abstract void initImpl();

	/**
	 * Call exitImpl() and then remove references to player, track, and state.
	 * Finally it will dispose of the frame.
	 * 
	 * @see com.mixblendr.audio.AudioEffect#exit()
	 */
	public final void exit() {
		synchronized (lock) {
			exitImpl();
			// free references
			this.player = null;
			this.track = null;
			this.state = null;
			guiInited = false;
		}
		dispose();
	}

	/**
	 * Implementors should override this method. It's called from the exit()
	 * method.
	 */
	public abstract void exitImpl();

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioEffect#getShortName()
	 */
	public String getShortName() {
		return super.getTitle();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioEffect#getTrack()
	 */
	public AudioTrack getTrack() {
		return track;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioEffect#hasSettingsWindow()
	 */
	public boolean hasSettingsWindow() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioEffect#showSettingsWindow()
	 */
	public void showSettingsWindow() {
		if (!guiInited) {
			initGUI();
		}
		setVisible(true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioEffect#process(long,
	 *      org.tritonus.share.sampled.FloatSampleBuffer, int, int)
	 */
	public abstract boolean process(long samplePos, FloatSampleBuffer buffer,
			int offset, int sampleCount);

	// --------------------------------- Settings Window

	private void initGUI() {
		JPanel main = new JPanel();
		initGUI(main);
		main.setOpaque(true); // content panes must be opaque
		setContentPane(main);
		pack();
		guiInited = true;
	}

	/**
	 * Implementors should implement this method to create the GUI components.
	 * You can use the utility class SliderStrip and the utility methods in
	 * GUIUtils.
	 * 
	 * @param main the panel to fill with elements.
	 */
	protected abstract void initGUI(JPanel main);

	/** width of the left column where the slider strip's name is presented. */
	protected int STRIP_LEFT_LABEL_WIDTH = 70;
	/** width of the right column where the slider strip's position is presented. */
	protected int STRIP_RIGHT_LABEL_WIDTH = 70;

	/**
	 * An inner class providing the components for one strip in the GUI with
	 * labels and slider.
	 * 
	 * @author Florian Bomers
	 */
	protected class SliderStrip extends JPanel {

		public JSlider slider;
		public JLabel label;

		public SliderStrip(String caption, int min, int max, int def,
				String minCaption, String maxCaption) {
			super();
			setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
			setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

			add(createLabel(caption, SwingConstants.LEFT,
					STRIP_LEFT_LABEL_WIDTH));
			slider = createSlider(min, max, def, GUIEffectsBase.this);
			slider.addMouseListener(GUIEffectsBase.this);
			if (minCaption != "") {
				Dictionary<Integer, JLabel> labels = new Hashtable<Integer, JLabel>();
				labels.put(min, createLabel(minCaption, SwingConstants.LEFT));
				labels.put(max, createLabel(maxCaption, SwingConstants.RIGHT));
				slider.setLabelTable(labels);
				slider.setPaintLabels(true);
			}
			add(slider);
			add(Box.createHorizontalStrut(5));
			add((label = createLabel("", SwingConstants.RIGHT,
					STRIP_RIGHT_LABEL_WIDTH)));
		}

		/* satisfy compiler */
		private static final long serialVersionUID = 0;
	}

	// --------------------------------- interface MouseListener

	/**
	 * called when the user clicks on a slider. In response, notify the engine
	 * that we're tracking this automation object.
	 */
	public abstract void mousePressed(MouseEvent e);

	/**
	 * called when the user releases the mouse button from a slider. Notify the
	 * engine that we're not tracking this automation object anymore.
	 */
	public abstract void mouseReleased(MouseEvent e);

	public void mouseClicked(MouseEvent e) {
		// not used
	}

	public void mouseEntered(MouseEvent e) {
		// not used
	}

	public void mouseExited(MouseEvent e) {
		// not used
	}

	// interface ChangeListener

	/**
	 * Called when the user or the implementation moves a slider
	 */
	public abstract void stateChanged(ChangeEvent e);

	/* satisfy compiler */
	private static final long serialVersionUID = 0;

}
