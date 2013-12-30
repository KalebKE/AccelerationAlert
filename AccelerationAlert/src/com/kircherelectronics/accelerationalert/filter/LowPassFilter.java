package com.kircherelectronics.accelerationalert.filter;

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
 * An interface for classes that implement low-pass filters.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public interface LowPassFilter
{

	/**
	 * Add a sample.
	 * @param acceleration The acceleration data.
	 * @return Returns the output of the filter.
	 */
	public float[] addSamples(float[] acceleration);

	/**
	 * Indicate if alpha should be static.
	 * @param alphaStatic A static value for alpha
	 */
	public void setAlphaStatic(boolean alphaStatic);

	/**
	 * Set static alpha.
	 * @param alpha The value for alpha, 0 < alpha <= 1
	 */
	public void setAlpha(float alpha);

}
