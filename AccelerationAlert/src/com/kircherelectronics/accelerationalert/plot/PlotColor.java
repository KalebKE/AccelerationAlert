package com.kircherelectronics.accelerationalert.plot;

import android.content.Context;

import com.kircherelectronics.accelerationalert.R;

/**
 * Manages colors.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public class PlotColor
{
	private int lightBlue;
	private int lightPurple;
	private int lightGreen;
	private int lightOrange;
	private int lightRed;

	private int midBlue;
	private int midPurple;
	private int midGreen;
	private int midOrange;
	private int midRed;

	private int darkBlue;
	private int darkPurple;
	private int darkGreen;
	private int darkOrange;
	private int darkRed;

	public PlotColor(Context context)
	{
		lightBlue = context.getResources().getColor(R.color.light_blue);
		lightPurple = context.getResources().getColor(R.color.light_purple);
		lightGreen = context.getResources().getColor(R.color.light_green);
		lightOrange = context.getResources().getColor(R.color.light_orange);
		lightRed = context.getResources().getColor(R.color.light_red);

		midBlue = context.getResources().getColor(R.color.mid_blue);
		midPurple = context.getResources().getColor(R.color.mid_purple);
		midGreen = context.getResources().getColor(R.color.mid_green);
		midOrange = context.getResources().getColor(R.color.mid_orange);
		midRed = context.getResources().getColor(R.color.mid_red);

		darkBlue = context.getResources().getColor(R.color.dark_blue);
		darkPurple = context.getResources().getColor(R.color.dark_purple);
		darkGreen = context.getResources().getColor(R.color.dark_green);
		darkOrange = context.getResources().getColor(R.color.dark_orange);
		darkRed = context.getResources().getColor(R.color.dark_red);
	}

	public int getLightBlue()
	{
		return lightBlue;
	}

	public int getLightPurple()
	{
		return lightPurple;
	}

	public int getLightGreen()
	{
		return lightGreen;
	}

	public int getLightOrange()
	{
		return lightOrange;
	}

	public int getLightRed()
	{
		return lightRed;
	}

	public int getMidBlue()
	{
		return midBlue;
	}

	public int getMidPurple()
	{
		return midPurple;
	}

	public int getMidGreen()
	{
		return midGreen;
	}

	public int getMidOrange()
	{
		return midOrange;
	}

	public int getMidRed()
	{
		return midRed;
	}

	public int getDarkBlue()
	{
		return darkBlue;
	}

	public int getDarkPurple()
	{
		return darkPurple;
	}

	public int getDarkGreen()
	{
		return darkGreen;
	}

	public int getDarkOrange()
	{
		return darkOrange;
	}

	public int getDarkRed()
	{
		return darkRed;
	}
}
