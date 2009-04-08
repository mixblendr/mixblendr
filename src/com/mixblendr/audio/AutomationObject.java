/**
 *
 */
package com.mixblendr.audio;

/**
 * Base class for time stamped objects that are kept in the playlist of a track
 * for automation purposes.
 * <p>
 * One automation object can only belong to one playlist at a time.
 * 
 * @author Florian Bomers
 */
public abstract class AutomationObject {

	/**
	 * the start time in samples, when to start playback of this object in the
	 * playlist
	 */
	private long startTime;

	private AudioState state;

	/**
	 * this field will be set whenever the automation object is added to a
	 * playlist
	 */
	Playlist owner;

	/** the handler associated with this automation object */
	private AutomationHandler handler;

	/** private def constructor to prevent using this constructor */
	private AutomationObject() {
		super();
		owner = null;
		// register its handler
		handler = AutomationManager.getHandler(this);
	}

	/**
	 * Create a new automation object and initialize the state object.
	 * 
	 * @param state
	 */
	protected AutomationObject(AudioState state) {
		this();
		this.state = state;
	}

	/**
	 * Create a new automation object, initialize the state object, and set the
	 * start time.
	 * 
	 * @param state
	 * @param startSample the start time in samples
	 */
	protected AutomationObject(AudioState state, long startSample) {
		this(state);
		setStartTimeSamples(startSample);
	}

	/**
	 * @return the handler
	 */
	public AutomationHandler getHandler() {
		return handler;
	}

	/**
	 * Used to identify an automation object type instance. The type instance is
	 * defined by equality of class type and track owner that it is assigned to.
	 * 
	 * @param other the other AutomationObject to query equality of type
	 *            instance
	 * @return true if the type instance is the same
	 */
	public boolean isSameTypeInstance(AutomationObject other) {
		return (other.getClass() == getClass()) && (other.owner == owner);
	}

	/**
	 * assign all the properties of this automation object to <code>ao</code>.
	 * Implementing classes should overwrite this method and first call
	 * super.assignTo(ao) then assign their own fields. This method can be used
	 * for clone().
	 */
	public void assignTo(AutomationObject ao) {
		ao.owner = null; // must be done by adding it to a playlist
		ao.startTime = this.startTime;
		ao.state = this.state;
	}

	/**
	 * @return the state
	 */
	public final AudioState getState() {
		return state;
	}

	/**
	 * @return the startTime in samples
	 */
	public long getStartTimeSamples() {
		return startTime;
	}

	/**
	 * Return the start time in milliseconds. Note that this can be slightly
	 * different from the value used for setting the start time because
	 * internally the start time is set in samples.
	 * 
	 * @return the startTime in milliseconds
	 */
	public double getStartTimeMillis() {
		return state.sample2millis(startTime);
	}

	/**
	 * Set a new time when this automation object is executed. It will
	 * automatically notify its owner playlist of the new position.
	 * 
	 * @param startSample the startTime to set in samples
	 */
	public void setStartTimeSamples(long startSample) {
		if (this.startTime != startSample) {
			this.startTime = startSample;
			Playlist pl = this.owner;
			if (pl != null) {
				pl.automationObjectStartChanged(this);
			}
		}
	}

	/**
	 * Return this automation object's owner (the playlist). The owner is set
	 * automatically when this object is added to a playlist.
	 * 
	 * @return the owner, or null if not set
	 */
	public Playlist getOwner() {
		return owner;
	}

	/**
	 * Set a new time when this automation object is executed. It will
	 * automatically notify its owner playlist of the new position.
	 * 
	 * @param startMillis the startTime to set in milliseconds
	 */
	public void setStartTimeMillis(double startMillis) {
		setStartTimeSamples(state.millis2sample(startMillis));
	}

	/**
	 * This method is called from the actual audio render loop when this
	 * automation object is triggered.
	 * <p>
	 * The implementation will first call executeImpl(), then notify the
	 * AudioState of the occurence of this automation event for asynchronous
	 * event dispatching.
	 * 
	 * @param track the audio track on which this automation event occured
	 */
	final void execute(AudioTrack track) {
		if (handler.isTracking(track)) {
			// ignore this object if currently tracking
			return;
		}
		executeImpl(track);
		state.getAutomationEventDispatcher().dispatchEvent(this, track);
	}

	/**
	 * This method is called during playback in the context of the actual audio
	 * render loop when this automation object is triggered.
	 * <p>
	 * This method must be overwritten for actual audio setting processing and
	 * initialization. No processor intense operations should be carried out in
	 * this method, if possible.
	 * <p>
	 * During tracking (i.e. the implementor has set the tracking property of
	 * this automation object's AutomationHandler), this method is never called.
	 * 
	 * @param track the audio track on which this automation event occured
	 */
	protected abstract void executeImpl(AudioTrack track);

	/**
	 * @return a string representation of this object (mainly for debugging
	 *         purposes)
	 */
	@Override
	public String toString() {
		return getClass().getSimpleName() + ": startTime="
				+ getStartTimeMillis() / 1000.0 + "s";
	}
}
