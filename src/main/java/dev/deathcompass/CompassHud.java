package dev.deathcompass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import java.util.Locale;

/** HUD uses block coordinates and orientation math; no server data required. */
public final class CompassHud {
    private CompassHud() {}
    public static void draw(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc=Minecraft.getInstance();TrackerData cfg=DeathCompass.data;
        if(!cfg.enabled||mc.player==null||mc.level==null)return;
        int hazardY=g.guiHeight()/2-59;
        for(HazardScanner.Warning hazard : HazardScanner.warnings()) {
            String line="! " + hazard.message() + " !";
            int middle=g.guiWidth()/2;
            int half=mc.font.width(line)/2+7;
            g.fill(middle-half,hazardY-3,middle+half,hazardY+12,0xB50F1218);
            center(g,mc,line,middle,hazardY,hazard.color());
            hazardY+=15;
        }
        TrackerData.WorldRecord w=DeathCompass.world();
        boolean searching="block".equals(w.targetMode)||"structure".equals(w.targetMode);
        boolean waypointMode="waypoint".equals(w.targetMode);
        TrackerData.Waypoint waypoint=waypointMode?DeathCompass.selectedWaypoint():null;
        TrackerData.Death d=DeathCompass.active();
        if(waypointMode && waypoint==null){center(g,mc,"NO WAYPOINT SELECTED",g.guiWidth()/2+cfg.xOffset,cfg.yOffset,0xFFFFB55F);return;}
        if(!searching && !waypointMode && d==null)return;
        if(searching && !w.targetFound){
            int r=Math.max(4,Math.min(64,cfg.scanRadius));
            int total=(2*r+1)*(2*r+1)*(2*r+1);
            String status=w.scanCursor>=total?"NOT DETECTED - MOVE TO SEARCH MORE":"SCANNING "+Math.min(100,100L*w.scanCursor/total)+"%";
            center(g,mc,status+": "+w.searchId,g.guiWidth()/2+cfg.xOffset,cfg.yOffset,cfg.color|0xFF000000);
            return;
        }
        int goalX=waypointMode?waypoint.x:searching?w.targetX:d.x;
        int goalY=waypointMode?waypoint.y:searching?w.targetY:d.y;
        int goalZ=waypointMode?waypoint.z:searching?w.targetZ:d.z;
        String goalDimension=waypointMode?waypoint.dimension:searching?w.targetDimension:d.dimension;
        String goalLabel=waypointMode?"WAYPOINT "+waypoint.name:searching?("block".equals(w.targetMode)?"LOCKED BLOCK ":"STRUCTURE CLUE ")+w.searchId:"DEATH";
        int cx=g.guiWidth()/2+cfg.xOffset, top=cfg.yOffset;
        int color=cfg.color|0xFF000000;
        boolean other=!DeathCompass.dimension(mc).equals(goalDimension);
        // Relative bearing: degrees clockwise from player's facing direction.
        double tx=goalX+0.5,ty=goalY+0.5,tz=goalZ+0.5;
        TrackerData.Portal portal=null;
        String message="";
        if(other){
            String dest=shortDimension(goalDimension);
            message="ENTER "+dest.toUpperCase(Locale.ROOT)+" TO TRACK TARGET";
            if(cfg.portalGuidance && !searching){
                portal=DeathCompass.nearestPortal(mc);
                if(portal!=null){tx=portal.x+0.5;ty=portal.y+0.5;tz=portal.z+0.5;message="KNOWN PORTAL -> "+dest.toUpperCase(Locale.ROOT);}
                else message += " | NO KNOWN PORTAL";
            }
        }
        double dx=tx-mc.player.getX(),dy=ty-mc.player.getY(),dz=tz-mc.player.getZ();
        double horizontal=Math.hypot(dx,dz);
        int blocks=(int)Math.round(Math.sqrt(dx*dx+dy*dy+dz*dz));
        double targetYaw=Math.toDegrees(Math.atan2(-dx,dz));
        double relative=wrap(targetYaw-mc.player.getYRot());
        String dir=relative>7?"RIGHT":relative< -7?"LEFT":"STRAIGHT";
        int turn=(int)Math.round(Math.abs(relative));
        int vertical=(int)Math.round(Math.abs(dy));
        int xpos=cx;
        int scale=Math.max(50,Math.min(200,cfg.scalePercent));
        // Compact, vanilla-inspired cardinal compass strip centered on the player.
        if(cfg.compass){
            int half=90*scale/100;
            g.fill(cx-half,top-2,cx+half,top+19,0x990B1019);
            double facing=mc.player.getYRot();
            String[] cardinal={"N","E","S","W"};
            int[] bearings={180,-90,0,90};
            for(int i=0;i<4;i++){
                double diff=wrap(bearings[i]-facing);
                int px=cx+(int)Math.round(diff*half/95.0);
                if(px>cx-half+9&&px<cx+half-9)g.text(mc.font,cardinal[i],px-3,top+3,0xFFDDDDDD,true);
            }
            int deathX=cx+(int)Math.round(relative*half/95.0);
            if(Math.abs(relative)<=95)g.text(mc.font,"◆",deathX-3,top+3,0xFFFF6868,true);
            g.text(mc.font,"▼",cx-3,top+10,color,true);
            top+=25*scale/100;
        }
        if(!message.isEmpty()){
            center(g,mc,message,cx,top,0xFFFFB55F);top+=12;
        }
        if(cfg.arrows){
            // Direction changes continuously with player heading; degrees provide fine aiming.
            String turnText=horizontal<1?"ON TARGET":("↑ "+dir+" "+turn+"°");
            String verticalText=vertical<=1?"LEVEL":dy>0?"UP "+vertical+" BLOCKS":"DOWN "+vertical+" BLOCKS";
            center(g,mc,turnText+"   |   "+verticalText,cx,top,color);top+=12;
        }
        if(cfg.distance&&!other){center(g,mc,blocks+" blocks away",cx,top,0xFFFFFFFF);top+=12;}
        if(cfg.coordinates){
            String loc=goalX+", "+goalY+", "+goalZ;
            center(g,mc,goalLabel+" "+shortDimension(goalDimension)+"  "+loc,cx,top,0xFFB0C6D9);top+=12;
            if(other){
                if(goalDimension.equals("minecraft:overworld") && DeathCompass.dimension(mc).equals("minecraft:the_nether"))
                    center(g,mc,"Nether estimate X:"+Math.floorDiv(goalX,8)+" Z:"+Math.floorDiv(goalZ,8),cx,top,0xFFBBCCDD);
                else if(goalDimension.equals("minecraft:the_nether") && DeathCompass.dimension(mc).equals("minecraft:overworld"))
                    center(g,mc,"Overworld estimate X:"+(goalX*8L)+" Z:"+(goalZ*8L),cx,top,0xFFBBCCDD);
            }
        }
        if(!searching && !waypointMode && !other && Math.sqrt(dx*dx+dy*dy+dz*dz)<=cfg.arrivalRadius)
            center(g,mc,"DEATH REACHED - OPEN SETTINGS TO END MISSION",cx,top+13,0xFF68FF95);
    }
    private static void center(GuiGraphicsExtractor g,Minecraft mc,String s,int x,int y,int color){g.text(mc.font,s,x-mc.font.width(s)/2,y,color,true);}
    private static double wrap(double deg){deg%=360;if(deg>180)deg-=360;if(deg< -180)deg+=360;return deg;}
    static String shortDimension(String id){return id.startsWith("minecraft:")?id.substring(10).replace("the_","").replace('_',' '):id;}
}
