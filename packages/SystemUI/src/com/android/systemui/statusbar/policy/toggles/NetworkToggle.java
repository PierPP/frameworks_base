/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.policy.toggles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import com.android.systemui.R;
import android.os.Handler;
import android.os.Message;

public class NetworkToggle extends Toggle {
    private ToggleUpdateThread a_toggleUpdateThread; 
    boolean mDataEnabled;
    
    public NetworkToggle(Context context) {
        super(context);
        setLabel(R.string.toggle_data);
        mDataEnabled = isMobileDataEnabled();
        context.registerReceiver(getBroadcastReceiver(), getIntentFilter());
        
        a_toggleUpdateThread = new ToggleUpdateThread(handler);
        a_toggleUpdateThread.start();
    }

    private boolean isMobileDataEnabled() {
        ConnectivityManager cm = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getMobileDataEnabled();
    }

    private void setMobileDataEnabled(boolean on) {
        mDataEnabled = on;
        ConnectivityManager cm = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.setMobileDataEnabled(on);
    }

    @Override
    protected void onCheckChanged(boolean isChecked) {
        setMobileDataEnabled(isChecked);        
        if (isChecked) {
            setIcon(R.drawable.toggle_data);
        } else {
            setIcon(R.drawable.toggle_data_off);
        }
    }

    protected BroadcastReceiver getBroadcastReceiver() {
        return new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                    mDataEnabled = isMobileDataEnabled();
                    updateState();
                }
            }
        };
    }

    protected IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        return filter;
    }

    @Override
    protected boolean updateInternalToggleState() {
        mToggle.setChecked(mDataEnabled);
        if (mToggle.isChecked()) {
            setIcon(R.drawable.toggle_data);
        } else {
            setIcon(R.drawable.toggle_data_off);
        }
        return mToggle.isChecked();
    }

    @Override
    protected boolean onLongPress() {
        Intent intent = new Intent(
                android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        return true;
    }
    
    final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            updateState();
    	}
    };
    
    private class ToggleUpdateThread extends Thread {
        Handler mHandler;
       
        ToggleUpdateThread(Handler h) {
            mHandler = h;
        }
       
        public void run() {
            while (true) {
                 try {
                    Thread.sleep(500);
                } catch (InterruptedException e) { }
                Message msg = mHandler.obtainMessage();
                mHandler.sendMessage(msg);
            }
        }
    }
        
}
