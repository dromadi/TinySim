package android.opengl
import kotlin.math.*
/** Column-major port of android.opengl.Matrix for the preview harness. */
object Matrix {
  @JvmStatic fun setIdentityM(m:FloatArray,o:Int){ for(i in 0..15)m[o+i]=0f; for(i in 0..3)m[o+i*5]=1f }
  @JvmStatic fun multiplyMM(r:FloatArray,ro:Int,a:FloatArray,ao:Int,b:FloatArray,bo:Int){ val t=FloatArray(16); for(i in 0..3)for(j in 0..3){var s=0f;for(k in 0..3)s+=a[ao+k*4+j]*b[bo+i*4+k];t[i*4+j]=s}; t.copyInto(r,ro) }
  @JvmStatic fun translateM(m:FloatArray,o:Int,x:Float,y:Float,z:Float){ for(i in 0..3)m[o+12+i]+=m[o+i]*x+m[o+4+i]*y+m[o+8+i]*z }
  @JvmStatic fun scaleM(m:FloatArray,o:Int,x:Float,y:Float,z:Float){ for(i in 0..3){m[o+i]*=x;m[o+4+i]*=y;m[o+8+i]*=z} }
  @JvmStatic fun rotateM(m:FloatArray,o:Int,a:Float,x:Float,y:Float,z:Float){ val r=FloatArray(16); setRotateM(r,0,a,x,y,z); val t=FloatArray(16); multiplyMM(t,0,m,o,r,0); t.copyInto(m,o) }
  @JvmStatic fun setRotateM(r:FloatArray,o:Int,a:Float,x0:Float,y0:Float,z0:Float){ setIdentityM(r,o); val rad=a*PI.toFloat()/180f; val s=sin(rad); val c=cos(rad); val l=sqrt(x0*x0+y0*y0+z0*z0); val x=x0/l;val y=y0/l;val z=z0/l; val nc=1-c
    r[o]=x*x*nc+c; r[o+4]=x*y*nc-z*s; r[o+8]=x*z*nc+y*s
    r[o+1]=y*x*nc+z*s; r[o+5]=y*y*nc+c; r[o+9]=y*z*nc-x*s
    r[o+2]=x*z*nc-y*s; r[o+6]=y*z*nc+x*s; r[o+10]=z*z*nc+c }
  @JvmStatic fun perspectiveM(m:FloatArray,o:Int,fovy:Float,aspect:Float,n:Float,f:Float){ val ff=1f/tan(fovy*PI.toFloat()/360f); val r=1f/(n-f); for(i in 0..15)m[o+i]=0f; m[o]=ff/aspect; m[o+5]=ff; m[o+10]=(f+n)*r; m[o+11]=-1f; m[o+14]=2f*f*n*r }
  @JvmStatic fun orthoM(m:FloatArray,o:Int,l:Float,r:Float,b:Float,t:Float,n:Float,f:Float){ for(i in 0..15)m[o+i]=0f; m[o]=2f/(r-l); m[o+5]=2f/(t-b); m[o+10]=-2f/(f-n); m[o+12]=-(r+l)/(r-l); m[o+13]=-(t+b)/(t-b); m[o+14]=-(f+n)/(f-n); m[o+15]=1f }
  @JvmStatic fun setLookAtM(rm:FloatArray,o:Int,ex:Float,ey:Float,ez:Float,cx:Float,cy:Float,cz:Float,ux:Float,uy:Float,uz:Float){
    var fx=cx-ex;var fy=cy-ey;var fz=cz-ez; val rlf=1f/sqrt(fx*fx+fy*fy+fz*fz); fx*=rlf;fy*=rlf;fz*=rlf
    var sx=fy*uz-fz*uy; var sy=fz*ux-fx*uz; var sz=fx*uy-fy*ux; val rls=1f/sqrt(sx*sx+sy*sy+sz*sz); sx*=rls;sy*=rls;sz*=rls
    val ux2=sy*fz-sz*fy; val uy2=sz*fx-sx*fz; val uz2=sx*fy-sy*fx
    val m=floatArrayOf(sx,ux2,-fx,0f, sy,uy2,-fy,0f, sz,uz2,-fz,0f, 0f,0f,0f,1f); m.copyInto(rm,o); translateM(rm,o,-ex,-ey,-ez) }
  /** General 4x4 inverse (column-major), matching android.opengl.Matrix.invertM. */
  @JvmStatic fun invertM(inv:FloatArray,io:Int,m:FloatArray,mo:Int):Boolean {
    val a=DoubleArray(16){m[mo+it].toDouble()};val r=DoubleArray(16)
    r[0]=a[5]*a[10]*a[15]-a[5]*a[11]*a[14]-a[9]*a[6]*a[15]+a[9]*a[7]*a[14]+a[13]*a[6]*a[11]-a[13]*a[7]*a[10]
    r[4]=-a[4]*a[10]*a[15]+a[4]*a[11]*a[14]+a[8]*a[6]*a[15]-a[8]*a[7]*a[14]-a[12]*a[6]*a[11]+a[12]*a[7]*a[10]
    r[8]=a[4]*a[9]*a[15]-a[4]*a[11]*a[13]-a[8]*a[5]*a[15]+a[8]*a[7]*a[13]+a[12]*a[5]*a[11]-a[12]*a[7]*a[9]
    r[12]=-a[4]*a[9]*a[14]+a[4]*a[10]*a[13]+a[8]*a[5]*a[14]-a[8]*a[6]*a[13]-a[12]*a[5]*a[10]+a[12]*a[6]*a[9]
    r[1]=-a[1]*a[10]*a[15]+a[1]*a[11]*a[14]+a[9]*a[2]*a[15]-a[9]*a[3]*a[14]-a[13]*a[2]*a[11]+a[13]*a[3]*a[10]
    r[5]=a[0]*a[10]*a[15]-a[0]*a[11]*a[14]-a[8]*a[2]*a[15]+a[8]*a[3]*a[14]+a[12]*a[2]*a[11]-a[12]*a[3]*a[10]
    r[9]=-a[0]*a[9]*a[15]+a[0]*a[11]*a[13]+a[8]*a[1]*a[15]-a[8]*a[3]*a[13]-a[12]*a[1]*a[11]+a[12]*a[3]*a[9]
    r[13]=a[0]*a[9]*a[14]-a[0]*a[10]*a[13]-a[8]*a[1]*a[14]+a[8]*a[2]*a[13]+a[12]*a[1]*a[10]-a[12]*a[2]*a[9]
    r[2]=a[1]*a[6]*a[15]-a[1]*a[7]*a[14]-a[5]*a[2]*a[15]+a[5]*a[3]*a[14]+a[13]*a[2]*a[7]-a[13]*a[3]*a[6]
    r[6]=-a[0]*a[6]*a[15]+a[0]*a[7]*a[14]+a[4]*a[2]*a[15]-a[4]*a[3]*a[14]-a[12]*a[2]*a[7]+a[12]*a[3]*a[6]
    r[10]=a[0]*a[5]*a[15]-a[0]*a[7]*a[13]-a[4]*a[1]*a[15]+a[4]*a[3]*a[13]+a[12]*a[1]*a[7]-a[12]*a[3]*a[5]
    r[14]=-a[0]*a[5]*a[14]+a[0]*a[6]*a[13]+a[4]*a[1]*a[14]-a[4]*a[2]*a[13]-a[12]*a[1]*a[6]+a[12]*a[2]*a[5]
    r[3]=-a[1]*a[6]*a[11]+a[1]*a[7]*a[10]+a[5]*a[2]*a[11]-a[5]*a[3]*a[10]-a[9]*a[2]*a[7]+a[9]*a[3]*a[6]
    r[7]=a[0]*a[6]*a[11]-a[0]*a[7]*a[10]-a[4]*a[2]*a[11]+a[4]*a[3]*a[10]+a[8]*a[2]*a[7]-a[8]*a[3]*a[6]
    r[11]=-a[0]*a[5]*a[11]+a[0]*a[7]*a[9]+a[4]*a[1]*a[11]-a[4]*a[3]*a[9]-a[8]*a[1]*a[7]+a[8]*a[3]*a[5]
    r[15]=a[0]*a[5]*a[10]-a[0]*a[6]*a[9]-a[4]*a[1]*a[10]+a[4]*a[2]*a[9]+a[8]*a[1]*a[6]-a[8]*a[2]*a[5]
    val det=a[0]*r[0]+a[1]*r[4]+a[2]*r[8]+a[3]*r[12]
    if(det==0.0)return false
    for(i in 0..15)inv[io+i]=(r[i]/det).toFloat()
    return true
  }
}
