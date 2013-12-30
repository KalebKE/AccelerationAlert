package com.kircherelectronics.accelerationalert.dialog;

import com.kircherelectronics.accelerationalert.AccelerationAlertActivity;
import com.kircherelectronics.accelerationalert.R;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

public class SettingsDialog extends Dialog
{
	public SettingsDialog(final AccelerationAlertActivity context)
	{
		super(context);

		LayoutInflater inflater = (LayoutInflater) getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.settings, null);

		this.setCancelable(true);
		this.setCanceledOnTouchOutside(true);

		final EditText etThresholdMax = (EditText) view
				.findViewById(R.id.edit_text_threshold_max);
		final EditText etThresholdMin = (EditText) view
				.findViewById(R.id.edit_text_threshold_min);

		etThresholdMax.setText(String.valueOf(context.getThresholdMax()));
		etThresholdMin.setText(String.valueOf(context.getThresholdMin()));

		final EditText etThresholdCountMax = (EditText) view
				.findViewById(R.id.edit_text_threshold_max_count);
		final EditText etThresholdCountMin = (EditText) view
				.findViewById(R.id.edit_text_threshold_min_count);

		etThresholdCountMax.setText(String.valueOf(context
				.getThresholdCountMaxLimit()));
		etThresholdCountMin.setText(String.valueOf(context
				.getThresholdCountMinLimit()));

		final EditText etLPFAlpha = (EditText) view
				.findViewById(R.id.edit_text_lpf_alpha);

		etLPFAlpha.setText(String.valueOf(context.getLPFStaticAlpha()));

		Button buttonSetValue = (Button) view
				.findViewById(R.id.button_set_values);

		buttonSetValue.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				context.setThresholdMax(Float.valueOf(etThresholdMax.getText()
						.toString()));
				context.setThresholdMin(Float.valueOf(etThresholdMin.getText()
						.toString()));

				context.setThresholdMaxCountLimit(Integer
						.valueOf(etThresholdCountMax.getText().toString()));
				context.setThresholdMinCountLimit(Integer
						.valueOf(etThresholdCountMin.getText().toString()));

				context.setLPFStaticAlpha(Float.valueOf(etLPFAlpha.getText()
						.toString()));
			}
		});

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.setContentView(view);
	}
}