package com.kircherelectronics.accelerationalert.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

import com.kircherelectronics.accelerationalert.R;

/**
 * A settings dialog for the acceleration event settings.
 * 
 * @author Kaleb
 * 
 */
public class SettingsDialog extends Dialog
{
	private static final String tag = SettingsDialog.class.getSimpleName();

	// The default thresholds for the acceleration detection. The measured
	// acceleration must exceed the thresholds before an acceleration event will
	// start and stop.
	private float thresholdMax = 0.5f;
	private float thresholdMin = 0.15f;

	// The count thresholds for the acceleration detection. The measured
	// acceleration must exceed the thresholds for a consecutive number of
	// counts before an acceleration event will start or stop.
	private int thresholdCountMaxLimit = 3;
	private int thresholdCountMinLimit = 5;

	// The static alpha for the LPF Android Developer
	private float lpfStaticAlpha = 0.3f;

	private Context context;

	private EditText etThresholdMax;
	private EditText etThresholdMin;

	private EditText etThresholdCountMax;

	private EditText etThresholdCountMin;

	private EditText etLPFAlpha;

	// Set the prefs
	private SharedPreferences prefs;
	private SharedPreferences.Editor editor;

	public SettingsDialog(Context context)
	{
		super(context);

		this.context = context;

		prefs = this.context.getSharedPreferences("lpf_prefs",
				Activity.MODE_PRIVATE);

		editor = prefs.edit();

		readPrefs();

		LayoutInflater inflater = (LayoutInflater) getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.settings, null);

		this.setCancelable(true);
		this.setCanceledOnTouchOutside(true);

		etThresholdMax = (EditText) view
				.findViewById(R.id.edit_text_threshold_max);
		etThresholdMin = (EditText) view
				.findViewById(R.id.edit_text_threshold_min);

		etThresholdMax.setText(String.valueOf(thresholdMax));
		etThresholdMin.setText(String.valueOf(thresholdMin));

		etThresholdCountMax = (EditText) view
				.findViewById(R.id.edit_text_threshold_max_count);
		etThresholdCountMin = (EditText) view
				.findViewById(R.id.edit_text_threshold_min_count);

		etThresholdCountMax.setText(String.valueOf(thresholdCountMaxLimit));
		etThresholdCountMin.setText(String.valueOf(thresholdCountMinLimit));

		etLPFAlpha = (EditText) view.findViewById(R.id.edit_text_lpf_alpha);

		etLPFAlpha.setText(String.valueOf(lpfStaticAlpha));

		Button buttonSetValue = (Button) view
				.findViewById(R.id.button_set_values);

		buttonSetValue.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				// Set the UI
				thresholdMax = Float.valueOf(etThresholdMax.getText()
						.toString());

				thresholdMin = Float.valueOf(etThresholdMin.getText()
						.toString());

				thresholdCountMaxLimit = Integer.valueOf(etThresholdCountMax
						.getText().toString());
				thresholdCountMinLimit = Integer.valueOf(etThresholdCountMin
						.getText().toString());

				lpfStaticAlpha = Float.valueOf(etLPFAlpha.getText().toString());

				writePrefs();

				SettingsDialog.this.dismiss();
			}
		});

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.setContentView(view);
	}

	/**
	 * Read in the current user preferences.
	 */
	private void readPrefs()
	{
		thresholdMax = prefs.getFloat("threshold_max", thresholdMax);

		thresholdMin = prefs.getFloat("threshold_min", thresholdMin);

		thresholdCountMaxLimit = prefs.getInt("threshold_count_max",
				thresholdCountMaxLimit);
		thresholdCountMinLimit = prefs.getInt("threshold_count_min",
				thresholdCountMinLimit);

		lpfStaticAlpha = prefs.getFloat("lpf_alpha", lpfStaticAlpha);
	}

	private void writePrefs()
	{
		editor.putFloat("threshold_max", thresholdMax);

		editor.putFloat("threshold_min", thresholdMin);

		editor.putInt("threshold_count_max", thresholdCountMaxLimit);
		editor.putInt("threshold_count_min", thresholdCountMinLimit);

		editor.putFloat("lpf_alpha", lpfStaticAlpha);

		editor.commit();
	}
}