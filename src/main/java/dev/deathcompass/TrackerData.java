package dev.deathcompass;

import java.util.*;

public final class TrackerData {
    public boolean enabled = true, compass = true, arrows = true, coordinates = true, distance = true;
    public boolean portalGuidance = false, lavaWarning = true;
    public int scanRadius = 24, scanBudget = 1500;
    public int xOffset = 0, yOffset = 14, scalePercent = 100;
    public int color = 0xFF55FFFF;
    public int arrivalRadius = 5;
    public Map<String, WorldRecord> worlds = new HashMap<>();
    public static class WorldRecord {
        public Death active;
        public List<Death> history = new ArrayList<>();
        public List<Portal> portals = new ArrayList<>();
        public List<Waypoint> waypoints = new ArrayList<>();
        public String selectedWaypointId = "";
        // Only one active destination: death, block, structure, or custom waypoint.
        public String targetMode = "death";
        public String searchId = "minecraft:ancient_debris";
        public int targetX, targetY, targetZ;
        public String targetDimension = "";
        public boolean targetFound = false;
        public transient int scanCursor = 0;
        public transient String scanSignature = "";
    }
    public static class Death {
        public int x,y,z;
        public String dimension;
        public long timestamp;
        public Death(int x,int y,int z,String dimension) {
            this.x=x;this.y=y;this.z=z;this.dimension=dimension;this.timestamp=System.currentTimeMillis();
        }
    }
    public static class Waypoint {
        public String id, name, dimension;
        public int x, y, z;
        public Waypoint(String id, String name, int x, int y, int z, String dimension) {
            this.id=id; this.name=name; this.x=x; this.y=y; this.z=z; this.dimension=dimension;
        }
    }
    public static class Portal {
        public int x,y,z;
        public String dimension;
        public Portal(int x,int y,int z,String dimension) {this.x=x;this.y=y;this.z=z;this.dimension=dimension;}
    }
}
