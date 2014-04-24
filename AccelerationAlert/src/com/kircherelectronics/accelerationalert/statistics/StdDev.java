package com.kircherelectronics.accelerationalert.statistics;

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
	public final static int SAMPLE_WINDOW = 25;

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
