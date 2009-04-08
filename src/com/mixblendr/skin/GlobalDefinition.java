/**
 *
 */
package com.mixblendr.skin;

import java.util.HashMap;

/**
 * Container for the global skin definitions. Only one field is fixed, the
 * background image. All other fields are free form, and can contain any number
 * of key=value pairs.
 * 
 * @author Florian Bomers
 */
public class GlobalDefinition {

	private HashMap<String, String> props = new HashMap<String, String>();

	/** get the value from the given key in the properties */
	public String getValue(String key) {
		return props.get(key);
	}

	/**
	 * Parse a property as a Rect instance. The value must be "x,y,w,h".
	 * 
	 * @return the Rect instance, or null if the value does not exist
	 */
	public Rect getRectProperty(String key) throws ParseException {
		String val = getValue(key);
		if (val == null || val.length() == 0) {
			return null;
		}
		return SkinFileReader.parseRectangle(-2, val, true, false, null);
	}

	/**
	 * add a property defined by its key and its value. If a property with the
	 * given key already exists, it will be overwritten
	 */
	public void add(String key, String value) {
		props.put(key, value);
	}

	/** for debugging purposes: print the contents of this global definition */
	void dump() {
		System.out.println("Global section:");
		for (String key : props.keySet()) {
			String val = getValue(key);
			System.out.println("  " + key + "=" + val);
		}
	}
}
