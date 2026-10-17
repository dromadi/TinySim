package android.opengl
import java.nio.Buffer
import java.nio.FloatBuffer
/** Records every call as a JSON op so WebGL can replay the exact frame. */
object GLES20 {
  const val GL_DEPTH_TEST=0x0B71; const val GL_LEQUAL=0x0203; const val GL_CULL_FACE=0x0B44; const val GL_BACK=0x0405; const val GL_FRONT=0x0404
  const val GL_BLEND=0x0BE2; const val GL_SRC_ALPHA=0x0302; const val GL_ONE_MINUS_SRC_ALPHA=0x0303; const val GL_SCISSOR_TEST=0x0C11
  const val GL_COLOR_BUFFER_BIT=0x4000; const val GL_DEPTH_BUFFER_BIT=0x100; const val GL_NO_ERROR=0; const val GL_RENDERER=0x1F01
  const val GL_ARRAY_BUFFER=0x8892; const val GL_STATIC_DRAW=0x88E4; const val GL_FLOAT=0x1406; const val GL_TRIANGLES=4
  const val GL_VERTEX_SHADER=0x8B31; const val GL_FRAGMENT_SHADER=0x8B30; const val GL_COMPILE_STATUS=0x8B81; const val GL_LINK_STATUS=0x8B82
  const val GL_HIGH_FLOAT=0x8DF2; const val GL_MAX_TEXTURE_SIZE=0x0D33; const val GL_TEXTURE_2D=0x0DE1; const val GL_RGBA=0x1908; const val GL_UNSIGNED_BYTE=0x1401
  const val GL_TEXTURE_MIN_FILTER=0x2801; const val GL_TEXTURE_MAG_FILTER=0x2800; const val GL_NEAREST=0x2600; const val GL_TEXTURE_WRAP_S=0x2802; const val GL_TEXTURE_WRAP_T=0x2803; const val GL_CLAMP_TO_EDGE=0x812F
  const val GL_RENDERBUFFER=0x8D41; const val GL_DEPTH_COMPONENT16=0x81A5; const val GL_FRAMEBUFFER=0x8D40; const val GL_COLOR_ATTACHMENT0=0x8CE0; const val GL_DEPTH_ATTACHMENT=0x8D00; const val GL_FRAMEBUFFER_COMPLETE=0x8CD5
  const val GL_TEXTURE0=0x84C0
  val ops=StringBuilder(); private var next=1
  private fun op(name:String,vararg a:Any?){ if(ops.isNotEmpty())ops.append(",\n"); ops.append("[\"").append(name).append('"'); for(x in a){ ops.append(','); ops.append(when(x){ null->"null"; is String->"\""+x.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\""; is FloatArray->x.joinToString(",","[","]"); is Boolean->x.toString(); else->x.toString() }) }; ops.append(']') }
  private fun id(name:String,vararg a:Any?):Int{ val i=next++; op(name,i,*a); return i }
  @JvmStatic fun glCreateShader(t:Int)=id("createShader",t)
  @JvmStatic fun glShaderSource(s:Int,src:String)=op("shaderSource",s,src)
  @JvmStatic fun glCompileShader(s:Int)=op("compileShader",s)
  @JvmStatic fun glGetShaderiv(s:Int,p:Int,r:IntArray,o:Int){ r[o]=1 }
  @JvmStatic fun glGetShaderInfoLog(s:Int)=""
  @JvmStatic fun glCreateProgram()=id("createProgram")
  @JvmStatic fun glAttachShader(p:Int,s:Int)=op("attachShader",p,s)
  @JvmStatic fun glLinkProgram(p:Int)=op("linkProgram",p)
  @JvmStatic fun glGetProgramiv(p:Int,q:Int,r:IntArray,o:Int){ r[o]=1 }
  @JvmStatic fun glGetProgramInfoLog(p:Int)=""
  @JvmStatic fun glDeleteShader(s:Int){}
  @JvmStatic fun glGetAttribLocation(p:Int,n:String)=id("attrib",p,n)
  @JvmStatic fun glGetUniformLocation(p:Int,n:String)=id("uniformLoc",p,n)
  @JvmStatic fun glGenBuffers(n:Int,r:IntArray,o:Int){ r[o]=id("createBuffer") }
  @JvmStatic fun glBindBuffer(t:Int,b:Int)=op("bindBuffer",b)
  @JvmStatic fun glBufferData(t:Int,size:Int,data:Buffer,u:Int){ val fb=data as FloatBuffer; val a=FloatArray(fb.remaining()); fb.duplicate().get(a); op("bufferData",a) }
  @JvmStatic fun glVertexAttribPointer(i:Int,s:Int,t:Int,n:Boolean,stride:Int,off:Int)=op("attribPointer",i,s,stride,off)
  @JvmStatic fun glEnableVertexAttribArray(i:Int)=op("enableAttrib",i)
  @JvmStatic fun glDisableVertexAttribArray(i:Int)=op("disableAttrib",i)
  @JvmStatic fun glDrawArrays(m:Int,f:Int,c:Int)=op("drawArrays",f,c)
  @JvmStatic fun glEnable(c:Int)=op("enable",c)
  @JvmStatic fun glDisable(c:Int)=op("disable",c)
  @JvmStatic fun glDepthFunc(f:Int)=op("depthFunc",f)
  @JvmStatic fun glCullFace(f:Int)=op("cullFace",f)
  @JvmStatic fun glBlendFunc(a:Int,b:Int)=op("blendFunc",a,b)
  @JvmStatic fun glDepthMask(b:Boolean)=op("depthMask",b)
  @JvmStatic fun glClearColor(r:Float,g:Float,b:Float,a:Float)=op("clearColor",r,g,b,a)
  @JvmStatic fun glClear(m:Int)=op("clear",m)
  @JvmStatic fun glViewport(x:Int,y:Int,w:Int,h:Int)=op("viewport",x,y,w,h)
  @JvmStatic fun glScissor(x:Int,y:Int,w:Int,h:Int)=op("scissor",x,y,w,h)
  @JvmStatic fun glUseProgram(p:Int)=op("useProgram",p)
  @JvmStatic fun glUniformMatrix4fv(l:Int,c:Int,t:Boolean,v:FloatArray,o:Int)=op("m4",l,v.copyOfRange(o,o+16))
  @JvmStatic fun glUniformMatrix3fv(l:Int,c:Int,t:Boolean,v:FloatArray,o:Int)=op("m3",l,v.copyOfRange(o,o+9))
  @JvmStatic fun glUniform4fv(l:Int,c:Int,v:FloatArray,o:Int)=op("u4",l,v[o],v[o+1],v[o+2],v[o+3])
  @JvmStatic fun glUniform4f(l:Int,a:Float,b:Float,c:Float,d:Float)=op("u4",l,a,b,c,d)
  @JvmStatic fun glUniform3f(l:Int,a:Float,b:Float,c:Float)=op("u3",l,a,b,c)
  @JvmStatic fun glUniform2f(l:Int,a:Float,b:Float)=op("u2",l,a,b)
  @JvmStatic fun glUniform1f(l:Int,a:Float)=op("u1",l,a)
  @JvmStatic fun glUniform1i(l:Int,a:Int)=op("u1i",l,a)
  @JvmStatic fun glGetError()=0
  @JvmStatic fun glGetString(n:Int)="harness"
  @JvmStatic fun glGetShaderPrecisionFormat(s:Int,p:Int,r:IntArray,ro:Int,pr:IntArray,po:Int){ pr[po]=if(System.getProperty("tinysim.preview.noHighp")=="true")0 else 23 }
  @JvmStatic fun glGetIntegerv(p:Int,r:IntArray,o:Int){ r[o]=4096 }
  @JvmStatic fun glGenTextures(n:Int,r:IntArray,o:Int){ r[o]=id("createTexture") }
  @JvmStatic fun glBindTexture(t:Int,x:Int)=op("bindTexture",x)
  @JvmStatic fun glTexImage2D(t:Int,l:Int,i:Int,w:Int,h:Int,b:Int,f:Int,ty:Int,d:Buffer?)=op("texImage",w,h)
  @JvmStatic fun glTexParameteri(t:Int,p:Int,v:Int)=op("texParam",p,v)
  @JvmStatic fun glGenRenderbuffers(n:Int,r:IntArray,o:Int){ r[o]=id("createRenderbuffer") }
  @JvmStatic fun glBindRenderbuffer(t:Int,x:Int)=op("bindRenderbuffer",x)
  @JvmStatic fun glRenderbufferStorage(t:Int,f:Int,w:Int,h:Int)=op("renderbufferStorage",w,h)
  @JvmStatic fun glGenFramebuffers(n:Int,r:IntArray,o:Int){ r[o]=id("createFramebuffer") }
  @JvmStatic fun glBindFramebuffer(t:Int,x:Int)=op("bindFramebuffer",x)
  @JvmStatic fun glFramebufferTexture2D(t:Int,a:Int,tt:Int,x:Int,l:Int)=op("fbTexture",x)
  @JvmStatic fun glFramebufferRenderbuffer(t:Int,a:Int,rt:Int,x:Int)=op("fbRenderbuffer",x)
  @JvmStatic fun glCheckFramebufferStatus(t:Int)=GL_FRAMEBUFFER_COMPLETE
  @JvmStatic fun glActiveTexture(t:Int)=op("activeTexture",t)
}
