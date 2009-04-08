package com.mixblendr.skin;

/**
 * Exception class specifying the line number where the error occurred.
 * 
 * @author Florian Bomers
 */
public class ParseException extends Exception {
	private static final long serialVersionUID = 0;

	private int lineNumber;

	/**
	 * @param lineNumber
	 * @param message
	 */
	public ParseException(int lineNumber, String message) {
		super(message + " [line " + (lineNumber + 1) + "]");
		this.lineNumber = lineNumber;
	}

	/**
	 * @param lineNumber
	 * @param message
	 * @param cause
	 */
	public ParseException(int lineNumber, String message, Throwable cause) {
		this(lineNumber, message);
		initCause(cause);
	}

	/**
	 * @return the lineNumber
	 */
	public int getLineNumber() {
		return lineNumber;
	}

}
