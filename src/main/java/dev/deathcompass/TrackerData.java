
package dev.deathcompass;

import java.util.*;

public final class TrackerData {
    public boolean enabled = true, compass = true, arrows = true, coordinates = true, distance = true;
    public boolean portalGuidance = false, lavaWarning = true;
    public boolean hazardAlerts = true, gravelWarning = true, mobWarning = true;
    public boolean environmentWarning = true, lavaSpreadWarning = true, showNearbyLava = false;
    // Audible alerts never play unless there's an urgent warning. All settings are optional.
    public boolean warningSounds = true;
    public boolean soundLava = true, soundFallingBlocks = true, soundHostiles = true, soundOther = true;
    public int warningSoundVolume = 65; // percent, 0..100
    public int warningSoundCooldownTicks = 80; // 4 seconds at 20 TPS
    public int hazardRadius = 5;
    // Don't switch the finder to another match until this far from the locked match.
    public int lockReleaseDistance = 96;
    public int scanRadius = 256, scanBudget = 1500;
    public boolean expandedSearchRadius = true;

    // Independent, optional HUD and camera features.
    // All persisted in deathcompass.json.
    public boolean minimapEnabled = true, minimapVisible = true, minimapTerrain = true;
    public boolean minimapWaypoints = true, minimapWaypointNames = false;
    public boolean minimapDeaths = true, minimapEntities = false;
    public boolean minimapCoords = true, minimapDirection = true;
    public boolean minimapCircle = true, minimapNorthUp = true;
    public int minimapSize = 123, minimapZoom = 2;
    public int minimapXOffset = 0, minimapYOffset = 0;
    public boolean freecamEnabled = true;
    public int freecamSpeed = 6; // 0.6 blocks/tick; sprint boosts speed
    public boolean blockFinderEnabled = true;
    public boolean quickWaypointEnabled = true;
    public boolean veinSuggestionEnabled = true;
    public boolean autoNextOreEnabled = true;
    public boolean deathTrackingEnabled = true;
    public boolean waypointHudEnabled = true;
    public boolean secondaryKeybindsEnabled = true;
    public boolean structureCluesEnabled = true;

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

        // Suggestions are temporary: never save a choice the player did not accept.
        public transient int targetVeinSize;
        public transient boolean suggestedVeinAvailable;
        public transient boolean suggestionDismissed;
        public transient int suggestedX, suggestedY, suggestedZ, suggestedVeinSize;
        public transient int scanCursor = 0;
        public transient String scanSignature = "";
        public transient int scanOriginX, scanOriginY, scanOriginZ;
        public transient boolean scanCandidateFound;
        public transient int scanCandidateX, scanCandidateY, scanCandidateZ;
        public transient double scanCandidateDistance = Double.POSITIVE_INFINITY;
    }

    public static class Death {
        public int x,y,z;
        public String dimension;
        public long timestamp;

        public Death(int x,int y,int z,String dimension) {
            this.x=x;this.y=y;this.z=z;this.dimension=dimension;
            this.timestamp=System.currentTimeMillis();
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

        public Portal(int x,int y,int z,String dimension) {
            this.x=x;this.y=y;this.z=z;this.dimension=dimension;
        }
    }
}
