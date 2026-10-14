package dev.deathcompass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
    private int page=0, historyPage=0, structurePage=0;
    private static final String[] STRUCTURES={
        "minecraft:ancient_city", "minecraft:nether_fortress", "minecraft:bastion_remnant",
        "minecraft:stronghold", "minecraft:trial_chambers", "minecraft:monument",
        "minecraft:desert_pyramid", "minecraft:jungle_temple", "minecraft:end_city",
        "minecraft:woodland_mansion", "minecraft:pillager_outpost", "minecraft:shipwreck",
        "minecraft:ruined_portal", "minecraft:mineshaft", "minecraft:village",
        "minecraft:ocean_ruin"
    };
    private EditBox idBox, nameBox, xBox, yBox, zBox;
    private int waypointPage=0;
    private String chosenDimension="minecraft:overworld";
    private String searchMessage="";
    public SettingsScreen(){super(Component.literal("Death Compass Settings"));}
    private void btn(int x,int y,int w,String label,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b->{action.run();DeathCompass.save();refreshWidgets();}).bounds(x,y,w,20).build());}
    private void refreshWidgets(){clearWidgets();init();}
    @Override protected void init(){
        int x=width/2-155,y=height/2-105,w=150;
        if(page==0){
            TrackerData d=DeathCompass.data;
            btn(x,y,w,"Mod: "+on(d.enabled),()->d.enabled=!d.enabled);
            btn(x+160,y,w,"Compass: "+on(d.compass),()->d.compass=!d.compass);
            btn(x,y+25,w,"Arrows: "+on(d.arrows),()->d.arrows=!d.arrows);
            btn(x+160,y+25,w,"Death coords: "+on(d.coordinates),()->d.coordinates=!d.coordinates);
            btn(x,y+50,w,"Distance: "+on(d.distance),()->d.distance=!d.distance);
            btn(x+160,y+50,w,"Known portals: "+on(d.portalGuidance),()->d.portalGuidance=!d.portalGuidance);
            btn(x,y+75,w,"HUD left",()->d.xOffset-=10);
            btn(x+160,y+75,w,"HUD right",()->d.xOffset+=10);
            btn(x,y+100,w,"HUD up",()->d.yOffset=Math.max(0,d.yOffset-5));
            btn(x+160,y+100,w,"HUD down",()->d.yOffset+=5);
            btn(x,y+125,w,"HUD size -",()->d.scalePercent=Math.max(50,d.scalePercent-10));
            btn(x+160,y+125,w,"HUD size +",()->d.scalePercent=Math.min(200,d.scalePercent+10));
            btn(x,y+150,w,"Change color",()->d.color=switch(d.color){case 0xFF55FFFF->0xFFFF7777;case 0xFFFF7777->0xFF90FF80;case 0xFF90FF80->0xFFFFFFFF;default->0xFF55FFFF;});
            btn(x+160,y+150,w,"Reach radius: "+d.arrivalRadius,()->d.arrivalRadius=d.arrivalRadius>=20?1:d.arrivalRadius+1);
            btn(x,y+175,w,"Death history",()->page=1);
            btn(x+160,y+175,w,"End mission",DeathCompass::endMission);
            btn(x,y+200,w,"Find block / structure",()->page=2);
            btn(x+160,y+200,w,"Custom waypoints",()->page=3);
            btn(x,y+225,w,"Safety & mining alerts",()->page=6);
            addRenderableWidget(Button.builder(Component.literal("Secondary keybinds"),b ->
                Minecraft.getInstance().gui.setScreen(new SecondaryKeybindsScreen(this)))
                .bounds(x+160,y+225,w,20).build());
        }else if(page==1){
            var history=DeathCompass.world().history;
            int start=historyPage*5;
            for(int i=start;i<Math.min(start+5,history.size());i++){
                TrackerData.Death d=history.get(i);
                int row=i-start;
                btn(x,y+row*27,310,CompassHud.shortDimension(d.dimension)+"  "+d.x+","+d.y+","+d.z+"  [TRACK]",()->DeathCompass.setMission(d));
            }
            btn(x,y+150,w,"Previous",()->historyPage=Math.max(0,historyPage-1));
            btn(x+160,y+150,w,"Next",()->historyPage=Math.min((Math.max(0,history.size()-1))/5,historyPage+1));
            btn(x+80,y+175,w,"Back",()->page=0);
        }else if(page==3){
            TrackerData.WorldRecord record=DeathCompass.world();
            if(record.waypoints==null)record.waypoints=new java.util.ArrayList<>();
            btn(x,y,310,"Create waypoint (enter coordinates)",()->{page=4;});
            int start=waypointPage*4;
            for(int i=start;i<Math.min(start+4,record.waypoints.size());i++){
                TrackerData.Waypoint point=record.waypoints.get(i);
                int row=i-start;
                String title=point.name+" ("+point.x+","+point.y+","+point.z+")";
                btn(x,y+29+row*30,240,truncate(title,38),()->DeathCompass.chooseWaypoint(point.id));
                btn(x+245,y+29+row*30,65,"Delete",()->DeathCompass.deleteWaypoint(point.id));
            }
            btn(x,y+156,w,"Previous",()->waypointPage=Math.max(0,waypointPage-1));
            btn(x+160,y+156,w,"Next",()->waypointPage=Math.min(Math.max(0,(record.waypoints.size()-1)/4),waypointPage+1));
            btn(x,y+184,w,"Resume death",DeathCompass::stopWaypoint);
            btn(x+160,y+184,w,"Back",()->page=0);
        }else if(page==4){
            nameBox=new EditBox(font,x,y+8,310,20,Component.literal("Waypoint name"));nameBox.setValue("My Base");addRenderableWidget(nameBox);
            xBox=numberBox(x,y+43,"X",1250);yBox=numberBox(x+107,y+43,"Y",64);zBox=numberBox(x+214,y+43,"Z",-830);
            btn(x,y+77,310,"Dimension: "+CompassHud.shortDimension(chosenDimension),()->chosenDimension=switch(chosenDimension){
                case "minecraft:overworld"->"minecraft:the_nether";
                case "minecraft:the_nether"->"minecraft:the_end";
                default->"minecraft:overworld";
            });
            btn(x,y+107,310,"Save and track waypoint",()->{
                try {
                    String name=nameBox.getValue().trim();
                    if(name.isEmpty()||name.length()>48){searchMessage="Name must be 1-48 characters";return;}
                    int ax=Integer.parseInt(xBox.getValue().trim()),ay=Integer.parseInt(yBox.getValue().trim()),az=Integer.parseInt(zBox.getValue().trim());
                    if(ay< -2048||ay>2048){searchMessage="Y must be between -2048 and 2048";return;}
                    DeathCompass.addWaypoint(name,ax,ay,az,chosenDimension);
                    searchMessage="Waypoint saved!";page=3;
                }catch(NumberFormatException e){searchMessage="X, Y and Z must be whole numbers";}
            });
            btn(x+80,y+143,w,"Back",()->{searchMessage="";page=3;});
        } else if(page==5){
            int start=structurePage*5;
            for(int i=start;i<Math.min(start+5,STRUCTURES.length);i++){
                String id=STRUCTURES[i];
                int row=i-start;
                btn(x,y+row*28,310,"Find clue: "+id.substring(10).replace('_',' '),()->{
                    DeathCompass.trackStructure(id);
                    searchMessage="Searching loaded chunks for "+id+" clue";
                    page=2;
                });
            }
            btn(x,y+154,w,"Previous",()->structurePage=Math.max(0,structurePage-1));
            btn(x+160,y+154,w,"Next",()->structurePage=Math.min((STRUCTURES.length-1)/5,structurePage+1));
            btn(x+80,y+185,w,"Back",()->page=2);
        } else if(page==6){
            TrackerData c=DeathCompass.data;
            btn(x,y,310,"Safety warnings: "+on(c.hazardAlerts),()->c.hazardAlerts=!c.hazardAlerts);
            btn(x,y+25,w,"Predict lava: "+on(c.lavaWarning),()->c.lavaWarning=!c.lavaWarning);
            btn(x+160,y+25,w,"Falling gravel: "+on(c.gravelWarning),()->c.gravelWarning=!c.gravelWarning);
            btn(x,y+50,w,"Nearby mobs: "+on(c.mobWarning),()->c.mobWarning=!c.mobWarning);
            btn(x+160,y+50,w,"Other hazards: "+on(c.environmentWarning),()->c.environmentWarning=!c.environmentWarning);
            btn(x,y+75,w,"Lava spreading: "+on(c.lavaSpreadWarning),()->c.lavaSpreadWarning=!c.lavaSpreadWarning);
            btn(x+160,y+75,w,"Lava in walls: "+on(c.showNearbyLava),()->c.showNearbyLava=!c.showNearbyLava);
            btn(x,y+105,w,"Danger radius - ("+c.hazardRadius+")",()->c.hazardRadius=Math.max(2,c.hazardRadius-1));
            btn(x+160,y+105,w,"Danger radius + ("+c.hazardRadius+")",()->c.hazardRadius=Math.min(8,c.hazardRadius+1));
            btn(x,y+137,310,"Warning sounds & volume...",()->page=7);
            btn(x+80,y+176,w,"Back",()->page=0);
        } else if(page==7){
            TrackerData c=DeathCompass.data;
            btn(x,y,310,"Audible hazard warnings: "+on(c.warningSounds),()->c.warningSounds=!c.warningSounds);
            btn(x,y+28,w,"Lava alerts: "+on(c.soundLava),()->c.soundLava=!c.soundLava);
            btn(x+160,y+28,w,"Falling blocks: "+on(c.soundFallingBlocks),()->c.soundFallingBlocks=!c.soundFallingBlocks);
            btn(x,y+56,w,"Hostiles: "+on(c.soundHostiles),()->c.soundHostiles=!c.soundHostiles);
            btn(x+160,y+56,w,"Other dangers: "+on(c.soundOther),()->c.soundOther=!c.soundOther);
            btn(x,y+86,w,"Volume - ("+c.warningSoundVolume+"%)",()->c.warningSoundVolume=Math.max(0,c.warningSoundVolume-10));
            btn(x+160,y+86,w,"Volume + ("+c.warningSoundVolume+"%)",()->c.warningSoundVolume=Math.min(100,c.warningSoundVolume+10));
            btn(x,y+114,w,"Repeat slower",()->c.warningSoundCooldownTicks=Math.min(600,c.warningSoundCooldownTicks+20));
            btn(x+160,y+114,w,"Repeat faster",()->c.warningSoundCooldownTicks=Math.max(20,c.warningSoundCooldownTicks-20));
            btn(x,y+142,310,"Min interval: "+c.warningSoundCooldownTicks/20.0f+" seconds",()->{});
            btn(x+80,y+178,w,"Back",()->page=6);
        } else {
            TrackerData.WorldRecord record=DeathCompass.world();
            TrackerData cfg=DeathCompass.data;
            String prior=record.searchId==null?"minecraft:ancient_debris":record.searchId;
            idBox=new EditBox(font,x,y+14,310,20,Component.literal("Block or structure ID"));
            idBox.setValue(prior);addRenderableWidget(idBox);
            btn(x,y+38,310,"Track nearest loaded BLOCK",()->{
                String id=normalize(idBox.getValue());
                if(id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                    DeathCompass.trackBlock(id);
                    searchMessage="Searching loaded chunks for "+id;
                } else searchMessage="Enter a block ID, e.g. ancient_debris";
            });
            btn(x,y+63,310,"Choose structure from list (clues only)",()->page=5);
            btn(x,y+91,w,"Radius - ("+cfg.scanRadius+")",()->cfg.scanRadius=Math.max(4,cfg.scanRadius-4));
            btn(x+160,y+91,w,"Radius + ("+cfg.scanRadius+")",()->cfg.scanRadius=Math.min(64,cfg.scanRadius+4));
            btn(x,y+117,w,"Scan speed -",()->cfg.scanBudget=Math.max(100,cfg.scanBudget-500));
            btn(x+160,y+117,w,"Scan speed +",()->cfg.scanBudget=Math.min(10000,cfg.scanBudget+500));
            btn(x,y+143,310,"Lava warning: "+on(cfg.lavaWarning),()->cfg.lavaWarning=!cfg.lavaWarning);
            btn(x,y+171,w,"Resume death",DeathCompass::stopSearch);
            btn(x+160,y+171,w,"Stop finder",DeathCompass::stopSearch);
            btn(x,y+197,310,"Keep target until "+cfg.lockReleaseDistance+" blocks away",()->{
                cfg.lockReleaseDistance=switch(cfg.lockReleaseDistance){
                    case 64->96;case 96->128;case 128->192;case 192->256;default->64;
                };
            });
            btn(x+80,y+223,w,"Back",()->page=0);
        }
        btn(width/2-50,height-25,100,"Done",()->Minecraft.getInstance().gui.setScreen(null));
    }
    private static String truncate(String text,int length){return text.length()<=length?text:text.substring(0,length-3)+"...";}
    private EditBox numberBox(int x,int y,String name,int value){
        EditBox e=new EditBox(font,x,y,96,20,Component.literal(name));
        e.setValue(Integer.toString(value));addRenderableWidget(e);return e;
    }
    private static String normalize(String id) {String s=id.trim().toLowerCase(java.util.Locale.ROOT);return s.contains(":")?s:"minecraft:"+s;}
    private static String on(boolean v){return v?"ON":"OFF";}
    @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        if(page==4){
            int x=width/2-155,y=height/2-105;
            graphics.text(font,"Waypoint name",x,y-3,0xFFFFFFFF,true);
            graphics.text(font,"X                             Y                            Z",x,y+32,0xFFFFFFFF,true);
            graphics.text(font,searchMessage,x,y+175,0xFFFFCC88,true);
        }
        if(page==5){
            graphics.text(font,"SELECT A STRUCTURE (LANDMARK CLUES ONLY)",width/2-155,height/2-123,0xFFFFFFFF,true);
        }
        if(page==6){
            graphics.text(font,"DANGER ALERTS - ESTIMATES ONLY",width/2-155,height/2-125,0xFFFFD088,true);
        }
        if(page==7){
            graphics.text(font,"SOUND ALERTS (RED/URGENT WARNINGS ONLY)",width/2-155,height/2-124,0xFFFFD088,true);
        }
        if(page==2){
            int x=width/2-155,y=height/2-105;
            graphics.text(font,"Search ID (e.g. minecraft:ancient_debris)",x,y+5,0xFFFFFFFF,true);
            graphics.text(font,"Loaded chunks only. Structures = clues, not verified locations.",x,y+246,0xFFDDCC88,true);
            graphics.text(font,truncate(searchMessage,65),x,y+257,0xFFFFCC88,true);
        }
    }
    @Override public boolean isPauseScreen(){return false;}
}
