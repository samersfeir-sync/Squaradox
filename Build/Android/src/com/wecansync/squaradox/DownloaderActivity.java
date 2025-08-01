/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wecansync.squaradox;

import com.android.vending.expansion.zipfile.ZipResourceFile;
import com.android.vending.expansion.zipfile.ZipResourceFile.ZipEntryRO;
import com.google.android.vending.expansion.downloader.Constants;
import com.google.android.vending.expansion.downloader.DownloadProgressInfo;
import com.google.android.vending.expansion.downloader.DownloaderClientMarshaller;
import com.google.android.vending.expansion.downloader.DownloaderServiceMarshaller;
import com.google.android.vending.expansion.downloader.Helpers;
import com.google.android.vending.expansion.downloader.IDownloaderClient;
import com.google.android.vending.expansion.downloader.IDownloaderService;
import com.google.android.vending.expansion.downloader.IStub;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Messenger;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.zip.CRC32;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import com.epicgames.unreal.GameActivity;

/**
 * This is sample code for a project built against the downloader library. It
 * implements the IDownloaderClient that the client marshaler will talk to as
 * messages are delivered from the DownloaderService.
 */
public class DownloaderActivity extends Activity implements IDownloaderClient {
    private static final String LOG_TAG = "LVLDownloader";
    private ProgressBar mPB;

    private TextView mStatusText;
    private TextView mProgressFraction;
    private TextView mProgressPercent;
    private TextView mAverageSpeed;
    private TextView mTimeRemaining;

    private View mDashboard;
    private View mCellMessage;

    private Button mPauseButton;
    private Button mWiFiSettingsButton;

    private boolean mStatePaused;
    private int mState;

    private IDownloaderService mRemoteService;

    private IStub mDownloaderClientStub;
	
	private final CharSequence[] OBBSelectItems = { "Use Store Data", "Use Development Data" };

	
    private void setState(int newState) {
        if (mState != newState) {
            mState = newState;
            mStatusText.setText(Helpers.getDownloaderStringResourceIDFromState(newState));
        }
    }

    private void setButtonPausedState(boolean paused) {
        mStatePaused = paused;
        int stringResourceID = paused ? R.string.text_button_resume :
                R.string.text_button_pause;
        mPauseButton.setText(stringResourceID);
    }
	
	static DownloaderActivity _download;
	
	private Intent OutputData;

    /**
     * Go through each of the APK Expansion files defined in the structure above
     * and determine if the files are present and match the required size. Free
     * applications should definitely consider doing this, as this allows the
     * application to be launched for the first time without having a network
     * connection present. Paid applications that use LVL should probably do at
     * least one LVL check that requires the network to be present, so this is
     * not as necessary.
     * 
     * @return true if they are present.
     */
    boolean expansionFilesDelivered() {
		
        for (OBBData.XAPKFile xf : OBBData.xAPKS) {
            String fileName = Helpers.getExpansionAPKFileName(this, xf.mIsMain, xf.mFileVersion, OBBData.AppType);
			GameActivity.Log.debug("Checking for file : " + fileName);
			String fileForNewFile = Helpers.generateSaveFileName(this, fileName);
			String fileForDevFile = Helpers.generateSaveFileNameDevelopment(this, fileName);
			GameActivity.Log.debug("which is really being resolved to : " + fileForNewFile + "\n Or : " + fileForDevFile);
            if (!Helpers.doesFileExist(this, fileName, xf.mFileSize, false) &&
				!Helpers.doesFileExistDev(this, fileName, xf.mFileSize, false))
                return false;
        }
        return true;
    }
	
	boolean onlySingleExpansionFileFound() {
		for (OBBData.XAPKFile xf : OBBData.xAPKS) {
            String fileName = Helpers.getExpansionAPKFileName(this, xf.mIsMain, xf.mFileVersion, OBBData.AppType);
			GameActivity.Log.debug("Checking for file : " + fileName);
			String fileForNewFile = Helpers.generateSaveFileName(this, fileName);
			String fileForDevFile = Helpers.generateSaveFileNameDevelopment(this, fileName);
			
			if (Helpers.doesFileExist(this, fileName, xf.mFileSize, false) &&
				Helpers.doesFileExistDev(this, fileName, xf.mFileSize, false))
                return false;
		}
		
		return true;		
	}
	
	File getFileDetailsCacheFile() {
		return new File(this.getExternalFilesDir(null), "cacheFile.txt");
	}
	
	boolean expansionFilesUptoData() {
	
		File cacheFile = getFileDetailsCacheFile();
		// Read data into an array or something...
		Map<String, Long> fileDetailsMap = new HashMap<String, Long>();
		
		if(cacheFile.exists()) {
			try {
				FileReader fileCache = new FileReader(cacheFile);
				BufferedReader bufferedFileCache = new BufferedReader(fileCache);
				List<String> lines = new ArrayList<String>();
				String line = null;
				while ((line = bufferedFileCache.readLine()) != null) {
					lines.add(line);
				}
				bufferedFileCache.close();
				
				for(String dataLine : lines)
				{
					GameActivity.Log.debug("Splitting dataLine => " + dataLine);
					String[] parts = dataLine.split(",");
					fileDetailsMap.put(parts[0], Long.parseLong(parts[1]));
				}
			}
			catch(Exception e)
			{
				GameActivity.Log.debug("Exception thrown during file details reading.");
				e.printStackTrace();
				fileDetailsMap.clear();
			}	
		}
		
		for (OBBData.XAPKFile xf : OBBData.xAPKS) {
            String fileName = Helpers.getExpansionAPKFileName(this, xf.mIsMain, xf.mFileVersion, OBBData.AppType);
			String fileForNewFile = Helpers.generateSaveFileName(this, fileName);
			String fileForDevFile = Helpers.generateSaveFileNameDevelopment(this, fileName);
			// check to see if time/data on files match cached version
			// if not return false
			File srcFile = new File(fileForNewFile);
			File srcDevFile = new File(fileForDevFile);
			long lastModified = srcFile.lastModified();
			long lastModifiedDev = srcDevFile.lastModified();
			if(!(srcFile.exists() && fileDetailsMap.containsKey(fileName) && lastModified == fileDetailsMap.get(fileName)) 
				&&
			   !(srcDevFile.exists() && fileDetailsMap.containsKey(fileName) && lastModifiedDev == fileDetailsMap.get(fileName)))
				return false;
		}
		return true;
	}
	
	static private void RemoveOBBFile(int OBBToDelete) {
		
		for (OBBData.XAPKFile xf : OBBData.xAPKS) {
		    String fileName = Helpers.getExpansionAPKFileName(DownloaderActivity._download, xf.mIsMain, xf.mFileVersion, OBBData.AppType);
			switch(OBBToDelete)
			{
			case 0:
				String fileForNewFile = Helpers.generateSaveFileName(DownloaderActivity._download, fileName);
				File srcFile = new File(fileForNewFile);
				srcFile.delete();
				break;
			case 1:
				String fileForDevFile = Helpers.generateSaveFileNameDevelopment(DownloaderActivity._download, fileName);
				File srcDevFile = new File(fileForDevFile);
				srcDevFile.delete();
				break;
			}
		}		
	}
	
	private void ProcessOBBFiles()
	{
		if(GameActivity.Get().VerifyOBBOnStartUp && !expansionFilesUptoData()) {
				validateXAPKZipFiles();
		} else {
				OutputData.putExtra(GameActivity.DOWNLOAD_RETURN_NAME, GameActivity.DOWNLOAD_FILES_PRESENT);		
				setResult(RESULT_OK, OutputData);
				finish();
				overridePendingTransition(R.anim.noaction, R.anim.noaction);
		}
	}

    /**
     * Calculating a moving average for the validation speed so we don't get
     * jumpy calculations for time etc.
     */
    static private final float SMOOTHING_FACTOR = 0.005f;

    /**
     * Used by the async task
     */
    private boolean mCancelValidation;

    /**
     * Go through each of the Expansion APK files and open each as a zip file.
     * Calculate the CRC for each file and return false if any fail to match.
     * 
     * @return true if XAPKZipFile is successful
     */
    void validateXAPKZipFiles() {
        AsyncTask<Object, DownloadProgressInfo, Boolean> validationTask = new AsyncTask<Object, DownloadProgressInfo, Boolean>() {

            @Override
            protected void onPreExecute() {
                mDashboard.setVisibility(View.VISIBLE);
                mCellMessage.setVisibility(View.GONE);
                mStatusText.setText(R.string.text_verifying_download);
                mPauseButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mCancelValidation = true;
                    }
                });
				mPauseButton.setVisibility(View.GONE);
                // mPauseButton.setText(R.string.text_button_cancel_verify);
                super.onPreExecute();
            }

            @Override
            protected Boolean doInBackground(Object... params) {
                for (OBBData.XAPKFile xf : OBBData.xAPKS) {
                    String fileName = Helpers.getExpansionAPKFileName(
                            DownloaderActivity.this,
                            xf.mIsMain, xf.mFileVersion, OBBData.AppType);
					boolean normalFile = Helpers.doesFileExist(DownloaderActivity.this, fileName, xf.mFileSize, false);
					boolean devFile = Helpers.doesFileExistDev(DownloaderActivity.this, fileName, xf.mFileSize, false);
							
                    if (!normalFile &&	!devFile )
                        return false;
					
					if(normalFile)
                    {
						fileName = Helpers.generateSaveFileName(DownloaderActivity.this, fileName);
					}
					else
					{
						fileName = Helpers.generateSaveFileNameDevelopment(DownloaderActivity.this, fileName);
					}			
					
                    ZipResourceFile zrf;
                    byte[] buf = new byte[1024 * 256];
                    try {
                        zrf = new ZipResourceFile(fileName);
                        ZipEntryRO[] entries = zrf.getAllEntries();
                        /**
                         * First calculate the total compressed length
                         */
                        long totalCompressedLength = 0;
                        for (ZipEntryRO entry : entries) {
                            totalCompressedLength += entry.mCompressedLength;
                        }
                        float averageVerifySpeed = 0;
                        long totalBytesRemaining = totalCompressedLength;
                        long timeRemaining;
                        /**
                         * Then calculate a CRC for every file in the Zip file,
                         * comparing it to what is stored in the Zip directory.
                         * Note that for compressed Zip files we must extract
                         * the contents to do this comparison.
                         */
                        for (ZipEntryRO entry : entries) {
                            if (-1 != entry.mCRC32) {
                                long length = entry.mUncompressedLength;
                                CRC32 crc = new CRC32();
                                DataInputStream dis = null;
                                try {
                                    dis = new DataInputStream(
                                            zrf.getInputStream(entry.mFileName));

                                    long startTime = SystemClock.uptimeMillis();
                                    while (length > 0) {
                                        int seek = (int) (length > buf.length ? buf.length
                                                : length);
                                        dis.readFully(buf, 0, seek);
                                        crc.update(buf, 0, seek);
                                        length -= seek;
                                        long currentTime = SystemClock.uptimeMillis();
                                        long timePassed = currentTime - startTime;
                                        if (timePassed > 0) {
                                            float currentSpeedSample = (float) seek
                                                    / (float) timePassed;
                                            if (0 != averageVerifySpeed) {
                                                averageVerifySpeed = SMOOTHING_FACTOR
                                                        * currentSpeedSample
                                                        + (1 - SMOOTHING_FACTOR)
                                                        * averageVerifySpeed;
                                            } else {
                                                averageVerifySpeed = currentSpeedSample;
                                            }
                                            totalBytesRemaining -= seek;
                                            timeRemaining = (long) (totalBytesRemaining / averageVerifySpeed);
                                            this.publishProgress(
                                                    new DownloadProgressInfo(
                                                            totalCompressedLength,
                                                            totalCompressedLength
                                                                    - totalBytesRemaining,
                                                            timeRemaining,
                                                            averageVerifySpeed)
                                                    );
                                        }
                                        startTime = currentTime;
                                        if (mCancelValidation)
                                            return true;
                                    }
                                    if (crc.getValue() != entry.mCRC32) {
                                        Log.e(Constants.TAG,
                                                "CRC does not match for entry: "
                                                        + entry.mFileName);
                                        Log.e(Constants.TAG,
                                                "In file: " + entry.getZipFileName());
                                        return false;
                                    }
                                } finally {
                                    if (null != dis) {
                                        dis.close();
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
                return true;
            }

            @Override
            protected void onProgressUpdate(DownloadProgressInfo... values) {
                onDownloadProgress(values[0]);
                super.onProgressUpdate(values);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (result) {
					// save details to cache file...
					try {
						File cacheFile = getFileDetailsCacheFile();
						FileWriter fileCache = new FileWriter(cacheFile);
						BufferedWriter bufferedFileCache = new BufferedWriter(fileCache);
					
										
						for (OBBData.XAPKFile xf : OBBData.xAPKS) {
							String fileName = Helpers.getExpansionAPKFileName(DownloaderActivity.this, xf.mIsMain, xf.mFileVersion, OBBData.AppType);
							String fileForNewFile = Helpers.generateSaveFileName(DownloaderActivity.this, fileName);
							String fileForDevFile = Helpers.generateSaveFileNameDevelopment(DownloaderActivity.this, fileName);
														
							GameActivity.Log.debug("Writing details for file : " + fileName);
								
							File srcFile = new File(fileForNewFile);
							File srcDevFile = new File(fileForDevFile);
							if(srcFile.exists()) {
								long lastModified = srcFile.lastModified();
								bufferedFileCache.write(fileName);
								bufferedFileCache.write(",");
								bufferedFileCache.write(new Long(lastModified).toString());
								bufferedFileCache.newLine();
								GameActivity.Log.debug("Details for file : " + fileName + " with modified time of " + new Long(lastModified).toString() );
							}	
							else {
								long lastModified = srcDevFile.lastModified();
								bufferedFileCache.write(fileName);
								bufferedFileCache.write(",");
								bufferedFileCache.write(new Long(lastModified).toString());
								bufferedFileCache.newLine();
								GameActivity.Log.debug("Details for file : " + fileName + " with modified time of " + new Long(lastModified).toString() );
							}							
						}
						
						bufferedFileCache.close();
						
					}
					catch(Exception e)
					{
						GameActivity.Log.debug("Exception thrown during file details writing.");
						e.printStackTrace();
					}
					/*
                    mDashboard.setVisibility(View.VISIBLE);
                    mCellMessage.setVisibility(View.GONE);
                    mStatusText.setText(R.string.text_validation_complete);
                    mPauseButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
							OutputData.putExtra(GameActivity.DOWNLOAD_RETURN_NAME, GameActivity.DOWNLOAD_FILES_PRESENT);		
							setResult(RESULT_OK, OutputData);
							finish();
                        }
                    });
                    mPauseButton.setText(android.R.string.ok);
					*/
					OutputData.putExtra(GameActivity.DOWNLOAD_RETURN_NAME, GameActivity.DOWNLOAD_FILES_PRESENT);		
					setResult(RESULT_OK, OutputData);
					finish();
					
					
                } else {
					// clear cache file if it exists...
					File cacheFile = getFileDetailsCacheFile();
					if(cacheFile.exists()) {
						cacheFile.delete();
					}
					
                    mDashboard.setVisibility(View.VISIBLE);
                    mCellMessage.setVisibility(View.GONE);
                    mStatusText.setText(R.string.text_validation_failed);
                    mPauseButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
							OutputData.putExtra(GameActivity.DOWNLOAD_RETURN_NAME, GameActivity.DOWNLOAD_INVALID);		
							setResult(RESULT_OK, OutputData);
							finish();
                        }
                    });
                    mPauseButton.setText(android.R.string.cancel);
                }
                super.onPostExecute(result);
            }

        };
        validationTask.execute(new Object());
    }

    /**
     * If the download isn't present, we initialize the download UI. This ties
     * all of the controls into the remote service calls.
     */
    private void initializeDownloadUI() {
        mDownloaderClientStub = DownloaderClientMarshaller.CreateStub
                (this, OBBDownloaderService.class);
        setContentView(R.layout.downloader_progress);

        mPB = (ProgressBar) findViewById(R.id.progressBar);
        mStatusText = (TextView) findViewById(R.id.statusText);
        mProgressFraction = (TextView) findViewById(R.id.progressAsFraction);
        mProgressPercent = (TextView) findViewById(R.id.progressAsPercentage);
        mAverageSpeed = (TextView) findViewById(R.id.progressAverageSpeed);
        mTimeRemaining = (TextView) findViewById(R.id.progressTimeRemaining);
        mDashboard = findViewById(R.id.downloaderDashboard);
        mCellMessage = findViewById(R.id.approveCellular);
        mPauseButton = (Button) findViewById(R.id.pauseButton);
        mWiFiSettingsButton = (Button) findViewById(R.id.wifiSettingsButton);

        mPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mStatePaused) {
                    mRemoteService.requestContinueDownload();
                } else {
                    mRemoteService.requestPauseDownload();
                }
                setButtonPausedState(!mStatePaused);
            }
        });

        mWiFiSettingsButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });

        Button resumeOnCell = (Button) findViewById(R.id.resumeOverCellular);
        resumeOnCell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRemoteService.setDownloadFlags(IDownloaderService.FLAGS_DOWNLOAD_OVER_CELLULAR);
                mRemoteService.requestContinueDownload();
                mCellMessage.setVisibility(View.GONE);
            }
        });

    }

    /**
     * Called when the activity is first create; we wouldn't create a layout in
     * the case where we have the file and are moving to another activity
     * without downloading.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		GameActivity.Log.debug("Starting DownloaderActivity...");
		_download = this;
		// Create somewhere to place the output - we'll check this on 'finish' to make sure we are returning 'something'
		OutputData = new Intent();
		
        /**
         * Both downloading and validation make use of the "download" UI
         */
        initializeDownloadUI();
		GameActivity.Log.debug("... UI setup. Checking for files.");
		
        /**
         * Before we do anything, are the files we expect already here and
         * delivered (presumably by Market) For free titles, this is probably
         * worth doing. (so no Market request is necessary)
         */
        if (!expansionFilesDelivered()) {
				GameActivity.Log.debug("... Whoops... missing; go go go download system!");
				
            try {
			
				// Make sure we have a key before we try to start the service
				if(OBBDownloaderService.getPublicKeyLength() == 0) {
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
				
					builder.setCancelable(false)
							.setTitle("No Google Play Store Key")
							.setMessage("No OBB found and no store key to try to download. Please set one up in Android Project Settings")
							.setPositiveButton("Exit", new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int item) {
									OutputData.putExtra(GameActivity.DOWNLOAD_RETURN_NAME, GameActivity.DOWNLOAD_NO_PLAY_KEY);
									setResult(RESULT_OK, OutputData);
									finish();
								}
							});
					
					AlertDialog alert = builder.create();
					alert.show();
				}
				else
				{
			
					Intent launchIntent = DownloaderActivity.this
							.getIntent();
					Intent intentToLaunchThisActivityFromNotification = new Intent(
							DownloaderActivity
							.this, DownloaderActivity.this.getClass());
					intentToLaunchThisActivityFromNotification.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
							Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intentToLaunchThisActivityFromNotification.setAction(launchIntent.getAction());

					if (launchIntent.getCategories() != null) {
						for (String category : launchIntent.getCategories()) {
							intentToLaunchThisActivityFromNotification.addCategory(category);
						}
					}

					// Build PendingIntent used to open this activity from
					// Notification
					PendingIntent pendingIntent = PendingIntent.getActivity(
							DownloaderActivity.this,
							0, intentToLaunchThisActivityFromNotification,
							PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
					// Request to start the download
					int startResult = DownloaderClientMarshaller.startDownloadServiceIfRequired(this,
							pendingIntent, OBBDownloaderService.class);

					if (startResult != DownloaderClientMarshaller.NO_DOWNLOAD_REQUIRED) {
						// The DownloaderService has started downloading the files,
						// show progress
						initializeDownloadUI();
						return;
					} // otherwise, download not needed so we fall through to saying all is OK
					else
					{
						OutputData.putExtra(GameActivity.DOWNLOAD_RETURN_NAME, GameActivity.DOWNLOAD_FILES_PRESENT);
						setResult(RESULT_OK, OutputData);
						finish();
					}
				}
                  
            } catch (NameNotFoundException e) {
                Log.e(LOG_TAG, "Cannot find own package! MAYDAY!");
                e.printStackTrace();
            }

        } else {
			GameActivity.Log.debug("... Can has! Check 'em Dano!");
			if(!onlySingleExpansionFileFound())	{
				// Do some UI here to figure out which we want to keep
				
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				
				builder.setCancelable(false)
						.setTitle("Select OBB to use")
					    .setItems(OBBSelectItems, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int item) {
								DownloaderActivity.RemoveOBBFile(item);
								ProcessOBBFiles();
							}
						});
				
				AlertDialog alert = builder.create();
				alert.show();
			}
			else {
				ProcessOBBFiles();
			}
        }
    }

    /**
     * Connect the stub to our service on start.
     */
    @Override
    protected void onStart() {
        if (null != mDownloaderClientStub) {
            mDownloaderClientStub.connect(this);
        }
        super.onStart();
    }
	
	@Override
	protected void onPause() {
		super.onPause();
		GameActivity.Log.debug("In onPause");
		
		if(OutputData.getIntExtra(GameActivity.DOWNLOAD_RETURN_NAME, GameActivity.DOWNLOAD_NO_RETURN_CODE) == GameActivity.DOWNLOAD_NO_RETURN_CODE)
		{
			GameActivity.Log.debug("onPause returning that user quit the download.");
			OutputData.putExtra(GameActivity.DOWNLOAD_RETURN_NAME, GameActivity.DOWNLOAD_USER_QUIT);
			setResult(RESULT_OK, OutputData);
		}
	}

    /**
     * Disconnect the stub from our service on stop
     */
    @Override
    protected void onStop() {
        if (null != mDownloaderClientStub) {
            mDownloaderClientStub.disconnect(this);
        }
        super.onStop();
		setResult(RESULT_OK, OutputData);
    }

    /**
     * Critical implementation detail. In onServiceConnected we create the
     * remote service and marshaler. This is how we pass the client information
     * back to the service so the client can be properly notified of changes. We
     * must do this every time we reconnect to the service.
     */
    @Override
    public void onServiceConnected(Messenger m) {
        mRemoteService = DownloaderServiceMarshaller.CreateProxy(m);
        mRemoteService.onClientUpdated(mDownloaderClientStub.getMessenger());
    }

    /**
     * The download state should trigger changes in the UI --- it may be useful
     * to show the state as being indeterminate at times. This sample can be
     * considered a guideline.
     */
    @Override
    public void onDownloadStateChanged(int newState) {
        setState(newState);
        boolean showDashboard = true;
        boolean showCellMessage = false;
        boolean paused;
        boolean indeterminate;
        switch (newState) {
            case IDownloaderClient.STATE_IDLE:
                // STATE_IDLE means the service is listening, so it's
                // safe to start making calls via mRemoteService.
                paused = false;
                indeterminate = true;
                break;
            case IDownloaderClient.STATE_CONNECTING:
            case IDownloaderClient.STATE_FETCHING_URL:
                showDashboard = true;
                paused = false;
                indeterminate = true;
                break;
            case IDownloaderClient.STATE_DOWNLOADING:
                paused = false;
                showDashboard = true;
                indeterminate = false;
                break;

            case IDownloaderClient.STATE_FAILED_CANCELED:
            case IDownloaderClient.STATE_FAILED:
            case IDownloaderClient.STATE_FAILED_FETCHING_URL:
            case IDownloaderClient.STATE_FAILED_UNLICENSED:
                paused = true;
                showDashboard = false;
                indeterminate = false;
                break;
            case IDownloaderClient.STATE_PAUSED_NEED_CELLULAR_PERMISSION:
            case IDownloaderClient.STATE_PAUSED_WIFI_DISABLED_NEED_CELLULAR_PERMISSION:
                showDashboard = false;
                paused = true;
                indeterminate = false;
                showCellMessage = true;
                break;

            case IDownloaderClient.STATE_PAUSED_BY_REQUEST:
                paused = true;
                indeterminate = false;
                break;
            case IDownloaderClient.STATE_PAUSED_ROAMING:
            case IDownloaderClient.STATE_PAUSED_SDCARD_UNAVAILABLE:
                paused = true;
                indeterminate = false;
                break;
            case IDownloaderClient.STATE_COMPLETED:
                showDashboard = false;
                paused = false;
                indeterminate = false;
                validateXAPKZipFiles();
                return;
            default:
                paused = true;
                indeterminate = true;
                showDashboard = true;
        }
        int newDashboardVisibility = showDashboard ? View.VISIBLE : View.GONE;
        if (mDashboard.getVisibility() != newDashboardVisibility) {
            mDashboard.setVisibility(newDashboardVisibility);
        }
        int cellMessageVisibility = showCellMessage ? View.VISIBLE : View.GONE;
        if (mCellMessage.getVisibility() != cellMessageVisibility) {
            mCellMessage.setVisibility(cellMessageVisibility);
        }

        mPB.setIndeterminate(indeterminate);
        setButtonPausedState(paused);
    }

    /**
     * Sets the state of the various controls based on the progressinfo object
     * sent from the downloader service.
     */
    @Override
    public void onDownloadProgress(DownloadProgressInfo progress) {
        mAverageSpeed.setText(getString(R.string.kilobytes_per_second,
                Helpers.getSpeedString(progress.mCurrentSpeed)));
        mTimeRemaining.setText(getString(R.string.time_remaining,
                Helpers.getTimeRemaining(progress.mTimeRemaining)));

        progress.mOverallTotal = progress.mOverallTotal;
        mPB.setMax((int) (progress.mOverallTotal >> 8));
        mPB.setProgress((int) (progress.mOverallProgress >> 8));
        mProgressPercent.setText(Long.toString(progress.mOverallProgress
                * 100 /
                progress.mOverallTotal) + "%");
        mProgressFraction.setText(Helpers.getDownloadProgressString
                (progress.mOverallProgress,
                        progress.mOverallTotal));
    }

    @Override
    protected void onDestroy() {
        this.mCancelValidation = true;
        super.onDestroy();
    }

}
