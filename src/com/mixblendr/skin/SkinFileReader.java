/**
 *
 */
package com.mixblendr.skin;

import java.io.*;
import java.util.*;
import static com.mixblendr.util.Debug.*;

/**
 * The parser for the skin definition file. Reads all elements from the file and
 * stores them in a structure that can be efficiently queried.
 * 
 * @author Florian Bomers
 */
public class SkinFileReader {

	private static final boolean TRACE = false;

	public static final String MASTER_PANEL_NAME = "panel.master";
	public static final String DEFAULT_SECTION_NAME = "default";
	public static final String GLOBAL_SECTION_NAME = "global";

	/** The list of control definitions. It will not take the default controls */
	private ArrayList<ControlDefinition> controlDefs = new ArrayList<ControlDefinition>(
			50);

	/** the global section definition object */
	private GlobalDefinition globalDef = null;

	/** return the actual instance of the control definitions -- do not modify. */
	public List<ControlDefinition> getControlDefinitions() {
		return controlDefs;
	}

	/** return the parsed global definition */
	public GlobalDefinition getGlobalDefinition() {
		return globalDef;
	}

	/**
	 * Parse the skin file passed as an input stream.
	 * 
	 * @param is the skin file stream
	 * @throws ParseException when a parse error occurs
	 */
	public void load(InputStream is) throws Exception {
		LineNumberReader input = new LineNumberReader(new InputStreamReader(is,
				"ISO-8859-1"));
		try {
			String line;
			ControlDefinition currCtrl = null;
			ControlDefinition defaultCtrl = null;
			boolean inGlobalDef = false;
			boolean readFirstLine = false;
			/*
			 * readline returns the content of a line without the newline,
			 * returns null at the end of the stream, returns an empty String
			 * for empty lines
			 */
			while ((line = input.readLine()) != null) {
				line = line.trim();
				if (TRACE) debug("->" + line);
				int len = line.length();
				if (len == 0 || line.charAt(0) == '#') {
					continue;
				}
				if (line.charAt(0) == '[') {
					if (len < 2 || line.charAt(len - 1) != ']') {
						throw new ParseException(input.getLineNumber(),
								"illegal control syntax");
					}
					// beginning of a new control element
					if (currCtrl != null) {
						if (currCtrl != defaultCtrl) {
							onControlDefined(currCtrl);
						}
						currCtrl = null;
					}
					String fullname = line.substring(1, len - 1).trim();
					if (fullname.length() == 0) {
						throw new ParseException(input.getLineNumber(),
								"illegal control name");
					}
					// special case: global definition
					if (fullname.equals(GLOBAL_SECTION_NAME)) {
						if (globalDef != null) {
							throw new ParseException(input.getLineNumber(),
									"duplicate [global] section");
						}
						globalDef = new GlobalDefinition();
						inGlobalDef = true;
					} else {
						inGlobalDef = false;
						currCtrl = new ControlDefinition();
						currCtrl.fullName = fullname;
						readFirstLine = false;
						if (fullname.equals(DEFAULT_SECTION_NAME)) {
							defaultCtrl = currCtrl;
							readFirstLine = true;
						} else {
							// initialize with default state images
							if (defaultCtrl != null) {
								currCtrl.assignImagesFrom(defaultCtrl);
								currCtrl.fontname = defaultCtrl.fontname;
							}
							if (fullname.indexOf('.') < 0) {
								throw new ParseException(input.getLineNumber(),
										"illegal name for a control (missing dot): "
												+ fullname);
							}
						}
					}
				} else {
					// read lines of a control
					if (inGlobalDef) {
						// parse global section
						parseGlobalLine(line, input.getLineNumber());
					} else {
						// parse normal control
						if (currCtrl == null) {
							throw new ParseException(input.getLineNumber(),
									"unexpected statement outside of a section");
						}
						if (!readFirstLine) {
							currCtrl.targetBounds = parseRectangle(
									input.getLineNumber(), line, true, false,
									null);
							readFirstLine = true;
						} else {
							parseControlLine(line, currCtrl,
									currCtrl == defaultCtrl,
									input.getLineNumber());
						}
					}
				}
			}
			if (currCtrl != null) {
				if (currCtrl != defaultCtrl) {
					onControlDefined(currCtrl);
				}
				currCtrl = null;
			}
			if (globalDef == null) {
				globalDef = new GlobalDefinition();
			}
		} catch (ParseException pe) {
			throw pe;
		} catch (Exception e) {
			throw new ParseException(input.getLineNumber(),
					"Error reading from skin definition file", e);
		} finally {
			try {
				input.close();
			} catch (IOException ex) {
				// ignore
			}
		}
	}

	/** add this control to the list and do some fixing up on it */
	private void onControlDefined(ControlDefinition ctrl) {
		controlDefs.add(ctrl);
		for (int i = 0; i < ControlState.length; i++) {
			String img = ctrl.sourceImage[i];
			if (img != null && img.length() > 0) {
				if (ctrl.sourcePos[i] == null && ctrl.targetBounds != null) {
					ctrl.sourcePos[i] = new Pos(ctrl.targetBounds.x,
							ctrl.targetBounds.y);
				}
			}
		}
	}

	/**
	 * parse a string in the global section
	 * 
	 * @param line the current line
	 * @param lineNumber the line number for exceptions
	 */
	private void parseGlobalLine(String line, int lineNumber)
			throws ParseException {
		String id = split(line)[0];
		String value = splitString[1];
		if (id == "" || value == null) {
			throw new ParseException(lineNumber, "unexpected statement");
		}
		// all parameters are free form
		globalDef.add(id, value);
	}

	/**
	 * parse a string for a general control, except the first line
	 * 
	 * @param line the current line
	 * @param ctrl the current control
	 * @param lineNumber the line number for exceptions
	 */
	private void parseControlLine(String line, ControlDefinition ctrl,
			boolean isDefaultSection, int lineNumber) throws ParseException {
		String id = split(line)[0];
		String value = splitString[1];
		if (id == "" || value == null) {
			throw new ParseException(lineNumber, "unexpected statement");
		}
		if (id.equals("knob")) {
			ctrl.knob = value;
			return;
		} else if (id.equals("tooltip")) {
			ctrl.tooltip = value;
			return;
		} else if (id.equals("tooltip.normal")) {
			ctrl.tooltip = value;
			return;
		} else if (id.equals("tooltip.down")) {
			ctrl.tooltipDown = value;
			return;
		} else if (id.equals("parent")) {
			ctrl.parent = value;
			return;
		} else if (id.equals("font")) {
			ctrl.fontname = value;
			return;
		}

		int state = -1;
		for (ControlState cs : ControlState.values()) {
			if (cs.matchesID(id)) {
				state = cs.ordinal();
				break;
			}
		}
		if (state < 0) {
			throw new ParseException(lineNumber, "unknown definition state '"
					+ id + "' in control " + ctrl.fullName);
		}
		// all other id's require a filename
		int comma = value.indexOf(',');
		String fn;
		if (comma >= 0) {
			fn = value.substring(0, comma).trim();
			comma++;
		} else {
			comma = 0;
			fn = value;
		}
		if (fn == "" && comma > 0) {
			throw new ParseException(lineNumber,
					"filename expected for control " + ctrl.fullName);
		}
		ctrl.sourceImage[state] = fn;
		if (fn.length() > 0) {
			Pos p = null;
			if (comma > 0) {
				// optional position
				p = parsePosition(lineNumber, value.substring(comma), true,
						true, null);
			}
			if (p == null && ctrl.targetBounds != null) {
				p = new Pos(ctrl.targetBounds.x, ctrl.targetBounds.y);
			}
			ctrl.sourcePos[state] = p;
		} else {
			ctrl.sourceImage[state] = null;
			ctrl.sourcePos[state] = null;
		}
	}

	/**
	 * parse 4 comma-separated numbers x,y,w,h
	 * 
	 * @param numbers the string containing the 4 comma-separated numbers
	 * @param exceptOnParseError if true, throw exception when the string
	 *            contains wrong characters
	 * @param def the default value to return if numbers is empty, or on parse
	 *            error
	 * @return the rectangle created from the 4 numbers, or def if numbers
	 *         doesn't contain a valid rectangle definition
	 */
	static Rect parseRectangle(int lineNr, String numbers,
			boolean exceptOnParseError, boolean allowEmpty, Rect def)
			throws ParseException {
		int i1 = numbers.indexOf(',');
		if (i1 < 0 && numbers.trim() == "") {
			if (allowEmpty || !exceptOnParseError) {
				return def;
			}
			throw new ParseException(lineNr, "pos/size expected");
		}
		Rect r = new Rect();
		Throwable t = null;
		try {
			r.x = Integer.parseInt(numbers.substring(0, i1).trim());
			int i2 = numbers.indexOf(',', i1 + 1);
			if (i2 >= 0) {
				r.y = Integer.parseInt(numbers.substring(i1 + 1, i2).trim());
				int i3 = numbers.indexOf(',', i2 + 1);
				if (i3 >= 0) {
					r.w = Integer.parseInt(numbers.substring(i2 + 1, i3).trim());
					r.h = Integer.parseInt(numbers.substring(i3 + 1).trim());
					return r;
				}
			}
		} catch (Exception e) {
			t = e;
		}
		if (exceptOnParseError) {
			throw new ParseException(lineNr,
					"position/size definition expected", t);
		}
		return def;
	}

	/**
	 * parse 2 comma-separated numbers x,y
	 * 
	 * @param numbers the string containing the 2 comma-separated numbers
	 * @param exceptOnParseError if true, throw exception when the string
	 *            contains wrong characters
	 * @param def the default value to return if numbers is empty, or on parse
	 *            error
	 * @return the Dimension object created from the 2 numbers, or def if
	 *         numbers doesn't contain a valid position definition
	 */
	static Pos parsePosition(int lineNr, String numbers,
			boolean exceptOnParseError, boolean allowEmpty, Pos def)
			throws ParseException {
		int i1 = numbers.indexOf(',');
		if (i1 < 0 && numbers.trim() == "") {
			if (allowEmpty || !exceptOnParseError) {
				return def;
			}
			throw new ParseException(lineNr, "pos expected");
		}
		Throwable t = null;
		Pos p = new Pos();
		try {
			p.x = Integer.parseInt(numbers.substring(0, i1).trim());
			p.y = Integer.parseInt(numbers.substring(i1 + 1).trim());
			return p;
		} catch (Exception e) {
			t = e;
		}
		if (exceptOnParseError) {
			throw new ParseException(lineNr, "position definition expected", t);
		}
		return def;
	}

	private String[] splitString = new String[2];

	/**
	 * split the passed string into 2 strings returned in the global variable
	 * <code>splitString</code>. The 2 strings are separated by an equals
	 * sign. If there is no equals sign in <code>s</code>, the returned array
	 * will have only the first element set and the second element will be null.
	 * 
	 * @param s the string to split
	 * @return the 2 string parts split at the equals sign
	 */
	private String[] split(String s) {
		int e = s.indexOf('=');
		if (e >= 0) {
			splitString[0] = s.substring(0, e).trim();
			splitString[1] = s.substring(e + 1).trim();
		} else {
			splitString[0] = s;
			splitString[1] = null;
		}
		return splitString;
	}

	/** dump this class'es description */
	private void dump() {
		if (globalDef == null) {
			System.out.println("No global definition");
		} else {
			globalDef.dump();
		}
		for (ControlDefinition c : controlDefs) {
			c.dump();
		}
	}

	/** unit test for SkinFileReader */
	public static void main(String[] args) throws Exception {
		SkinFileReader sfr = new SkinFileReader();
		sfr.load(new FileInputStream("C:\\test.sdf"));
		sfr.dump();
	}
}
