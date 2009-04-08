/**
 *
 */
package com.mixblendr.gui.main;

import java.awt.Color;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.mixblendr.audio.AudioPlayer;
import com.mixblendr.audio.AudioState;
import com.mixblendr.skin.*;

import static com.mixblendr.util.Debug.*;
import com.mixblendr.util.Debug;
import com.mixblendr.util.Utils;

/**
 * The class managing the buttons in the mixblendr GUI. Most actions are done
 * directly here, others are delegated to the Main class.
 * 
 * @author Florian Bomers
 */
class ButtonPanel implements ActionListener, AudioPlayer.Listener,
		AudioState.StateListener, MouseListener, FocusListener {

	private final static boolean DEBUG = false;

	/**
	 * When clicking the rewind button, it goes back to the beginning of the
	 * previous measure (instead of the current measure) if still during this
	 * grace period of the current measure. The grace period is specified in
	 * milliseconds.
	 */
	private static final int REWIND_GRACEPERIOD = 600;

	/** how many seconds to wind at once */
	private static final int WIND_SECONDS = 5;

	private Main main;

	MToggle snap;
	MButton zoomIn;
	MButton zoomOut;
	MButton addTrack;
	MToggle grabTool;
	MToggle scissorTool;
	MButton rewind;
	MButton play;
    MButton save;
    MButton fastForward;
	MToggle loop;

	MLabel position;
	MEdit tempo;
	MLabel tempoLabel;
	MLabel loopDisplay;
	MLabel loopOnOff;

	private ControlDelegate pauseDelegate;
	private ControlDelegate playDelegate;

	MButton testLoadDef;

	/**
	 * create the Button panel instance by retrieving the elements from the GUI
	 * Builder.
	 */
	ButtonPanel(Main main, GUIBuilder builder) throws Exception {
		this.main = main;
		snap = getToggle(builder, "snap");
		zoomIn = getButton(builder, "zoomIn");
		zoomOut = getButton(builder, "zoomOut");
		addTrack = getButton(builder, "add");
		grabTool = getToggle(builder, "grab");
		scissorTool = getToggle(builder, "scissor");
		rewind = getButton(builder, "rewind");
		play = getButton(builder, "play");
        save = getButton(builder, "save");
        fastForward = getButton(builder, "fastForward");
		loop = getToggle(builder, "loop");

		position = getLabel(builder, "transportDisplay");
		tempo = getEdit(builder, "tempo");
		tempoLabel = getLabel(builder, "tempo");
		loopDisplay = getLabel(builder, "loopDisplay");
		loopOnOff = getLabel(builder, "loopOnOff");

		testLoadDef = getButton(builder, "testLoadDef");

		// some magic for play button: change its control delegate
		// depending on the play/pause state
		pauseDelegate = builder.getDelegate("knob.pause");
		playDelegate = play.getDelegate();
	}

	/**
	 * Set some reasonable defaults and initialize visual controls.
	 */
	void defaults() {
		if (position != null) {
			position.setHorizontalAlignment(SwingConstants.RIGHT);
			// double click to change tempo/time
			position.addMouseListener(this);
		}
		if (snap != null) {
			snap.setSelected(main.getGlobals().getSnapToBeats().isEnabled());
			// enable focusing of snap, so that the tempo edit is not focused as
			// default
			snap.setFocusable(true);
			snap.requestFocusInWindow();
			if (getGlobals() != null
					&& getGlobals().getGlobalKeyListener() != null) {
				snap.addKeyListener(getGlobals().getGlobalKeyListener());
			}
		}
		if (tempoLabel != null) {
			// double click to change tempo/time
			tempoLabel.addMouseListener(this);
		}
		if (tempo != null) {
			tempo.addFocusListener(this);
			tempo.setBackground(Color.blue);
		}
		AudioPlayer player = getPlayer();
		if (player != null) {
			player.removeListener(this);
			player.addListener(this);
			player.getState().addStateListener(this);
		}
		onGrabToolClick();
		// display start time
		onDisplayTimer(true);
		displayLoop(true);
		displayTempo(true);
		displayTimeMode();
	}

	/**
	 * Display either TEMPO or TIME in the tempo label.
	 */
	private void displayTimeMode() {
		if (tempoLabel != null && getState() != null) {
			if (getState().isTimeDisplayInBeats()) {
				tempoLabel.setText("TEMPO");
			} else {
				tempoLabel.setText("TIME");
			}
		}
	}

	/**
	 * Get the named button and register its ActionListener
	 * 
	 * @param name the name of the button, without the type
	 * @return the button, or null if it does not exist
	 */
	private MButton getButton(GUIBuilder builder, String name) {
		MButton ctrl = (MButton) builder.getControl("button." + name);
		if (ctrl != null) {
			ctrl.addActionListener(this);
		}
		if (DEBUG && ctrl == null && name.indexOf("test") < 0) {
			debug("Cannot find GUI definition for button." + name);
		}
		return ctrl;
	}

	/**
	 * Get the named toggle button and register its ActionListener
	 * 
	 * @param name the name of the toggle, without the type
	 * @return the toggle button, or null if it does not exist
	 */
	private MToggle getToggle(GUIBuilder builder, String name) {
		MToggle ctrl = (MToggle) builder.getControl("toggle." + name);
		if (ctrl != null) {
			ctrl.addActionListener(this);
		}
		if (DEBUG && ctrl == null && name.indexOf("test") < 0) {
			debug("Cannot find GUI definition for toggle." + name);
		}
		return ctrl;
	}

	/**
	 * Get the named label
	 * 
	 * @param name the name of the label, without the type
	 * @return the label, or null if it does not exist
	 */
	private MLabel getLabel(GUIBuilder builder, String name) {
		MLabel ctrl = (MLabel) builder.getControl("label." + name);
		if (DEBUG && ctrl == null) {
			debug("Cannot find GUI definition for label." + name);
		}
		if (ctrl != null) {
			ctrl.setForeground(Color.white);
		}
		return ctrl;
	}

	/**
	 * Get the named edit control
	 * 
	 * @param name the name of the edit control, without the type
	 * @return the edit control, or null if it does not exist
	 */
	private MEdit getEdit(GUIBuilder builder, String name) {
		MEdit ctrl = (MEdit) builder.getControl("edit." + name);
		if (DEBUG && ctrl == null) {
			debug("Cannot find GUI definition for edit." + name);
		}
		if (ctrl != null) {
			ctrl.addActionListener(this);
			ctrl.setForeground(Color.white);
		}
		return ctrl;
	}

	/** return the instance of Globals or null if not available */
	private Globals getGlobals() {
		if (main == null) return null;
		return main.getGlobals();
	}

	/** return the instance of AudioPlayer used for this app, or null */
	private AudioPlayer getPlayer() {
		if (getGlobals() == null) return null;
		return getGlobals().getPlayer();
	}

	/** return the instance of AudioState used for this app, or null */
	private AudioState getState() {
		if (getGlobals() == null) return null;
		return getGlobals().getState();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src == snap) {
			onSnap();
		} else if (src == guiTimer) {
			onDisplayTimer(false);
		} else if (src == zoomIn) {
			onZoomIn();
		} else if (src == zoomOut) {
			onZoomOut();
		} else if (src == addTrack) {
			main.onAddTrackClick();
		} else if (src == scissorTool) {
			onScissorClick();
		} else if (src == grabTool) {
			onGrabToolClick();
		} else if (src == rewind) {
			onWindToMeasure(false);
		} else if (src == play) {
			onPlay();
        } else if (src == save) {
            onSave();
        } else if (src == fastForward) {
			onWindToMeasure(true);
		} else if (src == loop) {
			onLoop();
		} else if (src == testLoadDef) {
			main.loadDefaultSong();
		} else if (src == tempo) {
			onTempoChange();
		}
	}

	/** remember the last loop status to not unnecessarily paint the loop state */
	private boolean oldLoopEnabled = false;
	private long oldLoopBegin = -2;
	private long oldLoopEnd = -2;

	/**
	 * @param force if true, always redisplay the loop status
	 */
	public void displayLoop(boolean force) {
		AudioState state = getState();
		if (state == null) return;
		if (force || oldLoopBegin != state.getLoopStartSamples()
				|| oldLoopEnd != state.getLoopEndSamples()) {
			// may need to display LoopOnOff below
			force = force
					|| ((oldLoopBegin < oldLoopEnd) && (state.getLoopStartSamples() >= state.getLoopEndSamples()))
					|| ((oldLoopBegin >= oldLoopEnd) && (state.getLoopStartSamples() < state.getLoopEndSamples()));
			oldLoopBegin = state.getLoopStartSamples();
			oldLoopEnd = state.getLoopEndSamples();
			if (loopDisplay != null) {
				if (oldLoopBegin < oldLoopEnd) {
					loopDisplay.setText(state.samples2MeasureString(oldLoopBegin)
							+ " - " + state.samples2MeasureString(oldLoopEnd));
				} else {
					loopDisplay.setText("");
				}
			}
		}
		if (loopOnOff != null
				&& (force || state.isLoopEnabled() != oldLoopEnabled)) {
			oldLoopEnabled = state.isLoopEnabled();
			if (oldLoopEnabled) {
				loopOnOff.setText("LOOP ON");
			} else {
				loopOnOff.setText("LOOP OFF");
			}
		}
	}

	private double oldTempo;

	private String getTempoString(double tempo1) {
		return Double.toString(Math.round(tempo1 * 100) / 100.0);
	}

	/**
	 * @param force if true, always redisplay the tempo
	 */
	public void displayTempo(boolean force) {
		AudioState state = getState();
		if (state == null) return;
		if (!state.isTimeDisplayInBeats()) {
			tempo.setText("");
		} else if (force || (state.getTempo() != oldTempo)) {
			oldTempo = state.getTempo();
			tempo.setText(getTempoString(oldTempo));
		}
	}

	/**
	 * Called when the user edits the tempo and presses ENTER.
	 */
	private void onTempoChange() {
		AudioState state = getState();
		if (state == null) return;
		String sTempo = tempo.getText();
		// unfocus tempo
		KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent();
		if (sTempo.length() == 0) {
			// no tempo set, switch to time display
			state.setTimeDisplayInBeats(false);
			return;
		}
		try {
			double newTempo = Double.parseDouble(sTempo);
			oldTempo = newTempo;
			state.setTimeDisplayInBeats(true);
			state.setTempo(newTempo);
			displayTempo(false);
		} catch (NumberFormatException nfe) {
			displayTempo(true);
		}

	}

	/**
	 * Method that is called when the user clicks the snap checkbox.
	 */
	private void onSnap() {
		getGlobals().getSnapToBeats().setEnabled(snap.isSelected());
	}

	/**
	 * Scale the scale factor by the given factor and reposition the current
	 * view's x position so that the currently visible portion stays visible
	 * after rescaling.
	 * 
	 * @param factor the factor to apply to the scale factor
	 */
	private void scaleScaleFactor(double factor) {
		GraphScale scale = getGlobals().getScale();
		double newScaleFactor = scale.getScaleFactor() * factor;
		if (newScaleFactor < GraphScale.MIN_SCALE_FACTOR
				|| newScaleFactor > GraphScale.MAX_SCALE_FACTOR) {
			return;
		}

		int newScrollX = 0;
		JViewport vp = getGlobals().getAllRegionsViewPort();
		Component c = (vp != null) ? vp.getView() : null;
		int cWidth = (c != null) ? c.getWidth() : 0;
		if (cWidth > 0 && vp != null) {
			Rectangle r = vp.getViewRect();
			newScrollX = r.x;
			int viewSize = cWidth - r.width;
			if (viewSize > 0) {
				// if (r.x > 2 * viewSize / 3) {
				// align to right
				// newScrollX = (int) (((r.x + r.width) * factor) - r.width);
				// } else
				// if (r.x > viewSize / 4) {
				// align to center
				newScrollX = (int) (((r.x + (r.width / 2)) * factor) - (r.width / 2));
				// } else {
				// align to left
				// newScrollX = (int) (r.x * factor);
				// }
				if (newScrollX < 0) {
					newScrollX = 0;
				}
			}
		}
		scale.setScaleFactor(scale.getScaleFactor() * factor);
		if (vp != null && c != null && cWidth > 0) {
			cWidth *= factor;
			if (c.getWidth() < cWidth) {
				c.setBounds(c.getX(), c.getY(), cWidth, c.getHeight());
			}
			vp.setViewPosition(new Point(newScrollX, 0));
		}
		main.getWorkarea().revalidate();
	}

	/**
	 * Method that is called when the user presses zoom in button
	 */
	public void onZoomIn() {
		scaleScaleFactor(1.0 / 0.8);
	}

	/**
	 * Method that is called when the user presses zoom out button
	 */
	public void onZoomOut() {
		scaleScaleFactor(0.8);
	}

	/**
	 * Switch from grab tool and vice versa
	 */
	public void toggleTool() {
		if (getGlobals().getOperation() == RegionMouseOperation.SELECT) {
			onScissorClick();
		} else {
			onGrabToolClick();
		}
	}

	/**
	 *
	 */
	private void onGrabToolClick() {
		// implement radio button behavior
		if (!grabTool.isSelected()) {
			grabTool.setSelected(true);
		}
		scissorTool.setSelected(false);
		getGlobals().setOperation(RegionMouseOperation.SELECT);
	}

	/**
	 *
	 */
	private void onScissorClick() {
		// implement radio button behavior
		if (!scissorTool.isSelected()) {
			scissorTool.setSelected(true);
		}
		grabTool.setSelected(false);
		getGlobals().setOperation(RegionMouseOperation.SCISSOR);
	}

	/**
	 * Rewind to beginning
	 */
	public void onRewindToBeginning() {
		AudioPlayer player = getPlayer();
		if (player == null) return;
		player.setPositionSamples(0);
		if (!player.isStarted()) {
			onDisplayTimer(false);
		}
	}

	/** action upon pressing one of the wind buttons */
	@SuppressWarnings("cast")
	protected void onWindToMeasure(boolean forward) {
		AudioPlayer player = getPlayer();
		AudioState state = getState();
		if (player == null || state == null) return;

		long currPos = player.getPositionSamples();
		// 1 sample grace period in stopped mode
		long gracePeriod = 1;
		if (player.isStarted()) {
			// grace period during playback given in millis
			gracePeriod = state.millis2sample(REWIND_GRACEPERIOD);
		}
		if (state.isTimeDisplayInBeats()) {
			// ** wind to measures **
			int beatsPerMeasure = state.getBeatsPerMeasure();
			long measure;
			if (forward) {
				// round the measure up
				measure = (((long) state.sample2beat(currPos)) / beatsPerMeasure) + 1;
			} else {
				// round down
				long beat;
				beat = (long) state.sample2beat(currPos - gracePeriod);
				measure = beat / beatsPerMeasure;
			}
			if (measure < 0) {
				measure = 0;
			}
			player.setPositionSamples(state.beat2sample((double) (measure * beatsPerMeasure)));
		} else {
			// ** wind to seconds **
			long second;
			if (forward) {
				// round seconds up
				second = ((long) (state.sample2millis(currPos) / (1000.0 * WIND_SECONDS))) + 1;
			} else {
				// round down
				second = (long) (state.sample2millis(currPos - gracePeriod) / (1000.0 * WIND_SECONDS));
			}
			if (second < 0) {
				second = 0;
			}
			player.setPositionSamples(state.millis2sample((double) (second * (1000 * WIND_SECONDS))));
		}
		if (!player.isStarted()) {
			onDisplayTimer(false);
		}
	}

	/** action upon pressing one of the wind buttons */
	protected void onWind(double seconds) {
		AudioPlayer player = getPlayer();
		if (player == null) return;
		player.setPositionSamples(player.getPositionSamples()
				+ player.getState().seconds2sample(seconds));
		if (!player.isStarted()) {
			onDisplayTimer(false);
		}
	}

	/**
	 *
	 */
	private void onLoop() {
		AudioPlayer player = getPlayer();
		if (loop == null || player == null) return;
		player.setLoopEnabled(loop.isSelected());
		displayLoop(true);
	}

	private static final int GUI_REFRESH_INTERVAL_MILLIS = 40;

	private Timer guiTimer = new Timer(GUI_REFRESH_INTERVAL_MILLIS, this);

	/**
	 * the interval when the GUI refresh timer expires, expressed in samples
	 * (will be calculated from GUI_REFRESH_INTERVAL_MILLIS).
	 */
	private int timerRefreshIntervalSamples = 100;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioPlayer.Listener#onPlaybackStart()
	 */
	public void onPlaybackStart(AudioPlayer player) {
		if (DEBUG) {
			if (!guiTimer.isRunning()) {
				debug("Starting display timer");
			}
		}
		guiTimer.start();
		timerRefreshIntervalSamples = (int) player.getState().millis2sample(
				GUI_REFRESH_INTERVAL_MILLIS * 3 / 2);
		play.setDelegate(pauseDelegate);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioPlayer.Listener#onPlaybackStop(boolean)
	 */
	public void onPlaybackStop(AudioPlayer player, boolean immediate) {
		if (immediate) {
			if (DEBUG) {
				if (guiTimer.isRunning()) {
					debug("Stopping display timer");
				}
			}
			guiTimer.stop();
		}
		onDisplayTimer(false);
		play.setDelegate(playDelegate);
	}

	/** action upon pressing the Start button */
	public void onPlay() {
		// will trigger call onPlaybackStart or onPlaybackStop
		getGlobals().togglePlayback();
	}

    /** action upon pressing the Start button */
    public void onSave() {
        // will trigger call onPlaybackStart or onPlaybackStop
        getGlobals().startSaveFile();
    }


    /**
	 * action upon pressing the Stop button: stop playback and reset position.
	 */
	public void stopPlayback(boolean immediate) {
		if (getGlobals().getPlayer() != null) {
			getGlobals().stopPlayback(immediate);
			if (!immediate) {
				try {
					// give some time to play out before setting a new
					// position
					Thread.sleep(40);
				} catch (InterruptedException ie) {
					// nothing
				}
			}
			getGlobals().getPlayer().setPositionSamples(0);
		}
	}

	private long lastPlaybackTime = -1;

	/** called in regular interval to display current playback time */
	protected void onDisplayTimer(boolean force) {
		AudioPlayer player = getPlayer();
		if (player == null) return;
		long currTime = player.getPositionSamples();
		if (force || currTime != lastPlaybackTime) {
			lastPlaybackTime = currTime;
			if (position != null) {
				position.setText(player.getState().samples2display(currTime,
						true));
			}
			main.handleNewPlaybackPosition(currTime);
		}
		// now notify the individual tracks and check if we still need the
		// displaytimer
		boolean stillActive = false;
		for (ChannelStrip strip : main.strips) {
			if (strip.displayLevel(currTime, timerRefreshIntervalSamples)) {
				stillActive = true;
			}
		}
		if (!stillActive && guiTimer.isRunning()) {
			if (DEBUG) {
				debug("Stopping display timer");
			}
			guiTimer.stop();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioPlayer.Listener#onPlaybackPositionChanged(com.mixblendr.audio.AudioPlayer,
	 *      long)
	 */
	public void onPlaybackPositionChanged(AudioPlayer player, long samplePos) {
		onDisplayTimer(false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioState.StateListener#displayModeChanged()
	 */
	public void displayModeChanged() {
		displayTimeMode();
		displayTempo(true);
		onDisplayTimer(true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioState.StateListener#tempoChanged()
	 */
	public void tempoChanged() {
		displayTempo(false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioState.StateListener#loopChanged(long, long,
	 *      long, long)
	 */
	public void loopChanged(long oldStart, long oldEnd, long newStart,
			long newEnd) {
		displayLoop(false);
	}

	/**
	 * Double-clicking on the tempo label switches from time to beats, and vice
	 * versa.
	 * 
	 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
	 */
	public void mouseClicked(MouseEvent e) {
		if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
			getState().setTimeDisplayInBeats(!getState().isTimeDisplayInBeats());
			e.consume();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
	 */
	public void mouseEntered(MouseEvent e) {
		// nothing to do
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
	 */
	public void mouseExited(MouseEvent e) {
		// nothing to do
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
	 */
	public void mousePressed(MouseEvent e) {
		// nothing to do
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
	 */
	public void mouseReleased(MouseEvent e) {
		// nothing to do
	}

	/**
	 * React to tempo edit's focusing by setting it to opaque so that it's
	 * visible.
	 * 
	 * @see java.awt.event.FocusListener#focusGained(java.awt.event.FocusEvent)
	 */
	public void focusGained(FocusEvent e) {
		if (e.getSource() == tempo) {
			tempo.setOpaque(true);
			tempo.repaint();
		}
	}

	/**
	 * React to tempo edit's unfocusing by setting it to non-opaque so that
	 * blends back into the GUI.
	 * 
	 * @see java.awt.event.FocusListener#focusLost(java.awt.event.FocusEvent)
	 */
	public void focusLost(FocusEvent e) {
		if (e.getSource() == tempo) {
			tempo.setOpaque(false);
			tempo.repaint();
		}
	}


    /** Only for test purpose
     *
     */
    /*public void addTrack() {
        // the inserted region
        AudioRegion region = null;
        long insertSample = handleDragOver(e.getLocation(), false);
        if (insertSample >= 0) {
                if (dragURL != null) {
                    boolean insertLast = false;
                    if (dragSampleCount == UNKNOWN_SAMPLE_COUNT) {
                        // insert at last so that clicking a region that
                        // overlaps is possible
                        insertLast = true;
                        if (DEBUG) {
                            debug("Insert new region as first component");
                        }
                        if (!extractDragSampleCount(dragURL.getFile())) {
                            // don't know the size
                            dragSampleCount = -1;
                        }
                    }
                    region = globals.addRegion(getTrack(), dragURL,
                            insertSample, dragSampleCount);
                    addRegion(region, insertLast ? getComponentCount() : -1);
                    // set the track name to this region's name
                    setTrackName(extractFilename(Utils.getBaseName(dragURL.getFile())));
                } else {
                    insertSample = -1;
                    Debug.displayErrorDialog(this, "Drag'n'Drop error",
                            "Cannot drop this link.");
                }
        }
        if (insertSample >= 0) {
            update();
            if (region != null) {
                RegionGraph rg = getRegionGraph(region);
                if (rg != null) {
                    rg.setSelected(true);
                    rg.requestFocus();
                }
            }
        }
        e.dropComplete(insertSample >= 0);
        clearDragVars();
        if (globals != null) {
            globals.setDraggingMouse(false);
        }
    }   */


}
