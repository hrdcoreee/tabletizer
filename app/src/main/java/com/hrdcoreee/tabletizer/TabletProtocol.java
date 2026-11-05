package com.hrdcoreee.tabletizer;

/** Stateless serialization and validation for the fixed-size tabletizer protocol. */
public final class TabletProtocol {
    public static final int PACKET_SIZE = 12;
    public static final int TOUCH_REPORT_ID = 1;
    public static final int CONFIG_REPORT_ID = 2;
    public static final int MAX_X = 32000;
    public static final int MAX_Y = 18000;
    public static final int MAX_PRESSURE = 4095;
    public static final float DIGITIZER_WIDTH_MM = 160f;
    public static final float DIGITIZER_HEIGHT_MM = 90f;

    private TabletProtocol() { }

    public static void writeTouch(byte[] target, boolean inRange, int x, int y, int pressure,
                                  boolean primaryButton, boolean secondaryButton) {
        if (target == null || target.length != PACKET_SIZE) {
            throw new IllegalArgumentException("Touch packet must contain exactly 12 bytes.");
        }
        x = clamp(x, 0, MAX_X);
        y = clamp(y, 0, MAX_Y);
        pressure = clamp(pressure, 0, MAX_PRESSURE);
        target[0] = TOUCH_REPORT_ID;
        target[1] = (byte) ((inRange ? 0x20 : 0) | (primaryButton ? 0x02 : 0) | (secondaryButton ? 0x04 : 0));
        writeUnsignedShort(target, 2, x);
        writeUnsignedShort(target, 4, y);
        writeUnsignedShort(target, 6, pressure);
        target[8] = 0;
        target[9] = 0;
        target[10] = 0;
        target[11] = 0;
    }

    public static void writeConfig(byte[] target, int ratioOrdinal, int scalePercent, int positionOrdinal,
                                   float widthMm, float heightMm, float posXMm, float posYMm) {
        if (target == null || target.length != PACKET_SIZE) {
            throw new IllegalArgumentException("Config packet must contain exactly 12 bytes.");
        }
        target[0] = CONFIG_REPORT_ID;
        target[1] = (byte) ratioOrdinal;
        target[2] = (byte) clamp(scalePercent, 0, 100);
        target[3] = (byte) positionOrdinal;
        writeUnsignedShort(target, 4, Math.round(clamp(widthMm, 0.1f, DIGITIZER_WIDTH_MM) * 10f));
        writeUnsignedShort(target, 6, Math.round(clamp(heightMm, 0.1f, DIGITIZER_HEIGHT_MM) * 10f));
        writeUnsignedShort(target, 8, Math.round(clamp(posXMm, 0f, DIGITIZER_WIDTH_MM) * 10f));
        writeUnsignedShort(target, 10, Math.round(clamp(posYMm, 0f, DIGITIZER_HEIGHT_MM) * 10f));
    }

    public static float readMillimeters(byte[] packet, int offset) {
        if (packet == null || packet.length != PACKET_SIZE || offset < 0 || offset + 1 >= packet.length) {
            throw new IllegalArgumentException("Invalid config packet offset.");
        }
        return ((packet[offset] & 0xFF) | ((packet[offset + 1] & 0xFF) << 8)) / 10f;
    }

    public static boolean isValidArea(float widthMm, float heightMm, float posXMm, float posYMm) {
        return widthMm > 0f && heightMm > 0f
                && widthMm <= DIGITIZER_WIDTH_MM && heightMm <= DIGITIZER_HEIGHT_MM
                && posXMm >= widthMm / 2f && posXMm <= DIGITIZER_WIDTH_MM - widthMm / 2f
                && posYMm >= heightMm / 2f && posYMm <= DIGITIZER_HEIGHT_MM - heightMm / 2f;
    }

    private static void writeUnsignedShort(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}