/*
 * Copyright 2015 Synced Synapse. All rights reserved.
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
package com.syncedsynapse.kore2.ui.hosts;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.syncedsynapse.kore2.R;
import com.syncedsynapse.kore2.host.HostInfo;
import com.syncedsynapse.kore2.jsonrpc.ApiCallback;
import com.syncedsynapse.kore2.jsonrpc.ApiException;
import com.syncedsynapse.kore2.jsonrpc.HostConnection;
import com.syncedsynapse.kore2.jsonrpc.method.JSONRPC;
import com.syncedsynapse.kore2.utils.LogUtils;
import com.syncedsynapse.kore2.utils.NetUtils;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * Fragment that presents the welcome message
 */
public class HostFragmentManualConfiguration extends Fragment {
    private static final String TAG = LogUtils.makeLogTag(HostFragmentManualConfiguration.class);

    /**
     * Fragment arguments
     */
    private static final String PREFIX = "com.syncedsynapse.kore2";
    public static final String HOST_ID = PREFIX + ".host_id",
            HOST_NAME = PREFIX + ".host_name",
            HOST_ADDRESS = PREFIX + ".host_address",
            HOST_HTTP_PORT = PREFIX + ".host_http_port",
            HOST_TCP_PORT = PREFIX + ".host_tcp_post",
            HOST_USERNAME = PREFIX + ".host_username",
            HOST_PASSWORD = PREFIX + ".host_password",
            HOST_MAC_ADDRESS = PREFIX + ".host_mac_address",
            HOST_WOL_PORT = PREFIX + ".host_wol_port";
    public static final String GO_STRAIGHT_TO_TEST = PREFIX + ".go_straight_to_test";

    /**
     * Callback interface to communicate with the encolsing activity
     */
    public interface HostManualConfigurationListener {
        public void onHostManualConfigurationNext(HostInfo hostInfo);
        public void onHostManualConfigurationCancel();
    }

    public static String CANCEL_BUTTON_LABEL_ARG = PREFIX + ".cancel_button_label";
    private HostManualConfigurationListener listener;
    private ProgressDialog progressDialog;

    @InjectView(R.id.xbmc_name) EditText xbmcNameEditText;
    @InjectView(R.id.xbmc_ip_address) EditText xbmcIpAddressEditText;
    @InjectView(R.id.xbmc_http_port) EditText xbmcHttpPortEditText;
    @InjectView(R.id.xbmc_tcp_port) EditText xbmcTcpPortEditText;
    @InjectView(R.id.xbmc_username) EditText xbmcUsernameEditText;
    @InjectView(R.id.xbmc_password) EditText xbmcPasswordEditText;
    @InjectView(R.id.xbmc_mac_address) EditText xbmcMacAddressEditText;
    @InjectView(R.id.xbmc_wol_port) EditText xbmcWolPortEditText;

    // Handler for callbacks
    final Handler handler = new Handler();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_add_host_manual_configuration, container, false);
        ButterKnife.inject(this, root);

        // Check if we were given a host info
        String hostName = getArguments().getString(HOST_NAME);
        String hostAddress = getArguments().getString(HOST_ADDRESS);
        int hostHttpPort = getArguments().getInt(HOST_HTTP_PORT, HostInfo.DEFAULT_HTTP_PORT);
        int hostTcpPort = getArguments().getInt(HOST_TCP_PORT, HostInfo.DEFAULT_TCP_PORT);
        String hostUsername = getArguments().getString(HOST_USERNAME);
        String hostPassword = getArguments().getString(HOST_PASSWORD);
        String hostMacAddress = getArguments().getString(HOST_MAC_ADDRESS);
        int hostWolPort = getArguments().getInt(HOST_WOL_PORT, HostInfo.DEFAULT_WOL_PORT);

        if (hostAddress != null) {
            xbmcNameEditText.setText(hostName);
            xbmcIpAddressEditText.setText(hostAddress);
            xbmcHttpPortEditText.setText(String.valueOf(hostHttpPort));
            if (!TextUtils.isEmpty(hostUsername))
                xbmcUsernameEditText.setText(hostUsername);
            if (!TextUtils.isEmpty(hostPassword))
                xbmcPasswordEditText.setText(hostPassword);
            if (hostTcpPort != HostInfo.DEFAULT_TCP_PORT)
                xbmcTcpPortEditText.setText(String.valueOf(hostTcpPort));
            if (!TextUtils.isEmpty(hostMacAddress))
                xbmcMacAddressEditText.setText(hostMacAddress);
            if (hostWolPort != HostInfo.DEFAULT_WOL_PORT)
                xbmcWolPortEditText.setText(String.valueOf(hostWolPort));
        }

        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (getView() == null)
            return;

        progressDialog = new ProgressDialog(getActivity());
        Button next, previous;

        // Next button
        next = (Button)getView().findViewById(R.id.next);
        next.setText(R.string.test_connection);
        next.setCompoundDrawables(null, null, null, null);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testConnection();
            }
        });

        // Previous button
        previous = (Button)getView().findViewById(R.id.previous);

        if (getArguments().getString(CANCEL_BUTTON_LABEL_ARG, null) != null) {
            previous.setText(getArguments().getString(CANCEL_BUTTON_LABEL_ARG));
        } else {
            previous.setText(android.R.string.cancel);
        }
        previous.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onHostManualConfigurationCancel();
            }
        });

        // Check if the activity wants us to go straight to test
        boolean goStraighToTest = getArguments().getBoolean(GO_STRAIGHT_TO_TEST, false);
        if (goStraighToTest) {
            testConnection();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (HostManualConfigurationListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement AddHostManualConfigurationListener interface.");
        }
    }

    /**
     * Tests a connection with the values set in the UI.
     * Checks whether the values are correctly set, and then tries to make
     * a ping call. First through HTTP, and if it succeeds, through TCP to
     * check availability. Finally adds the host and advances the wizard
     */
    private void testConnection() {
        String xbmcName = xbmcNameEditText.getText().toString();
        String xbmcAddress = xbmcIpAddressEditText.getText().toString();

        int xbmcHttpPort;
        String aux = xbmcHttpPortEditText.getText().toString();
        try {
            xbmcHttpPort = TextUtils.isEmpty(aux) ? HostInfo.DEFAULT_HTTP_PORT : Integer.valueOf(aux);
        } catch (NumberFormatException exc) {
            xbmcHttpPort = -1;
        }

        String xbmcUsername = xbmcUsernameEditText.getText().toString();
        String xbmcPassword = xbmcPasswordEditText.getText().toString();
        aux = xbmcTcpPortEditText.getText().toString();
        int xbmcTcpPort;
        try {
            xbmcTcpPort = TextUtils.isEmpty(aux) ? HostInfo.DEFAULT_TCP_PORT : Integer.valueOf(aux);
        } catch (NumberFormatException exc) {
            xbmcTcpPort = -1;
        }

        String macAddress = xbmcMacAddressEditText.getText().toString();
        aux = xbmcWolPortEditText.getText().toString();
        int xbmcWolPort = TextUtils.isEmpty(aux) ? HostInfo.DEFAULT_WOL_PORT : Integer.valueOf(aux);

        // Check Xbmc name and address
        if (TextUtils.isEmpty(xbmcName)) {
            Toast.makeText(getActivity(), R.string.wizard_no_name_specified, Toast.LENGTH_SHORT).show();
            xbmcNameEditText.requestFocus();
            return;
        } else if (TextUtils.isEmpty(xbmcAddress)) {
            Toast.makeText(getActivity(), R.string.wizard_no_address_specified, Toast.LENGTH_SHORT).show();
            xbmcIpAddressEditText.requestFocus();
            return;
        } else if (xbmcHttpPort <= 0) {
            Toast.makeText(getActivity(), R.string.wizard_invalid_http_port_specified, Toast.LENGTH_SHORT).show();
            xbmcHttpPortEditText.requestFocus();
            return;
        } else if (xbmcTcpPort <= 0) {
            Toast.makeText(getActivity(), R.string.wizard_invalid_tcp_port_specified, Toast.LENGTH_SHORT).show();
            xbmcTcpPortEditText.requestFocus();
            return;
        }

        // If username or password empty, set it to null
        if (TextUtils.isEmpty(xbmcUsername))
            xbmcUsername = null;
        if (TextUtils.isEmpty(xbmcPassword))
            xbmcPassword = null;

        // Ok, let's try to ping the host
        final HostInfo checkedHostInfo = new HostInfo(xbmcName, xbmcAddress,
                xbmcHttpPort, xbmcTcpPort,
                xbmcUsername, xbmcPassword);
        checkedHostInfo.setMacAddress(macAddress);
        checkedHostInfo.setWolPort(xbmcWolPort);

        progressDialog.setTitle(String.format(getResources().getString(R.string.wizard_connecting_to_xbmc_title), xbmcName));
        progressDialog.setMessage(getResources().getString(R.string.wizard_connecting_to_xbmc_message));
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        progressDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                // Let's ping the host through HTTP
                chainCallCheckHttpConnection(checkedHostInfo);
            }
        });
        progressDialog.show();
    }

    private void chainCallCheckHttpConnection(final HostInfo hostInfo) {
        // Let's ping the host through HTTP
        final HostConnection hostConnection = new HostConnection(hostInfo);
        hostConnection.setProtocol(HostConnection.PROTOCOL_HTTP);
        final JSONRPC.Ping httpPing = new JSONRPC.Ping();
        httpPing.execute(hostConnection, new ApiCallback<String>() {
            @Override
            public void onSucess(String result) {
                LogUtils.LOGD(TAG, "Successfully connected to new host through HTTP.");
                // Great, we managed to connect through HTTP, let's check it's version
                chainCallCheckTcpConnection(hostConnection, hostInfo);
            }

            @Override
            public void onError(int errorCode, String description) {
                // Couldn't connect through HTTP, abort, and initialize checkedHostInfo
                hostConnection.disconnect();
                hostConnectionError(errorCode, description);
            }
        }, handler);
    }

    private void chainCallCheckTcpConnection(final HostConnection hostConnection, final HostInfo hostInfo) {
        final JSONRPC.Ping tcpPing = new JSONRPC.Ping();
        hostConnection.setProtocol(HostConnection.PROTOCOL_TCP);
        tcpPing.execute(hostConnection, new ApiCallback<String>() {
            @Override
            public void onSucess(String result) {
                // Great, we managed to connect through HTTP and TCP
                hostConnection.disconnect();
                LogUtils.LOGD(TAG, "Successfully connected to new host through TCP.");
                // Notify connection checked through TCP
                hostConnectionChecked(hostInfo, HostConnection.PROTOCOL_TCP);
            }

            @Override
            public void onError(int errorCode, String description) {
                // We only managed to connect through HTTP, revert checkedHostInfo to use HTTP
                hostConnection.disconnect();
                LogUtils.LOGD(TAG, "Couldn't connect to host through TCP. Message: " + description);
                // Notify connection checked through TCP
                hostConnectionChecked(hostInfo, HostConnection.PROTOCOL_HTTP);
            }
        }, handler);
    }

    /**
     * The connection was checked, and hostInfo has all the correct parameters to communicate
     * with it
     * @param hostInfo {@link HostInfo} to add
     */
    private void hostConnectionChecked(final HostInfo hostInfo, int protocol) {
        hostInfo.setProtocol(protocol);
        // Let's get the MAC Address, if we don't have one
        if (TextUtils.isEmpty(hostInfo.getMacAddress())) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String localMacAddress = NetUtils.getMacAddress(hostInfo.getAddress());
                    hostInfo.setMacAddress(localMacAddress);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            listener.onHostManualConfigurationNext(hostInfo);
                        }
                    });
                }
            }).start();
        } else {
            // Mac address was supplied
            progressDialog.dismiss();
            listener.onHostManualConfigurationNext(hostInfo);
        }
    }

    /**
     * Treats errors occurred during the connection check
     * @param errorCode Error code
     * @param description Description
     */
    private void hostConnectionError(int errorCode, String description) {
        progressDialog.dismiss();
        LogUtils.LOGD(TAG, "An error occurred during connection testint. Message: " + description);
        switch (errorCode) {
            case ApiException.HTTP_RESPONSE_CODE_UNAUTHORIZED:
                String username = xbmcUsernameEditText.getText().toString(),
                        password = xbmcPasswordEditText.getText().toString();
                int messageResourceId;
                if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                    messageResourceId = R.string.wizard_empty_authentication;
                } else {
                    messageResourceId = R.string.wizard_incorrect_authentication;
                }
                Toast.makeText(getActivity(), messageResourceId, Toast.LENGTH_SHORT).show();
                xbmcUsernameEditText.requestFocus();
                break;
            default:
                Toast.makeText(getActivity(),
                        R.string.wizard_error_connecting,
                        Toast.LENGTH_SHORT).show();
                break;
        }
    }
}
