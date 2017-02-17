/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.hardware.wifi.V1_0.IWifiApIface;
import android.hardware.wifi.V1_0.IWifiChip;
import android.hardware.wifi.V1_0.IWifiIface;
import android.hardware.wifi.V1_0.IWifiRttController;
import android.hardware.wifi.V1_0.IWifiStaIface;
import android.hardware.wifi.V1_0.StaApfPacketFilterCapabilities;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.net.apf.ApfCapabilities;
import android.net.wifi.WifiManager;

import com.android.server.wifi.util.NativeUtil;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.os.HandlerThread;
import android.os.Looper;
import android.os.RemoteException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

/**
 * Unit tests for {@link com.android.server.wifi.WifiVendorHal}.
 */
public class WifiVendorHalTest {

    WifiVendorHal mWifiVendorHal;
    private WifiStatus mWifiStatusSuccess;
    private WifiStatus mWifiStatusFailure;
    @Mock
    private HalDeviceManager mHalDeviceManager;
    @Mock
    private HandlerThread mWifiStateMachineHandlerThread;
    @Mock
    private WifiVendorHal.HalDeviceManagerStatusListener mHalDeviceManagerStatusCallbacks;
    @Mock
    private IWifiApIface mIWifiApIface;
    @Mock
    private IWifiChip mIWifiChip;
    @Mock
    private IWifiStaIface mIWifiStaIface;
    @Mock
    private IWifiRttController mIWifiRttController;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mWifiStatusSuccess = new WifiStatus();
        mWifiStatusSuccess.code = WifiStatusCode.SUCCESS;
        mWifiStatusFailure = new WifiStatus();
        mWifiStatusFailure.code = WifiStatusCode.ERROR_UNKNOWN;
        mWifiStatusFailure.description = "I don't even know what a Mock Turtle is.";
        when(mIWifiStaIface.enableLinkLayerStatsCollection(false)).thenReturn(mWifiStatusSuccess);


        // Setup the HalDeviceManager mock's start/stop behaviour. This can be overridden in
        // individual tests, if needed.
        doAnswer(new AnswerWithArguments() {
            public boolean answer() {
                when(mHalDeviceManager.isReady()).thenReturn(true);
                when(mHalDeviceManager.isStarted()).thenReturn(true);
                mHalDeviceManagerStatusCallbacks.onStatusChanged();
                return true;
            }
        }).when(mHalDeviceManager).start();

        doAnswer(new AnswerWithArguments() {
            public void answer() {
                when(mHalDeviceManager.isReady()).thenReturn(true);
                when(mHalDeviceManager.isStarted()).thenReturn(false);
                mHalDeviceManagerStatusCallbacks.onStatusChanged();
            }
        }).when(mHalDeviceManager).stop();
        when(mHalDeviceManager.createStaIface(eq(null), eq(null)))
                .thenReturn(mIWifiStaIface);
        when(mHalDeviceManager.createApIface(eq(null), eq(null)))
                .thenReturn(mIWifiApIface);
        when(mHalDeviceManager.getChip(any(IWifiIface.class)))
                .thenReturn(mIWifiChip);
        when(mHalDeviceManager.createRttController(any(IWifiIface.class)))
                .thenReturn(mIWifiRttController);

        // Create the vendor HAL object under test.
        mWifiVendorHal = new WifiVendorHal(mHalDeviceManager, mWifiStateMachineHandlerThread);

        // Initialize the vendor HAL to capture the registered callback.
        mWifiVendorHal.initialize();
        ArgumentCaptor<WifiVendorHal.HalDeviceManagerStatusListener> callbackCaptor =
                ArgumentCaptor.forClass(WifiVendorHal.HalDeviceManagerStatusListener.class);
        verify(mHalDeviceManager).registerStatusListener(
                callbackCaptor.capture(), any(Looper.class));
        mHalDeviceManagerStatusCallbacks = callbackCaptor.getValue();
    }

    /**
     * Test that parsing a typical colon-delimited MAC adddress works
     */
    @Test
    public void testTypicalHexParse() throws Exception {
        byte[] sixBytes = new byte[6];
        mWifiVendorHal.parseUnquotedMacStrToByteArray("61:52:43:34:25:16", sixBytes);
        Assert.assertArrayEquals(new byte[]{0x61, 0x52, 0x43, 0x34, 0x25, 0x16}, sixBytes);
    }

    /**
     * Tests the successful starting of HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalSuccessInStaMode() {
        assertTrue(mWifiVendorHal.startVendorHal(true));
        assertTrue(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).getChip(eq(mIWifiStaIface));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager).isReady();
        verify(mHalDeviceManager).isStarted();

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
    }

    /**
     * Tests the successful starting of HAL in AP mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalSuccessInApMode() {
        assertTrue(mWifiVendorHal.startVendorHal(false));
        assertTrue(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager).getChip(eq(mIWifiApIface));
        verify(mHalDeviceManager).isReady();
        verify(mHalDeviceManager).isStarted();

        verify(mHalDeviceManager, never()).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInStaMode() {
        // No callbacks are invoked in this case since the start itself failed. So, override
        // default AnswerWithArguments that we setup.
        doAnswer(new AnswerWithArguments() {
            public boolean answer() {
                return false;
            }
        }).when(mHalDeviceManager).start();
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();

        verify(mHalDeviceManager, never()).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInIfaceCreationInStaMode() {
        when(mHalDeviceManager.createStaIface(eq(null), eq(null))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).stop();

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInRttControllerCreationInStaMode() {
        when(mHalDeviceManager.createRttController(any(IWifiIface.class))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager).stop();

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInChipGetInStaMode() {
        when(mHalDeviceManager.getChip(any(IWifiIface.class))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager).stop();

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInApMode() {
        when(mHalDeviceManager.createApIface(eq(null), eq(null))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHal(false));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager).stop();

        verify(mHalDeviceManager, never()).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Tests the stopping of HAL in STA mode using
     * {@link WifiVendorHal#stopVendorHal()}.
     */
    @Test
    public void testStopHalInStaMode() {
        assertTrue(mWifiVendorHal.startVendorHal(true));
        assertTrue(mWifiVendorHal.isHalStarted());

        mWifiVendorHal.stopVendorHal();
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).stop();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).getChip(eq(mIWifiStaIface));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager, times(2)).isReady();
        verify(mHalDeviceManager, times(2)).isStarted();

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
    }

    /**
     * Tests the stopping of HAL in AP mode using
     * {@link WifiVendorHal#stopVendorHal()}.
     */
    @Test
    public void testStopHalInApMode() {
        assertTrue(mWifiVendorHal.startVendorHal(false));
        assertTrue(mWifiVendorHal.isHalStarted());

        mWifiVendorHal.stopVendorHal();
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).stop();
        verify(mHalDeviceManager).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager).getChip(eq(mIWifiApIface));
        verify(mHalDeviceManager, times(2)).isReady();
        verify(mHalDeviceManager, times(2)).isStarted();

        verify(mHalDeviceManager, never()).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Test translation to WifiManager.WIFI_FEATURE_*
     *
     * Just do a spot-check with a few feature bits here; since the code is table-
     * driven we don't have to work hard to exercise all of it.
     */
    @Test
    public void testFeatureMaskTranslation() {
        int caps = (
                IWifiStaIface.StaIfaceCapabilityMask.BACKGROUND_SCAN
                | IWifiStaIface.StaIfaceCapabilityMask.LINK_LAYER_STATS
            );
        int expected = (
                WifiManager.WIFI_FEATURE_SCANNER
                | WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        assertEquals(expected, mWifiVendorHal.wifiFeatureMaskFromStaCapabilities(caps));
    }

    /**
     * Test enablement of link layer stats after startup
     * <p>
     * Request link layer stats before HAL start
     * - should not make it to the HAL layer
     * Start the HAL in STA mode
     * Request link layer stats twice more
     * - enable request should make it to the HAL layer
     * - HAL layer should have been called to make the requests (i.e., two calls total)
     */
    @Test
    public void testLinkLayerStatsEnableAfterStartup() throws Exception {
        doNothing().when(mIWifiStaIface).getLinkLayerStats(any());

        assertNull(mWifiVendorHal.getWifiLinkLayerStats());
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertTrue(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        mWifiVendorHal.getWifiLinkLayerStats();
        mWifiVendorHal.getWifiLinkLayerStats();
        verify(mIWifiStaIface).enableLinkLayerStatsCollection(false); // mLinkLayerStatsDebug
        verify(mIWifiStaIface, times(2)).getLinkLayerStats(any());
    }

    /**
     * Test that link layer stats are not enabled and harmless in AP mode
     * <p>
     * Start the HAL in AP mode
     * - stats should not be enabled
     * Request link layer stats
     * - HAL layer should have been called to make the request
     */
    @Test
    public void testLinkLayerStatsNotEnabledAndHarmlessInApMode() throws Exception {
        doNothing().when(mIWifiStaIface).getLinkLayerStats(any());

        assertTrue(mWifiVendorHal.startVendorHalAp());
        assertTrue(mWifiVendorHal.isHalStarted());
        assertNull(mWifiVendorHal.getWifiLinkLayerStats());

        verify(mHalDeviceManager).start();

        verify(mIWifiStaIface, never()).enableLinkLayerStatsCollection(false);
        verify(mIWifiStaIface, never()).getLinkLayerStats(any());
    }

    // TODO(b/34900534) add test for correct MOVE CORRESPONDING of fields

    /**
     * Test that getFirmwareVersion() and getDriverVersion() work
     *
     * Calls before the STA is started are expected to return null.
     */
    @Test
    public void testVersionGetters() throws Exception {
        String firmwareVersion = "fuzzy";
        String driverVersion = "dizzy";
        IWifiChip.ChipDebugInfo chipDebugInfo = new IWifiChip.ChipDebugInfo();
        chipDebugInfo.firmwareDescription = firmwareVersion;
        chipDebugInfo.driverDescription = driverVersion;

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiChip.requestChipDebugInfoCallback cb) throws RemoteException {
                cb.onValues(mWifiStatusSuccess, chipDebugInfo);
            }
        }).when(mIWifiChip).requestChipDebugInfo(any(IWifiChip.requestChipDebugInfoCallback.class));

        assertNull(mWifiVendorHal.getFirmwareVersion());
        assertNull(mWifiVendorHal.getDriverVersion());

        assertTrue(mWifiVendorHal.startVendorHalSta());

        assertEquals(firmwareVersion, mWifiVendorHal.getFirmwareVersion());
        assertEquals(driverVersion, mWifiVendorHal.getDriverVersion());
    }

    /**
     * Test that setScanningMacOui is hooked up to the HAL correctly
     */
    @Test
    public void testSetScanningMacOui() throws Exception {
        byte[] oui = NativeUtil.macAddressOuiToByteArray("DA:A1:19");
        byte[] zzz = NativeUtil.macAddressOuiToByteArray("00:00:00");

        when(mIWifiStaIface.setScanningMacOui(any())).thenReturn(mWifiStatusSuccess);

        assertFalse(mWifiVendorHal.setScanningMacOui(oui)); // expect fail - STA not started
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertFalse(mWifiVendorHal.setScanningMacOui(null));  // expect fail - null
        assertFalse(mWifiVendorHal.setScanningMacOui(new byte[]{(byte) 1})); // expect fail - len
        assertTrue(mWifiVendorHal.setScanningMacOui(oui));
        assertTrue(mWifiVendorHal.setScanningMacOui(zzz));

        verify(mIWifiStaIface).setScanningMacOui(eq(oui));
        verify(mIWifiStaIface).setScanningMacOui(eq(zzz));
    }

    /**
     * Test that getApfCapabilities is hooked up to the HAL correctly
     *
     * A call before the vendor HAL is started should return a non-null result with version 0
     *
     * A call after the HAL is started should return the mocked values.
     */
    @Test
    public void testApfCapabilities() throws Exception {
        int myVersion = 33;
        int myMaxSize = 1234;

        StaApfPacketFilterCapabilities capabilities = new StaApfPacketFilterCapabilities();
        capabilities.version = myVersion;
        capabilities.maxLength = myMaxSize;

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiStaIface.getApfPacketFilterCapabilitiesCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess, capabilities);
            }
        }).when(mIWifiStaIface).getApfPacketFilterCapabilities(any(
                IWifiStaIface.getApfPacketFilterCapabilitiesCallback.class));


        assertEquals(0, mWifiVendorHal.getApfCapabilities().apfVersionSupported);

        assertTrue(mWifiVendorHal.startVendorHalSta());

        ApfCapabilities actual = mWifiVendorHal.getApfCapabilities();

        assertEquals(myVersion, actual.apfVersionSupported);
        assertEquals(myMaxSize, actual.maximumApfProgramSize);
        assertEquals(android.system.OsConstants.ARPHRD_ETHER, actual.apfPacketFormat);
        assertNotEquals(0, actual.apfPacketFormat);
    }

    /**
     * Test that an APF program can be installed/
     */
    @Test
    public void testInstallApf() throws Exception {
        byte[] filter = new byte[] {19, 53, 10};

        ArrayList<Byte> expected = new ArrayList<>(3);
        for (byte b : filter) expected.add(b);

        when(mIWifiStaIface.installApfPacketFilter(anyInt(), any(ArrayList.class)))
                .thenReturn(mWifiStatusSuccess);

        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertTrue(mWifiVendorHal.installPacketFilter(filter));

        verify(mIWifiStaIface).installApfPacketFilter(eq(0), eq(expected));
    }

    /**
     * Test that the country code is set in AP mode (when it should be).
     */
    @Test
    public void testSetCountryCodeHal() throws Exception {
        byte[] expected = new byte[]{(byte) 'C', (byte) 'A'};

        when(mIWifiApIface.setCountryCode(any()))
                .thenReturn(mWifiStatusSuccess);

        assertTrue(mWifiVendorHal.startVendorHalAp());

        assertFalse(mWifiVendorHal.setCountryCodeHal(null));
        assertFalse(mWifiVendorHal.setCountryCodeHal(""));
        assertFalse(mWifiVendorHal.setCountryCodeHal("A"));
        assertTrue(mWifiVendorHal.setCountryCodeHal("CA")); // Only one expected to succeed
        assertFalse(mWifiVendorHal.setCountryCodeHal("ZZZ"));

        verify(mIWifiApIface).setCountryCode(eq(expected));
    }
}
