package brooklyn.entity.dns;

import java.net.InetAddress;

import brooklyn.entity.Entity;

/**
 * Encapsulates geo-IP information for a given server.
 */
public class ServerGeoInfo {
    public final String address;
    public final String displayName;
    public final double latitude;
    public final double longitude;

    
    public static ServerGeoInfo fromEntity(Entity e) {
        String displayName = e.getDisplayName();
        InetAddress address = LocationUtils.findIpAddress(e);
        Double latitude = LocationUtils.findLatitude(e);
        Double longitude = LocationUtils.findLongitude(e);
        
        return new ServerGeoInfo(address.toString(), displayName,
                (latitude == null ? 0.0 : latitude),
                (longitude == null ? 0.0 : longitude));
    }
    
    
    public ServerGeoInfo(String address, String displayName, double latitude, double longitude) {
        this.address = address;
        this.displayName = displayName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    public String toString() {
        return "ServerGeoInfo["+displayName+": "+address+" at ("+latitude+","+longitude+")]";
    }
    
    @Override
    public boolean equals(Object o) {
        // Slight cheat: only tests the address field.
        return (o instanceof ServerGeoInfo) && address.equals(((ServerGeoInfo) o).address);
    }
    
    @Override
    public int hashCode() {
        // Slight cheat: only includes the address field.
        return address.hashCode();
    }
    
}
