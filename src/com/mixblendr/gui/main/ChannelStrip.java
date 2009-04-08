/**
 *
 */
package com.mixblendr.gui.main;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import com.mixblendr.audio.AudioEffect;
import com.mixblendr.audio.AudioFile;
import com.mixblendr.audio.AudioListener;
import com.mixblendr.audio.AudioPlayer;
import com.mixblendr.audio.AudioRegion;
import com.mixblendr.audio.AudioState;
import com.mixblendr.audio.AudioTrack;
import com.mixblendr.audio.AudioRegion.State;
import com.mixblendr.audio.AudioTrack.SoloState;
import com.mixblendr.automation.AutomationPan;
import com.mixblendr.automation.AutomationVolume;
import com.mixblendr.skin.*;
import com.mixblendr.util.Debug;
import com.mixblendr.util.Utils;

/**
 * Manage the GUI components for one channel strip.
 * 
 * @author Florian Bomers
 */
class ChannelStrip implements MSlider.Listener, ActionListener, AudioListener {

	private final static boolean DEBUG = false;

	/**
	 * used for slow decay of the peak meter: maximum units of the progress bar
	 * that the peak can decay per fresh quantum.
	 */
	private final static double MAXIMUM_PEAK_DECAY = 0.08;

	private Main main;

	/** the panel holding the channel strip controls */
	MPanel channelstrip;
	MButton moveUp, moveDown, remove;
	MLabel name;
	MSlider volume, pan;
	MLED clip;
	MButton FXselect;
	MButton FXsettings;
	MToggle mute, solo, auto;
	/** the panel holding the region grahs */
	TrackPanel trackPanel;
	private ControlDelegate waveBackground, waveBackgroundSel;

	/**
	 * create a channel strip instance by retrieving the elements from the GUI
	 * Builder.
	 */
	ChannelStrip(Main main, GUIBuilder builder) throws Exception {
		this.main = main;
		channelstrip = (MPanel) builder.getControlExc("panel.channelstrip");
		if (channelstrip.getParent() != null) {
			// adding/removing the controls is done by Main
			channelstrip.getParent().remove(channelstrip);
		}
		MPanel regions = (MPanel) builder.getControlExc("panel.regions");
		waveBackground = builder.getDelegate("panel.wave_container");
		waveBackgroundSel = builder.getDelegate("panel.wave_container_sel");
		moveUp = getButton(builder, "trackUp");
		moveDown = getButton(builder, "trackDown");
		remove = getButton(builder, "trackRemove");
		name = getLabel(builder, "trackDisplay");
		volume = getSlider(builder, "volume", 1.0, false);
		pan = getSlider(builder, "pan", 0.5, true);
		clip = getLED(builder, "volume_clip");
		FXselect = getButton(builder, "effectSelect");
		FXsettings = getButton(builder, "effectSettings");
		mute = getToggle(builder, "mute");
		solo = getToggle(builder, "solo");
		auto = getToggle(builder, "auto");

		createTrackPanel(regions);
		init();
	}

	/**
	 * create a deep copy of the specified strip, initializing the panel with
	 * the audio track
	 */
	ChannelStrip(ChannelStrip strip, AudioTrack track) {
		this.main = strip.main;
		this.waveBackground = strip.waveBackground;
		this.waveBackgroundSel = strip.waveBackgroundSel;
		// channel strip panel must be the first, because the other components
		// will be placed on it.
		channelstrip = copy(strip.channelstrip);
		moveUp = copy(strip.moveUp);
		moveDown = copy(strip.moveDown);
		remove = copy(strip.remove);
		name = copy(strip.name);
		volume = copy(strip.volume);
		pan = copy(strip.pan);
		clip = copy(strip.clip);
		FXselect = copy(strip.FXselect);
		FXsettings = copy(strip.FXsettings);
		mute = copy(strip.mute);
		solo = copy(strip.solo);
		auto = copy(strip.auto);
		trackPanel = copy(strip.trackPanel);
		trackPanel.init(track, main.getGlobals());
		init();
	}

	/** call this method to remove references */
	public void close() {
		AudioTrack track = getAudioTrack();
		if (track != null) {
			for (AudioEffect e : track.getEffects()) {
				e.exit();
			}
		}
		if (getState() != null) {
			getState().getAudioEventDispatcher().removeListener(this);
		}
		main = null;
		trackPanel = null;
	}

	/**
	 * Initialize some magic properties of the controls
	 */
	private void init() {
		displayAll();
		if (getState() != null) {
			getState().getAudioEventDispatcher().addListener(this);
		}
	}

	/** adjust the position of the ctrl based on the given masterControl */
	private void fixPosition(Component ctrl, Component masterControl) {
		ctrl.setBounds(masterControl.getX(), masterControl.getY(),
				ctrl.getWidth(), ctrl.getHeight());
	}

	/**
	 * Create an instance of TrackPanel and replace the regions panel with it.
	 * 
	 * @param regions the regions panel as defined in the skin
	 */
	private void createTrackPanel(MPanel regions) {
		trackPanel = new TrackPanel();
		trackPanel.setFixedHeight(regions.getDelegate().getCtrlDef().getSize().height);
		trackPanel.setWaveBackground(waveBackground);
		trackPanel.setWaveBackgroundSel(waveBackgroundSel);
		Container parent = regions.getParent();
		if (parent != null) {
			parent.remove(regions);
			// Main is responsible for adding the track panels to the parent
		}


    }

	/**
	 * Create a copy of the given TrackPanel and add it to its parent.
	 * 
	 * @param trackPanel1 the panel to copy
	 */
	private TrackPanel copy(TrackPanel trackPanel1) {
		TrackPanel ret = new TrackPanel();
		ret.setFixedHeight(trackPanel1.getFixedHeight());
		ret.setWaveBackground(trackPanel1.getWaveBackground());
		ret.setWaveBackgroundSel(trackPanel1.getWaveBackgroundSel());
		Container parent = trackPanel1.getParent();
		if (parent != null) {
			parent.add(ret);
		}
		// no need to fixup the position, trackpanels' positions are governed by
		// layout manager
		return ret;
	}

	/** create a copy of the provided panel */
	private MPanel copy(MPanel src) {
		MPanel ctrl = new MPanel((ControlDelegate) src.getDelegate().clone());
		fixPosition(ctrl, src);
		return ctrl;
	}

	/**
	 * Get the named button and register its ActionListener
	 * 
	 * @param name1 the name of the button, without the type
	 * @return the button, or null if it does not exist
	 */
	private MButton getButton(GUIBuilder builder, String name1) {
		MButton ctrl = (MButton) builder.getControl("button." + name1);
		init(ctrl);
		if (DEBUG && ctrl == null) {
			Debug.debug("Cannot find GUI definition for button." + name1);
		}
		return ctrl;
	}

	/** create a copy of the provided button */
	private MButton copy(MButton src) {
		if (src == null) return null;
		MButton ctrl = new MButton((ControlDelegate) src.getDelegate().clone());
		init(ctrl);
		fixPosition(ctrl, src);
		channelstrip.add(ctrl);
		return ctrl;
	}

	/** initialize this button */
	private void init(MButton ctrl) {
		if (ctrl != null) {
			ctrl.addActionListener(this);
		}
	}

	/**
	 * Get the named toggle button and register its ActionListener
	 * 
	 * @param name1 the name of the toggle, without the type
	 * @return the toggle button, or null if it does not exist
	 */
	private MToggle getToggle(GUIBuilder builder, String name1) {
		MToggle ctrl = (MToggle) builder.getControl("toggle." + name1);
		init(ctrl);
		if (DEBUG && ctrl == null) {
			Debug.debug("Cannot find GUI definition for toggle." + name1);
		}
		return ctrl;
	}

	/**
	 * Create a copied toggle based on the provided toggle
	 */
	private MToggle copy(MToggle src) {
		if (src == null) return null;
		MToggle ctrl = new MToggle((ControlDelegate) src.getDelegate().clone());
		init(ctrl);
		fixPosition(ctrl, src);
		channelstrip.add(ctrl);
		return ctrl;
	}

	/** initialize this toggle */
	private void init(MToggle ctrl) {
		if (ctrl != null) {
			ctrl.addActionListener(this);
		}
	}

	/**
	 * Get the named slider and register its listener
	 * 
	 * @param name1 the name of the slider, without the type
	 * @return the slider, or null if it does not exist
	 */
	private MSlider getSlider(GUIBuilder builder, String name1, double val,
			boolean snap) {
		MSlider ctrl = (MSlider) builder.getControl("trough." + name1);
		init(ctrl);
		if (ctrl != null) {
			ctrl.setValue(val);
			ctrl.setSnapToCenter(snap);
		}
		if (DEBUG && ctrl == null) {
			Debug.debug("Cannot find GUI definition for trough." + name1);
		}

		return ctrl;
	}

	/**
	 * Create a copied slider based on the provided control
	 */
	private MSlider copy(MSlider src) {
		if (src == null) return null;
		MSlider ctrl = new MSlider(src);
		init(ctrl);
		fixPosition(ctrl, src);
		channelstrip.add(ctrl);
		return ctrl;
	}

	/** initialize this slider */
	private void init(MSlider ctrl) {
		if (ctrl != null) {
			ctrl.addListener(this);
		}
	}

	/** create a copy of the provided edit control */
	@SuppressWarnings("unused")
	private MEdit copy(MEdit src) {
		if (src == null) return null;
		MEdit ctrl = new MEdit((ControlDelegate) src.getDelegate().clone());
		fixPosition(ctrl, src);
		channelstrip.add(ctrl);
		return ctrl;
	}

	/**
	 * Get the named label.
	 * 
	 * @param name1 the name of the label, without the type
	 * @return the label, or null if it does not exist
	 */
	private MLabel getLabel(GUIBuilder builder, String name1) {
		MLabel ctrl = (MLabel) builder.getControl("label." + name1);
		if (DEBUG && ctrl == null) {
			Debug.debug("Cannot find GUI definition for label." + name1);
		}
		return ctrl;
	}

	/** create a copy of the provided label control */
	private MLabel copy(MLabel src) {
		if (src == null) return null;
		MLabel ctrl = new MLabel((ControlDelegate) src.getDelegate().clone());
		fixPosition(ctrl, src);
		channelstrip.add(ctrl);
		return ctrl;
	}

	/**
	 * Get the named LED.
	 * 
	 * @param name1 the name of the LED, without the type
	 * @return the LED, or null if it does not exist
	 */
	private MLED getLED(GUIBuilder builder, String name1) {
		MLED ctrl = (MLED) builder.getControl("LED." + name1);
		if (DEBUG && ctrl == null) {
			Debug.debug("Cannot find GUI definition for LED." + name1);
		}
		return ctrl;
	}

	/** create a copy of the provided LED control */
	private MLED copy(MLED src) {
		if (src == null) return null;
		MLED ctrl = new MLED((ControlDelegate) src.getDelegate().clone());
		fixPosition(ctrl, src);
		channelstrip.add(ctrl);
		return ctrl;
	}

	/**
	 * @return the trackPanel
	 */
	public TrackPanel getTrackPanel() {
		return trackPanel;
	}

	/**
	 * @return the track
	 */
	public AudioTrack getAudioTrack() {
		if (trackPanel == null) {
			return null;
		}
		return trackPanel.getTrack();
	}

	/**
	 * @return the current effect, or null if no effect is selected
	 */
	public AudioEffect getAudioEffect() {
		AudioTrack track = getAudioTrack();
		if (track == null || track.getEffectCount() == 0) {
			return null;
		}
		return track.getEffect(0);
	}

	/**
	 * @return the player
	 */
	public AudioPlayer getPlayer() {
		if (main == null || main.getGlobals() == null) {
			return null;
		}
		return main.getGlobals().getPlayer();
	}

	/** set the audio track that this panel is serving */
	public void setAudioTrack(AudioTrack track) {
		trackPanel.init(track, main.getGlobals());
	}

	/** return the audio sta object */
	public AudioState getState() {
		if (main == null || main.getGlobals() == null) {
			return null;
		}
		return main.getGlobals().getState();
	}

	/*
	 * ********** DISPLAY METHODS (display the current state from the track)
	 */

	/** display all elements of this channel strip */
	public void displayAll() {
		displayName();
		displayVolume();
		displayPan();
		displayMute();
		displaySolo();
		displayAuto();
		displayFX();
	}

	/** display the name from the current setting in the AudioTrack */
	public void displayName() {
		AudioTrack track = getAudioTrack();
		if (track == null) return;
		if (name == null) return;
		name.setText(track.getName());
	}

	/** display the volume from the current setting in the AudioTrack */
	public void displayVolume() {
		AudioTrack track = getAudioTrack();
		if (track == null) return;
		if (volume == null) return;
		// double vol = track.getVolumeDB();
		// sVol.setValue((int) (vol + 100));
		volume.setValue(track.getVolume());
	}

	/** display the pan from the current setting in the AudioTrack */
	public void displayPan() {
		AudioTrack track = getAudioTrack();
		if (track == null) return;
		if (pan == null) return;
		pan.setValue((track.getBalance() + 1.0) / 2.0);
	}

	/**
	 * display the current audio level from the current setting in the
	 * AudioTrack
	 * 
	 * @return true if there is still a level displayed (when fading out after
	 *         stop)
	 */
	public boolean displayLevel(long currTime, int timerRefreshIntervalSamples) {
		AudioTrack track = getAudioTrack();
		AudioPlayer player = getPlayer();
		if (track == null || player == null) return false;
		if (volume == null && clip == null) return false;

		double level;
		boolean started = player.isStarted();
		if (!started || currTime == 0) {
			level = 0.0;
		} else {
			level = track.getPeakLevel(currTime, timerRefreshIntervalSamples);
			// convert to a logarithmic scale
			level = Utils.convertLinearToLog(level);
		}
		if (volume != null) {
			double maxValue = volume.getProgress() - MAXIMUM_PEAK_DECAY;
			if (level < maxValue) {
				level = maxValue;
			}
			volume.setProgress(level);
		}
		if (clip != null) {
			clip.setSelected(level >= 1.0);
		}
		return (started || (level > 0.0));
	}

	/** display the FX setting from the current setting in the AudioTrack */
	public void displayFX() {
		AudioEffect effect = getAudioEffect();
		if (FXselect == null) return;
		if (effect != null) {
			FXselect.setText(effect.getShortName());
			FXsettings.setEnabled(effect.hasSettingsWindow());
		} else {
			FXselect.setText(EffectManager.EFFECT_NONE);
			FXsettings.setEnabled(true);
		}
	}

	/** display the mute state from the current setting in the AudioTrack */
	public void displayMute() {
		AudioTrack track = getAudioTrack();
		if (track == null) return;
		if (mute == null) return;
		mute.setSelected(track.isMute());
	}

	/** display the solo state from the current setting in the AudioTrack */
	public void displaySolo() {
		AudioTrack track = getAudioTrack();
		if (track == null) return;
		if (solo == null) return;
		solo.setSelected(track.getSolo() == SoloState.SOLO);
	}

	/** display the auto state from the current setting in the AudioTrack */
	public void displayAuto() {
		AudioTrack track = getAudioTrack();
		if (track == null) return;
		if (auto == null) return;
		auto.setSelected(track.isAutomationEnabled());
	}

	/*
	 * ********** APPLY METHODS (apply the display values to the track)
	 */
	/** set the engine's vol from the GUI control */
	public void applyVolume(boolean canAutomate) {
		AudioTrack track = getAudioTrack();
		AudioPlayer player = getPlayer();
		if (track == null || volume == null) return;
		double vol = volume.getValue();
		// track.setVolumeDB(vol);
		track.setVolume(vol);
		if (canAutomate && (player != null) && (auto != null)
				&& auto.isSelected()) {
			track.addAutomationObject(new AutomationVolume(player.getState(),
					vol /* AudioUtils.decibel2linear(vol) */,
					player.getPositionSamples()));
		}

	}

	/** set the engine's pan from the GUI control */
	public void applyPan(boolean canAutomate) {
		AudioTrack track = getAudioTrack();
		AudioPlayer player = getPlayer();
		if (track == null || pan == null) return;
		double pa = (pan.getValue() * 2.0) - 1.0;
		track.setBalance(pa);
		if (canAutomate && (player != null) && (auto != null)
				&& auto.isSelected()) {
			track.addAutomationObject(new AutomationPan(player.getState(), pa,
					player.getPositionSamples()));
		}
	}

	/** set the engine's mute state from the GUI control */
	public void applyMute() {
		AudioTrack track = getAudioTrack();
		if (track == null) return;
		if (mute == null) return;
		track.setMute(mute.isSelected());
	}

	/** set the engine's solo state from the GUI control */
	public void applySolo() {
		AudioTrack track = getAudioTrack();
		AudioPlayer player = getPlayer();
		if (track == null || player == null) return;
		if (solo == null) return;
		player.getMixer().setSolo(track, solo.isSelected());
	}

	/** verify the automation status */
	public void applyAuto() {
		AudioTrack track = getAudioTrack();
		AudioPlayer player = getPlayer();
		if (track == null || player == null) return;
		if (auto == null) return;
		track.setAutomationEnabled(auto.isSelected());
	}

	/**
	 * Move this track up or down.
	 */
	private void moveTrack(boolean up) {
		AudioTrack track = getAudioTrack();
		AudioPlayer player = getPlayer();
		if (track == null || player == null) return;
		if (player.getMixer().moveTrack(track.getIndex(), up)) {
			main.updateTracks();
		}
	}

	/**
	 * Show the effect selector
	 */
	private void selectEffect() {
		AudioTrack track = getAudioTrack();
		if (track == null) return;
		AudioEffect effect = getAudioEffect();
		AudioEffect newEffect = EffectManager.chooseEffect(main.getGlobals(),
				channelstrip, track, effect);
		if (effect != newEffect) {
			for (AudioEffect e : track.getEffects()) {
				e.exit();
			}
			track.clearEffects();
			if (newEffect != null) {
				track.addEffect(newEffect);
			}
			displayFX();
		}
	}

	/**
	 * Show the effect's settings window
	 */
	private void effectSettings() {
		AudioEffect effect = getAudioEffect();
		if (effect == null) {
			selectEffect();
			return;
		}
		if (effect.hasSettingsWindow()) {
			effect.showSettingsWindow();
		} else {
			Debug.displayErrorDialog(channelstrip, "No Settings",
					"This effect does not have a settings window.");
		}
	}

	/*
	 * ********** EVENTS
	 */
	/**
	 * interface MSlider.Listener: called when the slider moves at all
	 */
	public void sliderValueChanged(MSlider source) {
		// nothing
	}

	/**
	 * interface MSlider.Listener: called when the slider is moved by the user
	 */
	public void sliderValueTracked(MSlider source) {
		if (source == volume) {
			applyVolume(true);
		} else if (source == pan) {
			applyPan(true);
		}
	}

	/**
	 * interface MSlider.Listener: called when the slider is grabbed
	 */
	public void sliderTrackingStart(MSlider source) {
		AudioTrack track = getAudioTrack();
		if (track == null) return;
		if (track.isAutomationEnabled()) {
			// from now on, any existing events will be removed
			main.volAutoHandler.setTracking(track, source == volume);
			main.panAutoHandler.setTracking(track, source == pan);
			// insert initial automation event
			sliderValueTracked(source);
		}
	}

	/**
	 * interface MSlider.Listener: called when the slider is ungrabbed
	 */
	public void sliderTrackingEnd(MSlider source) {
		AudioTrack track = getAudioTrack();
		// cancel tracking for this track
		main.volAutoHandler.setTracking(track, false);
		main.panAutoHandler.setTracking(track, false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src == moveUp) {
			moveTrack(true);
		} else if (src == moveDown) {
			moveTrack(false);
		} else if (src == remove) {
			main.onRemoveTrackClick(this);
		} else if (src == FXselect) {
			selectEffect();
		} else if (src == FXsettings) {
			effectSettings();
		} else if (src == mute) {
			applyMute();
		} else if (src == solo) {
			applySolo();
		} else if (src == auto) {
			applyAuto();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioListener#audioFileDownloadError(com.mixblendr.audio.AudioFile,
	 *      java.lang.Throwable)
	 */
	public void audioFileDownloadError(AudioFile file, Throwable t) {
		// nothing to do
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioListener#audioRegionStateChange(com.mixblendr.audio.AudioTrack,
	 *      com.mixblendr.audio.AudioRegion,
	 *      com.mixblendr.audio.AudioRegion.State)
	 */
	public void audioRegionStateChange(AudioTrack track, AudioRegion region,
			State state) {
		// nothing to do
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioListener#audioTrackNameChanged(com.mixblendr.audio.AudioTrack)
	 */
	public void audioTrackNameChanged(AudioTrack track) {
		displayName();
	}

}
