/**
 *
 */
package com.mixblendr.util;

import java.awt.Component;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Static class for logging utilities. You can log to the command line and to a
 * registered listener. The listener instance can, for example, display the log
 * strings in a GUI element.
 * 
 * @author Florian Bomers Bomers
 */
public class Debug {

	/** if true, debug messages are printed. */
	public static boolean DEBUG = false;

	/** if true, error messages are printed. */
	public static boolean ERROR = true;

	/** if true, all exceptions will be printed with stack trace */
	public static boolean DUMP_EXCEPTIONS = false;

	/** if true, prepend all log messages with millisecond time */
	public static boolean PREPEND_TIME = false;


    
    /**
	 * Output a debug message
	 */
	public final static void debug(String s) {
		if (DEBUG) {
			lastThrowable = null;
			out(s);
		}
	}

	/**
	 * Output a debug message
	 */
	public final static void debug(Throwable t) {
		if (DEBUG) {
			lastThrowable = null;
			out("ERROR:" + t.getClass().getSimpleName() + ": " + t.getMessage());
			if (DUMP_EXCEPTIONS) {
				t.printStackTrace();
			}
		}
	}

	/**
	 * Output an error message
	 */
	public final static void error(String s) {
		if (ERROR) {
			lastThrowable = null;
			out(s);
		}
	}

	/** keep the last throwable around to not output endless exceptions */
	private static Throwable lastThrowable = null;

	/** flag if the message that this exception is repeated, was shown */
	private static boolean printedRepeatMessage = false;

	/**
	 * Output the exception, if the lastThrowable is different
	 */
	public final static void error(Throwable t) {
		if (t == null) {
			assert (false);
			return;
		}
		if (lastThrowable != null
				&& lastThrowable.getClass().equals(t.getClass())
				&& ((lastThrowable.getMessage() == null && t.getMessage() == null) || lastThrowable.getMessage().equals(
						t.getMessage()))) {
			if (!printedRepeatMessage) {
				out("(last exception repeated, any consecutive ones will not be shown)");
				printedRepeatMessage = true;
			}
			return;
		}
		if (ERROR) {
			out("ERROR:" + t.getClass().getSimpleName() + ": " + t.getMessage());
		}
		if (DEBUG || (DUMP_EXCEPTIONS && ERROR)) {
			t.printStackTrace();
		}
		lastThrowable = t;
		printedRepeatMessage = false;
	}

	private static long startTime = System.nanoTime();

	/**
	 * @return the current time in milliseconds, as a string
	 */
	private static final String getTime() {
		return Long.toString((System.nanoTime() - startTime) / 1000000L);
	}

	/**
	 * output a debug,trace, or error message. If listener is set, the message
	 * is sent to the listener, otherwise the message is printed to stdout.
	 */
	public final static void out(String s) {
		if (PREPEND_TIME) {
			s = getTime() + ": " + s;
		}
		System.out.println(s);
	}

	/**
	 * Display an error message to the user
	 */
	public static void displayErrorDialog(Component parent, String title,
			String text) {
		JOptionPane.showMessageDialog(parent, text, title,
				JOptionPane.ERROR_MESSAGE);
	}
    /**
     * Display an error message to the user
     */
    public static void displayMessageDialog(Component parent, String title,
            String text) {
        JOptionPane.showMessageDialog(parent, text, title,
                JOptionPane.ERROR_MESSAGE);
    }

    public static void displayInfoMessageDialog(Component parent, String title,
            String text) {
        JOptionPane.showMessageDialog(parent, text, title,
                JOptionPane.INFORMATION_MESSAGE);

       
    }


    /**
	 * Display an error dialog with this exception's error message
	 */
	public static void displayErrorDialog(final Component parent, Throwable t,
			String context) {
		String errorMsg = "Error occured: " + context;
		error(errorMsg);
		if (t != null) {
			error(t);
			errorMsg += "\n" + t.getClass().getSimpleName() + ":"
					+ t.getMessage();
		}
		displayErrorDialog(parent, "Error", errorMsg);
	}

	/**
	 * Asynchronously display an error dialog with this exception's error
	 * message
	 */
	public static void displayErrorDialogAsync(final Component parent,
			final Throwable t, final String context) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				displayErrorDialog(parent, t, context);
			}
		});
	}

    public static void displayInfoDialogAsync(final Component parent,
            final String title, final String context) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                displayInfoMessageDialog(parent, title,  context);
            }
        });
    }


    public final static void onnl(String s) {
		System.out.print(s);
		System.out.flush();
	}

}
