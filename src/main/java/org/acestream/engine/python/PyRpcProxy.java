package org.acestream.engine.python;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.content.Context;

import com.googlecode.android_scripting.SimpleServer;
import com.googlecode.android_scripting.jsonrpc.JsonRpcServer;
import com.googlecode.android_scripting.jsonrpc.RpcReceiver;
import com.googlecode.android_scripting.jsonrpc.RpcReceiverManager;
import com.googlecode.android_scripting.jsonrpc.RpcReceiverManagerFactory;

public class PyRpcProxy {
	private InetSocketAddress mAddress;
	private final JsonRpcServer mJsonRpcServer;
	private final UUID mSecret;
	private final PyRpcManagerFactory mRpcManFactory;
	
	public class PyRpcManager extends RpcReceiverManager {
		private final Context mContext;
		private final PyEmbedded.Callback mCallback;
		public PyRpcManager(Context context, PyEmbedded.Callback callback, Collection<Class<? extends RpcReceiver>> classList) {
			super(classList);
			mContext = context;
			mCallback = callback;
		}
		public Context getContext() {
			return mContext;
		}
		public PyEmbedded.Callback getCallback() {
			return mCallback;
		}
	}
	
	public class PyRpcManagerFactory implements RpcReceiverManagerFactory {
		private final Collection<Class<? extends RpcReceiver>> mClassList;
		private final List<RpcReceiverManager> mRpcRecManagers;
		private final Context mContext;
		private final PyEmbedded.Callback mCallback;
		public PyRpcManagerFactory(Context context, PyEmbedded.Callback callback, Collection<Class<? extends RpcReceiver>> classList) {
				mContext = context;
				mCallback = callback;
			    mClassList = classList;
			    mRpcRecManagers = new ArrayList<RpcReceiverManager>();
		}
		@Override
		public PyRpcManager create() {
			PyRpcManager rpcReceiverManager = new PyRpcManager(mContext, mCallback, mClassList);
			mRpcRecManagers.add(rpcReceiverManager);
		    return rpcReceiverManager;
		}
		@Override
		public List<RpcReceiverManager> getRpcReceiverManagers() {
			return mRpcRecManagers;
		}
	}
	
	@SuppressWarnings("WeakerAccess")
	public PyRpcProxy(Context context, PyEmbedded.Callback callback, boolean requiresHandshake) {
		if(requiresHandshake) {
			mSecret = UUID.randomUUID();
	    } 
		else {
			mSecret = null;
	    }
		mRpcManFactory = new PyRpcManagerFactory(context, callback, getRpcPyModuleList());
	    mJsonRpcServer = new JsonRpcServer(mRpcManFactory, getSecret());
	}
	
	@SuppressWarnings("UnusedReturnValue")
	public InetSocketAddress startLocal() throws Exception {
        return startLocal(0);
	}

	@SuppressWarnings("WeakerAccess")
	public InetSocketAddress startLocal(int port) throws Exception {
	    mAddress = mJsonRpcServer.startLocal(port);
	    return mAddress;
	}
	
	public void shutdown() {
	    mJsonRpcServer.shutdown();
	}

	@SuppressWarnings("WeakerAccess")
	public InetSocketAddress getAddress() {
	    return mAddress;
	}
	
	public String getHost() {
		String result = getAddress().getHostName();
	    if(result.equals("0.0.0.0")) {
	    	try {
	    		return SimpleServer.getPublicInetAddress().getHostName();
	    	} 
	    	catch (UnknownHostException e) {
	    		e.printStackTrace();
	    	} 
	    	catch (SocketException e) {
	    		e.printStackTrace();
	    	}
	    }
	    return result;
	}
	
	public Integer getPort() {
		return mAddress.getPort();
	}
	
	public String getSecret() {
		if (mSecret == null) {
			return null;
		}
	    return mSecret.toString();
	}

	public RpcReceiverManagerFactory getRpcReceiverManagerFactory() {
		return mRpcManFactory;
	}
	
	public Collection<Class<? extends RpcReceiver>> getRpcPyModuleList() {
		Set<Class<? extends RpcReceiver>> pymodClassList = new HashSet<Class<? extends RpcReceiver>>();
		pymodClassList.add(PyRpcModule.class);
	    return pymodClassList;
	}
}
