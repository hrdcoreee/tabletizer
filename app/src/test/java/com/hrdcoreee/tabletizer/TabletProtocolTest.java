package com.hrdcoreee.tabletizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TabletProtocolTest {
    @Test
    public void touchPacketUsesProtocolLayoutAndClampsValues() {
        byte[] packet = new byte[TabletProtocol.PACKET_SIZE];
        TabletProtocol.writeTouch(packet, true, 50000, -5, 6000, true, false);

        assertEquals(TabletProtocol.TOUCH_REPORT_ID, packet[0]);
        assertEquals(0x22, packet[1] & 0xFF);
        assertEquals(TabletProtocol.MAX_X, (packet[2] & 0xFF) | ((packet[3] & 0xFF) << 8));
        assertEquals(0, (packet[4] & 0xFF) | ((packet[5] & 0xFF) << 8));
        assertEquals(TabletProtocol.MAX_PRESSURE, (packet[6] & 0xFF) | ((packet[7] & 0xFF) << 8));
    }

    @Test
    public void configPacketRoundTripsMillimetersAndConstrainsBounds() {
        byte[] packet = new byte[TabletProtocol.PACKET_SIZE];
        TabletProtocol.writeConfig(packet, 0, 120, 0, 170f, 100f, -1f, 100f);

        assertEquals(TabletProtocol.CONFIG_REPORT_ID, packet[0]);
        assertEquals(100, packet[2] & 0xFF);
        assertEquals(160f, TabletProtocol.readMillimeters(packet, 4), 0.01f);
        assertEquals(90f, TabletProtocol.readMillimeters(packet, 6), 0.01f);
        assertEquals(0f, TabletProtocol.readMillimeters(packet, 8), 0.01f);
        assertEquals(90f, TabletProtocol.readMillimeters(packet, 10), 0.01f);
    }

    @Test
    public void areaValidationRejectsOutOfDigitizerAreas() {
        assertTrue(TabletProtocol.isValidArea(160f, 90f, 80f, 45f));
        assertFalse(TabletProtocol.isValidArea(161f, 90f, 80f, 45f));
        assertFalse(TabletProtocol.isValidArea(80f, 45f, 0f, 45f));
    }
}