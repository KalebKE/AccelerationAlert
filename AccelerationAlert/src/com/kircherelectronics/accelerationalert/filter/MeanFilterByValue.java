package com.kircherelectronics.accelerationalert.filter;

import java.util.LinkedList;
import java.util.List;

/**
 * Implements a mean filter designed to smooth the data points based on a mean.
 * 
 * @author Kaleb
 * @version %I%, %G%
 * 
 */
public class MeanFilterByValue
{
	// The size of the mean filters rolling window.
	private int filterWindow = 20;

	private boolean dataInit;

	private LinkedList<Number> dataList;

	/**
	 * Initialize a new MeanFilter object.
	 */
	public MeanFilterByValue()
	{
		dataList = new LinkedList<Number>();
		dataInit = false;
	}

	/**
	 * Filter the data.
	 * 
	 * @param iterator
	 *            contains input the data.
	 * @return the filtered output data.
	 */
	public float filterFloat(float data)
	{

		// Initialize the data structures for the data set.
		if (!dataInit)
		{
			dataList = new LinkedList<Number>();
		}

		dataList.addLast(data);

		if (dataList.size() > filterWindow)
		{
			dataList.removeFirst();
		}

		dataInit = true;

		return getMean(dataList);
	}

	/**
	 * Get the mean of the data set.
	 * 
	 * @param data
	 *            the data set.
	 * @return the mean of the data set.
	 */
	private float getMean(List<Number> data)
	{
		float m = 0;
		float count = 0;

		for (int i = 0; i < data.size(); i++)
		{
			m += data.get(i).floatValue();
			count++;
		}

		if (count != 0)
		{
			m = m / count;
		}

		return m;
	}

	public void setWindowSize(int size)
	{
		this.filterWindow = size;
	}
}
