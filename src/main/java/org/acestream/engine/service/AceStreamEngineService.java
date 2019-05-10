package org.acestream.engine.service;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.MobileNetworksDialogActivity;
import org.acestream.engine.ServiceClient;
import org.acestream.engine.prefs.NotificationData;
import org.acestream.engine.python.IPyFinishedListener;
import org.acestream.engine.python.PyEmbedded;
import org.acestream.engine.service.v0.AceStreamEngineMessages;
import org.acestream.engine.service.v0.IStartEngineResponse;
import org.acestream.engine.util.LogcatOutputStreamWriter;
import org.acestream.engine.util.NetworkUtil;
import org.acestream.engine.util.Util;
import org.acestream.engine.Constants;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.engine.R;
import org.acestream.engine.BuildConfig;
import org.acestream.sdk.AceStream;
import org.acestream.engine.acecast.server.AceStreamDiscoveryServerService;
import org.acestream.sdk.utils.HttpAsyncTask;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;

public class AceStreamEngineService extends ForegroundService 
	implements IAceStreamEngineAsyncTaskListener {

	private static String TAG = "AS/Service";

	public static final String ACTION_CONNECTION_AVAILABILITY_CHANGED = "org.acestream.engine.CONNECTION_AVAILABILITY_CHANGED";
	public static final String EXTRA_CLIENT_TYPE = "org.acestream.engine.EXTRA_CLIENT_TYPE";
	public static final String EXTRA_CALLING_APP = "org.acestream.engine.EXTRA_CALLING_APP";

	private enum Status { IDLE, UNPACKING, CONNECTING, RUNNING, FINISHED, STOPPING };

	private static final int CLIENT_TYPE_AIDL = 0;
	private static final int CLIENT_TYPE_MESSENGER = 1;

	private Status mStatus = Status.IDLE;
	private final Object mStatusLock = new Object();

	private enum NetworkStatus { UNKNOWN, CONNECTED, DISCONNECTED };
	private NetworkStatus mNetworkStatus = NetworkStatus.UNKNOWN;
	private boolean mStopFlag = false;
	private boolean mRestartAfterStopFlag = false;
	private boolean mEnableAceCastServer = false;
	private long mLastAceCastServerStartAt = 0;

	private boolean mStarted = false;
	private int mApiPort = 0;
	private int mHttpPort = 0;
	private String mAccessToken = null;

	private HttpAsyncTask.Factory mHttpAsyncTaskFactory = null;

    private org.acestream.engine.service.v0.IAceStreamEngine mDelegateService = null;
    private boolean mDelegateBound = false;
	private Queue<String> mDelegateMessageQueue;

	private BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent == null) {
				return;
			}

			String action = intent.getAction();
			if(action == null) {
				return;
			}

			Log.d(TAG, "got local broadcast: action=" + action);

			switch(action) {
				case Constants.ACTION_MOBILE_NETWORK_DIALOG_RESULT:
					boolean value = intent.getBooleanExtra(Constants.MOBILE_NETWORK_DIALOG_RESULT_PARAM_ENABLED, false);
					Log.d(TAG, "allow mobile networks: value=" + value);

					// save value
					AceStreamEngineBaseApplication.setMobileNetworkingEnabled(value);

					// resend "ready" to all clients
					if(value) {
						notifyReady(false);
					}

					break;
			}
		}
	};

	private BroadcastReceiver mInstallListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent == null) {
				return;
			}

			if(TextUtils.equals(intent.getAction(), Intent.ACTION_PACKAGE_ADDED)) {
				Log.d(TAG, "app installed: id=" + intent.getData().toString());
			}
			else if(TextUtils.equals(intent.getAction(), Intent.ACTION_PACKAGE_REMOVED)) {
				Log.d(TAG, "app removed: id=" + intent.getData().toString());
			}

			boolean wasDelegatedService = mIsDelegatedService;
			String targetApp = AceStream.getTargetApp();
			mIsDelegatedService = (targetApp != null);

			if(mIsDelegatedService != wasDelegatedService) {
				Log.d(TAG, "update app: delegated: " + wasDelegatedService + "->" + mIsDelegatedService);
				if (mIsDelegatedService) {
					// stop AceCast server
					AceStreamDiscoveryServerService.Client.stopService(AceStreamEngineBaseApplication.context());

					//stop engine and connect to the main service
					mRestartAfterStopFlag = false;
					mStopFlag = true;
					notifyStopped();
					stopEngineService();
					startDelegatedService();
				} else {
					// start engine because i am now the main service
					mIsDelegatedService = false;
					stopDelegatedService();
					startEngineService();
				}
			}
		}
	};

	private BroadcastReceiver mNetworkStatusListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(!intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION))
				return;
			
			Log.d(TAG, "Connectivity changed");
			changeNetworkStatus(AceStreamEngineBaseApplication.isMobileNetworkingEnabled());
            executeOnConnectivityChanged();
		}
	};
	
	private BroadcastReceiver mSettingsListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(!intent.getAction().equals(ACTION_CONNECTION_AVAILABILITY_CHANGED))
				return;
			Log.d(TAG, "Application Prefs changed");
			changeNetworkStatus(AceStreamEngineBaseApplication.isMobileNetworkingEnabled());
			executeOnConnectivityChanged();
		}
	};

	private BroadcastReceiver mScreenStatusListener = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
	            Log.d(TAG, "Screen OFF");
	            fireScreenStatusChanged(false);
	        }
	        else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
	            Log.d(TAG, "Screen ON");
	            fireScreenStatusChanged(true);
	        }
	    }
	};
	
	private void changeNetworkStatus(boolean enableMobile) {
		synchronized (mNetworkStatus) {
			if(NetworkUtil.isConnected2AvailableNetwork(enableMobile)) {
				Log.d(TAG, "Network Connection status: Connected");
				mNetworkStatus = NetworkStatus.CONNECTED;
				restartAceCastServer();
			}
			else {
				Log.d(TAG, "Network Connection status: Disconnected");
				mNetworkStatus = NetworkStatus.DISCONNECTED;
			}
		}
	}
	
	private static final Handler mTaskHandler = new Handler();
	private AsyncTaskManager mTaskManager = new AsyncTaskManager();
	
	private PyEmbedded mPyEmbedded = null;
	
	/***************************************************
	 *                     AIDL stuff                  *
	 ***************************************************/
	private final RemoteCallbackList<IInterface> mRemoteCallbacks = new RemoteCallbackList<>();

    private void clientRegisterRemoteCallback(IInterface cb) {
        clientRegisterRemoteCallback(cb, null);
    }

	private void clientRegisterRemoteCallback(IInterface cb, AceStreamEngineCallbackData data) {
        if (cb != null) {
            mRemoteCallbacks.register(cb, data);
        }
    }

    private void clientUnregisterRemoteCallback(IInterface cb) {
        if (cb != null) {
            mRemoteCallbacks.unregister(cb);
        }
    }

    private void startDelegatedEngine() {
		try {
			mDelegateService.startEngineWithCallback(new IStartEngineResponse.Stub() {
				@Override
				public void onResult(boolean success) throws RemoteException {
					if(success) {
						//String token = mDelegateService.getAccessToken();
						int engineApiPort = mDelegateService.getEngineApiPort();
						int httpApiPort = mDelegateService.getHttpApiPort();

						Log.d(TAG, "delegated engine started: engineApiPort=" + engineApiPort + " httpApiPort=" + httpApiPort);

						Message msg = mBroadcaster.obtainMessage(REPORT_READY, engineApiPort, 0);
						mBroadcaster.sendMessage(msg);
					}
					else {
						Log.d(TAG, "Failed to start delegated engine");
					}
				}
			});
		} catch (RemoteException e) {
			Log.e(TAG, "error", e);
		}
	}

	private void checkAdsNotification(int clientType, String callingApp) {
        if (callingApp == null || !(callingApp.startsWith("org.acestream") || callingApp.startsWith("org.videolan"))) {
            NotificationData notification = AceStreamEngineBaseApplication.getPendingNotification("service");
            if(notification != null) {
                AceStreamEngineBaseApplication.showNotification(notification, this);
            }
        }
	}

    private void clientStartEngine(
    		final int clientType,
    		final String clientApp,
			final org.acestream.engine.service.v0.IStartEngineResponse callback) {
        Logger.v(TAG, "clientStartEngine: post to main thread: clientType=" + clientType  + " clientApp=" + clientApp + " callback=" + callback);

        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                Logger.vv(TAG, "clientStartEngine: delegate=" + mIsDelegatedService + " service=" + mDelegateService);

                if(mIsDelegatedService) {
                    //TODO: deal with callback
                    if(mDelegateService == null) {
                        //TODO: make class for messages
                        mDelegateMessageQueue.add("startEngine");
                    }
                    else {
                        startDelegatedEngine();
                    }
                }
                else {
                    executeClientStartCommand(clientType, clientApp, callback);
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    // v0
	private final org.acestream.engine.service.v0.IAceStreamEngine.Stub mBinderPublicV0 =
			new org.acestream.engine.service.v0.IAceStreamEngine.Stub() {
				@Override
				public void unregisterCallback(org.acestream.engine.service.v0.IAceStreamEngineCallback cb) {
					clientUnregisterRemoteCallback(cb);
				}

				@Override
				public void registerCallback(org.acestream.engine.service.v0.IAceStreamEngineCallback cb) {
					clientRegisterRemoteCallback(cb);
				}

				@Override
				public void registerCallbackExt(org.acestream.engine.service.v0.IAceStreamEngineCallback cb, boolean supportsExtendedNotifications) {
					AceStreamEngineCallbackData data = new AceStreamEngineCallbackData(supportsExtendedNotifications);
					clientRegisterRemoteCallback(cb, data);
				}

				@Override
				public void startEngine() throws RemoteException {
					if(!AceStream.checkStorageAccess()) {
						Log.d(TAG, "aidl:startEngine: no storage access");
						throw new RemoteException("No storage access");
					}
					Logger.vv(TAG, "aidl:startEngine");
					clientStartEngine(CLIENT_TYPE_AIDL, getCallingApp(), null);
				}

				@Override
				public void startEngineWithCallback(org.acestream.engine.service.v0.IStartEngineResponse callback) throws RemoteException {
					if(!AceStream.checkStorageAccess()) {
						Log.d(TAG, "aidl:startEngineWithCallback: no storage access");
						throw new RemoteException("No storage access");
					}
					Logger.vv(TAG, "aidl:startEngineWithCallback: callback=" + callback);
					clientStartEngine(CLIENT_TYPE_AIDL, getCallingApp(), callback);
				}

				@Override
				public int getEngineApiPort() {
					return mApiPort;
				}

				@Override
				public int getHttpApiPort() {
					return mHttpPort;
				}

				@Override
				public String getAccessToken() {
					// Allow only from our apps
					int callingUid = Binder.getCallingUid();
					String callingApp = getPackageManager().getNameForUid(callingUid);

					if(callingApp == null) {
						return null;
					}

					if(callingApp.startsWith("org.acestream.")) {
						return mAccessToken;
					}

					return null;
				}

				@Override
				public void enableAceCastServer() {
					Log.v(TAG, "enableAceCastServer");
					startAceCastServer();
				}
			};

	private void broadcastAIDLClients(Message msg) {
        final int cnt = mRemoteCallbacks.beginBroadcast();
        for(int i = 0; i < cnt; i++) {
            try {
                IInterface callback = mRemoteCallbacks.getBroadcastItem(i);
                Object cookie = mRemoteCallbacks.getBroadcastCookie(i);
                AceStreamEngineCallbackData data = null;
                if(cookie != null) {
                    data = (AceStreamEngineCallbackData) cookie;
                }
                AceStreamEngineCallbackWrapper wrapper = new AceStreamEngineCallbackWrapper(callback, data);

                switch (msg.what) {
                    case REPORT_UNPACKING:
                        wrapper.onUnpacking();
                        break;
                    case REPORT_STARTING:
                        wrapper.onStarting();
                        break;
                    case REPORT_READY:
                        int port;
                        if(msg.arg1 != 0) {
                            // got port from message
                            port = msg.arg1;
                        }
                        else {
                            // -1 means "failed to start"
                            port = -1;
                        }

                        sendOnReady(wrapper, port);
                        break;
                    case REPORT_STOPPED:
                        wrapper.onStopped();
                        break;
					case REPORT_PLAYLIST_UPDATED:
						wrapper.onPlaylistUpdated();
						break;
					case REPORT_EPG_UPDATED:
						wrapper.onEPGUpdated();
						break;
					case REPORT_RESTART_PLAYER:
						wrapper.onRestartPlayer();
						break;
					case REPORT_SETTINGS_UPDATED:
						wrapper.onSettingsUpdated();
						break;
                    default:
                        break;
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Cannot broadcast callback, maybe client is dead.");
            }
        }
        mRemoteCallbacks.finishBroadcast();
	}
	
	private void sendOnReady(AceStreamEngineCallbackWrapper callback, int port) {
		try {
            boolean supportsExtendedNotifications = callback.getData().supportsExtendedNotifications();
			boolean isConnectedToMobileNetwork = MiscUtils.isConnectedToMobileNetwork(this);
			boolean askedAboutMobileNetworking = AceStreamEngineBaseApplication.isMobileNetworkingEnabled();

			Log.d(TAG, "sendOnReady: mobile network: port=" + port + " connected=" + isConnectedToMobileNetwork + " asked=" + askedAboutMobileNetworking + " supportsExtendedNotifications=" + supportsExtendedNotifications);

			if (!supportsExtendedNotifications && !askedAboutMobileNetworking && isConnectedToMobileNetwork) {
				askAboutMobileNetwork();
				return;
			}

			try {
			    callback.onReady(port);
			} catch (RemoteException e) {
				Log.e(TAG, "Cannot broadcast callback, maybe client is dead.", e);
			}
		}
		catch(Exception e) {
			Log.e(TAG, "sendOnReady: error", e);
		}
	}

	/***************************************************
	 *                Messenger stuff                  *
	 ***************************************************/
	/* v0 */
	private final ArrayList<Messenger> mClients_V0 = new ArrayList<Messenger>();
	private final Messenger mMessenger_V0 = new Messenger(new IncomingHandler());

	@SuppressLint("HandlerLeak")
	class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AceStreamEngineMessages.MSG_REGISTER_CLIENT:
                	synchronized (mClients_V0) {
                		mClients_V0.add(msg.replyTo);
                	}
                    break;
                case AceStreamEngineMessages.MSG_UNREGISTER_CLIENT:
                	synchronized (mClients_V0) {
                		mClients_V0.remove(msg.replyTo);
                	}
                    break;
                case AceStreamEngineMessages.MSG_START:
					clientStartEngine(CLIENT_TYPE_MESSENGER, null,null);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }	
	
	private void broadcastMessengerClients(Message msg) {
		synchronized (mClients_V0) {
			final int cnt = mClients_V0.size();
			if(cnt > 0) {
				for(int i = cnt-1; i >= 0; i--) {
					try {
						switch (msg.what) {
						case REPORT_UNPACKING:
		                	mClients_V0.get(i).send(Message.obtain(null, AceStreamEngineMessages.MSG_ENGINE_UNPACKING));
		                    break;
						case REPORT_STARTING:
							mClients_V0.get(i).send(Message.obtain(null, AceStreamEngineMessages.MSG_ENGINE_STARTING));
							break;
						case REPORT_READY:
							synchronized (mStatusLock) {
								int port = -1;
								if(msg.arg1 != 0) {
									port = msg.arg1;
								}
								mClients_V0.get(i).send(Message.obtain(null, AceStreamEngineMessages.MSG_ENGINE_READY, port, 0));
							}
							break;
						case REPORT_STOPPED:
							mClients_V0.get(i).send(Message.obtain(null, AceStreamEngineMessages.MSG_ENGINE_STOPPED));
							break;
						case REPORT_PLAYLIST_UPDATED:
							mClients_V0.get(i).send(Message.obtain(null, AceStreamEngineMessages.MSG_PLAYLIST_UPDATED));
							break;
						case REPORT_EPG_UPDATED:
							mClients_V0.get(i).send(Message.obtain(null, AceStreamEngineMessages.MSG_EPG_UPDATED));
							break;
						case REPORT_RESTART_PLAYER:
							mClients_V0.get(i).send(Message.obtain(null, AceStreamEngineMessages.MSG_RESTART_PLAYER));
							break;
						case REPORT_SETTINGS_UPDATED:
							mClients_V0.get(i).send(Message.obtain(null, AceStreamEngineMessages.MSG_SETTINGS_UPDATED));
							break;
						default:
							break;
						} 
					} catch (RemoteException e) {
                    	mClients_V0.remove(i);
                    }
				}
			}
		}
	}
	
	/* Client Broadcaster */
	private static final int REPORT_UNPACKING = 1;
	private static final int REPORT_STARTING = 2;
	private static final int REPORT_READY = 3;
	private static final int REPORT_STOPPED = 4;
	private static final int REPORT_PLAYLIST_UPDATED = 5;
	private static final int REPORT_EPG_UPDATED = 6;
	private static final int REPORT_RESTART_PLAYER = 7;
	private static final int REPORT_SETTINGS_UPDATED = 8;

	private static final int MAINTAIN_INTERVAL = 900000;
	private static final int ACECAST_MIN_RESTART_INTERVAL = 60000;

	private final Runnable mMaintainTask = new Runnable() {
		@Override
		public void run() {
			try {
				Log.d(TAG, "maintain task");
				restartAceCastServer();
			}
			catch(Throwable e) {
				Log.e(TAG, "maintain error", e);
			}
			finally {
				mTaskHandler.postDelayed(mMaintainTask, MAINTAIN_INTERVAL);
			}
		}
	};

    private org.acestream.engine.service.v0.IAceStreamEngineCallback mCallback = new org.acestream.engine.service.v0.IAceStreamEngineCallback.Stub() {
        @Override
        public void onUnpacking() {
        	Log.d(TAG, "delegate callback: onUnpacking");
			mBroadcaster.sendEmptyMessage(REPORT_UNPACKING);
        }
        @Override
        public void onStarting() {
			Log.d(TAG, "delegate callback: onStarting");
			mBroadcaster.sendEmptyMessage(REPORT_STARTING);
        }
        @Override
        public void onReady(int port) {
			Log.d(TAG, "delegate callback: onReady: port=" + port);
			Message msg = mBroadcaster.obtainMessage(REPORT_READY, port, 0);
			mBroadcaster.sendMessage(msg);
        }
        @Override
        public void onWaitForNetworkConnection() {
        	// Old method, do nothing
        }

		@Override
		public void onPlaylistUpdated() {
			Log.d(TAG, "delegate callback: onPlaylistUpdated");
			mBroadcaster.sendEmptyMessage(REPORT_PLAYLIST_UPDATED);
		}

		@Override
		public void onEPGUpdated() {
			Log.d(TAG, "delegate callback: onEPGUpdated");
			mBroadcaster.sendEmptyMessage(REPORT_EPG_UPDATED);
		}

		@Override
		public void onSettingsUpdated() {
			Log.d(TAG, "delegate callback: onSettingsUpdated");
			mBroadcaster.sendEmptyMessage(REPORT_SETTINGS_UPDATED);
		}

		@Override
		public void onRestartPlayer() {
			Log.d(TAG, "delegate callback: onRestartPlayer");
			mBroadcaster.sendEmptyMessage(REPORT_RESTART_PLAYER);
		}

		@Override
        public void onStopped() {
			Log.d(TAG, "delegate callback: onStopped");
			mBroadcaster.sendEmptyMessage(REPORT_STOPPED);
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected: package=" + name.getPackageName());
            mDelegateService = null;
            mDelegateBound = false;
        }
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected: package=" + name.getPackageName());
            mDelegateService = org.acestream.engine.service.v0.IAceStreamEngine.Stub.asInterface(service);
            try {
                mDelegateService.registerCallback(mCallback);

				// send from queue
				boolean startEngineSent = false;
				while (!mDelegateMessageQueue.isEmpty()) {
					String msg = mDelegateMessageQueue.poll();
					if (msg != null) {
						Log.d(TAG, "onServiceConnected: process message from queue: msg=" + msg);
						if(TextUtils.equals(msg, "startEngine")) {
							// Start engine only once. There may be multiple "start" commands in queue.
							if(!startEngineSent) {
								startDelegatedEngine();
								startEngineSent = true;
							}
						}
					}
				}

            } catch (RemoteException e) {
                Log.e(TAG, "error", e);
            }
        }
    };

	private void startAceCastServer() {
		Log.v(TAG, "startAceCastServer");

		if(AceStreamEngineBaseApplication.shouldStartAceCastServer()) {
			// restart acecast server
			mEnableAceCastServer = true;
			mLastAceCastServerStartAt = System.currentTimeMillis();
			AceStreamDiscoveryServerService.Client.startService(AceStreamEngineBaseApplication.context());
		}
	}

	private void restartAceCastServer() {
		if(mEnableAceCastServer && AceStreamEngineBaseApplication.shouldStartAceCastServer()) {
			// restart acecast server
			long now = System.currentTimeMillis();
			long age = now - mLastAceCastServerStartAt;

			if(age > ACECAST_MIN_RESTART_INTERVAL) {
				mLastAceCastServerStartAt = now;
				AceStreamDiscoveryServerService.Client.restartService(AceStreamEngineBaseApplication.context());
			}
			else {
				Log.v(TAG, "restartAceCastServer: skip restart, age=" + age + "ms");
			}
		}
	}

	@SuppressLint("HandlerLeak")
	private final Handler mBroadcaster = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// Handle playlist/EPG update.
			// Need to handle these events here because there is no client that is guarantied to
			// listen to them.
			switch (msg.what) {
				case REPORT_PLAYLIST_UPDATED:
					AceStreamEngineBaseApplication.requestChannelsSync();
					break;
				case REPORT_EPG_UPDATED:
					AceStreamEngineBaseApplication.requestEPGSync();
					break;
				default:
					break;
			}

			broadcastAIDLClients(msg);
			broadcastMessengerClients(msg);
		}
	};
	
	@Override
	public IBinder onBind(Intent intent) {
	    String action = intent.getAction();
	    //TODO: check for null action for messenger?

		if("org.acestream.engine.service.v0.IAceStreamEngine".equals(action)) {
			return mBinderPublicV0;
		}
		else {
			return mMessenger_V0.getBinder();
		}
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.d(TAG, "onUnbind");
		return false;
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "#Create Service# (" + BuildConfig.APPLICATION_ID + ")");

        super.onCreate();

		mStopFlag = false;
		mNetworkStatus = NetworkStatus.CONNECTED;

		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_PACKAGE_ADDED);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		filter.addDataScheme("package");
		registerReceiver(mInstallListener, filter);

		if(mIsDelegatedService) {
			startDelegatedService();
		}
		else {
			startEngineService();
		}
	}

	private void startDelegatedService() {
		String targetApp = AceStream.getTargetApp();
		Log.d(TAG, "startDelegatedService: this=" + BuildConfig.APPLICATION_ID + " target=" + targetApp);
		Intent intent = new Intent(org.acestream.engine.service.v0.IAceStreamEngine.class.getName());
		intent.setPackage(targetApp);
		mDelegateBound = bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		if(!mDelegateBound) {
			Log.e(TAG, "startDelegatedService: failed to bound to target service");
		}
		mDelegateMessageQueue = new ArrayDeque<>();
	}

	private void startEngineService() {
		Log.d(TAG, "startEngineService");

		mStarted = true;
		mApiPort = 62062;
		mHttpPort = 6878;
		mAccessToken = generateToken();

		mHttpAsyncTaskFactory = new HttpAsyncTask.Factory(mHttpPort, mAccessToken);

		if (AceStream.canWriteToExternalFilesDir()) {
			String logcatPath = AceStream.externalFilesDir() + "/logcat.log";
			Log.d(TAG, "Start writing logcat to " + logcatPath);
			File logcatFile = new File(logcatPath);
			File logcatDir = logcatFile.getParentFile();
			if (logcatDir != null) {
				if (!logcatDir.exists()) {
					Log.d(TAG, "Create app directory: " + logcatDir.getPath());
					logcatDir.mkdirs();
				}
			}
			LogcatOutputStreamWriter.getInstanse().setOutputFile(logcatFile);
			LogcatOutputStreamWriter.getInstanse().start();
		}

		registerReceiver(mNetworkStatusListener, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		registerReceiver(mSettingsListener, new IntentFilter(ACTION_CONNECTION_AVAILABILITY_CHANGED));

		LocalBroadcastManager.getInstance(this).registerReceiver(mLocalBroadcastReceiver, new IntentFilter(Constants.ACTION_MOBILE_NETWORK_DIALOG_RESULT));

		// listen to screen on/off events
		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(mScreenStatusListener, filter);

		Log.d(TAG, ">>> START DEVICE INFO <<<");
		Log.d(TAG, "compiled ABI: " + PyEmbedded.getCompiledABI());
		Log.d(TAG, "Files dir: " + AceStream.filesDir());
		Log.d(TAG, "External files dir: " + AceStream.externalFilesDir());
		Log.d(TAG, "External files dir state: " + Environment.getExternalStorageState());
		Log.d(TAG, "SDK: " + String.valueOf(Build.VERSION.SDK_INT));
		Log.d(TAG, "Device: " + Build.DEVICE);
		Log.d(TAG, "Model: " + Build.MODEL);
		Log.d(TAG, "Abi: " + Build.CPU_ABI);
		Log.d(TAG, "Abi2: " + Build.CPU_ABI2);
		Log.d(TAG, "Product: " + Build.PRODUCT);
		Runtime rt = Runtime.getRuntime();
		Log.d(TAG, "Total memory: " + Long.toString(rt.totalMemory()));
		Log.d(TAG, "Max memory: " + Long.toString(rt.maxMemory()));
		ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		if(am != null) {
			Log.d(TAG, "Memory class: " + Integer.toString(am.getMemoryClass()));
		}
		Log.d(TAG, ">>> END DEVICE INFO <<<");

		mTaskHandler.postDelayed(mMaintainTask, MAINTAIN_INTERVAL);

		AceStreamEngineBaseApplication.requestChannelsSync();
	}

	private void stopDelegatedService() {
		Log.d(TAG, "stopDelegatedService");
		if(mDelegateService != null) {
			try {
				mDelegateService.unregisterCallback(mCallback);
			}
			catch(RemoteException e) {
				Log.e(TAG, "unregisterCallback() failed", e);
			}
			mDelegateService = null;

			unbindService(mConnection);
			mDelegateBound = false;
		}
	}

	private void stopEngineService() {
		Log.d(TAG, "stopEngineService: started=" + mStarted);

		if(!mStarted) {
			return;
		}

		killPythonScriptProcess();

		unregisterReceiver(mNetworkStatusListener);
		unregisterReceiver(mSettingsListener);
		unregisterReceiver(mScreenStatusListener);

		LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalBroadcastReceiver);
		LogcatOutputStreamWriter.getInstanse().stop();

		mStarted = false;
	}
	
	@Override
	public void onDestroy() {
		Log.d(TAG, "#Destroy Service#");

		if(mIsDelegatedService) {
			stopDelegatedService();
		}
		else {
			stopEngineService();
		}

		unregisterReceiver(mInstallListener);

		mStatus = Status.FINISHED;

		mTaskManager.stop();
		mTaskHandler.removeCallbacksAndMessages(null);

		mRemoteCallbacks.kill();
		mBroadcaster.removeCallbacksAndMessages(null);

		super.onDestroy();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		boolean setStopFlag = false;
		boolean skipEngineStart = false;
		int clientType = -1;
		String callingApp = null;

		if(!AceStream.checkStorageAccess()) {
			Log.d(TAG, "onStartCommand: no storage access");
			return START_NOT_STICKY;
		}

		if(!mStarted) {
			Log.d(TAG, "onStartCommand: start service");
			startEngineService();
		}

		if(intent != null) {
			clientType = intent.getIntExtra(EXTRA_CLIENT_TYPE, -1);
			callingApp = intent.getStringExtra(EXTRA_CALLING_APP);

			setStopFlag = intent.getBooleanExtra("setStopFlag", false);
			skipEngineStart = intent.getBooleanExtra("skipEngineStart", false);

			if(intent.getBooleanExtra("startAceCastServer", false)) {
				Log.d(TAG, "onStartCommand: start AceCast server");
				startAceCastServer();
			}
		}

		if(setStopFlag) {
			Log.d(TAG, "onStartCommand: set stop flag");
			mStopFlag = true;
			synchronized (mStatusLock) {
				mStatus = Status.STOPPING;
				showNotification(R.string.notify_stopping);
			}
		}
		else {
			if(skipEngineStart) {
				Log.d(TAG, "onStartCommand: skip engine start");
			}
			else {
				Log.d(TAG, "onStartCommand: start engine");
				checkAdsNotification(clientType, callingApp);
				mStopFlag = false;
				synchronized (mStatusLock) {
					if (mStatus == Status.IDLE) {
						mStatus = Status.CONNECTING;
						Log.d(TAG, "#Start Service#: IDLE -> Unpack or Run script");
						if (Util.isUnpackRequired()) {
							startUnpackTask();
						} else {
							startPythonScript();
						}
					} else if (mStatus == Status.FINISHED) {
						mStatus = Status.CONNECTING;
						Log.d(TAG, "#Start Service#: FINISHED -> Run script");
						startPythonScript();
					} else {
						Log.d(TAG, "#Start Service#: " + String.valueOf(mStatus) + " -> skip");
					}
				}
			}
		}
		return START_NOT_STICKY;
	}

	@Override
	public void OnAceStreamAsyncTaskComplete(boolean success) {
		int task = mTaskManager.getTaskType();
		if(task == AsyncTaskManager.TASK_UNPACK) {
			Log.d(TAG, "Unpack callback: success=" + success);
			if(success) {
				startPythonScript();
			}
			else {
				notifyError();
			}
		}
		else if(task == AsyncTaskManager.TASK_TRY_TO_CONNECT) {
			Log.d(TAG, "Connect callback: "+String.valueOf(success));
			if(success) {
				notifyReady(true);
			}
			else if( !mTaskManager.isTerminated() ) {
				notifyError();
			}
		}
	}

	private void executeClientStartCommand(
			final int clientType,
			final String clientApp,
			final org.acestream.engine.service.v0.IStartEngineResponse callback) {
		synchronized (mStatusLock) {
			mRestartAfterStopFlag = false;

			try {
				if (mStatus == Status.IDLE) {
					Logger.v(TAG, "Client command to Start: IDLE -> start service");
					Intent serviceIntent = ServiceClient.getServiceIntent(this);
					serviceIntent.putExtra(EXTRA_CLIENT_TYPE, clientType);
					serviceIntent.putExtra(EXTRA_CALLING_APP, clientApp);
					Util.startForegroundService(this, serviceIntent);
				} else if (mStatus == Status.FINISHED) {
					Logger.v(TAG, "Client command to Start: FINISHED -> start service");
					Intent serviceIntent = ServiceClient.getServiceIntent(this);
					serviceIntent.putExtra(EXTRA_CLIENT_TYPE, clientType);
					serviceIntent.putExtra(EXTRA_CALLING_APP, clientApp);
					Util.startForegroundService(this, serviceIntent);
				} else if (mStatus == Status.RUNNING) {
					Logger.vv(TAG, "Client command to Start: RUNNING -> notify ready: callback=" + callback);
					if (callback != null) {
						try {
							callback.onResult(true);
						} catch (RemoteException e) {
							Log.e(TAG, "onResponse failed", e);
						}
					} else {
						notifyReady(false);
					}
				} else if (mStatus == Status.STOPPING) {
					Logger.v(TAG, "Client command to Start: STOPPING -> restart after stop");
					mRestartAfterStopFlag = true;
				} else {
					Logger.v(TAG, "Client command to Start: " + String.valueOf(mStatus) + " -> skip");
				}
			}
			catch(ServiceClient.ServiceMissingException e) {
				Log.e(TAG, "AceStream is not installed");
			}
		}
	}
	
	private void executeOnConnectivityChanged() {
		synchronized(mStatusLock) {
			Logger.v(TAG, "executeOnConnectivityChanged: status=" + mStatus + " net=" + mNetworkStatus);
			if(mStatus == Status.RUNNING) {
				updateEngineOnlineStatus();
			}
		}
	}

	private void updateEngineOnlineStatus() {
		boolean isOnline;
		if (AceStreamEngineBaseApplication.isMobileNetworkingEnabled()) {
			isOnline = true;
		}
		else {
			isOnline = mNetworkStatus == NetworkStatus.CONNECTED;
		}

		Log.d(TAG, "updateEngineOnlineStatus: isOnline=" + isOnline);

		if(mHttpAsyncTaskFactory != null) {
			mHttpAsyncTaskFactory.build(HttpAsyncTask.HTTPTASK_SET_IS_ONLINE, null).execute2("GET", "value=" + String.valueOf(isOnline));
		}
	}
	
	private void fireScreenStatusChanged(boolean screenOn) {
	    synchronized (mStatusLock) {
	        if(mStatus == Status.RUNNING) {
	            Log.d(TAG, "fireScreenStatusChanged: value=" + String.valueOf(screenOn));
	            if(mHttpAsyncTaskFactory != null) {
					mHttpAsyncTaskFactory.build(HttpAsyncTask.HTTPTASK_SET_SCREEN_STATUS, null).execute2("GET", "value=" + (screenOn ? "true" : "false"));
				}
	        }
	        else {
	            Log.d(TAG, "fireScreenStatusChanged: skip: status=" + mStatus);
	        }
        }
	}
	
	private void startUnpackTask() {
		Log.d(TAG, "startUnpackTask");
		
		synchronized (mStatusLock) {
			mStatus = Status.UNPACKING;
			
			mBroadcaster.sendEmptyMessage(REPORT_UNPACKING);
			
			mTaskManager.setTaskType(AsyncTaskManager.TASK_UNPACK);
			mTaskManager.setListener(this);
			mTaskManager.setNotificationManager(getNotificationManager());
			
			mTaskHandler.post(mTaskManager);
		}
	}
	
	private void startConnectingTask() {
		Log.d(TAG, "startConnectingTask");
		
		synchronized (mStatusLock) {
			mStatus = Status.CONNECTING;
			
			mBroadcaster.sendEmptyMessage(REPORT_STARTING);
			
			mTaskManager.setTaskType(AsyncTaskManager.TASK_TRY_TO_CONNECT);
			mTaskManager.setListener(this);
			mTaskManager.setNotificationManager(getNotificationManager());
			
			mTaskHandler.post(mTaskManager);
		}
	}
	
	private void startPythonScript() {
		Log.d(TAG, "startPythonScript: mNetworkStatus=" + mNetworkStatus);

		killPythonScriptProcess();
		new RunPythonScript().execute();
	}

	private void askAboutMobileNetwork() {
		Intent intent = new Intent(this, MobileNetworksDialogActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}

	public void notifyPlaylistUpdated() {
		Log.d(TAG, "notifyPlaylistUpdated");
		mBroadcaster.sendEmptyMessage(REPORT_PLAYLIST_UPDATED);
	}

	public void notifyEPGUpdated() {
		Log.d(TAG, "notifyEPGUpdated");
		mBroadcaster.sendEmptyMessage(REPORT_EPG_UPDATED);
	}

	public void notifyRestartPlayer() {
		Log.d(TAG, "notifyRestartPlayer");
		mBroadcaster.sendEmptyMessage(REPORT_RESTART_PLAYER);
	}

	public void notifySettingsUpdated() {
		Log.d(TAG, "notifySettingsUpdated");
		mBroadcaster.sendEmptyMessage(REPORT_SETTINGS_UPDATED);
	}
	
	private void notifyReady(boolean justStarted) {
		Log.d(TAG, "notifyReady: justStarted=" + justStarted);
		
		synchronized (mStatusLock) {
			mStatus = Status.RUNNING;
			Message msg = mBroadcaster.obtainMessage(REPORT_READY, mApiPort, 0);
			mBroadcaster.sendMessage(msg);
			showNotification(R.string.notify_running);
		}
	}
	
	private void notifyError() {
		Log.d(TAG, "notifyError");
		
		synchronized (mStatusLock) {
			mStatus = Status.FINISHED;
			mBroadcaster.sendEmptyMessage(REPORT_READY); // with value -1
			mTaskManager.stop();
			showNotification(R.string.notify_error);
		}
	}
	
	private void notifyErrorAndStop() {
		Log.d(TAG, "notifyError: stopFlag=" + mStopFlag);
		
		synchronized (mStatusLock) {
			mStatus = Status.FINISHED;
			mBroadcaster.sendEmptyMessage(REPORT_READY); // with value -1
			mTaskManager.stop();
			showNotification(R.string.notify_error);
			if(mStopFlag) {
				stopSelf();
			}
		}
	}
	
	private void notifyStopped() {
		Log.d(TAG, "notifyStopped: stopFlag=" + mStopFlag + " restartAfterStopFlag=" + mRestartAfterStopFlag);
		
		synchronized (mStatusLock) {
			mStatus = Status.FINISHED;

			if(mRestartAfterStopFlag) {
				mRestartAfterStopFlag = false;
				try {
					Util.startForegroundService(this, ServiceClient.getServiceIntent(this));
				}
				catch(ServiceClient.ServiceMissingException e) {
					Log.e(TAG, "AceStream is not installed");
				}
			}
			else {
				mTaskManager.stop();
				mBroadcaster.sendEmptyMessage(REPORT_STOPPED);
				showNotification(R.string.notify_stopped);
				if (mStopFlag) {
					mStopFlag = false;
					stopSelf();
				}
			}
		}
	}
	
	private void killPythonScriptProcess() {
		if(mPyEmbedded != null) {
			mPyEmbedded.kill();
		}
	}
	
	private class RunPythonScript extends AsyncTask<Void, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Void... arg0) {
			try {
				mPyEmbedded = new PyEmbedded(AceStreamEngineService.this, null, mApiPort, mHttpPort, mAccessToken);
				mPyEmbedded.setOnPythonProcessFinishedListener(mPythonListener);
				mPyEmbedded.start();
			}
			catch(Exception e) {
				Log.e(TAG, "Failed to start pyembedded", e);
				return false;
			}
			return true;
		}
		@Override
		protected void onPostExecute(Boolean status) {
			if(status) {
				Log.d(TAG, "Started");
				startConnectingTask();
			}
			else {
				notifyErrorAndStop();
			}
		}
	}
	
	private PythonProcessFinishedListener mPythonListener = new PythonProcessFinishedListener();

	private class PythonProcessFinishedListener implements IPyFinishedListener {
		@Override
		public void run() {
			Log.d(TAG, "Finishing script task");
			if(mStatus == Status.CONNECTING) {
				notifyErrorAndStop();
			}
			else {
				notifyStopped();
			}
		}
	}

	private class AceStreamEngineCallbackData {
	    private boolean mSupportsExtendedNotifications;
	    AceStreamEngineCallbackData(boolean supportsExtendedNotifications) {
            mSupportsExtendedNotifications = supportsExtendedNotifications;
        }

        boolean supportsExtendedNotifications() {
	        return mSupportsExtendedNotifications;
        }
    }

	private class AceStreamEngineCallbackWrapper {
	    final private IInterface mCallback;
	    final private AceStreamEngineCallbackData mData;

	    AceStreamEngineCallbackWrapper(@NonNull IInterface callback, AceStreamEngineCallbackData data) {
            mCallback = callback;

            if(data == null) {
                mData = new AceStreamEngineCallbackData(false);
            }
            else {
                mData = data;
            }
        }

        AceStreamEngineCallbackData getData() {
	        return mData;
        }

        void onReady(int engineApiPort) throws RemoteException {
            if(mCallback instanceof org.acestream.engine.service.v0.IAceStreamEngineCallback) {
                ((org.acestream.engine.service.v0.IAceStreamEngineCallback)mCallback).onReady(engineApiPort);
            }
            else {
                Log.e(TAG, "Unknown callback type: class=" + mCallback.getClass().getName());
            }
        }

        void onUnpacking() throws RemoteException {
            if(mCallback instanceof org.acestream.engine.service.v0.IAceStreamEngineCallback) {
                ((org.acestream.engine.service.v0.IAceStreamEngineCallback) mCallback).onUnpacking();
            }
            else {
                Log.e(TAG, "Unknown callback type: class=" + mCallback.getClass().getName());
            }
        }

        void onStarting() throws RemoteException {
            if(mCallback instanceof org.acestream.engine.service.v0.IAceStreamEngineCallback) {
                ((org.acestream.engine.service.v0.IAceStreamEngineCallback) mCallback).onStarting();
            }
            else {
                Log.e(TAG, "Unknown callback type: class=" + mCallback.getClass().getName());
            }
        }

        void onStopped() throws RemoteException {
            if(mCallback instanceof org.acestream.engine.service.v0.IAceStreamEngineCallback) {
                ((org.acestream.engine.service.v0.IAceStreamEngineCallback) mCallback).onStopped();
            }
            else {
                Log.e(TAG, "Unknown callback type: class=" + mCallback.getClass().getName());
            }
        }

		void onPlaylistUpdated() throws RemoteException {
			if(mCallback instanceof org.acestream.engine.service.v0.IAceStreamEngineCallback) {
				((org.acestream.engine.service.v0.IAceStreamEngineCallback) mCallback).onPlaylistUpdated();
			}
			else {
				Log.e(TAG, "Unknown callback type: class=" + mCallback.getClass().getName());
			}
		}

		void onEPGUpdated() throws RemoteException {
			if(mCallback instanceof org.acestream.engine.service.v0.IAceStreamEngineCallback) {
				((org.acestream.engine.service.v0.IAceStreamEngineCallback) mCallback).onEPGUpdated();
			}
			else {
				Log.e(TAG, "Unknown callback type: class=" + mCallback.getClass().getName());
			}
		}

		void onRestartPlayer() throws RemoteException {
			if(mCallback instanceof org.acestream.engine.service.v0.IAceStreamEngineCallback) {
				((org.acestream.engine.service.v0.IAceStreamEngineCallback) mCallback).onRestartPlayer();
			}
			else {
				Log.e(TAG, "Unknown callback type: class=" + mCallback.getClass().getName());
			}
		}

		void onSettingsUpdated() throws RemoteException {
			if(mCallback instanceof org.acestream.engine.service.v0.IAceStreamEngineCallback) {
				((org.acestream.engine.service.v0.IAceStreamEngineCallback) mCallback).onSettingsUpdated();
			}
			else {
				Log.e(TAG, "Unknown callback type: class=" + mCallback.getClass().getName());
			}
		}
    }

	private String generateToken() {
		final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		int chars_size = chars.length();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 10; i++) {
			int index = (int) (Math.random() * chars_size);
			sb.append(chars.substring(index, index + 1));
		}
		return sb.toString();
	}

	public String getCallingApp() {
		return getPackageManager().getNameForUid(Binder.getCallingUid());
	}
}
