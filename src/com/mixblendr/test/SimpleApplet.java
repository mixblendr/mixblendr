/**
 *
 */
package com.mixblendr.test;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
// import java.io.*;
import java.net.*;
import java.util.ArrayList;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.tritonus.share.sampled.AudioUtils;

import com.mixblendr.audio.*;
import com.mixblendr.audio.AudioRegion.State;
import com.mixblendr.automation.AutomationPan;
import com.mixblendr.automation.AutomationVolume;
import com.mixblendr.effects.*;
import com.mixblendr.gui.main.Globals;
import com.mixblendr.gui.main.GraphScale;
import com.mixblendr.gui.main.TrackPanel;
import com.mixblendr.util.*;

import static com.mixblendr.util.Debug.*;
import static com.mixblendr.util.Utils.*;
import static com.mixblendr.util.GUIUtils.*;

/**
 * A simple test applet.
 * 
 * @author Florian Bomers
 */
@SuppressWarnings("unchecked")
public class SimpleApplet extends JApplet {

	static void setDefaultUI() {
		// Tritonus debug
		// org.tritonus.share.TDebug.TraceAudioFileReader = true;
		try {
			// Set System L&F
			javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
			Toolkit.getDefaultToolkit().setDynamicLayout(true);
			System.setProperty("sun.awt.noerasebackground", "true");
		} catch (Exception e) {
			// debug(e);
		}
	}

	private static final long serialVersionUID = 0;

	protected App app;

	private static void preload(String clazz) {
		try {
			Class.forName(clazz);
		} catch (Throwable t) {
			debug(t);
		}
	}

	static {
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

	/**
	 * Method called by browser before display of the applet.
	 */
	@Override
	public void init() {
		setDefaultUI();
        System.setSecurityManager(null);
        SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				app = new App();
				JPanel p = new JPanel(new BorderLayout());
				p.setBorder(new SoftBevelBorder(BevelBorder.RAISED));
				p.setOpaque(true);
				app.createGUI(p);
				SimpleApplet.this.setContentPane(p);
			}
		});
	}

	/** called by the browser upon starting the applet */
	@Override
	public void start() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				app.createEngine();
			}
		});
	}

	/** called by the browser when the user navigates away from this page */
	@Override
	public void stop() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				app.close();
			}
		});
	}

	protected static class App implements ActionListener,
			FatalExceptionListener, AudioListener, AudioFileDownloadListener,
			ChangeListener, ItemListener, AutomationListener, MouseListener {

		// -------------------- implementation

		// GUI stuff
		protected JTextArea taLog = null;
		private JButton bStart = null;
		private JButton bStop = null;
		private JButton bRewind = null;
		private JButton bFfwd = null;
		private JButton bFrew = null;
		private JButton bShake = null;
		private JButton bDumpTracks = null;
		private JButton bLoadDefaults = null;
		private JButton bLoop = null;
		private JLabel lCurrTime = null;
		private JLabel lCurrBar = null;

		private JCheckBox cbMute = null;
		private JCheckBox cbSolo = null;
		private JProgressBar pbVol = null;
		private JSlider sVol = null;
		protected JSlider sPan = null;
		private JCheckBox cbAuto = null;

		private TrackPanel graphPanel;
		protected JSlider sGraphScale = null;

		private JButton[] bDropTrack = new JButton[5];

		private final int EFFECTS_COUNT = 3;
		private JComboBox[] effectChooser = new JComboBox[EFFECTS_COUNT];
		private JButton[] effectSettings = new JButton[EFFECTS_COUNT];
		private AudioEffect[] currentEffect = new AudioEffect[EFFECTS_COUNT];
		private Class[] effectClasses = {
				null, Delay.class, Flanger.class, Delay2.class
		};
		private String[] effectNames = {
				"<none>", "Delay", "Flanger", "Delay2",
		};

		/** a flag that change listeners shouldn't react */
		protected int noEvents = 0;

		public void createGUI(JPanel main) {
			main.setLayout(new BorderLayout());
			main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

			// LOGGING
			taLog = new JTextArea("");
			JScrollPane scrollPane = new JScrollPane(taLog,
					ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
					ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			scrollPane.setBorder(new SoftBevelBorder(BevelBorder.LOWERED));
			main.add(scrollPane, BorderLayout.CENTER);

			JPanel south = new JPanel();
			south.setLayout(new BoxLayout(south, BoxLayout.PAGE_AXIS));

			// TRACK PANEL
			JPanel p = new JPanel();
			p.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
			p.setLayout(new BoxLayout(p, BoxLayout.LINE_AXIS));
			for (int i = 0; i < bDropTrack.length; i++) {
				p.add((bDropTrack[i] = createButton("Track " + (i + 1), this)));
				// p.add(Box.createHorizontalStrut(5));
			}
			p.add(Box.createHorizontalGlue());
			p.add(Box.createHorizontalStrut(5));
			south.add(p);

			// TRANSPORT PANEL
			p = new JPanel();
			p.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
			p.setLayout(new BoxLayout(p, BoxLayout.LINE_AXIS));
			p.add((bFrew = createButton("<<", this)));
			bFrew.setToolTipText("jump 5 seconds backwards");
			p.add((bStart = createButton("Play", this)));
			p.add((bStop = createButton("Stop", this)));
			p.add((bRewind = createButton("0", this)));
			bFrew.setToolTipText("rewind");
			p.add((bFfwd = createButton(">>", this)));
			bFfwd.setToolTipText("jump 5 seconds forward");
			p.add(Box.createHorizontalStrut(5));
			p.add((bLoop = createButton("Loop", this)));
			bLoop.setToolTipText("Loop 2:1 to 3:1");
			p.add(Box.createHorizontalGlue());
			p.add((lCurrTime = new JLabel("")));
			p.add(Box.createHorizontalStrut(10));
			p.add((lCurrBar = new JLabel("")));
			p.add(Box.createHorizontalStrut(5));
			south.add(p);

			// ACTION PANEL
			p = new JPanel();
			p.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
			p.setLayout(new BoxLayout(p, BoxLayout.LINE_AXIS));
			p.add((bLoadDefaults = createButton("LoadDefaultSong", this)));
			p.add((bDumpTracks = createButton("DumpTracks", this)));
			p.add((bShake = createButton("MixUp", this)));
			p.add(Box.createHorizontalStrut(5));
			p.add(new JLabel("-"));
			p.add((sGraphScale = createSlider(1, 100, 25, this)));
			setFixedWidth(sGraphScale, 100);
			p.add(new JLabel("+"));
			p.add(Box.createHorizontalStrut(5));
			p.add(Box.createHorizontalGlue());
			south.add(p);

			// CHANNEL STRIP PANEL
			p = new JPanel();
			p.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
			p.setLayout(new BoxLayout(p, BoxLayout.LINE_AXIS));
			p.add(createLabelFixedWidth("Track 1:", SwingConstants.LEFT));
			p.add(Box.createHorizontalStrut(5));
			p.add((cbMute = createCheckbox("Mute", this)));
			p.add(Box.createHorizontalStrut(5));
			p.add((cbSolo = createCheckbox("Solo", this)));
			p.add(Box.createHorizontalStrut(5));
			p.add(createLabelFixedWidth("Vol:", SwingConstants.RIGHT));
			p.add((sVol = createSlider(0, 100, 100, this)));
			setFixedWidth(sVol, 100);
			sVol.addMouseListener(this);
			p.add(Box.createHorizontalStrut(5));
			p.add(createLabelFixedWidth("Pan:", SwingConstants.RIGHT));
			p.add((sPan = createSlider(-100 - PAN_SNAP, 100 + PAN_SNAP, 0, this)));
			setFixedWidth(sPan, 100);
			sPan.addMouseListener(this);
			p.add(Box.createHorizontalStrut(5));
			p.add((pbVol = createProgressBar(PROGRESSBAR_RANGE)));
			p.add(Box.createHorizontalStrut(5));
			p.add((cbAuto = createCheckbox("Auto", this)));
			p.add(Box.createHorizontalStrut(5));
			south.add(p);

			// EFFECTS PANEL
			p = new JPanel();
			p.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
			p.setLayout(new BoxLayout(p, BoxLayout.LINE_AXIS));
			p.add(createLabelFixedWidth("FX:  ", SwingConstants.LEFT));
			for (int i = 0; i < EFFECTS_COUNT; i++) {
				p.add(createLabelFixedWidth("Track " + (i + 1) + ":",
						SwingConstants.LEFT));
				p.add((effectChooser[i] = createComboBox(this)));
				effectChooser[i].setModel(new DefaultComboBoxModel(effectNames));
				effectChooser[i].setSelectedIndex(0);
				effectChooser[i].repaint(50); // work around for swing bug
				p.add((effectSettings[i] = createButton("S", this)));
				p.add(Box.createHorizontalStrut(10));
			}
			south.add(p);

			// audio graph display
			graphPanel = new TrackPanel();
			JScrollPane sp = new JScrollPane(graphPanel,
					ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
					ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			sp.setPreferredSize(new Dimension(Integer.MAX_VALUE, 90));
			south.add(sp);
			main.add(south, BorderLayout.SOUTH);

			// DND support
			MyTH mth = new MyTH();
			for (int i = 0; i < bDropTrack.length; i++) {
				bDropTrack[i].setTransferHandler(mth);
			}
			// prevent the text area from accepting text
			taLog.setTransferHandler(null);
		}

		public void startText() {
			text("Drag server audio files on the track buttons to add them.");
			text("Press a track button to clear the track.");
			text(" -- or --");
			text("press the LoadDefaultSong button to load the tracks to 3 tracks ");
			text("");
			text("Press the MixUp button to randomly split and shift regions");
			text("");
			text("You can do all this while playing.");
			text("");
		}

		protected class MyTH extends TransferHandler {
			private static final long serialVersionUID = 0;

			private boolean isUrlType(DataFlavor flavor) {
				return flavor.getRepresentationClass().isAssignableFrom(
						URL.class);
			}

			protected boolean isSupported(DataFlavor[] flavors) {
				for (DataFlavor f : flavors) {
					if (f.isFlavorTextType() || isUrlType(f)
							|| f.isFlavorJavaFileListType()) {
						return true;
					}
				}
				return false;
			}

			@Override
			public boolean canImport(JComponent comp,
					DataFlavor[] transferFlavors) {
				// we're only interested in URL's
				for (DataFlavor df : transferFlavors) {
					try {
						// URL and button?
						if (isUrlType(df) && comp instanceof JButton) {
							return true;
						}
					} catch (Exception e) {
						debug(e);
					}
				}
				return false;
			}

			// requires 1.6
			// boolean canImport(TransferHandler.TransferSupport support) {
			// This method is called repeatedly during a drag and drop operation
			// to allow the developer to configure properties of, and to return
			// the acceptability of transfers; with a return value of true
			// indicating that the transfer represented by the given
			// TransferSupport (which contains all of the details of the
			// transfer) is acceptable at the current time, and a value of false
			// rejecting the transfer.
			// }

			@Override
			public boolean importData(JComponent comp, Transferable t) {
				// Causes a transfer to a component from a clipboard or a DND
				// drop operation.
				DataFlavor[] transferFlavors = t.getTransferDataFlavors();
				// String flavors = "";
				// String flavor = "";
				// String data = "";
				for (DataFlavor df : transferFlavors) {
					try {
						// flavor = df.getHumanPresentableName();
						Object td = t.getTransferData(df);
						// URL?
						if (isUrlType(df) && td instanceof URL) {
							onDrop(comp, (URL) td, -1);
							// data = "URL: "+url.toString();
							return true;
						}

						// File list?
						if (df.isFlavorJavaFileListType()
								&& td instanceof java.util.List) {
							// java.util.List<File> files =
							// (java.util.List<File>) td;
							// data = "" + files.size() + " file(s), first one:
							// "
							// + files.get(0);
							break;
						}
						// text?
						if (df.isFlavorTextType() && td instanceof String) {
							// String text = (String) td;
							// data = "Text: " + text;
							break;
						}

						// out("Flavor: "+df.getHumanPresentableName()
						// out("Flavor: "+df.getMimeType()
						// +": "+td.getClass().getSimpleName());
						// flavors += td.toString()+", ";
					} catch (Exception ufe) {
						ufe.printStackTrace();
					}
				}
				// if (data.length() == 0) {
				// text("drop of non-supported type!");
				// } else {
				// text("drop on "+comp.getClass().getSimpleName()+": "+flavor);
				// }
				// text(data);
				return false;
			}

			// requires 1.6
			// public boolean importData(TransferHandler.TransferSupport
			// support) {
			// Causes a transfer to occur from a clipboard or a drag and drop
			// operation.
			// }

		}

		private static final int PROGRESSBAR_RANGE = 1000;

		// ---------------- GUI events

		/**
		 * Interface ActionListener: called when a button is pressed or the
		 * timer expires
		 */
		public void actionPerformed(ActionEvent event) {
			try {
				if (event.getSource() == bStart) {
					onStart();
				} else if (event.getSource() == bStop) {
					onStop(false);
				} else if (event.getSource() == bRewind) {
					onRewind();
				} else if (event.getSource() == bFfwd) {
					onWind(5);
				} else if (event.getSource() == bFrew) {
					onWind(-5);
				} else if (event.getSource() == bLoop) {
					onLoop();
				} else if (event.getSource() == bShake) {
					onShake();
				} else if (event.getSource() == bLoadDefaults) {
					onLoadDefaultSong();
				} else if (event.getSource() == bDumpTracks) {
					onDumpTracks();
				} else if (event.getSource() == guiTimer) {
					onDisplayTimer();
				} else {
					for (int i = 0; i < bDropTrack.length; i++) {
						if (event.getSource() == bDropTrack[i]) {
							onTrackClear(i);
							break;
						}
					}
					for (int i = 0; i < EFFECTS_COUNT; i++) {
						if (event.getSource() == effectSettings[i]) {
							if (currentEffect[i] != null) {
								currentEffect[i].showSettingsWindow();
							}
							break;
						}
					}
				}

			} catch (Throwable t) {
				error(t);
			}
		}

		/** Interface ChangeListener: slider movement */
		public void stateChanged(ChangeEvent e) {
			if (noEvents > 0) return;
			try {
				if (e.getSource() == sPan) {
					applyPan(true);
				} else if (e.getSource() == sVol) {
					applyVol(true);
				} else if (e.getSource() == sGraphScale) {
					applyGraphScale();
				}
			} catch (Throwable t) {
				error(t);
			}
		}

		/**
		 * Interface ItemStateListener: called when one of the comboboxes'
		 * selection changes, or a checkbox is changed
		 */
		public void itemStateChanged(ItemEvent arg0) {
			Object src = arg0.getSource();
			if (arg0.getStateChange() == ItemEvent.SELECTED
					|| ((src instanceof JCheckBox) && arg0.getStateChange() == ItemEvent.DESELECTED)) {
				try {
					if (src == cbAuto) {
						applyAuto();
					} else if ((src == cbMute)) {
						applyMute();
					} else if ((src == cbSolo)) {
						applySolo();
					} else {
						for (int i = 0; i < EFFECTS_COUNT; i++) {
							if (src == effectChooser[i]) {
								selectEffect(i);
								break;
							}
						}
					}
				} catch (Throwable t) {
					error(t);
				}
			}
		}

		// mouse listener

		/** the current object being tracked */
		private Object tracker = null;

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
		 */
		public void mouseClicked(MouseEvent e) {
			// nothing
		}

		public void mouseEntered(MouseEvent e) {
			// nothing
		}

		public void mouseExited(MouseEvent e) {
			// nothing
		}

		public void mousePressed(MouseEvent e) {
			if (tracker != e.getSource()) {
				tracker = e.getSource();
				applyAuto();
				// insert initial automation event
				if (tracker == sPan) {
					applyPan(true);
				} else if (tracker == sVol) {
					applyVol(true);
				}
			}
		}

		/** snap to zero asynchronously */
		private Runnable setPanToZero = new Runnable() {
			public void run() {
				noEvents++;
				try {
					sPan.setValue(0);
					// debug("0: "+sPan.getValue());
				} finally {
					noEvents--;
				}
			}
		};

		public void mouseReleased(MouseEvent e) {
			tracker = null;
			applyAuto();
			if (e.getSource() == sPan) {
				if (sPan.getValue() >= -PAN_SNAP && sPan.getValue() <= PAN_SNAP) {
					SwingUtilities.invokeLater(setPanToZero);
				}
			}
		}

		// ENGINE

		private AudioPlayer player;
		private Globals globals;
		private AutomationHandler volAutoHandler;
		private AutomationHandler panAutoHandler;

		protected void createEngine() {
			if (player == null) {
				player = new AudioPlayer(this, this);
				player.setTempo(62.5);
				player.getState().getAudioEventDispatcher().addListener(this);
				player.getState().getAutomationEventDispatcher().addListener(
						this);
				globals = new Globals(player);
				player.init();
				startText();
				// display start time
				onDisplayTimer();
				// get automation handlers
				panAutoHandler = AutomationManager.getHandler(AutomationPan.class);
				volAutoHandler = AutomationManager.getHandler(AutomationVolume.class);
				// create tracks
				for (int i = 0; i < bDropTrack.length; i++) {
					player.addAudioTrack();
				}
				applyTrackControls();
			}
		}

		protected void close() {
			for (int i = 0; i < EFFECTS_COUNT; i++) {
				if (currentEffect[i] != null) {
					currentEffect[i].exit();
					debug("Closing effect " + currentEffect[i]);
					currentEffect[i] = null;
				}
			}
			if (player != null) {
				debug("stopping guiTimer");
				guiTimer.stop();
				debug("stopping player");
				player.close();
				player = null;
			}
			debug("SimpleApplet: close done");
		}

		private static final int GUI_REFRESH_INTERVAL_MILLIS = 50;

		protected Timer guiTimer = new Timer(GUI_REFRESH_INTERVAL_MILLIS, this);

		protected void startImpl() {
			try {
				player.start();
				guiTimer.start();
				timerRefreshIntervalSamples = (int) player.getState().millis2sample(
						GUI_REFRESH_INTERVAL_MILLIS * 3 / 2);
			} catch (Throwable t) {
				fatalExceptionOccured(t, "when starting");
			}
			makeButtons();
		}

		protected void stopImpl(boolean immediate) {
			player.stop(immediate);
			makeButtons();
			onDisplayTimer();
		}

		/** action upon pressing the Start button */
		protected void onStart() {
			if (!player.isStarted()) {
				startImpl();
			} else {
				stopImpl(false);
			}
		}

		/** action upon pressing the Stop button */
		protected void onStop(boolean immediate) {
			if (player != null) {
				stopImpl(immediate);
				if (!immediate) {
					try {
						// give some time to play out before setting a new
						// position
						Thread.sleep(40);
					} catch (InterruptedException ie) {
						// nothing
					}
				}
				player.setPositionSamples(0);
			}
		}

		/** action upon pressing the Rewind button */
		protected void onRewind() {
			if (player != null) {
				player.setPositionSamples(0);
			}
		}

		/** action upon pressing one of the wind buttons */
		protected void onWind(double seconds) {
			if (player != null) {
				player.setPositionSamples(player.getPositionSamples()
						+ player.getState().seconds2sample(seconds));
			}
		}

		protected void onLoop() {
			if (player != null) {
				player.setLoopSamples(QUARTER_BEAT * 4, QUARTER_BEAT * 4);
				player.setLoopEnabled(!player.isLoopEnabled());
				bLoop.setSelected(player.isLoopEnabled());
			}
		}

		private static final int PAN_SNAP = 10;

		/** return the currently selected pan from the GUI control */
		private double getGUIPan() {
			int pan = sPan.getValue();
			if (pan <= 0) {
				// snap to middle
				if (pan >= -PAN_SNAP) {
					pan = 0;
				} else {
					pan += PAN_SNAP;
				}
			} else {
				// snap to middle
				if (pan <= PAN_SNAP) {
					pan = 0;
				} else {
					pan -= PAN_SNAP;
				}
			}
			return pan / 100.0;
		}

		/** set the engine's pan from the GUI control */
		private void applyPan(boolean canAutomate) {
			if (player != null && player.getMixer().getTrackCount() > 0) {
				double pan = getGUIPan();
				// debug("pan: "+sPan.getValue()+" -> "+pan);
				AudioTrack track = player.getMixer().getTrack(0);
				track.setBalance(pan);
				if (canAutomate && cbAuto.isSelected()) {
					track.addAutomationObject(new AutomationPan(
							player.getState(), pan, player.getPositionSamples()));
				}
			}
		}

		/** set the GUI control position from the track's pan setting */
		private void displayPan(AudioTrack track) {
			if (track == null) return;
			if (track.getIndex() == 0) {
				noEvents++;
				try {
					double pan = track.getBalance();
					if (pan == 0) {
						sPan.setValue(0);
					} else if (pan < 0) {
						sPan.setValue(((int) (pan * 100.0)) - PAN_SNAP);
					} else {
						sPan.setValue(((int) (pan * 100.0)) + PAN_SNAP);
					}
				} finally {
					noEvents--;
				}
			}
		}

		/** return the currently selected pan from the GUI control, linear */
		private double getGUIVol() {
			// in decibel return sVol.getValue() - 100;
			return sVol.getValue() / 100.0;
		}

		/** set the engine's vol from the GUI control */
		private void applyVol(boolean canAutomate) {
			if (player != null && player.getMixer().getTrackCount() > 0) {
				AudioTrack track = player.getMixer().getTrack(0);
				double vol = getGUIVol();
				// track.setVolumeDB(vol);
				track.setVolume(vol);
				if (canAutomate && cbAuto.isSelected()) {
					track.addAutomationObject(new AutomationVolume(
							player.getState(),
							vol /* AudioUtils.decibel2linear(vol) */,
							player.getPositionSamples()));
				}
			}
		}

		/** set the GUI control position from the track's vol setting */
		private void displayVol(AudioTrack track) {
			if (track == null) return;
			if (track.getIndex() == 0) {
				noEvents++;
				try {
					// double vol = track.getVolumeDB();
					// sVol.setValue((int) (vol + 100));
					double vol = track.getVolume();
					sVol.setValue((int) (vol * 100));
				} finally {
					noEvents--;
				}
			}
		}

		private void applyMute() {
			if (player != null && player.getMixer().getTrackCount() > 0) {
				player.getMixer().getTrack(0).setMute(cbMute.isSelected());
			}
		}

		private void applySolo() {
			if (player != null && player.getMixer().getTrackCount() > 0) {
				player.getMixer().setSolo(0, cbSolo.isSelected());
			}
		}

		/** verify the automation status */
		private void applyAuto() {
			if (player != null && player.getMixer().getTrackCount() > 0) {
				AudioTrack track = player.getMixer().getTrack(0);
				if (volAutoHandler.isTracking(track) != (tracker == sVol)) {
					if ((tracker == sVol)) {
						text("Volume Tracking on");
					} else {
						text("Volume Tracking off");
					}
				}
				if (panAutoHandler.isTracking(track) != (tracker == sPan)) {
					if ((tracker == sPan)) {
						text("Pan Tracking on");
					} else {
						text("Pan Tracking off");
					}
				}
				volAutoHandler.setTracking(track, tracker == sVol);
				panAutoHandler.setTracking(track, tracker == sPan);
				track.setAutomationEnabled(cbAuto.isSelected());
			}
		}

		/** changes the effect for the given track */
		private void selectEffect(int track) {
			int newIndex = effectChooser[track].getSelectedIndex();
			if (newIndex < 0) {
				newIndex = 0;
			}
			Class clazz = effectClasses[newIndex];
			// clean up existing effect
			if (currentEffect[track] != null
					&& (clazz == null || currentEffect[track].getClass() != clazz)) {
				currentEffect[track].exit();
				if (player != null && track < player.getMixer().getTrackCount()) {
					player.getMixer().getTrack(track).removeEffect(
							currentEffect[track]);
				}
				currentEffect[track] = null;
			}
			// create new effect
			if (clazz != null) {
				try {
					currentEffect[track] = (AudioEffect) clazz.newInstance();
					currentEffect[track].init(player.getState(), player,
							player.getMixer().getTrack(track));
					if (player != null
							&& track < player.getMixer().getTrackCount()) {
						player.getMixer().getTrack(track).addEffect(
								currentEffect[track]);
					}
				} catch (Throwable t) {
					displayErrorDialog(taLog, t, "when creating effect "
							+ effectNames[track]);
				}
			}
		}

		private void makeButtons() {
			if (player == null) return;
			if (player.isStarted()) {
				bStart.setText("Pause");
			} else {
				bStart.setText("Play");
			}
		}

		/** removes all events from track */
		protected void onTrackClear(int track) {
			if (track < player.getMixer().getTrackCount()) {
				player.getMixer().getTrack(track).getPlaylist().clear();
				text("Clear track " + (track + 1));
			}
			if (track == 0) {
				displayGraph();
			}

		}

		/** prints out layout of all tracks */
		protected void onDumpTracks() {
			beginUpdate();
			for (AudioTrack at : player.getMixer().getTracks()) {
				text(at.toString() + ":");
				Playlist pl = at.getPlaylist();
				for (int i = 0; i < pl.getObjectCount(); i++) {
					text(" -" + pl.getObject(i));
					if ((i % 10) == 9) {
						try {
							Thread.sleep(10);
						} catch (InterruptedException ie) {
							// nothing
						}
					}
				}
			}
			endUpdate();
		}

		private static final String SOUNDS_URL = "http://www.12fb.com/florian/nervesound/sounds/";

		protected void onLoadDefaultSong() {
			beginUpdate();
			for (int i = 0; i < bDropTrack.length; i++) {
				onTrackClear(i);
			}
			try {
				String ext = ".ogg";
				text("Loading track 1 with bass");
				onDrop(bDropTrack[0], new URL(SOUNDS_URL + "Bass1" + ext), 0);
				onDrop(bDropTrack[0], new URL(SOUNDS_URL + "Bass2" + ext),
						QUARTER_BEAT * 4);
				onDrop(bDropTrack[0], new URL(SOUNDS_URL + "Bass1" + ext),
						QUARTER_BEAT * 8);
				onDrop(bDropTrack[0], new URL(SOUNDS_URL + "Bass3" + ext),
						QUARTER_BEAT * 12);
				onDrop(bDropTrack[0], new URL(SOUNDS_URL + "Bass2" + ext),
						QUARTER_BEAT * 16);
				text("Loading track 2 with drums");
				URL url = new URL(SOUNDS_URL + "Drum" + ext);
				onDrop(bDropTrack[1], url, 0);
				onDrop(bDropTrack[1], url, QUARTER_BEAT * 4);
				onDrop(bDropTrack[1], url, QUARTER_BEAT * 8);
				onDrop(bDropTrack[1], url, QUARTER_BEAT * 12);
				onDrop(bDropTrack[1], url, QUARTER_BEAT * 16);
				AudioRegion drums = ((AudioRegion) player.getMixer().getTrack(1).getPlaylist().getObject(
						3));
				drums.setDuration(QUARTER_BEAT * 2);
				text("Loading track 3 with voice and FX");
				onDrop(bDropTrack[2], new URL(SOUNDS_URL + "texte" + ext),
						QUARTER_BEAT * 4 - (QUARTER_BEAT / 2));
				onDrop(bDropTrack[2], new URL(SOUNDS_URL + "texte" + ext),
						QUARTER_BEAT * 10);
				onDrop(bDropTrack[2], new URL(SOUNDS_URL + "slave" + ext),
						QUARTER_BEAT * 20);
				AudioRegion lastText = ((AudioRegion) player.getMixer().getTrack(
						2).getPlaylist().getObject(1));
				lastText.setAudioFileOffset(lastText.getAudioFileOffset()
						+ (QUARTER_BEAT * 4));
				lastText.setDuration(QUARTER_BEAT * 4);
			} catch (Exception e) {
				error(e);
			}
			endUpdate();
		}

		private boolean doDisplayGraph;

		private GraphScale getScale() {
			if (globals != null) {
				return globals.getScale();
			}
			return null;
		}

		/**
		 * Set the scale, according to the slider.
		 */
		private void applyGraphScale() {
			if (getScale() != null) {
				getScale().setScaleFactor(
						0.001 * Math.pow(2, (sGraphScale.getValue() / 7.5) - 4));
			}
		}

		/**
		 * display the graph for track 0
		 */
		private void displayGraph() {
			if (player == null || player.getMixer().getTrackCount() == 0)
				return;
			applyGraphScale();
			graphPanel.init(player.getMixer().getTrack(0), globals);
		}

		/** called when user drops a URL onto a component */
		protected void onDrop(JComponent c, URL url, long pos) {
			beginUpdate();
			int t = 0;
			for (int i = 0; i < bDropTrack.length; i++) {
				while (player.getMixer().getTrackCount() <= i) {
					text("adding empty track "
							+ (player.getMixer().getTrackCount() + 1) + "...");
					player.addAudioTrack();
					applyTrackControls();
				}
				if (c == bDropTrack[i]) {
					t = i;
					break;
				}
			}

			AudioTrack at = player.getMixer().getTrack(t);
			text("-creating AudioFile from URL, filename: "
					+ getBaseName(url.getPath()));
			AudioFile af = player.getFactory().getAudioFile(url);
			if (pos < 0) {
				pos = at.getDurationSamples();
			}
			text("-added region at time "
					+ player.getState().sample2seconds(pos) + "s");
			at.addRegion(af, pos);
			if (t == 0) {
				doDisplayGraph = true;
			}
			endUpdate();
		}

		/**
		 * apply the GUI settings for vol, pan, etc. to the engine. Should be
		 * called after adding a track
		 */
		private void applyTrackControls() {
			applyPan(false);
			applyVol(false);
			applyMute();
			applySolo();
		}

		// number of samples in a quarter beat
		private final static int QUARTER_BEAT = 42336;

		private String r2s(AudioRegion ar) {
			return "region at "
					+ player.getState().sample2seconds(ar.getStartTimeSamples())
					+ "s";
		}

		/** randomize the tracks */
		protected void onShake() {
			beginUpdate();
			doDisplayGraph = true;
			AudioState st = player.getState();
			text("Mixing up: force all regions into a matrix of 4/4 beats ");
			text("  and doing other randomizing.");
			int totalRegions = 0;
			for (int t = 0; t < player.getMixer().getTrackCount(); t++) {
				AudioTrack track = player.getMixer().getTrack(t);
				Playlist pl = track.getPlaylist();
				ArrayList<AudioRegion> objs = new ArrayList<AudioRegion>();
				for (int i = 0; i < pl.getObjectCount(); i++) {
					if (pl.getObject(i) instanceof AudioRegion) {
						objs.add((AudioRegion) pl.getObject(i));
					}
				}
				if (objs.size() > 0) {
					text("Mixing up track " + (t + 1) + ": " + objs.size()
							+ " regions");
					long pos = 0;
					while (objs.size() > 0) {
						// allow audio thread to do some stuff
						if ((objs.size() % 15) == 14) {
							try {
								Thread.sleep(10);
							} catch (InterruptedException ie) {
								// nothing
							}
						}
						// get a random region
						int index = (int) (Math.random() * objs.size());
						AudioRegion region = objs.get(index);
						int sel = (int) (Math.random() * 8.0);
						long regionLength = QUARTER_BEAT * 4;
						// int sel = 2;
						switch (sel) {
						case 0: {
							text(" -shifting " + r2s(region) + " to "
									+ st.sample2seconds(pos));
							region.setStartTimeSamples(pos);
							if (region.getEffectiveDurationSamples() < regionLength) {
								regionLength = (region.getEffectiveDurationSamples() / QUARTER_BEAT)
										* QUARTER_BEAT;
								if (regionLength <= 0) {
									regionLength = QUARTER_BEAT;
								}
							}
							break;
						}
						case 1: {
							if (region.getEffectiveDurationSamples() < QUARTER_BEAT) {
								continue;
							}
							text(" -stutter 1/4ths " + r2s(region) + " to "
									+ st.sample2seconds(pos) + "s");
							region.setStartTimeSamples(pos);
							regionLength = QUARTER_BEAT;
							for (int r = 0; r < 7; r++) {
								if (region == null) break;
								// text(" -split
								// ("+st.sample2seconds(QUARTER_BEAT / 2)+"s) of
								// "+ r2s(region));
								AudioRegion right = pl.splitRegion(region,
										QUARTER_BEAT / 2);
								if ((r % 2) == 1) {
									// text(" -delete left split");
									pl.removeObject(region);
									regionLength += QUARTER_BEAT;
								}
								region = right;
							}
							// text(" -delete last remainder");
							pl.removeObject(region);
							break;
						}
						case 2: {
							if (region.getEffectiveDurationSamples() < QUARTER_BEAT) {
								continue;
							}
							text(" -stutter 1/8ths " + r2s(region) + " to "
									+ st.sample2seconds(pos) + "s");
							region.setStartTimeSamples(pos);
							regionLength = 0;
							for (int r = 0; r < 15; r++) {
								if (region == null) break;
								// text(" -split
								// ("+st.sample2seconds(QUARTER_BEAT / 4)+"s) of
								// "+ r2s(region));
								AudioRegion right = pl.splitRegion(region,
										QUARTER_BEAT / 4);
								if ((r % 2) == 1) {
									// text(" -delete left split");
									pl.removeObject(region);
								}
								if ((r % 4) == 0) {
									regionLength += QUARTER_BEAT;
								}
								region = right;
							}
							// text(" -delete last remainder");
							pl.removeObject(region);
							break;
						}
						case 3: {
							text(" -split " + r2s(region)
									+ " in 2 and swap the 2 chunks");
							long length = region.getEffectiveDurationSamples();
							if (length < 0 || length > (4 * QUARTER_BEAT)) {
								length = 4 * QUARTER_BEAT;
							}
							AudioRegion right = pl.splitRegion(region,
									length / 2);
							region.setStartTimeSamples(pos + length / 2);
							if (right == null) break;
							right.setStartTimeSamples(pos);
							right.setDuration(length / 2);
							regionLength = length;
							break;
						}
						case 4: {
							if (region.getEffectiveDurationSamples() <= (QUARTER_BEAT * 2)) {
								continue;
							}
							region.setStartTimeSamples(pos);
							text(" -play 4 times the third 'quarter' of "
									+ r2s(region));
							region.setAudioFileOffset(region.getAudioFileOffset()
									+ (QUARTER_BEAT * 2));
							region.setDuration(QUARTER_BEAT);
							for (int r = 1; r <= 3; r++) {
								AudioRegion n = (AudioRegion) region.clone();
								n.setStartTimeSamples(pos + (QUARTER_BEAT * r));
								pl.addObject(n);
							}
							break;
						}
						case 5: {
							if (region.getEffectiveDurationSamples() <= QUARTER_BEAT) {
								continue;
							}
							region.setStartTimeSamples(pos);
							text(" -play 4 times the second 'quarter' of "
									+ r2s(region));
							region.setAudioFileOffset(region.getAudioFileOffset()
									+ QUARTER_BEAT);
							region.setDuration(QUARTER_BEAT);
							for (int r = 1; r <= 3; r++) {
								AudioRegion n = (AudioRegion) region.clone();
								n.setStartTimeSamples(pos + (QUARTER_BEAT * r));
								pl.addObject(n);
							}
							break;
						}
						case 6: {
							text(" -play 4 times the first 'quarter' of "
									+ r2s(region));
							region.setStartTimeSamples(pos);
							region.setDuration(QUARTER_BEAT);
							for (int r = 1; r <= 3; r++) {
								AudioRegion n = (AudioRegion) region.clone();
								n.setStartTimeSamples(pos + (QUARTER_BEAT * r));
								pl.addObject(n);
							}
							break;
						}
						case 7: {
							if (region.getEffectiveDurationSamples() <= (QUARTER_BEAT * 2)) {
								continue;
							}
							text(" -in the second half, play 4 times the first '1/8' of "
									+ r2s(region));
							region.setStartTimeSamples(pos);
							region.setDuration(QUARTER_BEAT * 2);
							for (int r = 0; r < 4; r++) {
								AudioRegion n = (AudioRegion) region.clone();
								n.setDuration(QUARTER_BEAT / 2);
								n.setStartTimeSamples(pos + (QUARTER_BEAT * 2)
										+ (QUARTER_BEAT * r / 2));
								pl.addObject(n);
							}
							break;
						}

						}
						pos += regionLength;
						objs.remove(index);
					}

				} else {
					text("Track " + (t + 1) + ": no audio regions");
				}
				totalRegions += pl.getAudioRegionCount();
			}
			if (totalRegions > 0) {
				text("Now " + totalRegions + " audio regions");
			}
			endUpdate();
		}

		private long lastPlaybackTime = -1;

		/** the interval when the GUI refresh timer expires, expressed in samples */
		private int timerRefreshIntervalSamples = 100;

		/** called in regular interval to display current playback time */
		protected void onDisplayTimer() {
			if (player == null) return;
			long currTime = player.getPositionSamples();
			if (currTime != lastPlaybackTime) {
				lastPlaybackTime = currTime;
				lCurrTime.setText(player.getState().samples2timeString(currTime));
				lCurrBar.setText(player.getState().samples2MeasureString(
						currTime));
			}
			displayPeakLevel(currTime, 0, pbVol);
		}

		/**
		 * convert the linear level 0..1 to a logarithmic level, also from 0..1
		 */
		private double convertLinearToLog(double linear) {
			double ret = AudioUtils.linear2decibel(linear);
			// convert back to range 0..1
			double silence = -70.0; // AudioUtils.SILENCE_DECIBEL
			ret = (ret - silence) / (-silence);
			if (ret < 0) ret = 0;
			if (ret > 1) ret = 1;
			return ret;
		}

		/**
		 * used for slow decay of the peak meter: maximum units of the progress
		 * bar that the peak can decay per fresh quantum.
		 */
		private final static int MAXIMUM_PEAK_DECAY = 80;

		private void displayPeakLevel(long currTime, int trackIndex,
				JProgressBar pb) {
			if (trackIndex < player.getMixer().getTrackCount()) {
				// display level on track 0
				double level;
				if (!player.isStarted() || currTime == 0) {
					level = 0;
				} else {
					level = player.getMixer().getTrack(trackIndex).getPeakLevel(
							currTime, timerRefreshIntervalSamples);
					// convert to a logarithmic scale
					level = convertLinearToLog(level);
				}
				int pbValue = (int) (level * PROGRESSBAR_RANGE);
				int maxValue = pb.getValue() - MAXIMUM_PEAK_DECAY;
				if (pbValue < maxValue) {
					pb.setValue(maxValue);
				} else {
					pb.setValue(pbValue);
				}
			}
		}

		/**
		 * Interface FatalExceptionListener: called upon an exception in the IO
		 * thread. This implementation will asynchronously call stop().
		 * 
		 * @see com.mixblendr.util.FatalExceptionListener#fatalExceptionOccured(java.lang.Throwable,
		 *      java.lang.String)
		 */
		public void fatalExceptionOccured(final Throwable t,
				final String context) {
			error(t);
			// need to stop asynchronously, because this
			// listener is probably called in the faulting thread, with deadlock
			// potential if stopped from this thread.
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					onStop(true);
					displayErrorDialog(taLog, t, context);
				}
			});
		}

        public void showMessage(String title, String context)
        {
            
        }

        public void showProgressDialog()
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public void hideProgressDialog()
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public void setSuccess() // uploading is success
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public void setFailed() // uploading is failed
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public void setProgressDialogMessage(String message)
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        /*
           * (non-Javadoc)
           *
           * @see com.mixblendr.audio.AudioListener#audioFileDownloadError(com.mixblendr.audio.AudioFile,
           *      java.lang.Throwable)
           */
		public void audioFileDownloadError(AudioFile file, Throwable t) {
			text("Error downloading " + file);
			text(t.getMessage());
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.mixblendr.audio.AudioListener#audioRegionStateChange(com.mixblendr.audio.AudioTrack,
		 *      com.mixblendr.audio.AudioRegion,
		 *      com.mixblendr.audio.AudioRegion.State)
		 */
		public void audioRegionStateChange(AudioTrack track,
				AudioRegion region, State state) {
			switch (state) {
			case DOWNLOAD_START:
				break;
			case DOWNLOAD_PROGRESS:
				break;
			case DOWNLOAD_END:
				break;
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.mixblendr.audio.AudioFileDownloadListener#downloadStarted(com.mixblendr.audio.AudioFile)
		 */
		public void downloadStarted(AudioFile af) {
			text(af.getName() + ": download start");
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.mixblendr.audio.AudioFileDownloadListener#downloadEnded(com.mixblendr.audio.AudioFile)
		 */
		public void downloadEnded(AudioFile af) {
			text(af.getName() + ": download done");
		}

		private int updateCtr = 0;

		protected void beginUpdate() {
			updateCtr++;
		}

		protected void endUpdate() {
			if (updateCtr > 0) {
				updateCtr--;
				if (updateCtr == 0) {
					taLog.setCaretPosition(taLog.getText().length());
					if (doDisplayGraph) {
						doDisplayGraph = false;
						displayGraph();
					}
				}
			}
		}

		// interface AutomationListener

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.mixblendr.audio.AutomationListener#automationEvent(com.mixblendr.audio.AudioTrack,
		 *      com.mixblendr.audio.AutomationObject)
		 */
		public void automationEvent(AudioTrack track, AutomationObject ao) {
			if (ao instanceof AutomationPan) {
				displayPan(track);
			} else if (ao instanceof AutomationVolume) {
				displayVol(track);
			}
		}

		protected void text(String s) {
			taLog.append(s + "\n");
			if (updateCtr == 0) {
				taLog.setCaretPosition(taLog.getText().length());
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.mixblendr.audio.AudioListener#audioTrackNameChanged(com.mixblendr.audio.AudioTrack)
		 */
		public void audioTrackNameChanged(AudioTrack track) {
			// nothing to do
		}

	}

	/**
	 * Create the GUI and show it. For thread safety, this method should be
	 * invoked from the event-dispatching thread.
	 */
	protected static void createAndShowGUI() {
		// Create and set up the window.
		final JFrame frame = new JFrame("Mixblendr Test");
		final App app = new App();
		WindowAdapter windowAdapter = new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent we) {
				app.close();
				frame.dispose();
			}
		};
		frame.addWindowListener(windowAdapter);

		// Create and set up the content pane.
		JPanel newContentPane = new JPanel();
		newContentPane.setOpaque(true); // content panes must be opaque
		app.createGUI(newContentPane);
		frame.setContentPane(newContentPane);

		// Display the window.
		frame.setSize(new Dimension(600, 500));
		frame.setVisible(true);
		app.createEngine();
	}

	public static void main(String[] args) {
		setDefaultUI();
		// Schedule a job for the event-dispatching thread:
		// creating and showing this application's GUI.
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				createAndShowGUI();
			}
		});
	}

}
