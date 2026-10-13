package com.huawei.satellite.explorer.model;

import java.util.ArrayList;
import java.util.List;

public class SatelliteTarget {
    public final String id;
    public final String name;
    public final double orbitalLongitude; // Positive for East, negative for West
    public final String category;         // "TV Broadcast", "BeiDou RDSS", "LEO Gateway"
    public final String description;
    public final String sampleFrequencies;

    public SatelliteTarget(String id, String name, double orbitalLongitude, String category, String description, String sampleFrequencies) {
        this.id = id;
        this.name = name;
        this.orbitalLongitude = orbitalLongitude;
        this.category = category;
        this.description = description;
        this.sampleFrequencies = sampleFrequencies;
    }

    @Override
    public String toString() {
        return name + " (" + formatLongitude(orbitalLongitude) + ")";
    }

    public static String formatLongitude(double lon) {
        if (lon >= 0) {
            return String.format("%.1f°E", lon);
        } else {
            return String.format("%.1f°W", Math.abs(lon));
        }
    }

    public static List<SatelliteTarget> getPresetSatellites() {
        List<SatelliteTarget> list = new ArrayList<>();

        // 1. Popular TV Broadcast Satellites (Middle East, Iran & Europe)
        list.add(new SatelliteTarget(
                "yahsat",
                "Yahsat 1A (Al Yah 1)",
                52.5,
                "TV Broadcast",
                "Primary Persian & Regional TV satellite. High power coverage over Middle East.",
                "11881 V 27500, 11958 V 27500, 12015 H 27500"
        ));

        list.add(new SatelliteTarget(
                "hotbird",
                "Hotbird 13F / 13G",
                13.0,
                "TV Broadcast",
                "Major European & International broadcast cluster over Europe & Middle East.",
                "11137 H 27500, 11541 V 22000, 12149 V 27500"
        ));

        list.add(new SatelliteTarget(
                "eutelsat7",
                "Eutelsat 7B / 7C",
                7.0,
                "TV Broadcast",
                "Popular Persian entertainment & music satellite (Manoto, etc.).",
                "11221 H 27500, 11304 H 29700, 11387 H 27500"
        ));

        list.add(new SatelliteTarget(
                "badr",
                "Badr 8 / Arabsat",
                26.0,
                "TV Broadcast",
                "Arab & Middle Eastern regional television and sports channels.",
                "11900 V 27500, 11996 H 27500, 12054 V 27500"
        ));

        list.add(new SatelliteTarget(
                "turksat",
                "Türksat 4A / 5B",
                42.0,
                "TV Broadcast",
                "Turkish & regional Eurasian broadcast network.",
                "12054 H 27500, 12245 H 27500, 12380 V 27500"
        ));

        list.add(new SatelliteTarget(
                "nilesat",
                "Nilesat 201 / 301",
                -7.0,
                "TV Broadcast",
                "North Africa & Middle East entertainment network.",
                "11747 V 27500, 11766 H 27500, 12015 V 27500"
        ));

        // 2. Official Chinese BeiDou Communication Satellites
        list.add(new SatelliteTarget(
                "bds_geo5",
                "BeiDou-3 GEO-5",
                58.75,
                "BeiDou RDSS",
                "Huawei Beidou satellite messaging primary anchor satellite (West Asia & Iran).",
                "L-Band (B1I/B1C), S-Band Transmit (2491.75 MHz)"
        ));

        list.add(new SatelliteTarget(
                "bds_geo1",
                "BeiDou-3 GEO-1",
                140.0,
                "BeiDou RDSS",
                "East Asia & Pacific regional communication payload.",
                "B1/B2/B3 Tri-band navigation & short message"
        ));

        list.add(new SatelliteTarget(
                "bds_geo2",
                "BeiDou-3 GEO-2",
                80.0,
                "BeiDou RDSS",
                "Central Asia & Indian subcontinent messaging payload.",
                "RDSS S/L-Band Transponder"
        ));

        // 3. Internet & LEO Satellites
        list.add(new SatelliteTarget(
                "starlink",
                "Starlink Gateway (Clarke Sub-point)",
                50.0,
                "LEO Constellation",
                "SpaceX Starlink Ku/Ka band orbital feeder reference corridor.",
                "Ku-Band: 10.7 - 12.7 GHz, Ka-Band: 14.0 - 14.5 GHz"
        ));

        list.add(new SatelliteTarget(
                "iss",
                "International Space Station (ISS)",
                45.0,
                "Space Station",
                "Low Earth Orbit research station (Visible pass alignment).",
                "VHF Voice: 145.800 MHz, Packet APRS: 145.825 MHz"
        ));

        return list;
    }
}
