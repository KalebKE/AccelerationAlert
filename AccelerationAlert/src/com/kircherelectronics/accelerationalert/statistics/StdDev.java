package com.kircherelectronics.accelerationalert.statistics;

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

import java.util.LinkedList;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import com.kircherelectronics.accelerationalert.AccelerationAlertActivity;

import android.util.Log;

/**
 * An implementation to calculate standard deviation from a rolling window.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public class StdDev
{
	// The size of the sample window that determines RMS Amplitude Noise
	// (standard deviation)
	public final static int SAMPLE_WINDOW = 50;

	private LinkedList<Double> list = new LinkedList<Double>();
	private double stdDev;
	private DescriptiveStatistics stats = new DescriptiveStatistics();

	/**
	 * Add a sample to the rolling window.
	 * 
	 * @param value
	 *            The sample value.
	 * @return The std dev of the rolling window.
	 */
	public double addSample(double value)
	{
		list.addLast(value);

		enforceWindow();

		return calculateStdDev();
	}

	/**
	 * Enforce the rolling window.
	 */
	private void enforceWindow()
	{
		if (list.size() > SAMPLE_WINDOW)
		{
			list.removeFirst();
		}
	}

	/**
	 * Calculate the std dev of the rolling window.
	 * 
	 * @return The std dev of the rolling window.
	 */
	private double calculateStdDev()
	{
		if (list.size() > 5)
		{
			stats.clear();

			// Add the data from the array
			for (int i = 0; i < list.size(); i++)
			{
				stats.addValue(list.get(i));
			}

			stdDev = stats.getStandardDeviation();
		}

		return stdDev;
	}

}
