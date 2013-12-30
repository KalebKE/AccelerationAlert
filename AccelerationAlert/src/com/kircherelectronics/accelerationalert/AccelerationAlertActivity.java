package com.kircherelectronics.accelerationalert;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.XYPlot;
import com.kircherelectronics.accelerationalert.dialog.SettingsDialog;
import com.kircherelectronics.accelerationalert.filter.LPFAndroidDeveloper;
import com.kircherelectronics.accelerationalert.filter.LowPassFilter;
import com.kircherelectronics.accelerationalert.filter.MeanFilterByArray;
import com.kircherelectronics.accelerationalert.filter.MeanFilterByValue;
import com.kircherelectronics.accelerationalert.plot.DynamicPlot;
import com.kircherelectronics.accelerationalert.plot.PlotColor;

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
 * Implements an Activity that is intended to run low-pass filters on
 * accelerometer inputs in an attempt to find the gravity and linear
 * acceleration components of the accelerometer signal. This is accomplished by
 * using a low-pass filter to filter out signals that are shorter than a
 * pre-determined period. The result is only the long term signal, or gravity,
 * which can then be subtracted from the acceleration to find the linear
 * acceleration.
 * 
 * Currently supports two versions of IIR digital low-pass filter. The low-pass
 * filters are classified as recursive, or infinite response filters (IIR). The
 * current, nth sample output depends on both current and previous inputs as
 * well as previous outputs. It is essentially a weighted moving average, which
 * comes in many different flavors depending on the values for the coefficients,
 * a and b.
 * 
 * The first low-pass filter, the Wikipedia LPF, is an IIR single-pole
 * implementation. The coefficient, a (alpha), can be adjusted based on the
 * sample period of the sensor to produce the desired time constant that the
 * filter will act on. It takes a simple form of y[i] = y[i] + alpha * (x[i] -
 * y[i]). Alpha is defined as alpha = dt / (timeConstant + dt);) where the time
 * constant is the length of signals the filter should act on and dt is the
 * sample period (1/frequency) of the sensor.
 * 
 * The second low-pass filter, the Android Developer LPF, is an IIR single-pole
 * implementation. The coefficient, a (alpha), can be adjusted based on the
 * sample period of the sensor to produce the desired time constant that the
 * filter will act on. It is essentially the same as the Wikipedia LPF. It takes
 * a simple form of y[0] = alpha * y[0] + (1 - alpha) * x[0]. Alpha is defined
 * as alpha = timeConstant / (timeConstant + dt) where the time constant is the
 * length of signals the filter should act on and dt is the sample period
 * (1/frequency) of the sensor.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public class AccelerationAlertActivity extends Activity implements
		SensorEventListener, Runnable, OnTouchListener
{
	private static final String tag = AccelerationAlertActivity.class
			.getSimpleName();

	// Plot keys for the LPF Android Developer plot
	private final static int PLOT_LPF_MAGNITUDE_KEY = 0;

	private final static int WINDOW_SIZE = 150;

	private boolean linearAccelerationActive = true;

	private boolean plotTaskActive = false;
	private boolean logTaskActive = false;

	private boolean start = false;

	private boolean accelerationEventActive = false;

	private boolean filterReady = false;

	// Indicate if a static alpha should be used for the LPF Android Developer
	private boolean staticAlpha = true;

	// Decimal formats for the UI outputs
	private DecimalFormat df;

	// Graph plot for the UI outputs
	private DynamicPlot dynamicPlot;

	// The static alpha for the LPF Android Developer
	private float lpfStaticAlpha = 0.99f;

	// Touch to zoom constants for the dynamicPlot
	private float distance = 0;
	private float zoom = 1.2f;

	private float thresholdMax = 0.5f;
	private float thresholdMin = 0.15f;
	private float magnitude = 0;

	private float frequency = 0;
	private float dt = 0;

	// Outputs for the acceleration and LPFs
	private float[] acceleration = new float[3];
	private float[] lpfAcceleration = new float[3];

	// Handler for the UI plots so everything plots smoothly
	private Handler handler;

	// The generation of the log output
	private int generation = 0;

	private int thresholdCountMax = 0;
	private int thresholdCountMin = 0;

	private int thresholdCountMaxLimit = 3;
	private int thresholdCountMinLimit = 5;

	private int filterCount = 0;

	// Color keys for the LPF Android Developer plot
	private int plotLPFMagnitudeAxisColor;

	private long timeOld = 0;

	private LinkedList<Number> plotMagnitudeList;

	private Iterator<Number> logMagnitudeIterator;
	private Iterator<Long> logTimeStampIterator;

	private LinkedList<Long> timeStampList;
	private LinkedList<Number> magnitudeList;

	private MeanFilterByValue meanFilterMagnitude;
	private MeanFilterByArray meanFilterAcceleration;

	private LowPassFilter lowPassFilter;

	// Plot colors
	private PlotColor color;

	// Sensor manager to access the accelerometer sensor
	private SensorManager sensorManager;

	// LPF Android Developer plot tiltes
	private String plotLPFMagnitudeAxisTitle = "Magnitude";

	// Output log
	private String log;

	// Acceleration UI outputs
	private TextView textViewXAxis;
	private TextView textViewYAxis;
	private TextView textViewZAxis;

	private TextView textViewFrequency;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.plot_sensor_activity);

		// Read in the saved prefs
		readPrefs();

		// Get the sensor manager ready
		sensorManager = (SensorManager) this
				.getSystemService(Context.SENSOR_SERVICE);

		meanFilterMagnitude = new MeanFilterByValue();
		meanFilterMagnitude.setWindowSize(10);

		meanFilterAcceleration = new MeanFilterByArray();
		meanFilterAcceleration.setWindowSize(10);

		timeStampList = new LinkedList<Long>();
		magnitudeList = new LinkedList<Number>();

		final Button startButton = (Button) this
				.findViewById(R.id.button_start);

		startButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				start = !start;

				if (start)
				{
					startButton
							.setBackgroundResource(R.drawable.stop_button_background);
					startButton.setText("Stop");
				}
				else
				{
					startButton
							.setBackgroundResource(R.drawable.start_button_background);
					startButton.setText("Start");
				}

			}
		});

		initTextViewOutputs();

		initFilters();

		// Initialize the plots
		initColor();
		initPlots();

		handler = new Handler();
	}

	public float getLPFStaticAlpha()
	{
		return this.lpfStaticAlpha;
	}

	public float getThresholdMax()
	{
		return this.thresholdMax;
	}

	public float getThresholdMin()
	{
		return this.thresholdMin;
	}

	public int getThresholdCountMaxLimit()
	{
		return this.thresholdCountMaxLimit;
	}

	public int getThresholdCountMinLimit()
	{
		return this.thresholdCountMinLimit;
	}

	@Override
	public void onPause()
	{
		super.onPause();

		sensorManager.unregisterListener(this);

		writePrefs();

		handler.removeCallbacks(this);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		readPrefs();

		handler.post(this);

		// Register for sensor updates.
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
		// Not used...
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		// Initialize the timestamp.
		if (timeOld == 0)
		{
			timeOld = event.timestamp;
		}

		// Get dt in units of seconds...
		dt = (float) ((event.timestamp - timeOld) / 1000000000.0);

		// Get the frequency...
		frequency = 1 / dt;

		textViewFrequency.setText(df.format(frequency));

		timeOld = event.timestamp;

		if (!plotTaskActive && !logTaskActive)
		{
			// Get a local copy of the sensor values
			System.arraycopy(event.values, 0, acceleration, 0,
					event.values.length);

			acceleration[0] = acceleration[0] / SensorManager.GRAVITY_EARTH;
			acceleration[1] = acceleration[1] / SensorManager.GRAVITY_EARTH;
			acceleration[2] = acceleration[2] / SensorManager.GRAVITY_EARTH;

			acceleration = meanFilterAcceleration.filterFloat(acceleration);

			if (linearAccelerationActive)
			{
				lpfAcceleration = lowPassFilter.addSamples(acceleration);

				// Magnitude should be equal to zero because the low pass filter
				// should
				// have accounted for gravity...
				magnitude = meanFilterMagnitude.filterFloat((float) Math
						.sqrt(Math.pow(lpfAcceleration[0], 2)
								+ Math.pow(lpfAcceleration[1], 2)
								+ Math.pow(lpfAcceleration[2], 2)));
			}
			else
			{
				magnitude = event.values[2] / SensorManager.GRAVITY_EARTH;
			}

			// Add values to our lists
			magnitudeList.addLast(magnitude);
			timeStampList.addLast(event.timestamp);

			// Enforce our rolling window...
			if (magnitudeList.size() > WINDOW_SIZE)
			{
				magnitudeList.removeFirst();
			}
			if (timeStampList.size() > WINDOW_SIZE)
			{
				timeStampList.removeFirst();
			}

			if (!filterReady)
			{
				filterCount++;
			}

			if (filterCount > 50)
			{
				filterReady = true;
			}

			if (start)
			{
				// Only attempt logging of an acceleration event if the
				// magnitude is
				// larger than the threshold, we aren't already in a
				// acceleration
				// event and the filters are ready.
				if (magnitude > thresholdMax && filterReady)
				{
					thresholdCountMax++;

					// Get more than five consecutive measurements above the
					// signal
					// threshold to activate the logging.
					if (thresholdCountMax > thresholdCountMaxLimit)
					{
						accelerationEventActive = true;

						// If we get an event active, reset the minimum
						// threshold
						// count.
						thresholdCountMin = 0;
					}
				}
				else if (magnitude < thresholdMin && accelerationEventActive)
				{
					thresholdCountMin++;

					// Get more than ten consecutive measurements below the
					// signal threshold to deactivate activate the logging.
					if (thresholdCountMin > thresholdCountMinLimit)
					{
						accelerationEventActive = false;

						PlotTask plotTask = new PlotTask();
						plotTask.execute();

						LogTask logTask = new LogTask();
						logTask.execute();

						thresholdCountMin = 0;
						thresholdCountMax = 0;
					}
				}
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.settings_logger_menu, menu);
		return true;
	}

	/**
	 * Event Handling for Individual menu item selected Identify single menu
	 * item by it's id
	 * */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{

		// Log the data
		case R.id.action_settings:
			showSettingsDialog();
			return true;

			// Log the data
		case R.id.action_config:
			Intent configIntent = new Intent(this, ConfigActivity.class);
			startActivity(configIntent);
			return true;

		case R.id.action_help:
			showHelpDialog();
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Pinch to zoom.
	 */
	@Override
	public boolean onTouch(View v, MotionEvent e)
	{
		// MotionEvent reports input details from the touch screen
		// and other input controls.
		float newDist = 0;

		switch (e.getAction())
		{

		case MotionEvent.ACTION_MOVE:

			// pinch to zoom
			if (e.getPointerCount() == 2)
			{
				if (distance == 0)
				{
					distance = fingerDist(e);
				}

				newDist = fingerDist(e);

				zoom *= distance / newDist;

				dynamicPlot.setMaxRange(zoom * Math.log(zoom));
				dynamicPlot.setMinRange(-zoom * Math.log(zoom));

				distance = newDist;
			}
		}

		return false;
	}

	/**
	 * Output and logs are run on their own thread to keep the UI from hanging
	 * and the output smooth.
	 */
	@Override
	public void run()
	{
		handler.postDelayed(this, 32);

		updateTextViewOutputs();
	}

	public void setLPFStaticAlpha(float alpha)
	{
		this.lpfStaticAlpha = alpha;

		TextView textViewAlpha = (TextView) this
				.findViewById(R.id.value_lpf_alpha);

		textViewAlpha.setText(String.valueOf(this.lpfStaticAlpha));
	}

	public void setThresholdMax(float thresholdMax)
	{
		this.thresholdMax = thresholdMax;

		TextView textViewThresholdMax = (TextView) this
				.findViewById(R.id.value_max_threshold);

		textViewThresholdMax.setText(String.valueOf(this.thresholdMax));
	}

	public void setThresholdMin(float thresholdMin)
	{
		this.thresholdMin = thresholdMin;

		TextView textViewThresholdMin = (TextView) this
				.findViewById(R.id.value_min_threshold);

		textViewThresholdMin.setText(String.valueOf(this.thresholdMin));
	}

	public void setThresholdMaxCountLimit(int thresholdCountMaxLimit)
	{
		this.thresholdCountMaxLimit = thresholdCountMaxLimit;

		TextView textViewThresholdCountMax = (TextView) this
				.findViewById(R.id.value_max_threshold_count);

		textViewThresholdCountMax.setText(String
				.valueOf(this.thresholdCountMaxLimit));
	}

	public void setThresholdMinCountLimit(int thresholdCountMinLimit)
	{
		this.thresholdCountMinLimit = thresholdCountMinLimit;

		TextView textViewThresholdCountMin = (TextView) this
				.findViewById(R.id.value_min_threshold_count);

		textViewThresholdCountMin.setText(String
				.valueOf(this.thresholdCountMinLimit));
	}

	/**
	 * Add the Android Developer LPF plot.
	 */
	private void addLPFMagnitudePlot()
	{
		addPlot(plotLPFMagnitudeAxisTitle, PLOT_LPF_MAGNITUDE_KEY,
				plotLPFMagnitudeAxisColor);
	}

	/**
	 * Add a plot to the graph.
	 * 
	 * @param title
	 *            The name of the plot.
	 * @param key
	 *            The unique plot key
	 * @param color
	 *            The color of the plot
	 */
	private void addPlot(String title, int key, int color)
	{
		dynamicPlot.addSeriesPlot(title, key, color);
	}

	/**
	 * Create the plot colors.
	 */
	private void initColor()
	{
		color = new PlotColor(this);

		plotLPFMagnitudeAxisColor = color.getLightGreen();
	}

	/**
	 * Initialize the filters.
	 */
	private void initFilters()
	{
		lowPassFilter = new LPFAndroidDeveloper();

		lowPassFilter.setAlphaStatic(staticAlpha);
		lowPassFilter.setAlpha(lpfStaticAlpha);
	}

	/**
	 * Initialize the plots.
	 */
	private void initPlots()
	{
		View view = findViewById(R.id.ScrollView01);
		view.setOnTouchListener(this);

		// Create the graph plot
		XYPlot plot = (XYPlot) findViewById(R.id.plot_sensor);
		plot.setTitle("Acceleration");
		dynamicPlot = new DynamicPlot(plot);
		dynamicPlot.setMaxRange(1);
		dynamicPlot.setMinRange(0);

		addLPFMagnitudePlot();
	}

	/**
	 * Initialize the Text View Sensor Outputs.
	 */
	private void initTextViewOutputs()
	{
		// Create the acceleration UI outputs
		textViewXAxis = (TextView) findViewById(R.id.value_x_axis);
		textViewYAxis = (TextView) findViewById(R.id.value_y_axis);
		textViewZAxis = (TextView) findViewById(R.id.value_z_axis);

		textViewFrequency = (TextView) findViewById(R.id.value_sensor_frequency);

		// Format the UI outputs so they look nice
		df = new DecimalFormat("#.##");
	}

	private void playNotification()
	{
		// Play a beep noise...
		try
		{
			Uri notification = RingtoneManager
					.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
			Ringtone r = RingtoneManager.getRingtone(getApplicationContext(),
					notification);
			r.play();
		}
		catch (Exception e)
		{
		}
	}

	/**
	 * Plot the output data in the UI.
	 */
	private void plotData()
	{
		dynamicPlot.setDataFromList(plotMagnitudeList, PLOT_LPF_MAGNITUDE_KEY);

		dynamicPlot.draw();
	}

	private void updateTextViewOutputs()
	{
		if (linearAccelerationActive)
		{
			// Update the view with the new acceleration data
			textViewXAxis.setText(df.format(lpfAcceleration[0]));
			textViewYAxis.setText(df.format(lpfAcceleration[1]));
			textViewZAxis.setText(df.format(lpfAcceleration[2]));
		}
		else
		{
			// Update the view with the new acceleration data
			textViewXAxis.setText(df.format(acceleration[0]));
			textViewYAxis.setText(df.format(acceleration[1]));
			textViewZAxis.setText(df.format(acceleration[2]));
		}
	}

	/**
	 * Log output data to an external .csv file.
	 */
	private void logData()
	{

		runOnUiThread(new Runnable()
		{
			public void run()
			{
				CharSequence text = "Logging Data";
				int duration = Toast.LENGTH_SHORT;

				Toast toast = Toast.makeText(AccelerationAlertActivity.this,
						text, duration);
				toast.show();
			}
		});

		String headers = "Generation" + ",";

		headers += "Timestamp" + ",";

		headers += this.plotLPFMagnitudeAxisTitle + ",";

		log = headers + "\n";

		long startTime = 0;

		while (logTimeStampIterator.hasNext() && logMagnitudeIterator.hasNext())
		{
			long time = logTimeStampIterator.next();

			if (startTime == 0)
			{
				startTime = time;
			}

			log += System.getProperty("line.separator");
			log += generation++ + ",";
			log += ((time - startTime) / 1000000000.0f) + ",";
			log += logMagnitudeIterator.next();
		}

		writeLogToFile();
	}

	/**
	 * Write the logged data out to a persisted file.
	 */
	private void writeLogToFile()
	{
		Calendar c = Calendar.getInstance();
		String filename = "AccelerationAlert-" + c.get(Calendar.YEAR) + "-"
				+ c.get(Calendar.DAY_OF_WEEK_IN_MONTH) + "-"
				+ c.get(Calendar.HOUR) + "-" + c.get(Calendar.HOUR) + "-"
				+ c.get(Calendar.MINUTE) + "-" + c.get(Calendar.SECOND)
				+ ".csv";

		File dir = new File(Environment.getExternalStorageDirectory()
				+ File.separator + "AccelerationAlert" + File.separator
				+ "Logs" + File.separator + "Acceleration");

		if (!dir.exists())
		{
			dir.mkdirs();
		}

		File file = new File(dir, filename);

		FileOutputStream fos;
		byte[] data = log.getBytes();
		try
		{
			fos = new FileOutputStream(file);
			fos.write(data);
			fos.flush();
			fos.close();

			runOnUiThread(new Runnable()
			{
				public void run()
				{
					CharSequence text = "Log Saved";
					int duration = Toast.LENGTH_SHORT;

					Toast toast = Toast.makeText(
							AccelerationAlertActivity.this, text, duration);
					toast.show();
				}
			});

		}
		catch (FileNotFoundException e)
		{
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					CharSequence text = "Log Error";
					int duration = Toast.LENGTH_SHORT;

					Toast toast = Toast.makeText(
							AccelerationAlertActivity.this, text, duration);
					toast.show();
				}
			});
		}
		catch (IOException e)
		{
			// handle exception
		}
		finally
		{
			// Update the MediaStore so we can view the file without rebooting.
			// Note that it appears that the ACTION_MEDIA_MOUNTED approach is
			// now blocked for non-system apps on Android 4.4.
			MediaScannerConnection.scanFile(this, new String[]
			{ "file://" + Environment.getExternalStorageDirectory() }, null,
					null);
		}
	}

	/**
	 * Get the distance between fingers for the touch to zoom.
	 * 
	 * @param event
	 * @return
	 */
	private final float fingerDist(MotionEvent event)
	{
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return (float) Math.sqrt(x * x + y * y);
	}

	/**
	 * Read in the current user preferences.
	 */
	private void readPrefs()
	{
		SharedPreferences prefs = this.getSharedPreferences("lpf_prefs",
				Activity.MODE_PRIVATE);

		setThresholdMax(prefs.getFloat("threshold_max", thresholdMax));
		setThresholdMin(prefs.getFloat("threshold_min", thresholdMin));

		setThresholdMaxCountLimit(prefs.getInt("threshold_count_max",
				thresholdCountMaxLimit));
		setThresholdMinCountLimit(prefs.getInt("threshold_count_min",
				thresholdCountMinLimit));

		setLPFStaticAlpha(prefs.getFloat("lpf_alpha", lpfStaticAlpha));

		linearAccelerationActive = prefs.getBoolean(
				ConfigActivity.LINEAR_ACCELERATION_PREFERENCE, true);
	}

	private void showHelpDialog()
	{
		Dialog helpDialog = new Dialog(this);
		helpDialog.setCancelable(true);
		helpDialog.setCanceledOnTouchOutside(true);

		helpDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

		helpDialog.setContentView(getLayoutInflater().inflate(R.layout.help,
				null));

		helpDialog.show();
	}

	private void writePrefs()
	{
		SharedPreferences prefs = this.getSharedPreferences("lpf_prefs",
				Activity.MODE_PRIVATE);

		prefs.edit().putFloat("threshold_max", thresholdMax);
		prefs.edit().putFloat("threshold_min", thresholdMin);

		prefs.edit().putInt("threshold_count_max", thresholdCountMaxLimit);
		prefs.edit().putInt("threshold_count_min", thresholdCountMinLimit);

		prefs.edit().putFloat("lpf_alpha", lpfStaticAlpha);

		prefs.edit().commit();
	}

	private void showSettingsDialog()
	{
		SettingsDialog dialog = new SettingsDialog(this);
		dialog.show();
	}

	private class PlotTask extends AsyncTask<Void, Void, Void>
	{

		@Override
		protected Void doInBackground(Void... params)
		{
			plotTaskActive = true;

			plotMagnitudeList = (LinkedList<Number>) magnitudeList.clone();

			plotData();

			return null;
		}

		@Override
		protected void onPostExecute(Void params)
		{
			plotTaskActive = false;
		}

	}

	private class LogTask extends AsyncTask<Void, Void, Void>
	{

		@Override
		protected Void doInBackground(Void... params)
		{
			logTaskActive = true;

			logMagnitudeIterator = magnitudeList.iterator();
			logTimeStampIterator = timeStampList.iterator();

			logData();

			return null;
		}

		@Override
		protected void onPostExecute(Void params)
		{
			logTaskActive = false;

			playNotification();
		}

	}
}
