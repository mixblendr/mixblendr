/**
 *
 */
package com.mixblendr.test;

import java.io.File;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.tritonus.share.sampled.AudioUtils;

import com.mixblendr.audio.SimpleEnvironment;

/**
 * Simple app that loads the filenames given on the command line, loads it as an
 * audio file, and renames it to include the sample count in the filename.
 * 
 * @author Florian Bomers
 */
public class AudioFileRenamer2 {
	private SimpleEnvironment player;

	private AudioFileRenamer2() {
		player = new SimpleEnvironment();
	}

	private static final int BUFFER_SIZE = 4096;

	/** rename the file synchronously */
	private void rename(String filename) {
		File file = new File(filename);
		System.out.print(file.getName() + "...");
		try {

			AudioInputStream ais = AudioSystem.getAudioInputStream(file);
			if (!AudioUtils.isPCM(ais.getFormat())) {
				// need to decode
				AudioFormat af = ais.getFormat();
				AudioFormat newFormat = new AudioFormat(af.getSampleRate(), 16,
						af.getChannels(), true, false);
				try {
					ais = AudioSystem.getAudioInputStream(newFormat, ais);
				} catch (Exception e) {
					throw new Exception("error decoding: encoder for "
							+ af.getEncoding() + " not installed");
				}
			}

			if (Math.abs(ais.getFormat().getSampleRate()
					- player.getState().getSampleRate()) > 0.0001) {
				// need to convert sample rate
				AudioFormat newFormat = new AudioFormat(
						player.getState().getSampleRate(), 16,
						ais.getFormat().getChannels(), true, false);
				try {
					ais = AudioSystem.getAudioInputStream(newFormat, ais);
				} catch (Exception e) {
					throw new Exception("error converting to "
							+ player.getState().getSampleRate()
							+ "Hz: sample rate converter not installed");
				}
			}

			long byteCount = 0;

			// now read through the entire stream in 4K-blocks
			byte[] buffer = new byte[BUFFER_SIZE];
			while (true) {
				int c = ais.read(buffer, 0, BUFFER_SIZE);
				if (c >= 0) {
					byteCount += c;
				} else {
					break;
				}
			}

			AudioFileRenamer.doRenameFile(filename, byteCount
					/ ais.getFormat().getFrameSize());

		} catch (Exception e) {
			out("  ERROR: " + e.getMessage());
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
		(new AudioFileRenamer2()).rename(args);
	}

	private static void out(String s) {
		System.out.println(s);
	}

}
