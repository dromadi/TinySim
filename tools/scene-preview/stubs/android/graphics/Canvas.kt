package android.graphics

/** Records the actual game HUD for desktop preview; native Android Canvas is untouched. */
class Canvas {
    val ops=StringBuilder()
    private fun json(x:Any?):String=when(x) {
        null->"null"
        is String->"\""+x.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\""
        is List<*>->x.joinToString(",","[","]") { json(it) }
        else->x.toString()
    }
    private fun op(name:String,vararg values:Any?) { if(ops.isNotEmpty())ops.append(',');ops.append(json(listOf(name)+values.toList())) }
    private fun paint(p:Paint):List<Any> = listOf(p.color,p.style.name,p.strokeWidth,p.textSize,p.textAlign.name)
    fun save() { op("save") }
    fun restore() { op("restore") }
    fun translate(x:Float,y:Float) { op("translate",x,y) }
    fun scale(x:Float,y:Float) { op("scale",x,y) }
    fun rotate(angle:Float) { op("rotate",angle) }
    fun rotate(angle:Float,x:Float,y:Float) { translate(x,y);rotate(angle);translate(-x,-y) }
    fun drawRoundRect(l:Float,t:Float,r:Float,b:Float,rx:Float,ry:Float,p:Paint) { op("roundRect",l,t,r-l,b-t,rx,paint(p)) }
    fun drawCircle(x:Float,y:Float,r:Float,p:Paint) { op("circle",x,y,r,paint(p)) }
    fun drawLine(x:Float,y:Float,x2:Float,y2:Float,p:Paint) { op("line",x,y,x2,y2,paint(p)) }
    fun drawText(text:String,x:Float,y:Float,p:Paint) { op("text",text,x,y,paint(p)) }
    fun drawPath(path:Path,p:Paint) { op("path",path.points.toList(),paint(p)) }
    fun drawArc(l:Float,t:Float,r:Float,b:Float,start:Float,sweep:Float,center:Boolean,p:Paint) { op("arc",l,t,r,b,start,sweep,center,paint(p)) }
}
class Paint(flags:Int=0) {
    var color=0;var style=Style.FILL;var strokeWidth=1f;var textSize=12f;var strokeCap=Cap.ROUND;var textAlign=Align.CENTER
    var typeface:Typeface?=null
    enum class Style { FILL, STROKE }
    enum class Cap { ROUND }
    enum class Align { CENTER }
    companion object { const val ANTI_ALIAS_FLAG=1 }
}
class Path {
    val points=mutableListOf<Float>()
    fun moveTo(x:Float,y:Float) { points.add(x);points.add(y) }
    fun lineTo(x:Float,y:Float) { points.add(x);points.add(y) }
    fun close() { }
}
class Typeface {
    companion object { const val BOLD=1;fun create(name:String,style:Int)=Typeface() }
}
object Color {
    fun rgb(r:Int,g:Int,b:Int)=argb(255,r,g,b)
    fun argb(a:Int,r:Int,g:Int,b:Int)=(a shl 24) or (r shl 16) or (g shl 8) or b
    fun parseColor(s:String):Int { val value=s.removePrefix("#").toLong(16);return if(s.length==7)(value or 0xff000000L).toInt() else value.toInt() }
}
