/*
 * TODO put header
 */
package com.kircherelectronics.accelerationalert;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;

/**
 * Configuration activity.
 */
public class ConfigActivity extends PreferenceActivity implements
		OnPreferenceChangeListener
{

	public static final String LINEAR_ACCELERATION_PREFERENCE = "linear_acceleration_preference";

	private Preference emailPreference;
	private PreferenceCategory emailHostCategory;
	private PreferenceCategory emailRecipientCategory;

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		/*
		 * Read preferences resources available at res/xml/preferences.xml
		 */
		addPreferencesFromResource(R.xml.preferences);

		emailHostCategory = (PreferenceCategory) findPreference("email_host_account_preferences");
		emailRecipientCategory = (PreferenceCategory) findPreference("email_recipient_account_preferences");

		emailPreference = (Preference) this
				.findPreference("email_logs_preference");

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		toggleEmailCategories(prefs.getBoolean("email_logs_preference", false));

		emailPreference.setOnPreferenceChangeListener(this);

		// Setup the email host account edit text to display the current
		// value...
		EditTextPreference editTextEmailHostAccount = (EditTextPreference) findPreference("email_host_address");
		editTextEmailHostAccount
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
				{
					public boolean onPreferenceChange(Preference preference,
							Object newValue)
					{
						preference.setSummary((String) newValue);

						return true;
					}
				});
		editTextEmailHostAccount.setSummary(prefs.getString(
				"email_host_address", "Enter Email Address"));

		// Setup the email host password edit text to display the current
		// value...
		EditTextPreference editTextEmailHostPassword = (EditTextPreference) findPreference("email_host_password");

		editTextEmailHostPassword
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
				{
					public boolean onPreferenceChange(Preference preference,
							Object newValue)
					{
						String obfuscatedPassword = "";

						for (int i = 0; i < ((String) newValue).length(); i++)
						{
							obfuscatedPassword += "•";
						}

						preference.setSummary(obfuscatedPassword);

						return true;
					}
				});

		String password = prefs.getString("email_host_password",
				"Enter Email Password");

		if (!password.equals("Enter Email Password"))
		{
			String obfuscatedPassword = "";

			for (int i = 0; i < password.length(); i++)
			{
				obfuscatedPassword += "•";
			}

			editTextEmailHostPassword.setSummary(obfuscatedPassword);
		}

		// Setup the email recipient account edit text to display the current
		// value...
		EditTextPreference editTextEmailRecipientAccount = (EditTextPreference) findPreference("email_recipient_address");
		editTextEmailRecipientAccount
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
				{
					public boolean onPreferenceChange(Preference preference,
							Object newValue)
					{
						preference.setSummary((String) newValue);

						return true;
					}
				});
		editTextEmailRecipientAccount.setSummary(prefs.getString(
				"email_recipient_address", "Enter Email Address"));
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue)
	{
		boolean enabled = (Boolean) newValue;

		Log.d("Enabled", String.valueOf(enabled));

		if (emailPreference.getKey().equals(preference.getKey()))
		{
			enabled = (Boolean) newValue;

			toggleEmailCategories(enabled);
		}

		return true;
	}

	private void toggleEmailCategories(boolean enabled)
	{
		PreferenceScreen screen = getPreferenceScreen();

		if (!enabled)
		{
			screen.removePreference(emailHostCategory);
			screen.removePreference(emailRecipientCategory);
		}
		else
		{
			screen.addPreference(emailHostCategory);
			screen.addPreference(emailRecipientCategory);
		}
	}
}