/**
 *
 */
package com.mixblendr.audio;

import java.util.HashMap;

/**
 * Manage automation types.
 * 
 * @author Florian Bomers
 */
@SuppressWarnings("unchecked")
public class AutomationManager {

	static HashMap<Class, AutomationHandler> types = new HashMap<Class, AutomationHandler>();

	/** prevent instanciation */
	private AutomationManager() {
		// nothing
	}

	/**
	 * Return the instance of AutomationHandler responsible for the provided
	 * instance of AutomationObject. All automation objects of one class type
	 * share one automation handler.
	 * <p>
	 * Use the automation handler to notify the system when tracking starts and
	 * when it ends. Tracking is when the user holds the visual GUI control.
	 * Tracking will inhibit automation events to be sent for this particular
	 * type. If automation is true, any existing automation objects are removed
	 * from the track. Additionally, the implementor is responsible for creating
	 * new automation objects and adding them to the track.
	 */
	public static AutomationHandler getHandler(AutomationObject ao) {
		return getHandler(ao.getClass());
	}

	/**
	 * Return the instance of AutomationHandler responsible for the provided
	 * type of AutomationObject. All automation objects of one class type share
	 * one automation handler.
	 * <p>
	 * Use the automation handler to notify the system when tracking starts and
	 * when it ends. Tracking is when the user holds the visual GUI control.
	 * Tracking will inhibit automation events to be sent for this particular
	 * type. If automation is true, any existing automation objects are removed
	 * from the track. Additionally, the implementor is responsible for creating
	 * new automation objects and adding them to the track.
	 */
	public static AutomationHandler getHandler(Class automationClass) {
		AutomationHandler ah = types.get(automationClass);
		if (ah == null) {
			ah = new AutomationHandler();
			types.put(automationClass, ah);
		}
		return ah;
	}
}
