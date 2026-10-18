package com.dromadi.tinysim

import kotlin.math.*

/** Conservative ground-plane envelopes include wheels/tracks, cab, mirrors, dozer blade and crane outriggers.
 * Rotation-independent bounds prevent a chassis corner sweeping into another vehicle while turning.
 * The excavator's elevated arm and the crane's boom stay free to reach over the truck bed for loading.
 * Every query also keeps vehicles on land: the river is only passable over the finished bridge deck.
 */
object VehicleNavigation {
    data class Point(val x:Float,val z:Float) {
        fun distance(b:Point)=hypot(x-b.x,z-b.z)
    }
    data class Body(val unit:GameState.Vehicle,val point:Point) {
        val radius get()=radius(unit)
    }
    const val GAP=.08f
    fun radius(unit:GameState.Vehicle)=when(unit) { GameState.Vehicle.EXCAVATOR->2f;GameState.Vehicle.TRUCK->2.4f;GameState.Vehicle.DOZER->2.5f;GameState.Vehicle.CRANE->2.2f;GameState.Vehicle.MIXER->2.7f;GameState.Vehicle.WRECKER->2.7f }
    fun minX(unit:GameState.Vehicle)=if(unit==GameState.Vehicle.EXCAVATOR)-8.5f else WorldMap.worldX(WorldMap.DRIVE_MIN_X)
    fun maxX(bridge:Boolean)=WorldMap.worldX(if(bridge)WorldMap.EAST_MAX_X else WorldMap.DRIVE_MAX_X)
    fun bounded(p:Point,unit:GameState.Vehicle,bridge:Boolean=false)=Point(p.x.coerceIn(minX(unit),maxX(bridge)),p.z.coerceIn(WorldMap.DRIVE_MIN_Z,WorldMap.DRIVE_MAX_Z))
    /** Off the open deck, the river channel between the banks is never driveable. */
    fun land(p:Point,bridge:Boolean)=p.x<=WorldMap.WEST_BANK_X-.2f || p.x>=WorldMap.EAST_BANK_X+.2f || (bridge && abs(p.z-WorldMap.BRIDGE_Z)<WorldMap.BRIDGE_HALF-.4f)
    fun inside(p:Point,unit:GameState.Vehicle,bridge:Boolean=false)=p==bounded(p,unit,bridge) && land(p,bridge)
    fun free(p:Point,unit:GameState.Vehicle,others:List<Body>)=others.all { p.distance(it.point)>=radius(unit)+it.radius+GAP-.0001f }
    /** Swept test, not endpoint-only: no tunnelling even on a long movement segment. */
    fun clear(a:Point,b:Point,unit:GameState.Vehicle,others:List<Body>,bridge:Boolean=false):Boolean {
        val dx=b.x-a.x;val dz=b.z-a.z;val length=dx*dx+dz*dz
        if(!dry(a,b,bridge))return false
        return others.all {
            val t=if(length<1e-10f)0f else (((it.point.x-a.x)*dx+(it.point.z-a.z)*dz)/length).coerceIn(0f,1f)
            hypot(a.x+dx*t-it.point.x,a.z+dz*t-it.point.z)>=radius(unit)+it.radius+GAP-.0001f
        }
    }
    /** A segment that touches the river channel must stay on the deck along its whole length. */
    private fun dry(a:Point,b:Point,bridge:Boolean):Boolean {
        if(max(a.x,b.x)<=WorldMap.WEST_BANK_X-.2f || min(a.x,b.x)>=WorldMap.EAST_BANK_X+.2f)return true
        val steps=ceil(a.distance(b)/.25f).toInt().coerceAtLeast(1)
        return (0..steps).all { i->val t=i.toFloat()/steps;land(Point(a.x+(b.x-a.x)*t,a.z+(b.z-a.z)*t),bridge) }
    }
    fun nearestFree(request:Point,from:Point,unit:GameState.Vehicle,others:List<Body>,bridge:Boolean=false):Point? {
        val desired=bounded(request,unit,bridge)
        if(land(desired,bridge) && free(desired,unit,others))return desired
        for(ring in 1..240) {
            val distance=ring*.25f
            val candidates=(0 until 64).map { i->val a=i*2*PI/64;Point(desired.x+cos(a).toFloat()*distance,desired.z+sin(a).toFloat()*distance) }
                .filter { inside(it,unit,bridge) && free(it,unit,others) }
            if(candidates.isNotEmpty())return candidates.minBy { it.distance(desired)+it.distance(from)*.001f }
        }
        return null
    }
    /** Visibility graph around the other vehicles' circles; all edges use the same swept test. */
    fun plan(start:Point,request:Point,unit:GameState.Vehicle,others:List<Body>,bridge:Boolean=false):List<Point> {
        val end=nearestFree(request,start,unit,others,bridge) ?: return emptyList()
        if(clear(start,end,unit,others,bridge))return listOf(end)
        val nodes=mutableListOf(start,end)
        for(body in others) {
            val ring=(radius(unit)+body.radius+GAP+.16f)/cos(PI.toFloat()/16)
            for(i in 0 until 16) {
                val a=i*2*PI/16;val p=bounded(Point(body.point.x+cos(a).toFloat()*ring,body.point.z+sin(a).toFloat()*ring),unit,bridge)
                if(inside(p,unit,bridge) && free(p,unit,others))nodes+=p
            }
        }
        val distance=FloatArray(nodes.size){Float.POSITIVE_INFINITY};distance[0]=0f
        val previous=IntArray(nodes.size){-1};val visited=BooleanArray(nodes.size)
        repeat(nodes.size) {
            val current=nodes.indices.filter { !visited[it] }.minByOrNull { distance[it] } ?: return@repeat
            if(!distance[current].isFinite())return@repeat
            visited[current]=true
            for(next in nodes.indices)if(!visited[next] && clear(nodes[current],nodes[next],unit,others,bridge)) {
                val cost=distance[current]+nodes[current].distance(nodes[next])
                if(cost<distance[next]) { distance[next]=cost;previous[next]=current }
            }
        }
        if(previous[1]<0)return emptyList()
        val result=mutableListOf<Point>();var i=1
        while(i!=0) { result+=nodes[i];i=previous[i] }
        return result.asReversed()
    }
}

/** A route is cached until a moving obstacle invalidates its next segment. */
class VehicleRoutes {
    private data class Route(val request:VehicleNavigation.Point,val points:MutableList<VehicleNavigation.Point>)
    private val routes=mutableMapOf<GameState.Vehicle,Route>()
    fun clear(unit:GameState.Vehicle?=null) { if(unit==null)routes.clear() else routes.remove(unit) }
    fun next(unit:GameState.Vehicle,start:VehicleNavigation.Point,request:VehicleNavigation.Point,others:List<VehicleNavigation.Body>,bridge:Boolean):VehicleNavigation.Point? {
        var route=routes[unit]
        if(route?.request!=request) { clear(unit);route=null }
        while(route!=null && route.points.size>1 && start.distance(route.points.first())<.03f)route.points.removeAt(0)
        if(route==null || route.points.isEmpty() || !VehicleNavigation.clear(start,route.points.first(),unit,others,bridge)) {
            val points=VehicleNavigation.plan(start,request,unit,others,bridge).toMutableList()
            if(points.isEmpty()) { clear(unit);return null }
            route=Route(request,points);routes[unit]=route
        }
        return route.points.firstOrNull()
    }
    fun last(unit:GameState.Vehicle)=routes[unit]?.points?.size==1
}
