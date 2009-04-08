/**
 *
 */
package com.mixblendr.gui.main;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import com.mixblendr.util.Debug;

/**
 * The Transferable used for drag operations (encapsulates a RegionGraph
 * object).
 * 
 * @author Florian Bomers
 */
public class RegionTransferable implements Transferable, ClipboardOwner {

	private final static boolean DEBUG = false;

	private RegionGraph region;

	/**
	 * @param region
	 */
	RegionTransferable(RegionGraph region) {
		super();
		this.region = region;
	}

	public static final DataFlavor regionFlavor = new DataFlavor(
	// do not use RegionGraph's class - Apple always tries to serialize it
			// (unsuccessfully)
			// "application/RegionGraph; class=" + RegionGraph.class.getName(),
			"application/RegionGraph; class=java.lang.Object", "Audio Region");

	public static final DataFlavor[] flavors = {
		regionFlavor
	};

	public synchronized DataFlavor[] getTransferDataFlavors() {
		return flavors;
	}

	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return flavor.equals(regionFlavor);
	}

	/** directly return the associated region */
	public synchronized RegionGraph getAudioRegion() {
		return region;
	}

	public synchronized Object getTransferData(DataFlavor flavor)
			throws UnsupportedFlavorException, IOException {
		if (DEBUG) {
			Debug.debug("getTransferData(" + flavor + ")");
		}
		if (flavor.equals(regionFlavor)) {
			return region;
		}
		throw new UnsupportedFlavorException(flavor);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.datatransfer.ClipboardOwner#lostOwnership(java.awt.datatransfer.Clipboard,
	 *      java.awt.datatransfer.Transferable)
	 */
	public void lostOwnership(Clipboard clipboard, Transferable contents) {
		// ignore
	}
}
