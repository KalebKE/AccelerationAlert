package com.kircherelectronics.accelerationalert.filter;

import java.util.LinkedList;
import java.util.List;

/*
 * Copyright 2013, Kircher Electronics
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
