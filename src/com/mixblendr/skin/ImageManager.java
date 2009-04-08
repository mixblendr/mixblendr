/**
 *
 */
package com.mixblendr.skin;

import java.awt.Component;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.net.*;
import java.util.*;

import static com.mixblendr.util.Debug.*;

/**
 * Class that loads and manages shared images. The images are identified by the
 * filename String.
 * 
 * @author Florian Bomers
 */
// TODO: can add a method "waitForImagesLoaded" and then an optional parameter
// "defer" for getImage().
// TODO: use a image manager factory method to return the same instance of
// ImageManager if the same base Path is given
@SuppressWarnings("unchecked")
public class ImageManager {
	private static final boolean TRACE = false;

	private Class resourceClass = null;

	private String basePath;
	private URL baseURL = null;

	private HashMap<String, Image> images = new HashMap<String, Image>();

	/**
	 * @param resourceClass
	 */
	public ImageManager(Class resourceClass, String basePath) {
		super();
		this.resourceClass = resourceClass;
		this.basePath = basePath;
	}

	protected final static Component component = new Component() {
		// nothing
	};

	public URL getImageURL(String filename) throws ImageNotFoundException {
		if (baseURL == null) {
			String path = basePath + '/' + filename;
			baseURL = resourceClass.getResource(path);
			if (baseURL == null) {
				throw new ImageNotFoundException("cannot find " + path);
			}
			if (TRACE) debug("ImageManager: baseURL=" + baseURL);
		}
		try {
			return new URL(baseURL, filename);
		} catch (Exception e) {
			throw new ImageNotFoundException("cannot find image " + basePath
					+ '/' + filename);
		}
	}

	protected final static MediaTracker tracker = new MediaTracker(component);
	private static int mediaTrackerID;

	public Image getImage(String filename) throws ImageNotFoundException {
		Image ret = images.get(filename);
		if (ret == null) {
			try {
				URL imageURL = getImageURL(filename);
				if (TRACE) debug("ImageManager: loading " + imageURL);

				ret = Toolkit.getDefaultToolkit().getImage(imageURL);
				int ID = mediaTrackerID++;
				tracker.addImage(ret, ID);
				tracker.waitForID(ID);
			} catch (ImageNotFoundException infe) {
				throw infe;
			} catch (Exception e) {
				throw new ImageNotFoundException("error loading image "
						+ basePath + '/' + filename, e);
			}
			if (tracker.isErrorAny()) {
				throw new ImageNotFoundException("cannot load image "
						+ basePath + '/' + filename);
			}
			images.put(filename, ret);
		}
		if (ret == null) {
			throw new ImageNotFoundException("error loading image " + basePath
					+ '/' + filename);
		}
		return ret;
	}

	public class ImageNotFoundException extends Exception {
		private static final long serialVersionUID = 0;

		/**
		 * @param message
		 */
		public ImageNotFoundException(String message) {
			super(message);
		}

		/**
		 * @param message
		 * @param cause
		 */
		public ImageNotFoundException(String message, Throwable cause) {
			super(message, cause);
		}

	}
}
