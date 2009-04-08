/**
 *
 */
package com.mixblendr.gui.main;

import static com.mixblendr.util.Debug.debug;
import static com.mixblendr.util.Utils.getBaseName;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.KeyListener;
import java.net.URL;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.mixblendr.audio.AudioFile;
import com.mixblendr.audio.AudioPlayer;
import com.mixblendr.audio.AudioRegion;
import com.mixblendr.audio.AudioState;
import com.mixblendr.audio.AudioTrack;
import com.mixblendr.util.Debug;
import com.mixblendr.util.Utils;

/**
 * References to global classes
 * 
 * @author Florian Bomers
 */
public class Globals implements ChangeListener {

	private static final boolean DEBUG = false;
	final static boolean DEBUG_DRAG_SCROLL = false;

	private GraphScale scale;

	private RegionMouseOperation operation;

	private SnapToBeats snapToBeats;

	private AudioState state;

	private AudioPlayer player;

	private RegionSelectionManager selectionManager;

	private JComponent masterPanel;
    private Globals globals;

    // if the user is currently doing something with the mouse
	private boolean draggingMouse;

	/** cache the all regions view port, set by Main */
	private JViewport allRegionsViewPort;
	private int allRegionsScrollX;
	private int allRegionsScrollWidth;

    private boolean isPublishDone = false;

    private String fileName;

    public String getFileName()
    {
        return fileName;
    }

    public Globals getGlobals()
    {
        return globals;
    }

    public synchronized void setGlobals(Globals globals)
    {
        this.globals = globals;
    }


    /**
	 * a global key listener that all focusable controls (except text fields
	 * that only receive temporary focus) should set
	 */
	private KeyListener globalKeyListener;

	/**
	 * Create a new globals object, instanciating the scale and snaptobeats
	 * objects.
	 * 
	 * @param player the player object to be set
	 */
	public Globals(AudioPlayer player) {
		super();
		setPlayer(player);
		scale = new GraphScale();
		operation = RegionMouseOperation.SCISSOR;
		snapToBeats = new SnapToBeats(state, scale);
		selectionManager = new RegionSelectionManager();
	}

	/**
	 * Return the current mouse action, i.e. one of the RegionMouseOperation's
	 * constants.
	 * 
	 * @return the operation
	 */
	public RegionMouseOperation getOperation() {
		return operation;
	}

	/**
	 * @param operation the operation to set
	 */
	public void setOperation(RegionMouseOperation operation) {
		this.operation = operation;
	}

	/**
	 * @return the actual instance of the scale
	 */
	public GraphScale getScale() {
		return scale;
	}

	/**
	 * @return the actual instance of snapToBeats
	 */
	public SnapToBeats getSnapToBeats() {
		return snapToBeats;
	}

	/**
	 * @return the state
	 */
	public AudioState getState() {
		return state;
	}

	/**
	 * Set the global player instance. This should only be done once.
	 * 
	 * @param player the player to set
	 */
	void setPlayer(AudioPlayer player) {
		this.player = player;
		if (player != null) {
			this.state = player.getState();
		}
	}

	/**
	 * @return the player
	 */
	public AudioPlayer getPlayer() {
		return player;
	}

	/**
	 * @return the selectionManager
	 */
	public RegionSelectionManager getSelectionManager() {
		return selectionManager;
	}

	/**
	 * @return the masterPanel
	 */
	public JComponent getMasterPanel() {
		return masterPanel;
	}

	/**
	 * @param masterPanel the masterPanel to set
	 */
	void setMasterPanel(JComponent masterPanel) {
		this.masterPanel = masterPanel;
	}

	/**
	 * a global key listener that all focusable controls (except text fields
	 * that only receive temporary focus) should set
	 * 
	 * @return the globalKeyListener
	 */
	public KeyListener getGlobalKeyListener() {
		return globalKeyListener;
	}

	/**
	 * @param globalKeyListener the globalKeyListener to set
	 */
	void setGlobalKeyListener(KeyListener globalKeyListener) {
		this.globalKeyListener = globalKeyListener;
	}

	/**
	 * Add a region from the given URL at the position in samples. This method
	 * will not update its track panel, should call update() afterwards.
	 * 
	 * @param pos the insert position, or -1 to insert at the end.
	 */
	public AudioRegion addRegion(AudioTrack at, URL url, long pos) {
		return addRegion(at, url, pos, -1);
	}

	/**
	 * Add a region from the given URL at the position in samples. This method
	 * will not update its track panel, should call update() afterwards.
	 * 
	 * @param pos the insert position, or -1 to insert at the end.
	 * @param durationInSamples the length in samples, if known, or -1 otherwise
	 */
	public AudioRegion addRegion(AudioTrack at, URL url, long pos,
			long durationInSamples) {
		if (at == null) return null;
		if (DEBUG) {
			debug("-creating AudioFile from URL, filename: "
					+ getBaseName(url.getPath()));
		}
		AudioFile af = player.getFactory().getAudioFile(url);
		if (pos < 0) {
			pos = at.getDurationSamples();
		}
		if (DEBUG) {
			debug("-added region at time "
					+ player.getState().sample2seconds(pos) + "s");
		}
		return at.addRegion(af, pos, durationInSamples);
	}

	/**
	 * @return the allRegionsViewPort
	 */
	JViewport getAllRegionsViewPort() {
		return allRegionsViewPort;
	}

	/**
	 * Called by Main
	 * 
	 * @param allRegionsViewPort the allRegionsViewPort to set
	 */
	void setAllRegionsViewPort(JViewport allRegionsViewPort) {
		if (this.allRegionsViewPort != null) {
			this.allRegionsViewPort.removeChangeListener(this);
		}
		this.allRegionsViewPort = allRegionsViewPort;
		allRegionsViewPort.addChangeListener(this);
		stateChanged(null);
	}

	/**
	 * Get the current view width of the all regions scroll area
	 * 
	 * @return the allRegionsScrollWidth
	 */
	int getAllRegionsScrollWidth() {
		return allRegionsScrollWidth;
	}

	/**
	 * Get the current x position of the scrolling view of the all regions
	 * scroll area
	 * 
	 * @return the allRegionsScrollX
	 */
	int getAllRegionsScrollX() {
		return allRegionsScrollX;
	}

	/**
	 * Return if the user is currently dragging the mouse interactively. If this
	 * is true, autoscroll during playback is disabled.
	 */
	public boolean isDraggingMouse() {
		return draggingMouse;
	}

	/**
	 * @param draggingMouse the draggingMouse to set
	 */
	void setDraggingMouse(boolean draggingMouse) {
		this.draggingMouse = draggingMouse;
	}

	/**
	 * Called by the allRegions view port when its size or position changes
	 * 
	 * @see javax.swing.event.ChangeListener#stateChanged(javax.swing.event.ChangeEvent)
	 */
	public void stateChanged(ChangeEvent e) {
		if (allRegionsViewPort != null) {
			allRegionsScrollWidth = allRegionsViewPort.getExtentSize().width;
			allRegionsScrollX = allRegionsViewPort.getViewPosition().x;
		}
	}

	/**
	 * Start playback from the current position. On error, display a dialog with
	 * the error message.
	 */
	public void startPlayback() {
		if (player == null) return;
		try {
            //player.getOutput().setPlaying(true);
            player.start();
        } catch (Throwable t) {
			Debug.displayErrorDialogAsync(allRegionsViewPort, t,
					"when starting playback");
		}
	}

	/**
	 * Stop playback without resetting the playback position. On error, display
	 * a dialog with the error message.
	 */
	public void stopPlayback() {
		stopPlayback(false);
	}

	/**
	 * Stop playback without resetting the playback position. On error, display
	 * a dialog with the error message.
	 * 
	 * @param immediate if true, stop will be done immediately (should only
	 *            happen at exit of the program), otherwise fade it out gently.
	 */
	void stopPlayback(boolean immediate) {
		if (player == null) return;
		try {
			player.stop(immediate);
            //player.getOutput().setPlaying(false);

        } catch (Throwable t) {
			Debug.displayErrorDialogAsync(allRegionsViewPort, t,
					"when stopping playback");
		}
	}

	/**
	 * Start or stop playback, depending on the current playback state. On
	 * error, display a dialog with the error message.
	 */
	public void togglePlayback() {

        if (player == null) return;

        if (isPublishDone) return;
        
        if (player.isStarted()) {
			stopPlayback();
        } else {
            startPlayback();
		}
	}


    public void startSaveFile()
    {
        if (isPublishDone) {
            Debug.displayErrorDialogAsync(allRegionsViewPort, null, "Publishing has been already done");
        }
        else {
            if (getPlayer().getOutput().IsPlaying()) {
                Debug.displayErrorDialogAsync(allRegionsViewPort, null, "Can't publish a track during playing");
                return;
            }



            if (player.getFactory().getTrackNumber() ==0 )
            {
                Debug.displayErrorDialogAsync(allRegionsViewPort, null, "Nothing to save");
                return;
            }

            double minStartTime = player.getMixer().getStartTimeSec();
            if (minStartTime > 60)
            {
                Debug.displayErrorDialogAsync(allRegionsViewPort, null, "Cannot find any audio for a period of 1 minute. To publish your track, please move it to the beginning of the timeline.");
                return;
            }


            int dialogresult = JOptionPane.showConfirmDialog(allRegionsViewPort, "Are you sure you want to publish this track? You won't be able to make any further edits after you publish it.", "Publishing track", JOptionPane.YES_NO_OPTION);
            if (dialogresult == JOptionPane.YES_OPTION)
            {
                String result = JOptionPane.showInputDialog(allRegionsViewPort, "Please enter the name of the track", "my_track.ogg");
                if (result != null && !result.equals(""))
                {


                    if (result.indexOf(".ogg") == -1)
                    {
                        result += ".ogg";
                    }
                    getPlayer().getOutput().setFileName(result);

                    isPublishDone = true;

                   getPlayer().getOutput().close(); 

                   //player.setGlobals(this);
                   //player.getOutput().setSaving(true);
                    try
                    {
                        player.start();
                    }
                    catch (Exception e)
                    {
                        Debug.displayErrorDialogAsync(allRegionsViewPort, e, "Error when publishing");
                    }

                }

            }


        }

    }

    // autoscroll support for dragging and for the position grid

	public final static int AUTO_SCROLL_PIXEL = 40;

	/**
	 * Calculate the number of pixels to move, given the current (absolute)
	 * mouse X coordinate
	 * 
	 * @param mouseX the absolute mouse x coordinate (where 0 corresponds to
	 *            sample 0)
	 * @return the number of pixels to move, or 0
	 */
	int getAutoScrollPixels(int mouseX, boolean isViewPortChild) {
		// auto scroll
		if (allRegionsViewPort != null) {
			int viewPortXPos = mouseX;
			if (isViewPortChild) {
				viewPortXPos -= allRegionsScrollX;
			}
			if (DEBUG_DRAG_SCROLL) {
				debug("TrackPanel: autoScroll " + mouseX + "   viewPortX="
						+ viewPortXPos);
			}
			int movePixels = 0;
			if (viewPortXPos < AUTO_SCROLL_PIXEL) {
				movePixels = viewPortXPos - AUTO_SCROLL_PIXEL;
			} else if (viewPortXPos > allRegionsScrollWidth - AUTO_SCROLL_PIXEL) {
				movePixels = viewPortXPos
						- (allRegionsScrollWidth - AUTO_SCROLL_PIXEL);
			}
			if (movePixels != 0) {
				// scale
				// debug("orig movePixels=" + movePixels);
				int sig = (movePixels < 0) ? -1 : 1;
				if (sig < 0) {
					movePixels = -movePixels;
				}
				if (movePixels > 2 * AUTO_SCROLL_PIXEL / 3) {
					movePixels = (movePixels - (AUTO_SCROLL_PIXEL / 4)) * 2
							* sig;
					// debug(" ->3: " + movePixels);
				} else if (movePixels < AUTO_SCROLL_PIXEL / 4) {
					movePixels = sig;
					// debug(" ->1: " + movePixels);
				} else {
					movePixels = (movePixels - (AUTO_SCROLL_PIXEL / 4)) * sig;
					// debug(" ->2: " + movePixels);
				}
			}
			return movePixels;
		}
		return 0;
	}

	/**
	 * Scroll the scrollpane by movePixels.
	 * 
	 * @param movePixels the number of pixels to move, negative or positive
	 * @return the actual pixels moved
	 */
	int scroll(int movePixels) {
		if (movePixels != 0) {
			int newX = allRegionsScrollX + movePixels;
			if (DEBUG_DRAG_SCROLL) {
				debug("    movePixels=" + movePixels + " newX=" + newX
						+ "  allRegionsScrollX=" + allRegionsScrollX);
			}
			Component lMasterPanel = allRegionsViewPort.getView();
			if (newX < 0) {
				newX = 0;
			} else if (lMasterPanel != null
					&& (newX + allRegionsScrollWidth > lMasterPanel.getWidth())) {
				newX = lMasterPanel.getWidth() - allRegionsScrollWidth;
			}
			movePixels = newX - allRegionsScrollX;
			if (movePixels != 0) {
				// scroll
				allRegionsViewPort.setViewPosition(new Point(newX, 0 /* viewR.y */));
				// need to modify the drag location
				if (DEBUG_DRAG_SCROLL) {
					debug("    moved scrollpanel by " + movePixels
							+ " pixels, new scrollPaneX=" + newX);
				}
			}
		}
		return movePixels;
	}

	/**
	 * ask the user with a confirmation dialog if the text is OK.
	 * 
	 * @param text the text to be acknowledged by the user
	 * @return true if the user clicked yes, false otherwise.
	 */
	public boolean confirm(String text) {
		return Utils.confirm(masterPanel, text);
	}

	/**
	 * Display the text in a dialog box and asks the user to press OK.
	 * 
	 * @param text the text to be acknowledged by the user
	 */
	public void FYI(String text) {
		Utils.FYI(masterPanel, text);
	}

	/**
	 * Ensures that the given sample position is visible in the GUI
	 * 
	 * @param samplePosition
	 */
	public void makeVisible(long samplePosition) {
		// if during playback, approaching the right border, scroll
		int x = scale.sample2pixel(samplePosition);
		if (x >= allRegionsScrollX + allRegionsScrollWidth) {
			// set new x position of the viewport
			x = x - allRegionsScrollWidth + 50;
		} else if (x < allRegionsScrollX) {
			// set new x position of the viewport
			x = x - 50;
		} else {
			// already visible
			return;
		}
		if (x < 0) x = 0;
		if (x > allRegionsViewPort.getView().getWidth() - allRegionsScrollWidth) {
			x = allRegionsViewPort.getView().getWidth() - allRegionsScrollWidth;
		}
		if (x != allRegionsScrollX) {
			// debug("Set new view port pos: " + x);
			allRegionsViewPort.setViewPosition(new Point(x, 0));
		}

	}


}
