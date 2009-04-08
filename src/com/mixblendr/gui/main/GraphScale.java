/**
 *
 */
package com.mixblendr.gui.main;

import java.util.ArrayList;
import java.util.List;
import static com.mixblendr.util.Debug.*;

/**
 * Class for managing the scale of a graph, i.e. the zoom factor. The scale is
 * the factor by which a sample is mapped to a pixel. One instance can be shared
 * by multiple graphs, this will cause all graphs to scale accordingly.
 * 
 * @author Florian Bomers
 */
public class GraphScale implements Cloneable {

	private final static boolean DEBUG = false;

	public static final double MIN_SCALE_FACTOR = 2.0769187434139358E-7;
	public static final double DEFAULT_SCALE_FACTOR = 0.001;
	public static final double MAX_SCALE_FACTOR = 120.0;

	/**
	 * The scale: multiply the number of samples with this value to get the
	 * pixels.
	 */
	private double scaleFactor = 0.001;

	private List<Listener> listeners = null;

	public GraphScale() {
		// nothing
	}

	/** calculate the pixel from the given sample */
	public double sample2pixel(double sample) {
		return sample * scaleFactor;
	}

	/** calculate the pixel from the given sample */
	public int sample2pixel(long sample) {
		return (int) Math.round(sample * scaleFactor);
	}

	/** calculate the pixel from the given sample */
	public long pixel2sample(int pixel) {
		if (scaleFactor == 0.0) return pixel;
		return Math.round(pixel / scaleFactor);
	}

	/** calculate the pixel from the given sample */
	public double pixel2sample(double pixel) {
		if (scaleFactor == 0.0) return pixel;
		return pixel / scaleFactor;
	}

	/**
	 * create a new GraphScale with the given scale.
	 * 
	 * @param scale the scale to initialize this instance public
	 */
	public GraphScale(GraphScale scale) {
		setScaleFactor(scale.scaleFactor);
	}

	/**
	 * create a new GraphScale with the given scale factor.
	 * 
	 * @param scaleFactor the sample-to-pixel factor: multiply the number of
	 *            samples with the scale to get the pixels.
	 */
	public GraphScale(double scaleFactor) {
		setScaleFactor(scaleFactor);
	}

	/**
	 * create a duplicate of this object
	 */
	@Override
	public Object clone() {
		return new GraphScale(this);
	}

	/**
	 * Get the current scale factor (i.e. the sample-to-pixel factor)
	 * 
	 * @return the current scale factor
	 */
	public double getScaleFactor() {
		return scaleFactor;
	}

	/** compares this scale and the specified scale */
	public boolean equals(GraphScale scale) {
		return scale.scaleFactor == this.scaleFactor;
	}

	/** add a listener, which whill receive events when the scale changes */
	public void addListener(Listener zl) {
		if (listeners == null) listeners = new ArrayList<Listener>();
		listeners.add(zl);
	}

	/** remove a listener */
	public void removeListener(Listener zl) {
		if (listeners != null) {
			listeners.remove(zl);
			if (listeners.size() == 0) {
				listeners = null;
			}
		}
	}

	/**
	 * Set new scale factor. If listeners are registered, they'll receive an
	 * event synchronously.
	 * 
	 * @return the new scale factor - may be limited
	 */
	public double setScaleFactor(double scaleFactor) {
		if (scaleFactor < 0) {
			scaleFactor = DEFAULT_SCALE_FACTOR;
		} else if (scaleFactor < MIN_SCALE_FACTOR) {
			scaleFactor = MIN_SCALE_FACTOR;
		} else if (scaleFactor > MAX_SCALE_FACTOR) {
			scaleFactor = MAX_SCALE_FACTOR;
		}
		if (this.scaleFactor != scaleFactor) {
			double oldFactor = this.scaleFactor;
			this.scaleFactor = scaleFactor;
			if (listeners != null) {
				for (Listener l : listeners)
					l.scaleChanged(this, oldFactor);
			}
			if (DEBUG) {
				debug("New ScaleFactor=" + scaleFactor);
			}
		}
		return scaleFactor;
	}

	/**
	 * Set new scale. If listeners are registered, they'll receive an event
	 * synchronously.
	 */
	public void setScale(GraphScale scale) {
		if (scale != null) {
			setScaleFactor(scale.scaleFactor);
		}
	}

	@Override
	public String toString() {
		return "Scale: scaleFactor=" + scaleFactor;
	}

	/**
	 * Listener interface that is implemented by classes that need to receive
	 * the GraphScale events
	 */
	public interface Listener {

		/**
		 * sent to listeners after the scale value is changed.
		 * 
		 * @param scale the object sending this event. Its scaleFactor is the
		 *            new value.
		 * @param oldScaleFactor the old scale factor.
		 */
		public void scaleChanged(GraphScale scale, double oldScaleFactor);
	}

}
