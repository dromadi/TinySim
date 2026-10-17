package com.dromadi.tinysim
import android.graphics.*
import kotlin.math.*

/** Canvas HUD and original vehicle pictograms; the world is drawn by SceneRenderer3D. */
class GameRenderer {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink=Color.rgb(36,70,80)
    private val white=Color.rgb(255,253,243)
    private val yellow=Color.rgb(250,190,63)
    private val orange=Color.rgb(232,137,59)
    private val teal=Color.rgb(39,158,159)
    private fun col(hex:String)=Color.parseColor(hex)
    private fun rect(c:Canvas,x:Float,y:Float,w:Float,h:Float,color:Int,r:Float=12f) { p.color=color; p.style=Paint.Style.FILL; c.drawRoundRect(x,y,x+w,y+h,r,r,p) }
    private fun circle(c:Canvas,x:Float,y:Float,r:Float,color:Int) { p.color=color; p.style=Paint.Style.FILL; c.drawCircle(x,y,r,p) }
    private fun line(c:Canvas,x:Float,y:Float,x2:Float,y2:Float,color:Int,w:Float=6f) { p.color=color; p.strokeWidth=w;p.strokeCap=Paint.Cap.ROUND; c.drawLine(x,y,x2,y2,p) }
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float=22f,color:Int=ink) { p.color=color;p.textSize=size;p.typeface=Typeface.create("sans-serif",Typeface.BOLD);p.textAlign=Paint.Align.CENTER;c.drawText(s,x,y,p) }
    private fun poly(c:Canvas,points:FloatArray,color:Int) { p.color=color;val path=Path();path.moveTo(points[0],points[1]);for(i in 2 until points.size step 2)path.lineTo(points[i],points[i+1]);path.close();c.drawPath(path,p) }
    private fun wheel(c:Canvas,x:Float,y:Float,r:Float=25f) { circle(c,x,y,r,ink); circle(c,x,y,r*.55f,col("#A6BCC1"));circle(c,x,y,r*.20f,white) }
    private fun cloud(c:Canvas,x:Float,y:Float) { rect(c,x-40,y,110f,28f,white,20f);circle(c,x,y,30f,white);circle(c,x+35,y+2,23f,white) }
    private fun star(c:Canvas,x:Float,y:Float,r:Float,color:Int) {
        val a=FloatArray(20);for(i in 0..9) {val t=i*PI/5-PI/2;val rad=if(i%2==0)r else r*.48f;a[i*2]=x+(cos(t)*rad).toFloat();a[i*2+1]=y+(sin(t)*rad).toFloat()};poly(c,a,color)
    }
    /**
     * Child HUD: three vehicle cards, one large action button and a pictogram progress card, with
     * no words to read. Zoom, camera, minimap and stop appear only when a parent enables them.
     * hintX/hintY place the pointing hand (NaN hides it).
     */
    fun draw(c:Canvas,g:GameState,sound:Boolean,parents:Boolean,hold:Float,time:Float,camera:Int,advanced:Boolean=false,orbitMode:Boolean=false,zoom:Float=1f,panX:Float=0f,panZ:Float=0f,hintX:Float=Float.NaN,hintY:Float=Float.NaN) {
        progressCard(c,g)
        rect(c,1100f,20f,76f,64f,white,20f);lock(c,1138f,52f)
        if(hold>0) { p.style=Paint.Style.STROKE;p.strokeWidth=5f;p.color=teal;c.drawArc(1104f,18f,1172f,86f,-90f,hold*360,false,p);p.style=Paint.Style.FILL }
        if(advanced) {
            rect(c,1000f,20f,70f,64f,white,20f);speaker(c,1035f,52f,sound)
            rect(c,1098f,106f,80f,76f,white,22f)
            rect(c,1114f,124f,45f,28f,ink,6f);circle(c,1137f,138f,9f,white)
            poly(c,floatArrayOf(1159f,129f,1170f,121f,1170f,155f,1159f,147f),teal)
            text(c,"${camera+1} · ${(zoom*100).toInt()}%",1138f,172f,13f,teal)
            rect(c,1098f,194f,80f,76f,if(orbitMode)yellow else white,22f)
            text(c,"↔",1138f,229f,32f);text(c,if(orbitMode)"Putar" else "Kamera",1138f,255f,14f,teal)
            for(i in 0..1) {
                rect(c,20f,110f+i*88,80f,76f,white,22f)
                text(c,if(i==0)"+" else "−",60f,151f+i*88,36f)
                text(c,if(i==0)"Dekat" else "Jauh",60f,175f+i*88,13f,teal)
            }
            rect(c,1098f,282f,80f,76f,white,22f)
            text(c,"⊙",1138f,320f,32f,teal);text(c,"Ikuti",1138f,344f,14f)
            minimap(c,g,panX,panZ)
        }
        // Large targets with room for imprecise touches.
        rect(c,0f,545f,1200f,130f,col("#F9F3E4"),0f)
        button(c,20f,160f,g.selected==GameState.Vehicle.EXCAVATOR);miniExcavator(c,cardCenter(GameState.Vehicle.EXCAVATOR),615f)
        button(c,192f,160f,g.selected==GameState.Vehicle.TRUCK);truck(c,cardCenter(GameState.Vehicle.TRUCK)+2f,620f,.46f,2,0f)
        button(c,364f,160f,g.selected==GameState.Vehicle.DOZER);dozer(c,cardCenter(GameState.Vehicle.DOZER)+2f,622f,.46f,1)
        button(c,536f,160f,g.selected==GameState.Vehicle.CRANE);crane(c,cardCenter(GameState.Vehicle.CRANE),640f,.9f,false)
        if(advanced) { rect(c,712f,557f,100f,98f,white,24f);rect(c,745f,585f,34f,34f,ink,6f) }
        rect(c,840f,556f,340f,104f,teal,28f)
        actionIcon(c,g,1010f,608f)
        if(g.celebrating>0) {
            star(c,600f,250f,70f+sin(time*6)*6,yellow)
            for(i in 0..7) star(c,380f+i*65,360f+sin(time*2+i)*16,14f,yellow)
        }
        if(!hintX.isNaN() && !parents)hand(c,hintX,hintY,time)
        if(parents) {
            rect(c,0f,0f,1200f,675f,Color.argb(185,22,49,58),0f)
            rect(c,300f,80f,600f,530f,white,35f)
            text(c,"Area orang tua",600f,130f,29f)
            for(i in 0..5) rect(c,350f,PARENT_TOP+i*PARENT_STEP,500f,60f,if(i==5)teal else col("#E6EFE7"),20f)
            val labels=listOf(if(sound)"Suara: aktif" else "Suara: nonaktif","Sudut kamera: ${camera+1} / 3",
                if(advanced)"Kontrol lanjutan: tampil" else "Kontrol lanjutan: sembunyi",
                if(g.missionMode)"Mode: misi bergambar" else "Mode: bermain bebas","Mulai ulang dunia","Kembali bermain")
            labels.forEachIndexed { i,label-> text(c,label,600f,PARENT_TOP+i*PARENT_STEP+39f,23f,if(i==5)white else ink) }
            text(c,"Offline • tanpa iklan • tanpa pengumpulan data",600f,592f,16f)
        }
    }
    /** Pictogram progress: mission icon or the house project stage, with dots instead of words. */
    private fun blockIcon(c:Canvas,x:Float,y:Float) {
        for(i in 0..2)rect(c,x-19f,y+17f-i*17f,38f,15f,listOf(orange,teal,yellow)[i],4f)
    }
    private fun progressCard(c:Canvas,g:GameState) {
        rect(c,24f,20f,300f,70f,white,22f)
        val dots:Int;val done:Int
        if(g.selected==GameState.Vehicle.CRANE && !g.bridgeReady && g.craneCargo==0) {
            blockIcon(c,72f,50f);dots=3;done=g.blocks.placed
        } else if(g.missionMode) {
            when(g.mission) {0->truck(c,72f,73f,.32f,4,0f);1->truck(c,72f,73f,.32f,2,-38f);2->dozer(c,72f,73f,.32f,1);3->roadIcon(c,72f,51f,g.roadProgress);else->houseIcon(c,72f,51f)}
            dots=4;done=g.missionProgress
        } else if(!g.houseReady && g.houseStage==0) {
            poly(c,floatArrayOf(50f,40f,96f,40f,86f,68f,60f,68f),yellow);line(c,73f,22f,73f,38f,ink,6f)
            dots=4;done=g.foundationProgress
        } else if(g.houseFill<8 && g.houseStage==0) { truck(c,72f,73f,.32f,4,0f);dots=8;done=g.houseFill }
        else if(g.houseStage<3) { houseIcon(c,72f,51f);dots=3;done=g.houseStage }
        else if(g.bridgeFill<8 && g.bridgeStage==0) { truck(c,72f,73f,.32f,4,0f);bridgeIcon(c,112f,51f,.6f);dots=8;done=g.bridgeFill }
        else { bridgeIcon(c,72f,51f,1f);dots=3;done=g.bridgeStage }
        val step=if(dots>4)22f else 34f
        for(i in 0 until dots)circle(c,140f+i*step,55f,if(dots>4)7f else 9f,if(done>i)teal else col("#DDE5DC"))
    }
    /** The single big action, drawn as what will happen next. */
    private fun actionIcon(c:Canvas,g:GameState,x:Float,y:Float) {
        when(g.selected) {
            GameState.Vehicle.EXCAVATOR -> if(g.bucket==0) {
                poly(c,floatArrayOf(x-60f,y-30f,x-6f,y-30f,x-18f,y+4f,x-48f,y+4f),yellow)
                arrowDown(c,x+40f,y-14f,white)
            } else {
                poly(c,floatArrayOf(x-110f,y-34f,x-62f,y-34f,x-72f,y-6f,x-100f,y-6f),yellow)
                poly(c,floatArrayOf(x-104f,y-36f,x-86f,y-50f,x-68f,y-36f),col("#F7D583"))
                line(c,x-50f,y-20f,x-10f,y-20f,white,9f);poly(c,floatArrayOf(x-4f,y-20f,x-22f,y-34f,x-22f,y-6f),white)
                truck(c,x+60f,y+36f,.42f,2,0f)
            }
            GameState.Vehicle.TRUCK -> truck(c,x,y+38f,.55f,3,if(g.tipping)0f else -32f)
            GameState.Vehicle.DOZER -> {
                dozer(c,x-30f,y+38f,.5f,1)
                poly(c,floatArrayOf(x+40f,y+30f,x+70f,y-2f,x+100f,y+30f),col("#F7D583"))
            }
            GameState.Vehicle.CRANE -> when {
                g.blocks.carried>=0 || (!g.blocks.complete && hypot(g.craneWorldX-BlockYard.TARGET_X,g.craneZ-BlockYard.TARGET_Z)<12f) -> { crane(c,x-50f,y+40f,1f,false);blockIcon(c,x+70f,y);arrowDown(c,x+110f,y-18f,white) }
                // Bridge ready: the crane lifts the next piece over the water.
                g.bridgeReady -> { crane(c,x-50f,y+40f,1f,false);bridgeIcon(c,x+80f,y+4f,.9f) }
                g.craneCargo>0 -> { crane(c,x-30f,y+40f,1f,true);arrowDown(c,x+70f,y-10f,white) }
                else -> { crane(c,x-30f,y+40f,1f,false);poly(c,floatArrayOf(x+50f,y+34f,x+80f,y+4f,x+110f,y+34f),col("#F7D583")) }
            }
        }
    }
    /** Mobile crane pictogram: wheeled carrier, angled boom, cable and clamshell grab. */
    private fun crane(c:Canvas,x:Float,y:Float,s:Float,loaded:Boolean) {
        c.save();c.translate(x,y);c.scale(s,s)
        // Dark outline keeps the yellow crane readable on the yellow selected card.
        rect(c,-51f,-33f,102f,24f,ink,8f);rect(c,-49f,-53f,36f,28f,ink,7f);line(c,-8f,-36f,42f,-88f,ink,16f)
        rect(c,-48f,-30f,96f,18f,yellow,6f);rect(c,-46f,-50f,30f,22f,yellow,5f);rect(c,-42f,-46f,20f,13f,col("#A5DFE2"),3f)
        wheel(c,-30f,-10f,10f);wheel(c,30f,-10f,10f)
        line(c,-8f,-36f,42f,-88f,yellow,10f);line(c,-8f,-36f,42f,-88f,ink,2f)
        line(c,42f,-88f,42f,-56f,ink,2f)
        poly(c,floatArrayOf(32f,-56f,52f,-56f,48f,-44f,36f,-44f),ink)
        if(loaded)poly(c,floatArrayOf(34f,-56f,42f,-64f,50f,-56f),col("#F7D583"))
        c.restore()
    }
    private fun arrowDown(c:Canvas,x:Float,y:Float,color:Int) { line(c,x,y-24f,x,y+14f,color,10f);poly(c,floatArrayOf(x-18f,y+8f,x+18f,y+8f,x,y+30f),color) }
    /** A bobbing pointing hand with a tap ripple; visible pictorial help for non-readers. */
    private fun hand(c:Canvas,x:Float,y:Float,time:Float) {
        val phase=(time*1.4f)%1f
        p.style=Paint.Style.STROKE;p.strokeWidth=6f
        p.color=Color.argb((200*(1f-phase)).toInt(),255,253,243);c.drawCircle(x,y,18f+phase*42f,p)
        p.style=Paint.Style.FILL
        val bob=abs(sin(time*PI.toFloat()*1.4f))*16f
        c.save()
        // Near the bottom controls the hand reaches down from above so it stays on screen.
        if(y>450f) { c.rotate(180f,x,y);c.translate(x-6f,y+10f+bob) } else c.translate(x+6f,y+10f+bob)
        p.color=ink
        c.drawRoundRect(-12f,-4f,12f,52f,12f,12f,p);c.drawRoundRect(-17f,32f,40f,90f,20f,20f,p)
        p.color=white
        c.drawRoundRect(-8f,0f,8f,52f,8f,8f,p);c.drawRoundRect(-13f,36f,36f,86f,16f,16f,p)
        circle(c,36f,52f,9f,white)
        c.restore()
    }
    private fun minimap(c:Canvas,g:GameState,panX:Float,panZ:Float) {
        rect(c,20f,298f,216f,122f,white,18f)
        rect(c,32f,314f,192f,82f,col("#D9DAB7"),9f)
        fun mx(x:Float)=32f+(x-WorldMap.MIN_X)/(WorldMap.MAX_X-WorldMap.MIN_X)*192f
        fun mz(z:Float)=314f+(z-WorldMap.MIN_Z)/(WorldMap.MAX_Z-WorldMap.MIN_Z)*82f
        line(c,mx(-8f),mz(0f),mx(47f),mz(0f),col("#F6E9C5"),9f)
        line(c,mx(18f),mz(0f),mx(18f),mz(10f),col("#F6E9C5"),7f)
        line(c,mx(36f),mz(0f),mx(36f),mz(10f),col("#F6E9C5"),7f)
        rect(c,mx(14f),mz(4f),20f,20f,teal,3f);rect(c,mx(32f),mz(4f),20f,20f,orange,3f)
        rect(c,mx(WorldMap.RIVER_MIN_X),mz(WorldMap.MIN_Z),mx(WorldMap.RIVER_MAX_X)-mx(WorldMap.RIVER_MIN_X),82f,col("#5DA9D6"),0f)
        if(g.bridgeOpen)line(c,mx(WorldMap.WEST_BANK_X),mz(WorldMap.BRIDGE_Z),mx(WorldMap.EAST_BANK_X),mz(WorldMap.BRIDGE_Z),orange,6f)
        line(c,mx(16f),mz(8f),mx(24f),mz(8f),yellow,5f)
        circle(c,mx(WorldMap.worldX(g.excavatorX)),mz(g.excavatorZ),6f,yellow)
        circle(c,mx(WorldMap.worldX(g.truckX)),mz(g.truckZ),5f,teal)
        circle(c,mx(WorldMap.worldX(g.dozerX)),mz(g.dozerZ),5f,ink)
        val goal=if(g.selected==GameState.Vehicle.TRUCK)g.truckGoal else if(g.selected==GameState.Vehicle.DOZER)g.dozerGoal else g.excavatorGoal
        if(goal!=null)star(c,mx(WorldMap.worldX(goal.x)),mz(goal.z),6f,white)
        val fx=mx(panX-.3f).coerceIn(37f,219f);val fz=mz(panZ).coerceIn(319f,391f)
        p.style=Paint.Style.STROKE;p.strokeWidth=2f;p.color=white;c.drawCircle(fx,fz,9f,p);p.style=Paint.Style.FILL
        text(c,"Pasir     •     Konstruksi     •     Bangun",128f,411f,9f,ink)
    }
    companion object {
        const val PARENT_TOP=160f;const val PARENT_STEP=70f
        /** Horizontal centre of each vehicle card in the bottom bar (1200-wide HUD space). */
        fun cardCenter(vehicle:GameState.Vehicle)=100f+vehicle.ordinal*172f
    }
    /** Arched bridge over blue water, as a pictogram. */
    private fun bridgeIcon(c:Canvas,x:Float,y:Float,s:Float) {
        rect(c,x-30f*s,y+10f*s,60f*s,12f*s,col("#5DA9D6"),4f)
        p.style=Paint.Style.STROKE;p.strokeWidth=6f*s;p.color=orange
        c.drawArc(x-28f*s,y-14f*s,x+28f*s,y+30f*s,180f,180f,false,p);p.style=Paint.Style.FILL
        rect(c,x-32f*s,y-2f*s,64f*s,7f*s,ink,3f)
    }
    private fun houseIcon(c:Canvas,x:Float,y:Float) {
        rect(c,x-19f,y-3f,38f,28f,teal,3f)
        poly(c,floatArrayOf(x-27f,y-4f,x,y-26f,x+27f,y-4f),orange)
        rect(c,x-5f,y+8f,10f,17f,white,2f)
    }
    private fun roadIcon(c:Canvas,x:Float,y:Float,progress:Int) {
        for(i in 0..3) {
            rect(c,x-38f+i*20f,y-19f,18f,38f,if(i<progress)teal else col("#DDE5DC"),3f)
            line(c,x-29f+i*20f,y-3f,x-29f+i*20f,y+3f,white,3f)
        }
    }
    private fun button(c:Canvas,x:Float,w:Float,selected:Boolean) { rect(c,x,557f,w,98f,if(selected)yellow else col("#E6EBDD"),24f) }
    private fun arrow(c:Canvas,x:Float,y:Float,right:Boolean,color:Int) { val d=if(right)1 else -1;line(c,x-d*24,y,x+d*23,y,color,11f);poly(c,floatArrayOf(x+d*30,y,x+d*5,y-23,x+d*5,y+23),color) }
    private fun speaker(c:Canvas,x:Float,y:Float,on:Boolean) { rect(c,x-20,y-9,13f,18f,ink,3f);poly(c,floatArrayOf(x-10,y-8,x+4,y-20,x+4,y+20,x-10,y+8),ink);if(on){line(c,x+13,y-12,x+20,y-5,teal,4f);line(c,x+20,y+5,x+13,y+12,teal,4f)}else line(c,x+10,y-12,x+23,y+12,orange,5f) }
    private fun lock(c:Canvas,x:Float,y:Float) { p.color=ink;p.style=Paint.Style.STROKE;p.strokeWidth=5f;c.drawRoundRect(x-10,y-22,x+10,y+3,10f,10f,p);p.style=Paint.Style.FILL;rect(c,x-17,y-6,34f,27f,ink,6f);circle(c,x,y+6,3f,white) }
    private fun excavator(c:Canvas,g:GameState) {
        // Cab on the left, articulated boom extending to the right.
        rect(c,125f,442f,150f,37f,ink,18f)
        for(i in 0..4)circle(c,145f+i*27,460f,10f,col("#819398"))
        rect(c,139f,406f,129f,37f,yellow,10f);rect(c,149f,340f,76f,78f,yellow,12f)
        rect(c,158f,350f,53f,49f,col("#91D9DF"),8f)
        line(c,169f,367f,194f,356f,white,5f)
        val jx=310f;val jy=285f
        line(c,243f,412f,jx,jy,orange,29f);line(c,jx,jy,g.armX,g.armY-28,yellow,24f)
        line(c,249f,392f,300f,293f,ink,6f);line(c,318f,295f,g.armX-6,g.armY-46,ink,5f)
        circle(c,243f,412f,13f,yellow);circle(c,jx,jy,13f,orange)
        poly(c,floatArrayOf(g.armX-24,g.armY-32,g.armX+25,g.armY-24,g.armX+19,g.armY+8,g.armX-10,g.armY+15,g.armX-28,g.armY),ink)
        if(g.bucket>0)poly(c,floatArrayOf(g.armX-19,g.armY-9,g.armX,g.armY-23,g.armX+19,g.armY-4),col("#F7D583"))
        circle(c,193f,379f,4f,ink)
    }
    private fun miniExcavator(c:Canvas,x:Float,y:Float) { c.save();c.translate(x-85,y-184);c.scale(.40f,.40f);val g=GameState();g.armX=340f;g.armY=390f;excavator(c,g);c.restore() }
    private fun truck(c:Canvas,x:Float,y:Float,s:Float,load:Int,angle:Float) { c.save();c.translate(x,y);c.scale(s,s)
        rect(c,-113f,-46f,224f,20f,ink,7f)
        rect(c,47f,-112f,72f,78f,orange,13f);rect(c,55f,-102f,52f,39f,col("#A5DFE2"),7f);line(c,62f,-94f,85f,-94f,white,4f)
        rect(c,104f,-57f,21f,15f,white,4f)
        c.save();c.rotate(angle,-108f,-53f)
        poly(c,floatArrayOf(-118f,-125f,43f,-125f,31f,-54f,-107f,-54f),teal)
        for(i in 0..3)line(c,-88f+i*32,-112f,-83f+i*32,-68f,col("#187F85"),5f)
        if(load>0) { val h=load*5f;poly(c,floatArrayOf(-111f,-125f,-38f,-125f-h,35f,-125f),col("#F7D583")) }
        c.restore();wheel(c,-74f,-24f);wheel(c,80f,-24f);c.restore()
    }
    private fun dozer(c:Canvas,x:Float,y:Float,s:Float,direction:Int,bladeDown:Boolean=true) { c.save();c.translate(x,y);c.scale(s,s)
        if(direction<0)c.scale(-1f,1f)
        rect(c,-83f,-42f,153f,39f,ink,19f);for(i in 0..4)circle(c,-63f+i*28,-23f,10f,col("#819398"))
        rect(c,-75f,-77f,135f,36f,yellow,10f);rect(c,-56f,-141f,76f,66f,yellow,8f);rect(c,-48f,-132f,57f,47f,col("#A5DFE2"),6f)
        rect(c,-64f,-150f,93f,12f,ink,5f);rect(c,26f,-98f,38f,29f,orange,6f)
        c.save(); if(!bladeDown)c.translate(0f,-30f)
        line(c,48f,-43f,101f,-36f,ink,13f);poly(c,floatArrayOf(89f,-76f,110f,-70f,117f,-9f,81f,-9f),col("#D6E2DD"));line(c,81f,-9f,119f,-9f,ink,7f)
        c.restore(); c.restore()
    }
}
