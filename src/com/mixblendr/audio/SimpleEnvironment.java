/**
 *
 */
package com.mixblendr.audio;

/**
 * A simple audio environment with state and audio file downloader
 * 
 * @author Florian Bomers
 */
public class SimpleEnvironment {
	private AudioState state;
	private AudioFileFactory factory;

	/**
	 * Create an instance of SimpleEnvironment
	 */
	public SimpleEnvironment() {
		super();
		state = new AudioState();
		factory = new AudioFileFactory(state);
	}

	/**
	 * @return the factory
	 */
	public AudioFileFactory getFactory() {
		return factory;
	}

	/**
	 * @return the state
	 */
	public AudioState getState() {
		return state;
	}

	/**
	 * Close this simple environment -- needs to be called before shutting down
	 * the program.
	 */
	public void close() {
		factory.close();
	}
}
