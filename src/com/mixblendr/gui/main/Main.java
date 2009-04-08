/**
 *
 */
package com.mixblendr.gui.main;

import static com.mixblendr.util.Debug.*;

import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

import javax.swing.*;

import com.mixblendr.audio.*;
import com.mixblendr.audio.AudioRegion.State;
import com.mixblendr.automation.AutomationPan;
import com.mixblendr.automation.AutomationVolume;
import com.mixblendr.skin.GUIBuilder;
import com.mixblendr.skin.MPanel;
import com.mixblendr.skin.MPanel.PaintListener;
import com.mixblendr.util.Debug;
import com.mixblendr.util.FatalExceptionListener;

/**
 * The main class for Mixblendr: create the GUI from the skin definition file,
 * and manage the different components.
 * 
 * @author Florian Bomers
 */
public class Main implements FatalExceptionListener, AutomationListener,
		MPanel.PaintListener, KeyListener, AudioListener, ActionListener
{

	public final static String VERSION = "0.19";
	public final static String NAME = "Mixblendr";
	public final static String SKIN = "/skins/main";

	private final static boolean DEBUG = false;
	private final static boolean DEBUG_REPAINT = false;

	private final static Color CURSOR_LINE_COLOR = new Color(216, 70, 70);

	private double defaultTempo = 96.0;

	private MPanel masterPanel;
	private MPanel allChannelstrips;
	private MPanel allRegions;
	private MPanel workarea;
	private JScrollPane workareaScrollPane;
	private JScrollPane allRegionsScrollPane;
	private PositionGrid positionGrid;

	private ChannelStrip masterStrip;
	List<ChannelStrip> strips = new ArrayList<ChannelStrip>();
	private ButtonPanel buttonPanel;
	private EffectManager effectManager;

	private AudioPlayer player;
	private Globals globals;
	AutomationHandler volAutoHandler;
	AutomationHandler panAutoHandler;

    private ProgressMonitor progressMonitor = null;
    public boolean isSaving = false;

    public JDialog publishingDialog =null;
    public boolean isPublishing = false;
    private boolean isPublishSuccess = false;

    private Applet applet;

    public Applet getApplet()
    {
        return applet;
    }

    public void setApplet(Applet applet)
    {
        this.applet = applet;
    }

    public double getDefaultTempo()
    {
        return defaultTempo;
    }

    public void setDefaultTempo(double defaultTempo)
    {
        this.defaultTempo = defaultTempo;
    }

    /**
	 * Create the GUI and initialize the GUI components. For thread safety, this
	 * method should be invoked from the event-dispatching thread.
	 */
	void createGUI() throws Exception {
		if (DEBUG) {
			debug("Loading skin from '" + SKIN + "'");
		}
		effectManager = new EffectManager();
		GUIBuilder builder = new GUIBuilder(this.getClass(), SKIN);
		// modify some controls
		initControls(builder);

		masterPanel.setOpaque(true); // content panes must be opaque
		/*
		 * Global key listeners don't work in applets. try {
		 * Toolkit.getDefaultToolkit().addAWTEventListener(this,
		 * AWTEvent.KEY_EVENT_MASK); } catch (Throwable t) { error(t); }
		 */

		if (DEBUG_REPAINT) {
			masterPanel.setPaintListener(new PaintListener() {

				public void panelBeforePaint(MPanel panel, Graphics g,
						Rectangle clip) {
					debug("MasterPanel: paint with clip " + clip);
				}

				public void panelAfterPaint(MPanel panel, Graphics g,
						Rectangle clip) {
					// ignore
				}

			});
		}
	}

	/**
	 * Register the individual controls and start listening to them.
	 * 
	 * @param builder
	 */
	private void initControls(GUIBuilder builder) throws Exception {
		player = new AudioPlayer(this);
		globals = new Globals(player);
		globals.setGlobalKeyListener(this);
		masterPanel = builder.getMasterPanel();
		globals.setMasterPanel(masterPanel);
		// get resize events

		// first patch the general layout
		// need to wrap "panel.workarea" in a scrollarea
		workarea = (MPanel) builder.getControlExc("panel.workarea");
		Container oldParent = workarea.getParent();
		if (oldParent == null) {
			throw new Exception("panel.workarea does not have a parent!");
		}
		oldParent.remove(workarea);
		JScrollPane sp = new JScrollPane(workarea,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
				// ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		sp.setBorder(null);
		sp.setViewportBorder(null);
		sp.setBounds(workarea.getBounds());
		sp.setOpaque(false);
		sp.getViewport().setOpaque(false);
		oldParent.add(sp);
		workareaScrollPane = sp;
		workarea.setLayout(new RigidFlowLayout());

		// need to make "panel.all_channelstrips" a RigidLineLayout
		allChannelstrips = (MPanel) builder.getControlExc("panel.all_channelstrips");
		allChannelstrips.setLayout(new RigidLineLayout());

		// need to wrap "panel.all_regions" in a horizontal scroll area
		allRegions = (MPanel) builder.getControlExc("panel.all_regions");
		oldParent = allRegions.getParent();
		if (oldParent == null) {
			throw new Exception("panel.allregions does not have a parent!");
		}
		oldParent.remove(allRegions);
		allRegions.setLayout(new RigidLineLayout());
		sp = new JScrollPane(allRegions,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		allRegionsScrollPane = sp;
		sp.setBorder(null);
		sp.setViewportBorder(null);
		sp.setOpaque(true);
		sp.getViewport().setOpaque(true);

		// move the scrollbar to the workarea scroller
		sp.remove(sp.getHorizontalScrollBar());
		workareaScrollPane.add(sp.getHorizontalScrollBar(),
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR);
		allRegionsScrollPane.setPreferredSize(new Dimension(-1, -1));
		globals.setAllRegionsViewPort(allRegionsScrollPane.getViewport());
		oldParent.add(sp);

		positionGrid = new PositionGrid(globals, builder);

		// init the first channel strip
		masterStrip = new ChannelStrip(this, builder);
		buttonPanel = new ButtonPanel(this, builder);

		// set scrollbar height for master panel to align to height of channel
		// strips
		int channelHeight = masterStrip.channelstrip.getHeight();
		workareaScrollPane.getVerticalScrollBar().setUnitIncrement(
				channelHeight / 2);
		workareaScrollPane.getVerticalScrollBar().setBlockIncrement(
				channelHeight);
		// adjust horizontal scrollbar with arbitrary sensible values
		allRegionsScrollPane.getHorizontalScrollBar().setUnitIncrement(50);
		allRegionsScrollPane.getHorizontalScrollBar().setBlockIncrement(300);

		setupAllRegions();

	}

	// ENGINE

	protected void createEngine() {
		if (panAutoHandler != null) return;
		player.init();
		player.setTempo(defaultTempo);
		player.getState().getAutomationEventDispatcher().addListener(this);
		player.getState().getAudioEventDispatcher().addListener(this);
		// get automation handlers
		panAutoHandler = AutomationManager.getHandler(AutomationPan.class);
		volAutoHandler = AutomationManager.getHandler(AutomationVolume.class);
		// create 1 initial track
		for (int i = 0; i < 1; i++) {
			player.addAudioTrack();
		}
	}

	private boolean inited = false;

	/** after creating the GUI and creating the engine, start the application */
	protected void start() {
		if (!inited) {
			buttonPanel.defaults();
			// 3 start-up tracks
			while (player.getMixer().getTrackCount() < 3) {
				player.addAudioTrack();
			}
			// create a 4-bar loop
			AudioState state = globals.getState();
			globals.getPlayer().setLoopSamples(0,
					state.beat2sample(4 * state.getBeatsPerMeasure()));
			updateTracks();
			inited = true;
		}
	}

	/** called to stop the player -- nothing to do? */
	protected void stop() {
		// nothing
	}

	/**
	 * Remove all dependencies, stop audio engine, etc.
	 */
	protected void close() {
		effectManager.close();
		for (ChannelStrip s : strips) {
			s.close();
		}
		if (player != null) {
			// debug("stopping guiTimer");
			// guiTimer.stop();
			debug("closing player");
			player.close();
			player = null;
		}
		if (DEBUG) {
			debug(NAME + ": close done");
		}
	}

	/**
	 * Patch the allRegions panel to display
	 */
	private void setupAllRegions() {
		allRegions.setBackground(Color.white);
		allRegions.setOpaque(true);
		allRegions.setPaintListener(this);
	}

	/**
	 * For debugging: print a control's parent hierarchy
	 * 
	 * @param c the component to print
	 */
	@SuppressWarnings("unused")
	private void printParentHierarchy(Component c) {
		int level = 0;
		while (c != null) {
			String s = "";
			for (int i = 0; i < level; i++)
				s += " ";
			Debug.debug(s + c.toString());
			c = c.getParent();
			level++;
		}
	}

	/**
	 * For debugging: print a control's child hierarchy
	 * 
	 * @param c the component to print
	 */
	@SuppressWarnings("unused")
	private void printChildHierarchy(Container c, int level) {
		String s = "";
		for (int x = 0; x < level; x++)
			s += " ";
		Debug.debug(s + c.toString());
		for (int i = 0; i < c.getComponentCount(); i++) {
			Component comp = c.getComponent(i);
			if (comp instanceof Container) {
				printChildHierarchy((Container) comp, level + 1);
			} else {
				Debug.debug(s + comp.toString());
			}
		}
	}

	/**
	 * @return the masterPanel
	 */
	public MPanel getMasterPanel() {
		return masterPanel;
	}

	/**
	 * @return the globals
	 */
	public Globals getGlobals() {
		return globals;
	}

	/**
	 * @return the workarea
	 */
	public MPanel getWorkarea() {
		return workarea;
	}

	/**
	 * update the channel strips and region areas to hold all tracks. If not in
	 * swing thread, do it asynchronously.
	 */
	void updateTracks() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					updateTracksSwingThread();
				}
			});
		} else {
			updateTracksSwingThread();
		}
	}

	/** update the channel strips and region areas to hold all tracks */
	protected void updateTracksSwingThread() {
		if (DEBUG) debug(">updateTracksSwingThread():");
		// just a precaution: never have less than 1 tracks
		if (player.getMixer().getTrackCount() == 0) {
			player.addAudioTrack();
			if (DEBUG) debug(" adding an audio track");
		}
		List<AudioTrack> tracks = player.getMixer().getTracks();
		int count = tracks.size();
		if (DEBUG) debug(" validating " + count + " audio tracks");
		// first validate the ChannelStrip list
		for (int i = 0; i < count; i++) {
			boolean found = false;
			if (i < strips.size()) {
				ChannelStrip cs = strips.get(i);
				AudioTrack t = tracks.get(i);
				if (cs.getAudioTrack() != t) {
					int correctIndex = getChannelStripIndex(t, i);
					if (correctIndex >= 0) {
						if (DEBUG) {
							debug(" moving ChannelStrip from position "
									+ correctIndex + " to position " + i);
						}
						strips.add(i, strips.remove(correctIndex));
						found = true;
					}
				}
			}
			if (!found) {
				// create a new channel strip and insert it to the channel
				// strips array
				ChannelStrip newStrip = new ChannelStrip(masterStrip,
						tracks.get(i));
				strips.add(i, newStrip);
				if (DEBUG) debug(" adding ChannelStrip at index " + i);
			}
		}
		// remove extra old strips
		if (DEBUG) {
			if (strips.size() > count) {
				debug(" removing " + (strips.size() - count)
						+ " excess ChannelStrip objects");
			}
		}
		while (strips.size() > count) {
			strips.remove(strips.size() - 1).close();
		}
		assert (strips.size() == count);

		// now update the components in the channel strip area
		for (int i = 0; i < count; i++) {
			boolean found = false;
			// remove components that are not a panel
			while (i < allChannelstrips.getComponentCount()
					&& !(allChannelstrips.getComponent(i) instanceof MPanel)) {
				allChannelstrips.remove(i);
			}
			ChannelStrip cs = strips.get(i);
			if (i < allChannelstrips.getComponentCount()) {
				Component panel = allChannelstrips.getComponent(i);
				if (cs.channelstrip != panel) {
					int correctIndex = getComponentIndex(allChannelstrips,
							cs.channelstrip, i);
					if (correctIndex >= 0) {
						allChannelstrips.remove(correctIndex);
						allChannelstrips.add(cs.channelstrip, i);
						if (DEBUG) {
							debug(" moving strip panel from index "
									+ correctIndex + " to index " + i);
						}
						found = true;
					}
				}
			}
			if (!found) {
				// add the channel strip to allChannelstrips
				allChannelstrips.add(cs.channelstrip, i);
				if (DEBUG) debug(" adding strip panel at index " + i);
			}
		}
		// remove extra excess components from this container
		if (DEBUG) {
			if (allChannelstrips.getComponentCount() > count) {
				debug(" removing "
						+ (allChannelstrips.getComponentCount() - count)
						+ " excess strip panels");
			}
		}
		while (allChannelstrips.getComponentCount() > count) {
			allChannelstrips.remove(allChannelstrips.getComponentCount() - 1);
		}

		// now update the components in the regions area
		for (int i = 0; i < count; i++) {
			boolean found = false;
			// remove components that are not a panel
			while (i < allRegions.getComponentCount()
					&& !(allRegions.getComponent(i) instanceof TrackPanel)) {
				allRegions.remove(i);
			}
			ChannelStrip cs = strips.get(i);
			if (i < allRegions.getComponentCount()) {
				Component panel = allRegions.getComponent(i);
				if (cs.trackPanel != panel) {
					int correctIndex = getComponentIndex(allRegions,
							cs.trackPanel, i);
					if (correctIndex >= 0) {
						allRegions.remove(correctIndex);
						allRegions.add(cs.trackPanel, i);
						if (DEBUG) {
							debug(" moving trackPanel from index "
									+ correctIndex + " to index " + i);
						}
						found = true;
					}
				}
			}
			if (!found) {
				// add the trackPanel to allRegions
				allRegions.add(cs.trackPanel, i);
				if (DEBUG) debug(" adding trackPanel at index " + i);
			}
		}
		// remove extra excess components from this container
		if (DEBUG) {
			if (allRegions.getComponentCount() > count) {
				debug(" removing " + (allRegions.getComponentCount() - count)
						+ " excess trackPanels");
			}
		}
		while (allRegions.getComponentCount() > count) {
			allRegions.remove(allRegions.getComponentCount() - 1);
		}

		// now fix up the audio tracks (if not already done)
		for (int i = 0; i < count; i++) {
			ChannelStrip cs = strips.get(i);
			cs.setAudioTrack(tracks.get(i));
			// also fix the close button (invisible if only one track)
			if (cs.remove != null) {
				cs.remove.setVisible(count > 1);
			}
		}

		// eventually re-layout the components
		masterPanel.validate();
		if (DEBUG) debug("<updateTracksSwingThread()");
	}

	/**
	 * get a list of components
	 * 
	 * @param container
	 */
	@SuppressWarnings("unused")
	private void getComponents(MPanel container, List<Component> list) {
		list.clear();
		for (int i = 0; i < container.getComponentCount(); i++) {
			list.add(container.getComponent(i));
		}
	}

	/**
	 * @param t the track to search for in the channel strips
	 * @param startIndex the start index where to search in the strips list
	 * @return the index in strips of the channel strip corresponding to the
	 *         track, or -1 if not found
	 */
	private int getChannelStripIndex(AudioTrack t, int startIndex) {
		for (int i = startIndex; i < strips.size(); i++) {
			if (strips.get(i).getAudioTrack() == t) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * @param container the container to search for the specified component
	 * @param comp the component to find
	 * @param startIndex the start index where to search in the strips list
	 * @return the index in the container, or -1 if not found
	 */
	private int getComponentIndex(Container container, Component comp,
			int startIndex) {
		for (int i = startIndex; i < container.getComponentCount(); i++) {
			if (container.getComponent(i) == comp) {
				return i;
			}
		}
		return -1;
	}

	/** called by the button panel if the plus button is pressed */
	void onAddTrackClick() {
		player.addAudioTrack();
		updateTracks();
	}

	/** called by the channelStrip if the x button is pressed */
	void onRemoveTrackClick(ChannelStrip cs) {
		if (globals.getPlayer().getMixer().getTrackCount() <= 1) {
			// do not allow removing the last track
			return;
		}
		if (globals.confirm("Do you really want to remove this track?")) {
			player.removeAudioTrack(cs.getAudioTrack());
			updateTracks();
			System.gc();
		}
	}

	// number of samples in a quarter beat
	private final static int QUARTER_BEAT = 42336;
	private static final String SOUNDS_URL = "http://www.12fb.com/florian/nervesound/sounds/";

	protected void loadDefaultSong() {
		AudioMixer mixer = player.getMixer();
		mixer.clear();
		try {
			player.setTempo(62.5);
			String ext = ".ogg";
			debug("Loading track 1 with bass");
			AudioTrack at = player.addAudioTrack();
			globals.addRegion(at, new URL(SOUNDS_URL + "Bass1" + ext), 0);
			globals.addRegion(at, new URL(SOUNDS_URL + "Bass2" + ext),
					QUARTER_BEAT * 4);
			globals.addRegion(at, new URL(SOUNDS_URL + "Bass1" + ext),
					QUARTER_BEAT * 8);
			globals.addRegion(at, new URL(SOUNDS_URL + "Bass3" + ext),
					QUARTER_BEAT * 12);
			globals.addRegion(at, new URL(SOUNDS_URL + "Bass2" + ext),
					QUARTER_BEAT * 16);
			debug("Loading track 2 with drums");
			at = player.addAudioTrack();
			URL url = new URL(SOUNDS_URL + "Drum" + ext);
			globals.addRegion(at, url, 0);
			globals.addRegion(at, url, QUARTER_BEAT * 4);
			globals.addRegion(at, url, QUARTER_BEAT * 8);
			globals.addRegion(at, url, QUARTER_BEAT * 12);
			globals.addRegion(at, url, QUARTER_BEAT * 16);
			AudioRegion drums = ((AudioRegion) at.getPlaylist().getObject(3));
			drums.setDuration(QUARTER_BEAT * 2);
			debug("Loading track 3 with voice and FX");
			at = player.addAudioTrack();
			globals.addRegion(at, new URL(SOUNDS_URL + "texte" + ext),
					QUARTER_BEAT * 4 - (QUARTER_BEAT / 2));
			globals.addRegion(at, new URL(SOUNDS_URL + "texte" + ext),
					QUARTER_BEAT * 10);
			globals.addRegion(at, new URL(SOUNDS_URL + "slave" + ext),
					QUARTER_BEAT * 20);
			AudioRegion lastText = ((AudioRegion) at.getPlaylist().getObject(1));
			lastText.setAudioFileOffset(lastText.getAudioFileOffset()
					+ (QUARTER_BEAT * 4));
			lastText.setDuration(QUARTER_BEAT * 4);
			updateTracks();
			player.setLoopSamples(QUARTER_BEAT * 4, QUARTER_BEAT * 4);
			buttonPanel.displayLoop(false);
		} catch (Exception e) {
			error(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.util.FatalExceptionListener#fatalExceptionOccured(java.lang.Throwable,
	 *      java.lang.String)
	 */
	public void fatalExceptionOccured(Throwable t, String context) {
		Debug.displayErrorDialogAsync(masterPanel, t, context);
	}

    public void showMessage(String title, String context)
    {
        Debug.displayInfoDialogAsync(masterPanel, title, context);
    }

    /*
      * (non-Javadoc)
      *
      * @see com.mixblendr.audio.AudioListener#audioFileDownloadError(com.mixblendr.audio.AudioFile,
      *      java.lang.Throwable)
      */
	public void audioFileDownloadError(AudioFile file, Throwable t) {
		String context = "while downloading " + file.getName();
		if (t instanceof OutOfMemoryError) {
			context += ":\nNot enough memory available to fully download the audio clip.";
			t = null;
		}
		Debug.displayErrorDialogAsync(masterPanel, t, context);
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
		// nothing to do
	}

	/**
	 * Run the program in stand-alone mode.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Start " + Main.NAME + " " + Main.VERSION);
		Performance.setDefaultUI();
		Performance.preload();
		// Schedule a job for the event-dispatching thread:
		// creating and showing this application's GUI.
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					final Main main = new Main();
					main.createGUI();
					main.createEngine();
					main.start();
					// Create and set up the window.
					final JFrame frame = new JFrame(NAME + " " + VERSION);
					frame.setContentPane(main.getMasterPanel());
					WindowAdapter windowAdapter = new WindowAdapter() {
						@Override
						public void windowClosing(WindowEvent we) {
							main.close();
							frame.dispose();
							System.out.println("End of mixblendr version "
									+ VERSION);
						}
					};
					frame.addWindowListener(windowAdapter);
					// Display the window.
					frame.pack();
					frame.setVisible(true);
				} catch (Exception e) {
					error(e);
					System.exit(1);
				}
			}
		});
	}

	// interface AutomationListener

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AutomationListener#automationEvent(com.mixblendr.audio.AudioTrack,
	 *      com.mixblendr.audio.AutomationObject)
	 */
	public void automationEvent(AudioTrack track, AutomationObject ao) {
		int index = track.getIndex();
		if (index >= strips.size()) return;
		ChannelStrip strip = strips.get(index);
		if (ao instanceof AutomationPan) {
			strip.displayPan();
		} else if (ao instanceof AutomationVolume) {
			strip.displayVolume();
		}
	}

	private Rectangle playbackRepaintArea = new Rectangle();

	private static final int PLAYBACK_SCROLL_THRESHOLD_PIXELS = 50;

	/** called by the ButtonPanel upon the display timer */
	void handleNewPlaybackPosition(long newSamplePosition) {
		if (globals != null && !globals.isDraggingMouse() && player != null) {
			if (player.isStarted()) {
				// if during playback, approaching the right border, scroll
				int x = globals.getScale().sample2pixel(newSamplePosition);
				int allX = globals.getAllRegionsScrollX();
				int allW = globals.getAllRegionsScrollWidth();
				if (x > allX + allW - PLAYBACK_SCROLL_THRESHOLD_PIXELS) {
					// set new x position of the viewport
					int newX = x - PLAYBACK_SCROLL_THRESHOLD_PIXELS;
					int lastPixel = globals.getAllRegionsViewPort().getView().getWidth();
					if (x > lastPixel) {
						// stop playback
						globals.stopPlayback();
					}
					if (newX > lastPixel - allW) {
						newX = lastPixel - allW;
					}
					globals.getAllRegionsViewPort().setViewPosition(
							new Point(newX, 0));
				} else if (x < allX) {
					globals.makeVisible(newSamplePosition);
				}
			} else {
				globals.makeVisible(newSamplePosition);
			}
		}
		if (positionGrid != null) {
			positionGrid.repaintPlayPosition(newSamplePosition,
					playbackRepaintArea);
			if (playbackRepaintArea.width > 0 && allRegions != null) {
				if (DEBUG_REPAINT) {
					debug("Main: allRegion.repaint(x=" + playbackRepaintArea.x
							+ " w=" + playbackRepaintArea.width + ")");
				}
				allRegions.repaint(playbackRepaintArea.x, 0,
						playbackRepaintArea.width, allRegions.getHeight());
			}
		}
	}

	/*
	 * paints the allRegion's position grid
	 * 
	 * @see com.mixblendr.skin.MPanel.PaintListener#panelBeforePaint(com.mixblendr.skin.MPanel,
	 *      java.awt.Graphics, java.awt.Rectangle)
	 */
	public void panelBeforePaint(MPanel panel, Graphics g, Rectangle clip) {
		if (DEBUG_REPAINT) {
			debug("Main: allRegion paint background(x=" + clip.x + ", w="
					+ clip.width + ")");
		}
		g.setColor(panel.getBackground());
		g.fillRect(clip.x, clip.y, clip.width, clip.height);
		positionGrid.paintGrid(panel, g, clip, false);
	}

	/**
	 * paint the playback position cursor
	 * 
	 * @see com.mixblendr.skin.MPanel.PaintListener#panelAfterPaint(com.mixblendr.skin.MPanel,
	 *      java.awt.Graphics, java.awt.Rectangle)
	 */
	public void panelAfterPaint(MPanel panel, Graphics g, Rectangle clip) {
		int pixel = globals.getScale().sample2pixel(
				positionGrid.getPlayPosition());
		if (pixel + 1 >= clip.x && pixel <= clip.x + clip.width) {
			g.setColor(CURSOR_LINE_COLOR);
			g.drawLine(pixel, clip.y, pixel, clip.y + clip.height);
		}
	}

	private boolean ignorePlusEvent = false;
	private boolean ignoreMinusEvent = false;
	private boolean ignoreZeroEvent = false;

	/**
	 * Global key listener. For apps, can be done using
	 * Toolkit.getDefaultToolkit().addAWTEventListener(this,AWTEvent.KEY_EVENT_MASK),
	 * but that doesn't work in applet, so a global key listener (i.e. this) is
	 * registered as KeyListener to all focusable components in ButtonPanel.
	 * 
	 * @see java.awt.event.AWTEventListener#eventDispatched(java.awt.AWTEvent)
	 */
	public void eventDispatched(AWTEvent event) {
		if (event.getID() >= KeyEvent.KEY_FIRST
				&& event.getID() <= KeyEvent.KEY_LAST
				&& (event instanceof KeyEvent)) {
			KeyEvent KE = (KeyEvent) event;
			if (DEBUG) {
				debug("key event: " + KE);
			}
			int vk = KE.getKeyCode();
			char c = KE.getKeyChar();
			if (c == ' ') {
				if (event.getID() == KeyEvent.KEY_PRESSED) {
					globals.togglePlayback();
				}
				KE.consume();
			} else if (c == 's') {
				if (event.getID() == KeyEvent.KEY_PRESSED) {
					if (buttonPanel != null && buttonPanel.snap != null) {
						buttonPanel.snap.setSelected(
								!buttonPanel.snap.isSelected(), true);
					}
				}
				KE.consume();
			} else if (c == 'c') {
				if (event.getID() == KeyEvent.KEY_PRESSED) {
					if (buttonPanel != null) {
						buttonPanel.toggleTool();
					}
				}
				KE.consume();
			} else if (vk == KeyEvent.VK_ADD) {
				ignorePlusEvent = (event.getID() == KeyEvent.KEY_PRESSED);
				if (ignorePlusEvent) {
					buttonPanel.onZoomIn();
				}
				KE.consume();
			} else if (vk == KeyEvent.VK_SUBTRACT) {
				ignoreMinusEvent = (event.getID() == KeyEvent.KEY_PRESSED);
				if (ignoreMinusEvent) {
					buttonPanel.onZoomOut();
				}
				KE.consume();
			} else if (vk == KeyEvent.VK_NUMPAD0) {
				ignoreZeroEvent = (event.getID() == KeyEvent.KEY_PRESSED);
				if (ignoreZeroEvent) {
					buttonPanel.onRewindToBeginning();
				}
				KE.consume();
			} else if (vk == KeyEvent.VK_LEFT
					&& event.getID() == KeyEvent.KEY_PRESSED) {
				buttonPanel.onWindToMeasure(false);
				KE.consume();
			} else if (vk == KeyEvent.VK_RIGHT
					&& event.getID() == KeyEvent.KEY_PRESSED) {
				buttonPanel.onWindToMeasure(true);
				KE.consume();
			} else if (c == '+' && ignorePlusEvent) {
				KE.consume();
			} else if (c == '-' && ignoreMinusEvent) {
				KE.consume();
			} else if (c == '0' && ignoreZeroEvent) {
				KE.consume();
			}
		}
	}

	/**
	 * Called when the button panel's global key listener fires a key. All key
	 * handling is relayed to the method eventDispatched().
	 * 
	 * @see java.awt.event.KeyListener#keyPressed(java.awt.event.KeyEvent)
	 */
	public void keyPressed(KeyEvent e) {
		eventDispatched(e);
	}

	/**
	 * Called when the button panel's global key listener fires a key. All key
	 * handling is relayed to the method eventDispatched().
	 * 
	 * @see java.awt.event.KeyListener#keyReleased(java.awt.event.KeyEvent)
	 */
	public void keyReleased(KeyEvent e) {
		eventDispatched(e);
	}

	/**
	 * Called when the button panel's global key listener fires a key. All key
	 * handling is relayed to the method eventDispatched().
	 * 
	 * @see java.awt.event.KeyListener#keyTyped(java.awt.event.KeyEvent)
	 */
	public void keyTyped(KeyEvent e) {
		eventDispatched(e);
	}

    public void setUrl(String url)
    {
        player.getOutput().setUrl(url);
    }

    private String redirectUrl;
    public void setRedirectUrl(String redirectUrl)
    {
        this.redirectUrl = redirectUrl;
    }

    public void  showProgressDialog() {
        String message = "Saving file...plase wait";
        String note ="Mixing tracks";
        String title = "Saving...";
        UIManager.put("ProgressMonitor.progressText", title);
        int min = 0;
        int max = 100;
//        progressMonitor = new ProgressMonitor(masterPanel,message, note, min, max);

//        JOptionPane pane;
//
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                //JOptionPane.showMessageDialog(masterPanel, "saving", "xxx",
                 //       JOptionPane.INFORMATION_MESSAGE);
//                String message = "Saving file...plase wait";
//                String note ="Mixing tracks";
//                String title = "Saving...";
//                UIManager.put("ProgressMonitor.progressText", title);
//                int min = 0;
//                int max = 100;
//                 progressMonitor = new ProgressMonitor(masterPanel,message, note, min, max);
//                JOptionPane pane = new JOptionPane();
//                pane.createDialog("xxx");
                isPublishing = true;
                isPublishSuccess = false;
                final JPanel panel = new JPanel();
                //panel.setLayout( new BoxLayout(panel, BoxLayout.Y_AXIS));
                panel.setLayout( new BorderLayout(10,10));
                panel.setPreferredSize(new Dimension(300,75));
                //rderLayout(20,20));
                JLabel label = new JLabel("      Uploading - this may take a few minutes.");
                label.setFont( new Font("Arial", Font.PLAIN, 12));
                JButton btnOK = new JButton("OK");
                btnOK.setVisible(false);

                JLabel empty = new JLabel("   ");
                //panel.add(label);
                panel.add(label,BorderLayout.CENTER,0);
                panel.add(btnOK, BorderLayout.PAGE_END,1);

                Rectangle r = masterPanel.getBounds();
                publishingDialog = new JDialog((Frame)null,
                                             "Publishing...",
                                             true);
                publishingDialog.setContentPane(panel);
                //publishingDialog.setPreferredSize(new Dimension(300,200));
                publishingDialog.setBounds((int)r.getCenterX(),(int)r.getCenterY(), 300,200);
                //
                publishingDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                publishingDialog.setResizable(false);


//                final JOptionPane optionPane = new JOptionPane(
//                                "Uploading... Please wait",
//                                JOptionPane.INFORMATION_MESSAGE);
//
//
//
//                optionPane.addPropertyChangeListener(
//                    new PropertyChangeListener() {
//                        public void propertyChange(PropertyChangeEvent e) {
//                            String prop = e.getPropertyName();
//
//                            if (publishingDialog.isVisible()
//                             && (e.getSource() == optionPane)
//                             && (prop.equals(JOptionPane.VALUE_PROPERTY) ||
//                                 prop.equals(JOptionPane.INPUT_VALUE_PROPERTY))) {
//                                //If you were going to check something
//                                //before closing the window, you'd do
//                                //it here.
//                                publishingDialog.setVisible(false);
//                            }
//                        }
//                    });
                publishingDialog.pack();
                publishingDialog.setVisible(true);
            }
        });

    }

//    public void setProgressDialogMessage(String message)
//    {
//        if (publishingDialog != null)
//        {
//            JPanel panel =(JPanel) publishingDialog.getContentPane();
//            Component component = panel.getComponent(0);
//            if (component instanceof JLabel)
//            {
//                JLabel label = (JLabel)component;
//                label.setText(message);
//            }
//
//        }
//    }

    public void setSuccess()
    {
          if (publishingDialog != null)
          {
                isPublishSuccess = true;      
                JPanel panel = (JPanel)publishingDialog.getContentPane();
                JButton btnOK = (JButton)panel.getComponent(1);
                btnOK.addActionListener(this);

                btnOK.setVisible(true);

                JLabel label = (JLabel)panel.getComponent(0);
                label.setText("      Publishing successful!");
          }
    }

    public void setFailed()
    {
        if (publishingDialog != null)
        {
            JPanel panel = (JPanel)publishingDialog.getContentPane();
            JButton btnOK = (JButton)panel.getComponent(1);
            btnOK.setVisible(true);
            btnOK.addActionListener(this);

             JLabel label = (JLabel)panel.getComponent(0);
             label.setText("      Publishing failed!");
        }

    }
    public void hideProgressDialog()
    {
        if (isPublishing && publishingDialog != null){
            publishingDialog.dispose();
            isPublishing = false;
        }
    }

    public void actionPerformed(ActionEvent e)
    {
        if (publishingDialog != null)
        {
            if (isPublishSuccess)
            {

                try
                {
                    publishingDialog.dispose();
                    close();
                    URL url = new URL(redirectUrl);
                    getApplet().getAppletContext().showDocument(url);
                }
                catch (MalformedURLException e1)
                {
                    
                }



            }
            else
            {
                publishingDialog.dispose();
            }
        }
    }
}
