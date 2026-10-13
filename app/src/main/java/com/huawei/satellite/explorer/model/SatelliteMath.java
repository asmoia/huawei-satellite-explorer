package com.huawei.satellite.explorer.model;

public class SatelliteMath {
    private static final double RE = 6378.137;  // Earth equatorial radius (km)
    private static final double RS = 42164.137; // Geostationary orbital radius (km)

    public static class DishLookAngles {
        public final double azimuth;     // Degrees true (0-360)
        public final double elevation;   // Degrees up from horizon (0-90)
        public final double lnbSkew;     // Degrees tilt (-90 to +90)
        public final double distanceKm;  // Distance from dish to satellite in space

        public DishLookAngles(double azimuth, double elevation, double lnbSkew, double distanceKm) {
            this.azimuth = azimuth;
            this.elevation = elevation;
            this.lnbSkew = lnbSkew;
            this.distanceKm = distanceKm;
        }
    }

    /**
     * Compute Azimuth, Elevation, LNB Skew, and Distance to any Geostationary Satellite
     */
    public static DishLookAngles calculateLookAngles(double userLat, double userLon, double satLon) {
        double phi = Math.toRadians(userLat);
        double deltaLam = Math.toRadians(satLon - userLon);

        // Azimuth
        double az = Math.toDegrees(Math.atan2(Math.sin(deltaLam), Math.tan(phi)));
        double azimuth = (180.0 - az) % 360.0;
        if (azimuth < 0) azimuth += 360.0;

        // Elevation
        double cosGamma = Math.cos(phi) * Math.cos(deltaLam);
        cosGamma = Math.max(-1.0, Math.min(1.0, cosGamma));
        double sinGamma = Math.sqrt(Math.max(0.0, 1.0 - cosGamma * cosGamma));
        double rRatio = RE / RS; // ~0.15127
        double elevation = Math.toDegrees(Math.atan((cosGamma - rRatio) / (sinGamma > 0 ? sinGamma : 0.0001)));

        // LNB Polarization Skew (tilt angle of LNB)
        // Positive = Clockwise (facing dish), Negative = Counter-Clockwise
        double lnbSkew = Math.toDegrees(Math.atan2(Math.sin(deltaLam), Math.tan(phi)));

        // Slant range distance (km)
        double distanceKm = Math.sqrt(RE * RE + RS * RS - 2 * RE * RS * cosGamma);

        return new DishLookAngles(azimuth, elevation, lnbSkew, distanceKm);
    }
}
