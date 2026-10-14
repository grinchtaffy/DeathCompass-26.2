package dev.deathcompass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
    private int page=0, historyPage=0;
    private EditBox idBox;
    private String searchMessage="";
    public SettingsScreen(){super(Component.literal("Death Compass Settings"));}
    private void btn(int x,int y,int w,String label,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b->{action.run();DeathCompass.save();rebuildWidgets();}).bounds(x,y,w,20).build());}
    private void rebuildWidgets(){clearWidgets();init();}
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
            btn(x+80,y+200,w,"Find block / structure",()->page=2);
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
            btn(x,y+63,310,"Track STRUCTURE clue (loaded chunks)",()->{
                String id=normalize(idBox.getValue());
                if(java.util.Set.of("minecraft:ancient_city","minecraft:nether_fortress","minecraft:bastion_remnant","minecraft:stronghold","minecraft:trial_chambers","minecraft:monument","minecraft:desert_pyramid","minecraft:jungle_temple").contains(id)) {
                    DeathCompass.trackStructure(id);
                    searchMessage="Looking for landmark blocks; not confirmed structure positions";
                } else searchMessage="Available: ancient_city, stronghold, bastion_remnant, ...";
            });
            btn(x,y+91,w,"Radius - ("+cfg.scanRadius+")",()->cfg.scanRadius=Math.max(4,cfg.scanRadius-4));
            btn(x+160,y+91,w,"Radius + ("+cfg.scanRadius+")",()->cfg.scanRadius=Math.min(64,cfg.scanRadius+4));
            btn(x,y+117,w,"Scan speed -",()->cfg.scanBudget=Math.max(100,cfg.scanBudget-500));
            btn(x+160,y+117,w,"Scan speed +",()->cfg.scanBudget=Math.min(10000,cfg.scanBudget+500));
            btn(x,y+143,310,"Lava warning: "+on(cfg.lavaWarning),()->cfg.lavaWarning=!cfg.lavaWarning);
            btn(x,y+171,w,"Resume death tracking",DeathCompass::stopSearch);
            btn(x+160,y+171,w,"Stop finder",DeathCompass::stopSearch);
            btn(x+80,y+197,w,"Back",()->page=0);
        }
        btn(width/2-50,height-25,100,"Done",()->Minecraft.getInstance().gui.setScreen(null));
    }
    private EditBox numberBox(int x,int y,String name,int value){
        EditBox e=new EditBox(font,x,y,96,20,Component.literal(name));
        e.setValue(Integer.toString(value));addRenderableWidget(e);return e;
    }
    private static String normalize(String id) {String s=id.trim().toLowerCase(java.util.Locale.ROOT);return s.contains(":")?s:"minecraft:"+s;}
    private static String on(boolean v){return v?"ON":"OFF";}
    @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        if(page==2){
            int x=width/2-155,y=height/2-105;
            graphics.drawString(font,"Search ID (e.g. minecraft:ancient_debris)",x,y+5,0xFFFFFFFF,true);
            graphics.drawString(font,"Client-loaded chunks only; structures are landmark guesses.",x,y+225,0xFFDDCC88,true);
            graphics.drawString(font,searchMessage,x,y+238,0xFFFFCC88,true);
        }
    }
    @Override public boolean isPauseScreen(){return false;}
}
