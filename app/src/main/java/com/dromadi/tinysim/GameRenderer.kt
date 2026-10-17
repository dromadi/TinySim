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
    fun draw(c:Canvas,g:GameState,sound:Boolean,parents:Boolean,hold:Float,time:Float,camera:Int,orbitMode:Boolean=false,zoom:Float=1f,panX:Float=0f,panZ:Float=0f,digging:Boolean=false) {
        // Top navigation and mission card.
        rect(c,24f,20f,201f,64f,white,22f)
        if(g.missionMode) { star(c,56f,52f,17f,yellow);text(c,"Misi",133f,60f,24f) }
        else { text(c,"∞",58f,65f,38f,teal);text(c,"Bebas",143f,60f,24f) }
        rect(c,245f,20f,270f,70f,white,22f)
        text(c,"TinySim",320f,52f,30f);text(c,"Dunia kecil, imajinasi besar.",376f,77f,14f)
        rect(c,544f,20f,410f,70f,white,22f)
        if(g.missionMode) {
            when(g.mission) {0->truck(c,600f,73f,.32f,4,0f);1->truck(c,600f,73f,.32f,2,-38f);2->dozer(c,600f,73f,.32f,1);3->roadIcon(c,600f,51f,g.roadProgress);else->houseIcon(c,600f,51f)}
            text(c,when(g.mission){0->"Isi truk";1->"Tuang pasir";2->"Ratakan";3->"Bangun jalan";else->"Bangun rumah"},756f,48f,20f)
            for(i in 0..3)circle(c,716f+i*30,70f,7f,if(g.missionProgress>i)teal else col("#DDE5DC"))
        } else { houseIcon(c,600f,51f);text(c,if(!g.houseReady)"Gali fondasi ${g.foundationProgress}/4" else if(g.houseFill<8)"Antar bahan ${g.houseFill}/8" else "Rakit rumah ${g.houseStage}/3",790f,61f,21f,teal) }
        rect(c,1000f,20f,70f,64f,white,20f);speaker(c,1035f,52f,sound)
        rect(c,1100f,20f,76f,64f,white,20f);lock(c,1138f,52f)
        if(hold>0) { p.style=Paint.Style.STROKE;p.strokeWidth=5f;p.color=teal;c.drawArc(1104f,18f,1172f,86f,-90f,hold*360,false,p);p.style=Paint.Style.FILL }
        // Presets, explicit orbit mode and large zoom buttons support imprecise touches.
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
        rect(c,1098f,370f,80f,76f,if(g.houseReady && g.houseFill==8)yellow else white,22f)
        houseIcon(c,1138f,398f);text(c,"Bangun",1138f,434f,13f)
        if(orbitMode) {
            rect(c,364f,492f,472f,40f,white,18f)
            text(c,"Geser untuk putar • cubit untuk zoom",600f,518f,17f,teal)
        }
        // Lower controls, comfortable targets with room for accidental touches.
        rect(c,0f,545f,1200f,130f,col("#F9F3E4"),0f)
        button(c,20f,125f,g.selected==GameState.Vehicle.EXCAVATOR);miniExcavator(c,81f,615f)
        button(c,155f,125f,g.selected==GameState.Vehicle.TRUCK);truck(c,218f,620f,.44f,2,0f)
        button(c,290f,125f,g.selected==GameState.Vehicle.DOZER);dozer(c,353f,620f,.44f,1)
        rect(c,425f,557f,100f,98f,if(digging)yellow else white,24f)
        poly(c,floatArrayOf(451f,582f,497f,582f,487f,607f,462f,607f),teal)
        text(c,if(digging)"Gali aktif" else "Gali",475f,638f,14f)
        text(c,if(g.selected==GameState.Vehicle.EXCAVATOR)"Ketuk / seret" else "Ketuk tujuan",610f,595f,21f,teal)
        text(c,"Geser • cubit zoom",610f,627f,13f,ink)
        rect(c,720f,557f,90f,98f,white,24f)
        rect(c,751f,582f,28f,28f,ink,5f);text(c,"Stop",765f,638f,15f)
        rect(c,850f,557f,325f,98f,teal,26f)
        when(g.selected) {
            GameState.Vehicle.EXCAVATOR -> {
                poly(c,floatArrayOf(888f,594f,932f,594f,920f,623f,898f,623f),yellow)
                text(c,if(g.bucket==0)"Ambil pasir" else "Isi truk",1040f,615f,26f,white)
            }
            GameState.Vehicle.TRUCK -> { truck(c,910f,629f,.38f,2,-32f);text(c,if(g.tipping)"Turunkan bak" else "Tuang pasir",1047f,615f,24f,white) }
            GameState.Vehicle.DOZER -> { dozer(c,909f,629f,.38f,1);text(c,if(g.bladeDown) "Angkat blade" else "Turunkan blade",1040f,615f,23f,white) }
        }
        if(g.celebrating>0) {
            rect(c,430f,130f,340f,100f,white,35f);text(c,"Hebat!",600f,194f,38f,teal)
            for(i in 0..7) star(c,380f+i*65,260f+sin(time*2+i)*16,14f,yellow)
        }
        if(parents) {
            rect(c,0f,0f,1200f,675f,Color.argb(185,22,49,58),0f)
            rect(c,300f,90f,600f,490f,white,35f)
            text(c,"Area orang tua",600f,142f,29f)
            for(i in 0..3) rect(c,350f,170f+i*90,500f,75f,if(i==3)teal else col("#E6EFE7"),20f)
            text(c,if(sound)"Suara: aktif" else "Suara: nonaktif",600f,217f,25f)
            text(c,"Mulai ulang dunia",600f,307f,25f)
            text(c,if(g.missionMode)"Mode: misi bergambar" else "Mode: bermain bebas",600f,397f,25f)
            text(c,"Kembali bermain",600f,487f,25f,white)
            text(c,"Offline • tanpa iklan • tanpa pengumpulan data",600f,550f,17f)
        }
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
