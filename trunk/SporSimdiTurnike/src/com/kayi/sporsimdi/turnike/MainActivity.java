package com.kayi.sporsimdi.turnike;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

public class MainActivity extends FragmentActivity {
	public final static String TAG = "SporSimdiTurnike";

	String receivedData = null;

	private Button pwdLoginButton;

	private TextView rfidTextView;

	private UsbManager usbManager;
	private UsbSerialDriver device;

	private String rfidTicket;

	private final ExecutorService mExecutor = Executors
			.newSingleThreadExecutor();

	private SerialInputOutputManager mSerialIoManager;

	private final SerialInputOutputManager.Listener mListener = new SerialInputOutputManager.Listener() {

		@Override
		public void onRunError(Exception e) {
			Log.d(TAG, "Runner stopped.");
		}

		@Override
		public void onNewData(final byte[] data) {

			MainActivity.this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					MainActivity.this.updateReceivedData(data);
				}
			});
		}

		// new HttpAsyncTask()
		// .execute("http://www.sporsimdi.com/mobile/turnstileRequest.jsf?request="
		// + ticket);

	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setupChat();
		// Get UsbManager from Android.
		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
	}

	private void setupChat() {

		// Initialize the array adapter for the conversation thread

		rfidTextView = (TextView) findViewById(R.id.edit_text_out);
		rfidTextView.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {

				String readRfid = rfidTextView.getText().toString();

				if (readRfid.contains("\n")) {

					rfidTicket = readRfid.replaceAll("\n", "");

					final String message = "Q-QR-" + rfidTicket;

					HttpAsyncTask task = new HttpAsyncTask();
					task.execute("http://www.sporsimdi.com/mobile/turnstileRequest.jsf?request="
							+ message);

					rfidTicket = null;
					rfidTextView.setText("");

				}

			}

			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}
		});

		// Initialize the send button with a listener that for click events
		pwdLoginButton = (Button) findViewById(R.id.button_send);
		pwdLoginButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// Send a message using content of the edit text widget
				TextView view = (TextView) findViewById(R.id.edit_text_out);
				final String message = view.getText().toString();

				String requestMessage = "Q-PW-" + message;

				HttpAsyncTask task = new HttpAsyncTask();
				task.execute("http://www.sporsimdi.com/mobile/turnstileRequest.jsf?request="
						+ requestMessage);

			}
		});

	}

	@Override
	protected void onPause() {
		super.onPause();
		// check if the device is already closed
		if (device != null) {
			try {
				device.close();
			} catch (IOException e) {
				// we couldn't close the device, but there's nothing we can do
				// about it!
			}
			// remove the reference to the device
			device = null;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		// get a USB to Serial device object
		device = UsbSerialProber.acquire(usbManager);
		if (device == null) {
			Toast.makeText(this, "ON resume cihaz bulumanadi",
					Toast.LENGTH_SHORT).show();
		} else {
			try {
				// open the device
				device.open();
				// set the communication speed
				device.setBaudRate(9600); // make sure this matches your
											// device's setting!

				Toast.makeText(this, "ON resume cihaz baglandi",
						Toast.LENGTH_SHORT).show();
			} catch (IOException err) {
				Log.e(TAG, "Error setting up USB device: " + err.getMessage(),
						err);
				try {
					// something failed, so try closing the device
					device.close();
				} catch (IOException err2) {
					// couldn't close, but there's nothing more to do!
				}
				device = null;
				return;
			}
		}

		onDeviceStateChange();
	}

	private void stopIoManager() {
		if (mSerialIoManager != null) {
			Log.i(TAG, "Stopping io manager ..");
			mSerialIoManager.stop();
			mSerialIoManager = null;
		}
	}

	private void startIoManager() {
		if (device != null) {
			Log.i(TAG, "Starting io manager ..");
			mSerialIoManager = new SerialInputOutputManager(device, mListener);
			mExecutor.submit(mSerialIoManager);
		}
	}

	private void onDeviceStateChange() {
		stopIoManager();
		startIoManager();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	// sends color data to a Serial device as {R, G, B, 0x0A}
	public void sendToArduino(String color) {

		byte[] dataToSend = color.getBytes();

		Toast.makeText(this, "Yollanacak veri:" + color, Toast.LENGTH_SHORT)
				.show();

		// send the color to the serial device
		if (device != null) {
			try {
				Toast.makeText(this,
						"device null degil Yollanacak veri:" + color,
						Toast.LENGTH_SHORT).show();

				device.write(dataToSend, 500);
				Toast.makeText(this, "Veri basarili yollandi",
						Toast.LENGTH_SHORT).show();
			} catch (IOException e) {
				Log.e(TAG, "couldn't write color bytes to serial device");
			}
		} else {
			Toast.makeText(this, "Bagli cihaz yok", Toast.LENGTH_SHORT).show();
		}
	}

	private void updateReceivedData(byte[] data) {

		String rec = new String(data);
		if (rec.contains("\n")) {
			if (receivedData == null) {
				receivedData = rec;
			} else {
				receivedData = receivedData + rec;
			}

			receivedData = receivedData.replaceAll("\n", "");

			Toast.makeText(this, "Veri tamamlandi " + receivedData,
					Toast.LENGTH_SHORT).show();

			HttpAsyncTask task = new HttpAsyncTask();
			task.execute("http://www.sporsimdi.com/mobile/turnstileRequest.jsf?request="
					+ receivedData);

			receivedData = null;

		} else {
			if (receivedData == null) {
				receivedData = rec;
			} else {
				receivedData = receivedData + rec;
			}

			Toast.makeText(
					this,
					"Veri parca geldi parca:" + rec + "Son hal:" + receivedData,
					Toast.LENGTH_SHORT).show();
		}

	}

	public class HttpAsyncTask extends AsyncTask<String, Void, String> {

		@Override
		protected String doInBackground(String... urls) {

			return GET(urls[0]);
		}

		// onPostExecute displays the results of the AsyncTask.
		@Override
		protected void onPostExecute(String result) {

			// TODO send ardunio open port
			String resultCode = result.substring(1, 2);

			sendToArduino(resultCode);

		}

	}

	private static String convertInputStreamToString(InputStream inputStream)
			throws IOException {
		BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(inputStream));
		String line = "";
		String result = "";
		while ((line = bufferedReader.readLine()) != null)
			result += line;

		inputStream.close();
		return result;

	}

	public static String GET(String url) {

		InputStream inputStream = null;
		String result = "";
		try {

			// create HttpClient
			HttpClient httpclient = new DefaultHttpClient();

			// make GET request to the given URL
			HttpResponse httpResponse = httpclient.execute(new HttpGet(url));

			// receive response as inputStream
			inputStream = httpResponse.getEntity().getContent();

			// convert inputstream to string
			if (inputStream != null)
				result = convertInputStreamToString(inputStream);
			else
				result = "Did not work!";

		} catch (Exception e) {
			Log.d("InputStream", e.getLocalizedMessage());
		}

		return result;
	}

}
