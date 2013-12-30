package com.kircherelectronics.accelerationalert.filter;

import android.util.Log;

/*
 * Low-Pass Linear Acceleration
 * Copyright (C) 2013, Kaleb Kircher - Boki Software, Kircher Engineering, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * An implementation of the Wikipedia low-pass filter. The Wikipedia LPF, is an
 * IIR single-pole implementation. The coefficient, a (alpha), can be adjusted
 * based on the sample period of the sensor to produce the desired time constant
 * that the filter will act on. It takes a simple form of y[i] = y[i] + alpha *
 * (x[i] - y[i]). Alpha is defined as alpha = dt / (timeConstant + dt);) where
 * the time constant is the length of signals the filter should act on and dt is
 * the sample period (1/frequency) of the sensor.
 * 
 * 
 * @author Kaleb
 * @see http://en.wikipedia.org/wiki/Low-pass_filter
 * @version %I%, %G%
 */
public class LPFWikipedia implements LowPassFilter
{
	private boolean alphaStatic = false;

	// Constants for the low-pass filters
	private float timeConstant = 0.18f;
	private float alpha = 0.1f;
	private float dt = 0;

	// Timestamps for the low-pass filters
	private float timestamp = System.nanoTime();
	private float timestampOld = System.nanoTime();

	private int count = 0;

	// Gravity and linear accelerations components for the
	// Wikipedia low-pass filter
	private float[] gravity = new float[]
	{ 0, 0, 0 };
	
	private float[] linearAcceleration = new float[]
	{ 0, 0, 0 };

	// Raw accelerometer data
	private float[] input = new float[]
	{ 0, 0, 0 };

	/**
	 * Add a sample.
	 * @param acceleration The acceleration data.
	 * @return Returns the output of the filter.
	 */
	public float[] addSamples(float[] acceleration)
	{
		// Get a local copy of the sensor values
		System.arraycopy(acceleration, 0, this.input, 0, acceleration.length);

		if (!alphaStatic)
		{
			timestamp = System.nanoTime();

			// Find the sample period (between updates).
			// Convert from nanoseconds to seconds
			dt = 1 / (count / ((timestamp - timestampOld) / 1000000000.0f));

			// Calculate Wikipedia low-pass alpha
			alpha = dt / (timeConstant + dt);

		}
		
		Log.d("tag", String.valueOf(alpha));
		
		count++;

		if (count > 5)
		{
			// Update the Wikipedia filter
			// y[i] = y[i] + alpha * (x[i] - y[i])
			gravity[0] = gravity[0] + alpha * (this.input[0] - gravity[0]);
			gravity[1] = gravity[1] + alpha * (this.input[1] - gravity[1]);
			gravity[2] = gravity[2] + alpha * (this.input[2] - gravity[2]);
			
			linearAcceleration[0] = input[0] - gravity[0];
			linearAcceleration[1] = input[1] - gravity[1];
			linearAcceleration[2] = input[2] - gravity[2];
		}

		return linearAcceleration;
	}

	/**
	 * Indicate if alpha should be static.
	 * @param alphaStatic A static value for alpha
	 */
	public void setAlphaStatic(boolean alphaStatic)
	{
		this.alphaStatic = alphaStatic;
	}

	/**
	 * Set static alpha.
	 * @param alpha The value for alpha, 0 < alpha <= 1
	 */
	public void setAlpha(float alpha)
	{
		this.alpha = alpha;
	}
}
