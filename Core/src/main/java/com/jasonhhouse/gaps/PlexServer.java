package com.jasonhhouse.gaps;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections4.list.SetUniqueList;

public class PlexServer {

    private final String friendlyName;
    private final String machineIdentifier;
    private final String plexToken;
    private final String address;
    private final Integer port;
    private final SetUniqueList<PlexLibrary> plexLibraries;

    public PlexServer(String friendlyName, String machineIdentifier, String plexToken, String address, Integer port) {
        this.friendlyName = friendlyName;
        this.machineIdentifier = machineIdentifier;
        this.plexToken = plexToken;
        this.address = address;
        this.port = port;
        plexLibraries = SetUniqueList.setUniqueList(new ArrayList<>());
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public String getMachineIdentifier() {
        return machineIdentifier;
    }

    public List<PlexLibrary> getPlexLibraries() {
        return plexLibraries;
    }

    public String getPlexToken() {
        return plexToken;
    }

    public String getAddress() {
        return address;
    }

    public Integer getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlexServer that = (PlexServer) o;
        return Objects.equals(machineIdentifier, that.machineIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(machineIdentifier);
    }

    @Override
    public String toString() {
        return "PlexServer{" +
                "friendlyName='" + friendlyName + '\'' +
                ", machineIdentifier='" + machineIdentifier + '\'' +
                ", plexToken='" + plexToken + '\'' +
                ", address='" + address + '\'' +
                ", port=" + port +
                ", plexLibraries=" + plexLibraries +
                '}';
    }
}