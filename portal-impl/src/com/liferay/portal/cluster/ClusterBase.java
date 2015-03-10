/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.cluster;

import com.liferay.portal.kernel.cluster.ClusterReceiver;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.CharPool;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.SocketUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.util.PropsValues;

import java.net.InetAddress;
import java.net.NetworkInterface;

import org.jgroups.JChannel;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolStack;

/**
 * @author Shuyang Zhou
 */
public abstract class ClusterBase {

	public void afterPropertiesSet() {
		if (!isEnabled()) {
			return;
		}

		if (!_initJGroupsProperties) {
			initSystemProperties();

			try {
				initBindAddress();
			}
			catch (Exception e) {
				if (_log.isWarnEnabled()) {
					_log.warn("Failed to initialize outgoing IP address", e);
				}
			}

			_initJGroupsProperties = true;
		}
	}

	public abstract void destroy();

	public boolean isEnabled() {
		return PropsValues.CLUSTER_LINK_ENABLED;
	}

	protected JChannel createJChannel(
			String properties, ClusterReceiver clusterReceiver,
			String clusterName)
		throws Exception {

		JChannel jChannel = new JChannel(properties);

		jChannel.setReceiver(new JGroupsReceiver(clusterReceiver));

		jChannel.connect(clusterName);

		if (_log.isInfoEnabled()) {
			_log.info(
				"Create a new channel with properties " +
					jChannel.getProperties());
		}

		return jChannel;
	}

	protected InetAddress getBindInetAddress(JChannel jChannel) {
		ProtocolStack protocolStack = jChannel.getProtocolStack();

		Protocol protocol = protocolStack.getBottomProtocol();

		return (InetAddress)protocol.getValue("bind_addr");
	}

	protected void initBindAddress() throws Exception {
		String autodetectAddress = PropsValues.CLUSTER_LINK_AUTODETECT_ADDRESS;

		if (Validator.isNull(autodetectAddress)) {
			return;
		}

		String host = autodetectAddress;
		int port = 80;

		int index = autodetectAddress.indexOf(CharPool.COLON);

		if (index != -1) {
			host = autodetectAddress.substring(0, index);
			port = GetterUtil.getInteger(
				autodetectAddress.substring(index + 1), port);
		}

		if (_log.isInfoEnabled()) {
			_log.info(
				"Autodetecting JGroups outgoing IP address and interface for " +
					host + ":" + port);
		}

		SocketUtil.BindInfo bindInfo = SocketUtil.getBindInfo(host, port);

		InetAddress inetAddress = bindInfo.getInetAddress();

		NetworkInterface networkInterface = bindInfo.getNetworkInterface();

		System.setProperty("jgroups.bind_addr", inetAddress.getHostAddress());
		System.setProperty(
			"jgroups.bind_interface", networkInterface.getName());

		if (_log.isInfoEnabled()) {
			_log.info(
				"Setting JGroups outgoing IP address to " +
					inetAddress.getHostAddress() + " and interface to " +
						networkInterface.getName());
		}
	}

	protected void initSystemProperties() {
		for (String systemProperty :
				PropsValues.CLUSTER_LINK_CHANNEL_SYSTEM_PROPERTIES) {

			int index = systemProperty.indexOf(CharPool.COLON);

			if (index == -1) {
				continue;
			}

			String key = systemProperty.substring(0, index);
			String value = systemProperty.substring(index + 1);

			System.setProperty(key, value);

			if (_log.isDebugEnabled()) {
				_log.debug(
					"Setting system property {key=" + key + ", value=" + value +
						"}");
			}
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(ClusterBase.class);

	private static boolean _initJGroupsProperties;

}