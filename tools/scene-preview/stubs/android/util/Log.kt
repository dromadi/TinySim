package android.util
object Log { @JvmStatic fun i(t:String,m:String)=println("I $t $m").let{0}; @JvmStatic fun w(t:String,m:String)=println("W $t $m").let{0}; @JvmStatic fun e(t:String,m:String)=println("E $t $m").let{0} }
