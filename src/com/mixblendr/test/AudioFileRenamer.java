/**
 *
 */
package com.mixblendr.test;

import java.io.File;

import com.mixblendr.audio.AudioFile;
import com.mixblendr.audio.AudioListener;
import com.mixblendr.audio.AudioRegion;
import com.mixblendr.audio.AudioTrack;
import com.mixblendr.audio.SimpleEnvironment;
import com.mixblendr.audio.AudioRegion.State;
import com.mixblendr.util.Debug;
import com.mixblendr.util.FatalExceptionListener;

/**
 * Simple app that loads the filenames given on the command line, loads it as an
 * audio file, and renames it to include the sample count in the filename.
 * 
 * @author Florian Bomers
 */
public class AudioFileRenamer implements FatalExceptionListener, AudioListener {
	private SimpleEnvironment player;
	private volatile boolean errorOccured;

	/**
	 * Create a new renamer instance
	 */
	private AudioFileRenamer() {
		super();
		Debug.DEBUG = false;
		player = new SimpleEnvironment();
		player.getState().getAudioEventDispatcher().addListener(this);
	}

	/** rename the file synchronously */
	private void rename(String filename) {
		File file = new File(filename);
		System.out.print(file.getName() + "...");
		try {
			if (!file.exists()) {
				throw new Exception("file does not exist");
			}
			errorOccured = false;
			AudioFile af = player.getFactory().getAudioFile(file);
			while (!errorOccured && !af.isFullyLoaded()) {
				Thread.sleep(50);
			}
			if (errorOccured) {
				return;
			}
			if (af.getDurationSamples() < 0) {
				throw new Exception("cannot get the sample count");
			}
			doRenameFile(filename, af.getDurationSamples());
			
		} catch (Exception e) {
			out("  ERROR: " + e.getMessage());
		}
	}

	/**
	 * @param filename
	 * @param sampleCount
	 */
	static void doRenameFile(String filename, long sampleCount) {
		File file = new File(filename);
		String count = Long.toString(sampleCount);
		String ext = "";
		int dotPos = filename.lastIndexOf('.');
		if (dotPos >= 0) {
			ext = filename.substring(dotPos);
			filename = filename.substring(0, dotPos);
		}
		if (filename.endsWith(count)) {
			out(" already includes sample count");
		} else {
			// check if filename ends with any count
			int digitCount = 0;
			for (int p = filename.length()-1; p>=0; p--) {
				char c = filename.charAt(p); 
				if (c >= '0' && c <='9') {
					digitCount++;
				} else {
					break;
				}
			}
			int underlinePos = filename.length() - digitCount - 1;
			if (digitCount>=5 && underlinePos >=0 && filename.charAt(underlinePos) == '_') {
				System.out.print(" [replacing existing sample count]");
				filename = filename.substring(0, underlinePos);
			}
			
			filename = filename + "_" + count + ext;
			File newFile = new File(filename);
			if (newFile.exists()) {
				out(" " + filename + " already exists");
			} else if (file.renameTo(newFile)) {
				out(" renamed to " + newFile.getName());
			} else {
				out(" ERROR renaming to " + filename);
			}
		}
	}

	private void rename(String[] filenames) {
		try {
			for (String fn : filenames) {
				rename(fn);
			}
		} finally {
			player.close();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		(new AudioFileRenamer()).rename(args);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.util.FatalExceptionListener#fatalExceptionOccured(java.lang.Throwable,
	 *      java.lang.String)
	 */
	public void fatalExceptionOccured(Throwable t, String context) {
		out(t.getMessage());
	}

    public void showMessage(String title, String context)
    {
        
    }

    public void showProgressDialog()
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void hideProgressDialog()
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setSuccess() // uploading is success
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setFailed() // uploading is failed
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    private static void out(String s) {
		System.out.println(s);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioListener#audioFileDownloadError(com.mixblendr.audio.AudioFile,
	 *      java.lang.Throwable)
	 */
	public void audioFileDownloadError(AudioFile file, Throwable t) {
		out(" ERROR loading " + file.getName() + ": " + t.getMessage());
		errorOccured = true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioListener#audioRegionStateChange(com.mixblendr.audio.AudioTrack,
	 *      com.mixblendr.audio.AudioRegion,
	 *      com.mixblendr.audio.AudioRegion.State)
	 */
	public void audioRegionStateChange(AudioTrack track, AudioRegion region,
			State state) {
		// nothing to do
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioListener#audioTrackNameChanged(com.mixblendr.audio.AudioTrack)
	 */
	public void audioTrackNameChanged(AudioTrack track) {
		// nothing to do
	}

}
