/*
 * TODO put header
 */
package com.kircherelectronics.accelerationalert;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;

/**
 * Configuration activity.
 */
public class ConfigActivity extends PreferenceActivity implements
		OnPreferenceChangeListener
{

	public static final String LINEAR_ACCELERATION_PREFERENCE = "linear_acceleration_preference";

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		/*
		 * Read preferences resources available at res/xml/preferences.xml
		 */
		addPreferencesFromResource(R.xml.preferences);

	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue)
	{
		return false;
	}
}