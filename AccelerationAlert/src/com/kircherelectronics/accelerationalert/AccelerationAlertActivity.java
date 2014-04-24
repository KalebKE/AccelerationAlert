package com.kircherelectronics.accelerationalert;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaScannerConnection;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.XYPlot;
import com.kircherelectronics.accelerationalert.dialog.SettingsDialog;
import com.kircherelectronics.accelerationalert.filter.LPFAndroidDeveloper;
import com.kircherelectronics.accelerationalert.filter.LowPassFilter;
import com.kircherelectronics.accelerationalert.filter.MeanFilterByArray;
import com.kircherelectronics.accelerationalert.filter.MeanFilterByValue;
import com.kircherelectronics.accelerationalert.mail.Mail;
import com.kircherelectronics.accelerationalert.plot.DynamicPlot;
import com.kircherelectronics.accelerationalert.plot.PlotColor;

/**
 * Implements an Activity that is intended to run low-pass filters on
 * accelerometer inputs in an attempt to find the gravity and linear
 * acceleration components of the accelerometer signal. This is accomplished by
 * using a low-pass filter to filter out signals that are shorter than a
 * pre-determined period. The result is only the long term signal, or gravity,
 * which can then be subtracted from the acceleration to find the linear
 * acceleration.
 * 
 * Currently supports a version of an IIR digital low-pass filter. The low-pass
 * filters are classified as recursive, or infinite response filters (IIR). The
 * current, nth sample output depends on both current and previous inputs as
 * well as previous outputs. It is essentially a weighted moving average, which
 * comes in many different flavors depending on the values for the coefficients,
 * a and b.
 * 
 * The Android Developer LPF, is an IIR single-pole implementation. The
 * coefficient, a (alpha), can be adjusted based on the sample period of the
 * sensor to produce the desired time constant that the filter will act on. It
 * takes a simple form of y[0] = alpha * y[0] + (1 - alpha) * x[0]. Alpha is
 * defined as alpha = timeConstant / (timeConstant + dt) where the time constant
 * is the length of signals the filter should act on and dt is the sample period
 * (1/frequency) of the sensor.
 * 
 * AccelerationAlertActivity is intended to monitor the output from the LPF for
 * acceleration events. An acceleration event is defined as a period of linear
 * acceleration that exceeds a defined threshold for prescribed period of time.
 * 
 * A rolling window of the acceleration is kept for defined period of time. If
 * an acceleration event is detected, the rolling window is used to record the
 * acceleration leading up to the acceleration event and the event itself. The
 * maximum acceleration, location, velocity and time are also recorded and
 * persisted via a .csv log file.
 * 
 * In addition to the log file, a .csv meta file is created for each trip. A
 * trip consists of zero-to-many acceleration events. The meta file contains a
 * start location, a stop location, a route, time stamps, the number of
 * acceleration events classified by the magnitude of the acceleration and an
 * opinion of the safety of the trip from the driver.
 * 
 * Both the log and meta files can be emailed transparently to a preconfigured
 * email address after each trip.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public class AccelerationAlertActivity extends Activity implements
		SensorEventListener, Runnable, OnTouchListener, LocationListener
{
	private static final String tag = AccelerationAlertActivity.class
			.getSimpleName();

	// Plot keys for the LPF Android Developer plot
	private final static int PLOT_LPF_MAGNITUDE_KEY = 0;

	private final static int WINDOW_SIZE = 150;

	// .csv log header labels
	private final static String LOG_MAGNITUDE_AXIS_TITLE = "Magnitude";
	private final static String LOG_EVENT_LAT_AXIS_TITLE = "Event Latitude";
	private final static String LOG_EVENT_LON_AXIS_TITLE = "Event Longitude";
	private final static String LOG_EVENT_TIME_AXIS_TITLE = "Event Time";
	private final static String LOG_EVENT_MAX_ACCELERATION_AXIS_TITLE = "Event Max Acceleration";
	private final static String LOG_EVENT_VELOCITY_AXIS_TITLE = "Event Velocity";
	private final static String LOG_EVENT_MAX_THRESHOLD_AXIS_TITLE = "Max Threshold";
	private final static String LOG_EVENT_MIN_THRESHOLD_AXIS_TITLE = "Min Threshold";
	private final static String LOG_EVENT_MAX_COUNT_AXIS_TITLE = "Max Count";
	private final static String LOG_EVENT_MIN_COUNT_AXIS_TITLE = "Min Count";
	private final static String LOG_EVENT_ALPHA_AXIS_TITLE = "Alpha";

	// .csv meta header labels
	private final static String META_TIME_AXIS_TITLE = "Time";
	private final static String META_LAT_AXIS_TITLE = "Latitude";
	private final static String META_LON_AXIS_TITLE = "Longitude";
	private final static String META_START_LAT_AXIS_TITLE = "Start Latitude";
	private final static String META_START_LON_AXIS_TITLE = "Start Longitude";
	private final static String META_START_TIME_AXIS_TITLE = "Start Time";
	private final static String META_STOP_LAT_AXIS_TITLE = "Stop Latitude";
	private final static String META_STOP_LON_AXIS_TITLE = "Stop Longitude";
	private final static String META_STOP_TIME_AXIS_TITLE = "Stop Time";
	private final static String META_EVENT_COUNT_0_TITLE = "0.2 G";
	private final static String META_EVENT_COUNT_1_TITLE = "0.3 G";
	private final static String META_EVENT_COUNT_2_TITLE = "0.4 G";
	private final static String META_EVENT_COUNT_3_TITLE = "0.5 G";
	private final static String META_SAFE_EVENT_TITLE = "Safe Trip";
	private final static String META_UNSAFE_EVENT_TITLE = "Unsafe Trip";
	private final static String META_NEUTRAL_EVENT_TITLE = "Neutral Trip";

	// Keep track of active acceleration events.
	private boolean accelerationEventActive = false;

	// Indicate the Acceleration LPF is ready.
	private boolean filterReady = false;

	// Indicate that linear acceleration is active.
	private boolean linearAccelerationActive = true;

	// Indicate that the start location has been acquired. This is the location
	// when the start button is pressed.
	private boolean locationStartAcquired = false;
	// Indicate that the stop location has been acquired. This is the location
	// when the start button is pressed.
	private boolean locationStopAcquired = false;
	// Indicate that the event location has been acquired. This is the location
	// when an acceleration event occurs.
	private boolean locationEventAcquired = false;

	// Keep track of the log task.
	private boolean logTaskActive = false;
	// Keep track of the plot task.
	private boolean plotTaskActive = false;

	// Indicate if a static alpha should be used for the LPF Android Developer
	private boolean staticAlpha = true;

	// Keep track of when the acceleration event search is active.
	private boolean start = false;

	// Indicate that emails containing the logs should be sent when the trip
	// ends.
	private boolean emailLog = false;

	// Indicate the drivers impression on the safety of the trip.
	private boolean tripSafe = false;
	private boolean tripUnsafe = false;
	private boolean tripNeutral = false;

	// Keep track of the latitudes.
	private double latitudeStart = 0.0;
	private double latitudeStop = 0.0;
	private double latitudeEvent = 0.0;

	// Keep track of the longitudes.
	private double longitudeStart = 0.0;
	private double longitudeStop = 0.0;
	private double longitudeEvent = 0.0;

	private double velocityEvent = 0.0;

	// The static alpha for the LPF Android Developer
	private float lpfStaticAlpha = 0.3f;

	// Touch to zoom constants for the dynamicPlot
	private float distance = 0;
	private float zoom = 1.2f;

	// Keep track of the maximum acceleration for each log.
	private float maxAcceleration = 0;

	// The default thresholds for the acceleration detection. The measured
	// acceleration must exceed the thresholds before an acceleration event will
	// start and stop.
	private float thresholdMax = 0.5f;
	private float thresholdMin = 0.15f;

	// The magnitude of the acceleration.
	private float magnitude = 0;

	// The frequency and delta time from the acceleration sensor.
	private float frequency = 0;
	private float dt = 0;

	// Outputs for the acceleration and LPFs
	private float[] acceleration = new float[3];
	private float[] lpfAcceleration = new float[3];

	// Keep track of the number of acceleration events.
	private int eventCount0 = 0;
	private int eventCount1 = 0;
	private int eventCount2 = 0;
	private int eventCount3 = 0;

	// The generation of the log output
	private int generation = 0;

	// The counts for the acceleration detection. The measured
	// acceleration must exceed the thresholds for a consecutive number of
	// counts before an acceleration event will start or stop.
	private int thresholdCountMax = 0;
	private int thresholdCountMin = 0;

	// The count thresholds for the acceleration detection. The measured
	// acceleration must exceed the thresholds for a consecutive number of
	// counts before an acceleration event will start or stop.
	private int thresholdCountMaxLimit = 3;
	private int thresholdCountMinLimit = 5;

	// A number of samples that have been passed through the low-pass-filter.
	private int filterCount = 0;

	// Color keys for the LPF Android Developer plot
	private int plotLPFMagnitudeAxisColor;

	// Keep track of the time stamps.
	private long timeEvent = 0;
	private long timeStart = 0;
	private long timeStop = 0;
	private long timeOld = 0;

	// Time stamp to update the GPS after a certain time period.
	private long gpsTimeStamp = 0;

	// Keep track of all the logs for one trip.
	private ArrayList<File> logs;

	// Keep track of the route for one trip.
	private ArrayList<Double> latitudes;
	private ArrayList<Double> longitudes;
	private ArrayList<Long> timeStamps;

	// Decimal formats for the UI outputs
	private DecimalFormat df;

	// Graph plot for the UI outputs
	private DynamicPlot dynamicPlot;

	// Handler for the UI plots so everything plots smoothly
	private Handler handler;

	// We need to register for Location updates.
	private LocationManager locationManager;

	// Keep track of the rolling window of plot magnitudes.
	private LinkedList<Number> plotMagnitudeList;

	// Iterators for manipulating, plotting and persisting the data.
	private Iterator<Number> logMaxMagnitudeIterator;
	private Iterator<Number> logMagnitudeIterator;
	private Iterator<Long> logTimeStampIterator;

	// Keep track of the rolling window for data log magnitudes.
	private LinkedList<Long> timeStampList;
	private LinkedList<Number> magnitudeList;

	// The location of the device.
	private Location location;

	// Mean filters for smoothing the data.
	private MeanFilterByValue meanFilterMagnitude;
	private MeanFilterByArray meanFilterAcceleration;

	// The low pass filter for finding the linear acceleration.
	private LowPassFilter lowPassFilter;

	// Plot colors
	private PlotColor color;

	// Sensor manager to access the accelerometer sensor
	private SensorManager sensorManager;

	// Meta data log.
	private String meta;

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

		logs = new ArrayList<File>();
		latitudes = new ArrayList<Double>();
		longitudes = new ArrayList<Double>();
		timeStamps = new ArrayList<Long>();

		// Get the location manager
		locationManager = (LocationManager) this
				.getSystemService(Context.LOCATION_SERVICE);

		// Get the sensor manager
		sensorManager = (SensorManager) this
				.getSystemService(Context.SENSOR_SERVICE);

		// Linked lists to generate the log files...
		timeStampList = new LinkedList<Long>();
		magnitudeList = new LinkedList<Number>();

		// The start/stop button...
		final Button startButton = (Button) this
				.findViewById(R.id.button_start);

		// Listener for the start/stop button that starts and stops the
		// application...
		startButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				start = !start;

				if (start)
				{
					// When started, change the button to a stop button...
					startButton
							.setBackgroundResource(R.drawable.stop_button_background);
					startButton.setText("Stop");

					// Get the start time...
					timeStart = System.currentTimeMillis();

					// Indicate that a start location is needed...
					locationStartAcquired = false;
				}
				else
				{
					// When stopped, change the button to a start button...
					startButton
							.setBackgroundResource(R.drawable.start_button_background);
					startButton.setText("Start");

					// Show a dialog that asks the user to share their
					// impression of the safety of the trip.
					showSafetyDialog();
				}

			}
		});

		// Initialize the text views for the UI...
		initTextViewOutputs();

		// Initialize the filters...
		initFilters();

		// Initialize the plots
		initColor();
		initPlots();

		// Get a handler to manage the UI update frequency...
		handler = new Handler();

		// Keep the window open...
		getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	/**
	 * Get the low-pass-filter static alpha.
	 * 
	 * @return The static alpha.
	 */
	public float getLPFStaticAlpha()
	{
		return this.lpfStaticAlpha;
	}

	/**
	 * Return the number of consecutive acceleration measurements that exceed
	 * the maximum threshold for an acceleration event to begin.
	 * 
	 * @return The maximum threshold count.
	 */
	public int getThresholdCountMaxLimit()
	{
		return this.thresholdCountMaxLimit;
	}

	/**
	 * Return the number of consecutive acceleration measurements that are below
	 * the minimum threshold for an acceleration event to stop.
	 * 
	 * @return The minimum threshold count.
	 */
	public int getThresholdCountMinLimit()
	{
		return this.thresholdCountMinLimit;
	}

	/**
	 * Return the maximum threshold that the acceleration must exceed for an
	 * acceleration event to begin.
	 * 
	 * @return The maximum acceleration threshold.
	 */
	public float getThresholdMax()
	{
		return this.thresholdMax;
	}

	/**
	 * Return the minimum threshold that the acceleration must be below for an
	 * acceleration event to stop.
	 * 
	 * @return The maximum acceleration threshold.
	 */
	public float getThresholdMin()
	{
		return this.thresholdMin;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.settings_logger_menu, menu);
		return true;
	}

	/**
	 * Keep track of the location of the device with the GPS sensor.
	 */
	@Override
	public void onLocationChanged(Location location)
	{
		this.location = location;

		// If the model was started and a start location has not been set.
		if (start && !locationStartAcquired)
		{
			// Get the latitude and longitude of the start position.
			latitudeStart = this.location.getLatitude();
			longitudeStart = this.location.getLongitude();

			locationStartAcquired = true;
			locationStopAcquired = false;
		}

		// Get the position of the device every 30 seconds.
		if (start && System.currentTimeMillis() - gpsTimeStamp > 30000)
		{
			// Store the route in their respective sets.
			latitudes.add(this.location.getLatitude());
			longitudes.add(this.location.getLongitude());
			timeStamps.add(this.location.getTime());

			// Keep track of the time between updates.
			gpsTimeStamp = System.currentTimeMillis();
		}
	}

	@Override
	public void onPause()
	{
		super.onPause();

		// Stop our sensors.
		locationManager.removeUpdates(this);
		sensorManager.unregisterListener(this);

		// Stop the plot handler.
		handler.removeCallbacks(this);

		// Write out the prefs.
		writePrefs();
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

		// Show the settings dialog for the acceleration event detection.
		case R.id.action_settings:
			showSettingsDialog();
			return true;

			// Show the configuration for the application
		case R.id.action_config:
			Intent configIntent = new Intent(this, ConfigActivity.class);
			startActivity(configIntent);
			return true;

			// Show the help dialog.
		case R.id.action_help:
			showHelpDialog();
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();

		// Read the prefs.
		readPrefs();

		// Register for location updates.
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,
				0, this);

		// Register for acceleration updates.
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_NORMAL);

		// Start our UI handler.
		handler.post(this);
	}

	/**
	 * Process our acceleration sensor measurements.
	 */
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

		// Update the UI with the sensor frequency...
		textViewFrequency.setText(df.format(frequency));

		// Reset our timestamp.
		timeOld = event.timestamp;

		// Only process the acceleration when the application is not already
		// processing a plot or a log from a previous acceleration event.
		if (!plotTaskActive && !logTaskActive)
		{
			// Get a local copy of the sensor values
			System.arraycopy(event.values, 0, acceleration, 0,
					event.values.length);

			// Convert from meters/sec^2 to gravities of earth. Be aware that
			// this is not a perfect estimation because the acceleration sensor
			// is imperfect at measuring gravity.
			acceleration[0] = acceleration[0] / SensorManager.GRAVITY_EARTH;
			acceleration[1] = acceleration[1] / SensorManager.GRAVITY_EARTH;
			acceleration[2] = acceleration[2] / SensorManager.GRAVITY_EARTH;

			// Smooth the acceleration measurement with a mean filter.
			acceleration = meanFilterAcceleration.filterFloat(acceleration);

			// If the user has indicated that the linear acceleration should be
			// used, attempt to estimate the linear acceleration.
			if (linearAccelerationActive)
			{
				// Use the low-pass-filter to estimate linear acceleration.
				lpfAcceleration = lowPassFilter.addSamples(acceleration);

				// Assuming the device is static, the magnitude should be equal
				// to zero because the low pass filter should have accounted for
				// gravity...
				magnitude = meanFilterMagnitude.filterFloat((float) Math
						.sqrt(Math.pow(lpfAcceleration[0], 2)
								+ Math.pow(lpfAcceleration[1], 2)
								+ Math.pow(lpfAcceleration[2], 2)));
			}
			else
			{
				// If we are just using the raw acceleration, the magnitude will
				// be equal to the acceleration on the z-axis.
				magnitude = acceleration[2];
			}

			// Add values to our lists so we can keep a moving window of the
			// recent acceleration activity.
			magnitudeList.addLast(magnitude);
			timeStampList.addLast(event.timestamp);

			// Enforce our rolling window on the lists...
			if (magnitudeList.size() > WINDOW_SIZE)
			{
				magnitudeList.removeFirst();
			}
			if (timeStampList.size() > WINDOW_SIZE)
			{
				timeStampList.removeFirst();
			}

			// Keep track of the number of samples that have been processed
			// while the filters are initializing...
			if (!filterReady)
			{
				filterCount++;
			}

			// We want to process at least 50 samples before the filters are
			// ready to be used.
			if (filterCount > 50)
			{
				filterReady = true;
			}

			// Only look for acceleration events when we are in start mode.
			if (start)
			{
				// Only attempt logging of an acceleration event if the
				// magnitude is larger than the threshold, we aren't already in
				// a acceleration event and the filters are ready.
				if (magnitude > thresholdMax && filterReady)
				{
					thresholdCountMax++;

					// Get more than five consecutive measurements above the
					// signal threshold to activate the logging.
					if (thresholdCountMax > thresholdCountMaxLimit)
					{
						accelerationEventActive = true;

						// Get the location of the acceleration event.
						if (!locationEventAcquired)
						{
							if (this.location != null)
							{
								latitudeEvent = this.location.getLatitude();
								longitudeEvent = this.location.getLongitude();
								velocityEvent = this.location.getSpeed();
								timeEvent = this.location.getTime();

								locationEventAcquired = true;
							}
						}

						// If we get an event active, reset the minimum
						// threshold count.
						thresholdCountMin = 0;
					}
				}
				else if (magnitude < thresholdMin && accelerationEventActive)
				{
					thresholdCountMin++;

					// Get more than ten consecutive measurements below the
					// signal threshold to end the acceleration event.
					if (thresholdCountMin > thresholdCountMinLimit)
					{
						// When the acceleration event stops we need to...

						// Reset the acceleration event and location of the
						// event.
						accelerationEventActive = false;
						locationEventAcquired = false;

						// Plot the acceleration event on the UI...
						PlotTask plotTask = new PlotTask();
						plotTask.execute();

						// Write the data out of a persisted .csv log file.
						LogTask logTask = new LogTask();
						logTask.execute();

						// Rest the counts.
						thresholdCountMin = 0;
						thresholdCountMax = 0;
					}
				}
			}
		}
	}

	/**
	 * Pinch to zoom on the plot.
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
	 * The UI text views run on their own handler because we don't need to
	 * update them constantly.
	 */
	@Override
	public void run()
	{
		handler.postDelayed(this, 32);

		updateTextViewOutputs();
	}

	/**
	 * Add the Android Developer LPF plot.
	 */
	private void addLPFMagnitudePlot()
	{
		addPlot(LOG_MAGNITUDE_AXIS_TITLE, PLOT_LPF_MAGNITUDE_KEY,
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
	 * Categorize an acceleration event by the maximum measured magnitude of
	 * acceleration.
	 * 
	 * @param value
	 *            The maximum measured magnitude from the acceleration event.
	 */
	private void categorizeAccelerationEvent(float value)
	{
		if (value > 0.2 && value <= 0.3)
		{
			eventCount0++;
		}
		if (value > 0.3 && value <= 0.4)
		{
			eventCount1++;
		}
		if (value > 0.4 && value <= 0.5)
		{
			eventCount2++;
		}
		if (value > 0.5)
		{
			eventCount3++;
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
	 * Finish a session/trip. A session is defined as the period between the
	 * start button being pressed and the stop button being pressed.
	 */
	private void finishSession()
	{
		if (AccelerationAlertActivity.this.location != null)
		{
			// Get the location where the session was stopped.
			latitudeStop = AccelerationAlertActivity.this.location
					.getLatitude();
			longitudeStop = AccelerationAlertActivity.this.location
					.getLongitude();
		}

		// Indicate the stop location has been aquired.
		locationStopAcquired = true;

		// Get the time the session was stopped.
		timeStop = System.currentTimeMillis();

		// Attempt to email the logs and meta data acquired during the session.
		EmailTask emailTask = new EmailTask();
		emailTask.execute();
	}

	/**
	 * Generate the meta data .csv file. A meta data file is created for each
	 * session/trip. The meta data includes the route, the start location, the
	 * start time, the stop location, the stop time, a count of the acceleration
	 * events classified by magnitude and the drivers impression of the safety
	 * of the trip.
	 * 
	 * @return A file containing the .csv format meta data.
	 */
	private File generateMetaData()
	{
		// Convert our UTC time stamp into a date-time...
		SimpleDateFormat dateFormat = new SimpleDateFormat("hh:ss MM/dd/yyyy",
				Locale.US);
		dateFormat.setTimeZone(TimeZone.getDefault());

		// Create our headers...
		String headers = "";

		headers += META_TIME_AXIS_TITLE + ",";

		headers += META_LAT_AXIS_TITLE + ",";

		headers += META_LON_AXIS_TITLE + ",";

		headers += META_START_LAT_AXIS_TITLE + ",";

		headers += META_START_LON_AXIS_TITLE + ",";

		headers += META_START_TIME_AXIS_TITLE + ",";

		headers += META_STOP_LAT_AXIS_TITLE + ",";

		headers += META_STOP_LON_AXIS_TITLE + ",";

		headers += META_STOP_TIME_AXIS_TITLE + ",";

		headers += META_EVENT_COUNT_0_TITLE + ",";

		headers += META_EVENT_COUNT_1_TITLE + ",";

		headers += META_EVENT_COUNT_2_TITLE + ",";

		headers += META_EVENT_COUNT_3_TITLE + ",";

		headers += META_SAFE_EVENT_TITLE + ",";

		headers += META_UNSAFE_EVENT_TITLE + ",";

		headers += META_NEUTRAL_EVENT_TITLE + ",";

		meta = headers;

		// Create our first row of meta data...
		meta += System.getProperty("line.separator");

		// Add the route time stamp...
		meta += dateFormat.format(timeStamps.get(0)) + ",";

		// Add the first route location
		meta += latitudes.get(0) + ",";
		meta += longitudes.get(0) + ",";

		// Add the start location...
		meta += latitudeStart + ",";
		meta += longitudeStart + ",";

		// Add the start time stamp...
		meta += dateFormat.format(timeStart) + ",";

		// Add the stop location...
		meta += latitudeStop + ",";
		meta += longitudeStop + ",";

		// Add the stop time stamp...
		meta += dateFormat.format(timeStop) + ",";

		// Add the acceleration event counts...
		meta += eventCount0 + ",";
		meta += eventCount1 + ",";
		meta += eventCount2 + ",";
		meta += eventCount3 + ",";

		// Add the drivers impression of safety...
		meta += String.valueOf(tripSafe) + ",";
		meta += String.valueOf(tripUnsafe) + ",";
		meta += String.valueOf(tripNeutral) + ",";

		// Add the route to the file
		for (int i = 1; i < latitudes.size(); i++)
		{
			// Add a new line.
			meta += System.getProperty("line.separator");

			// Add the route time stamp.
			meta += dateFormat.format(timeStamps.get(i)) + ",";

			// Add the route location.
			meta += latitudes.get(i) + ",";
			meta += longitudes.get(i);
		}

		return writeMetaToFile();
	}

	/**
	 * Log output data to an external .csv file. A log is generated for each
	 * acceleration event. The log includes the data from just before the
	 * acceleration event and the acceleration event itself. The log also
	 * includes the speed at which the acceleration event occurred, the maximum
	 * acceleration measured during the event and the settings of the
	 * application.
	 */
	private File generateLogData()
	{
		// This runs outside of the main UI thread, so we make it thread safe by
		// running on the UI thread.
		runOnUiThread(new Runnable()
		{
			// Create a toast indicating that the log is being created.
			public void run()
			{
				CharSequence text = "Creating Log";
				int duration = Toast.LENGTH_SHORT;

				Toast toast = Toast.makeText(AccelerationAlertActivity.this,
						text, duration);
				toast.show();
			}
		});

		// Create the headers.
		String headers = "Generation" + ",";

		headers += "Timestamp" + ",";

		headers += LOG_MAGNITUDE_AXIS_TITLE + ",";

		headers += LOG_EVENT_LAT_AXIS_TITLE + ",";

		headers += LOG_EVENT_LON_AXIS_TITLE + ",";

		headers += LOG_EVENT_MAX_ACCELERATION_AXIS_TITLE + ",";

		headers += LOG_EVENT_VELOCITY_AXIS_TITLE + ",";

		headers += LOG_EVENT_TIME_AXIS_TITLE + ",";

		headers += LOG_EVENT_MAX_THRESHOLD_AXIS_TITLE + ",";

		headers += LOG_EVENT_MIN_THRESHOLD_AXIS_TITLE + ",";

		headers += LOG_EVENT_MAX_COUNT_AXIS_TITLE + ",";

		headers += LOG_EVENT_MIN_COUNT_AXIS_TITLE + ",";

		headers += LOG_EVENT_ALPHA_AXIS_TITLE + ",";

		log = headers;

		long startTime = 0;

		int count = 0;

		// Convert our UTC time stamp into a date-time...
		SimpleDateFormat dateFormat = new SimpleDateFormat("hh:ss MM/dd/yyyy",
				Locale.US);
		dateFormat.setTimeZone(TimeZone.getDefault());

		maxAcceleration = 0;

		// Find the maximum value in the set.
		while (logMaxMagnitudeIterator.hasNext())
		{
			float value = logMaxMagnitudeIterator.next().floatValue();

			// Find the maximum value.
			if (value > maxAcceleration)
			{
				maxAcceleration = value;
			}
		}

		// Generate the log data.
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

			log += logMagnitudeIterator.next().floatValue() + ",";

			// Data displayed only on the first row after the headers...
			if (count == 0)
			{
				log += latitudeEvent + ",";
				log += longitudeEvent + ",";
				log += maxAcceleration + ",";
				log += velocityEvent + ",";

				// Convert our UTC time stamp into a date-time...
				log += dateFormat.format(timeEvent) + ",";

				log += thresholdMax + ",";
				log += thresholdMin + ",";

				log += thresholdCountMax + ",";
				log += thresholdCountMin + ",";

				log += lpfStaticAlpha + ",";
			}

			count++;
		}

		// Categorize the log by the maximum recorded acceleration.
		categorizeAccelerationEvent(maxAcceleration);

		return writeLogToFile();
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
		// Low pass filter for the linear acceleration...
		lowPassFilter = new LPFAndroidDeveloper();
		lowPassFilter.setAlphaStatic(staticAlpha);
		lowPassFilter.setAlpha(lpfStaticAlpha);

		// Mean filter for the acceleration magnitude...
		meanFilterMagnitude = new MeanFilterByValue();
		meanFilterMagnitude.setWindowSize(10);

		// Mean filter for the acceleration...
		meanFilterAcceleration = new MeanFilterByArray();
		meanFilterAcceleration.setWindowSize(10);
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

		// Add our magnitude plot.
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

	/**
	 * Play an audio notification.
	 */
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

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		emailLog = prefs.getBoolean("email_logs_preference", false);
	}

	/**
	 * Send an email to a Gmail account containing the meta data and log data
	 * files from the session.
	 */
	private void sendSessionEmail()
	{
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		String email = prefs.getString("email_host_address", "");
		String password = prefs.getString("email_host_password", "");
		String recipient = prefs.getString("email_recipient_address", "");

		if (!email.equals("") && !password.equals("") && !recipient.equals(""))
		{
			Mail mail = new Mail(email, password);

			mail.setTo(new String[]
			{ recipient });

			mail.setFrom(Secure.getString(getContentResolver(),
					Secure.ANDROID_ID));

			mail.setSubject("Acceleration Alert Log");

			mail.setBody("A log file from Acceleration Alert.");

			try
			{
				// Add the meta data to the email first...
				mail.addAttachment(generateMetaData().getAbsolutePath());

				// Then add the log data to the email.
				for (int i = 0; i < logs.size(); i++)
				{
					mail.addAttachment(logs.get(i).getAbsolutePath());
				}

				if (mail.send())
				{
					runOnUiThread(new Runnable()
					{
						public void run()
						{
							CharSequence text = "Email Sent";
							int duration = Toast.LENGTH_SHORT;

							Toast toast = Toast.makeText(
									AccelerationAlertActivity.this, text,
									duration);
							toast.show();
						}
					});
				}
			}
			catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * Set the UI Text View indicating the event count.
	 */
	private void setEventCount0()
	{
		TextView textViewThresholdCountMin = (TextView) this
				.findViewById(R.id.value_event_count_0);

		textViewThresholdCountMin.setText(String.valueOf(this.eventCount0));
	}

	/**
	 * Set the UI Text View indicating the event count.
	 */
	private void setEventCount1()
	{
		TextView textViewThresholdCountMin = (TextView) this
				.findViewById(R.id.value_event_count_1);

		textViewThresholdCountMin.setText(String.valueOf(this.eventCount1));
	}

	/**
	 * Set the UI Text View indicating the event count.
	 */
	private void setEventCount2()
	{
		TextView textViewThresholdCountMin = (TextView) this
				.findViewById(R.id.value_event_count_2);

		textViewThresholdCountMin.setText(String.valueOf(this.eventCount2));
	}

	/**
	 * Set the UI Text View indicating the event count.
	 */
	private void setEventCount3()
	{
		TextView textViewThresholdCountMin = (TextView) this
				.findViewById(R.id.value_event_count_3);

		textViewThresholdCountMin.setText(String.valueOf(this.eventCount3));
	}

	/**
	 * Set the low-pass-filter static alpha. A value between 0 and 1. A value
	 * close to 0 means the filter reacts quickly to compensate for changes in
	 * the rotation while a value close to 1 means the filter is much more stiff
	 * and slow to react to rotation changes.
	 * 
	 * @param alpha
	 *            The alpha value for the low-pass-filter.
	 */
	private void setLPFStaticAlpha(float alpha)
	{
		this.lpfStaticAlpha = alpha;

		if (lowPassFilter != null)
		{
			lowPassFilter.setAlpha(this.lpfStaticAlpha);
		}

		// Update the text views on the UI.
		TextView textViewAlpha = (TextView) this
				.findViewById(R.id.value_lpf_alpha);
		textViewAlpha.setText(String.valueOf(this.lpfStaticAlpha));
	}

	/**
	 * Set the number of consecutive acceleration measurements that need to
	 * exceed the maximum threshold for an acceleration event to begin.
	 * 
	 * @param thresholdCountMaxLimit
	 *            The maximum threshold count.
	 */
	private void setThresholdMaxCountLimit(int thresholdCountMaxLimit)
	{
		this.thresholdCountMaxLimit = thresholdCountMaxLimit;

		// Update the text views on the UI.
		TextView textViewThresholdCountMax = (TextView) this
				.findViewById(R.id.value_max_threshold_count);
		textViewThresholdCountMax.setText(String
				.valueOf(this.thresholdCountMaxLimit));
	}

	/**
	 * Set the number of consecutive acceleration measurements that need to be
	 * below the minimum threshold for an acceleration event to end.
	 * 
	 * @param thresholdCountMinLimit
	 *            The minimum threshold count.
	 */
	private void setThresholdMinCountLimit(int thresholdCountMinLimit)
	{
		this.thresholdCountMinLimit = thresholdCountMinLimit;

		// Update the text views on the UI.
		TextView textViewThresholdCountMin = (TextView) this
				.findViewById(R.id.value_min_threshold_count);
		textViewThresholdCountMin.setText(String
				.valueOf(this.thresholdCountMinLimit));
	}

	/**
	 * Set the threshold the acceleration must exceed for an acceleration event
	 * to being.
	 * 
	 * @param thresholdMax
	 *            The maximum acceleration threshold.
	 */
	private void setThresholdMax(float thresholdMax)
	{
		this.thresholdMax = thresholdMax;

		// Update the text views on the UI.
		TextView textViewThresholdMax = (TextView) this
				.findViewById(R.id.value_max_threshold);
		textViewThresholdMax.setText(String.valueOf(this.thresholdMax));
	}

	/**
	 * Set the threshold the acceleration must be below for an acceleration
	 * event to stop.
	 * 
	 * @param thresholdMin
	 *            The minimum acceleration threshold.
	 */
	private void setThresholdMin(float thresholdMin)
	{
		this.thresholdMin = thresholdMin;

		// Update the text views on the UI.
		TextView textViewThresholdMin = (TextView) this
				.findViewById(R.id.value_min_threshold);
		textViewThresholdMin.setText(String.valueOf(this.thresholdMin));
	}

	/**
	 * Show a dialog asking the user for their impression of the safety of the
	 * session/trip: Safe, Unsafe or Neutral.
	 */
	private void showSafetyDialog()
	{
		AlertDialog alertDialog = new AlertDialog.Builder(this).create();

		alertDialog.setTitle("Trip Comlete");

		alertDialog.setMessage("Rate your trip!");

		alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Safe",
				new DialogInterface.OnClickListener()
				{

					public void onClick(DialogInterface dialog, int id)
					{
						tripSafe = true;
						finishSession();
					}
				});

		alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Unsafe",
				new DialogInterface.OnClickListener()
				{

					public void onClick(DialogInterface dialog, int id)
					{
						tripUnsafe = true;
						finishSession();
					}
				});

		alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Neutral",
				new DialogInterface.OnClickListener()
				{

					public void onClick(DialogInterface dialog, int id)
					{
						tripNeutral = true;
						finishSession();
					}
				});

		alertDialog.show();
	}

	/**
	 * Show a help dialog.
	 */
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

	/**
	 * Show a dialog with the acceleration event settings.
	 */
	private void showSettingsDialog()
	{
		SettingsDialog dialog = new SettingsDialog(this);

		dialog.setOnDismissListener(new OnDismissListener()
		{
			@Override
			public void onDismiss(DialogInterface arg0)
			{
				// Read the new preferences.
				readPrefs();
			}
		});

		dialog.show();
	}

	/**
	 * Update the text views on the UI with the most recent acceleration
	 * measurements.
	 */
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
	 * Write the logged data out to a persisted file.
	 */
	private File writeMetaToFile()
	{
		Calendar c = Calendar.getInstance();
		String filename = "AccelerationAlert-TripMeta-" + c.get(Calendar.YEAR)
				+ "-" + c.get(Calendar.DAY_OF_WEEK_IN_MONTH) + "-"
				+ c.get(Calendar.HOUR) + "-" + c.get(Calendar.HOUR) + "-"
				+ c.get(Calendar.MINUTE) + "-" + c.get(Calendar.SECOND)
				+ ".csv";

		File dir = new File(Environment.getExternalStorageDirectory()
				+ File.separator + "AccelerationAlert" + File.separator
				+ "Trip" + File.separator + "Meta");

		if (!dir.exists())
		{
			dir.mkdirs();
		}

		File file = new File(dir, filename);

		FileOutputStream fos;
		byte[] data = meta.getBytes();
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
					CharSequence text = "Trip Saved";
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
					CharSequence text = "Tri Error";
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

		return file;
	}

	/**
	 * Write the logged data out to a persisted file.
	 */
	private File writeLogToFile()
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

		return file;
	}

	/**
	 * Write the application preferences.
	 */
	private void writePrefs()
	{
		SharedPreferences prefs = this.getSharedPreferences("lpf_prefs",
				Activity.MODE_PRIVATE);

		SharedPreferences.Editor editor = prefs.edit();

		editor.putFloat("threshold_max", thresholdMax);
		editor.putFloat("threshold_min", thresholdMin);

		editor.putInt("threshold_count_max", thresholdCountMaxLimit);
		editor.putInt("threshold_count_min", thresholdCountMinLimit);

		editor.putFloat("lpf_alpha", lpfStaticAlpha);

		editor.commit();
	}

	/**
	 * A task to plot the acceleration event data to the UI.
	 * 
	 * @author Kaleb
	 * 
	 */
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

	/**
	 * A task to create the .csv format log file.
	 * 
	 * @author Kaleb
	 * 
	 */
	private class LogTask extends AsyncTask<Void, Void, Void>
	{

		@Override
		protected Void doInBackground(Void... params)
		{
			logTaskActive = true;

			logMaxMagnitudeIterator = magnitudeList.iterator();
			logMagnitudeIterator = magnitudeList.iterator();
			logTimeStampIterator = timeStampList.iterator();

			logs.add(generateLogData());

			return null;
		}

		@Override
		protected void onPostExecute(Void params)
		{
			logTaskActive = false;

			playNotification();

			setEventCount0();
			setEventCount1();
			setEventCount2();
			setEventCount3();
		}
	}

	/**
	 * A task to create the .csv format meta file.
	 * 
	 * @author Kaleb
	 * 
	 */
	private class EmailTask extends AsyncTask<Void, Void, Void>
	{

		@Override
		protected Void doInBackground(Void... params)
		{
			if (emailLog)
			{
				sendSessionEmail();
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void params)
		{
			// Reset the data.
			logs = new ArrayList<File>();
			timeStamps = new ArrayList<Long>();
			latitudes = new ArrayList<Double>();
			longitudes = new ArrayList<Double>();

			tripSafe = false;
			tripUnsafe = false;
			tripNeutral = false;

			eventCount0 = 0;
			eventCount1 = 0;
			eventCount2 = 0;
			eventCount3 = 0;

			setEventCount0();
			setEventCount1();
			setEventCount2();
			setEventCount3();

			log = "";
			meta = "";
		}
	}

	@Override
	public void onProviderDisabled(String arg0)
	{
		// Not used...
	}

	@Override
	public void onProviderEnabled(String arg0)
	{
		// Not used...
	}

	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2)
	{
		// Not used...
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
		// Not used...
	}
}
