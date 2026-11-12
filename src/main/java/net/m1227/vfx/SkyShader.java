package net.m1227.vfx;

import net.m1227.Mod1227;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Procedural GLSL sky layer (GLSL 1.20, works on OpenGL 2.1). A sphere
 * around the camera is shaded per pixel from the view direction with 3D
 * value noise / fbm, so nebulae, aurora veils and clouds have real detail
 * instead of a few big sprites. Drawn additively over the sky gradient;
 * the geometric layers (stars, lines, bodies) still draw on top.
 * If the driver cannot compile it, the layer turns itself off.
 */
public final class SkyShader {
    public static final String[] STYLES = {"Off", "Nebula", "Aurora Veil", "Cosmic Ocean", "Plasma Storm", "Warp Stars",
            "Sunset Clouds", "Volumetric Clouds", "Storm Clouds"};

    /** Styles drawn with normal alpha blending (they can darken the sky) instead of additively. */
    public static boolean opaque(int style) {
        return style >= 7;
    }

    private static final String VERT = "#version 120\n"
            + "varying vec3 vDir;\n"
            + "void main(){ vDir = gl_Vertex.xyz; gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex; }\n";

    private static final String FRAG = "#version 120\n"
            + "varying vec3 vDir;\n"
            + "uniform float uTime; uniform int uStyle; uniform vec3 uA; uniform vec3 uB; uniform vec3 uC; uniform float uAlpha;\n"
            + "float hash(vec3 p){ p = fract(p*0.3183099+0.1); p *= 17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }\n"
            + "float noise(vec3 x){ vec3 i=floor(x); vec3 f=fract(x); f=f*f*(3.0-2.0*f);\n"
            + " return mix(mix(mix(hash(i),hash(i+vec3(1.0,0.0,0.0)),f.x), mix(hash(i+vec3(0.0,1.0,0.0)),hash(i+vec3(1.0,1.0,0.0)),f.x),f.y),\n"
            + "            mix(mix(hash(i+vec3(0.0,0.0,1.0)),hash(i+vec3(1.0,0.0,1.0)),f.x), mix(hash(i+vec3(0.0,1.0,1.0)),hash(i+vec3(1.0,1.0,1.0)),f.x),f.y), f.z); }\n"
            + "float fbm(vec3 p){ float v=0.0; float a=0.5; for(int i=0;i<5;i++){ v+=a*noise(p); p=p*2.03+vec3(1.7,9.2,3.1); a*=0.5; } return v; }\n"
            + "float stars(vec3 d, float scale, float th){ vec3 p=d*scale; vec3 i=floor(p); float h=hash(i); vec3 f=fract(p)-0.5;\n"
            + " return step(th,h)*smoothstep(0.12,0.0,length(f))*(0.55+0.45*sin(uTime*2.0+h*50.0)); }\n"
            + "void main(){\n"
            + " vec3 d = normalize(vDir); float el = d.y; vec3 col = vec3(0.0);\n"
            + " if (uStyle==1) {\n"
            + "  vec3 p = d*2.2+vec3(0.0,0.0,uTime*0.02); float n = fbm(p+fbm(p*1.7)*1.3); float m = smoothstep(0.38,0.85,n);\n"
            + "  col = mix(uA,uB,smoothstep(0.3,0.9,fbm(p*1.3+4.0)))*m*1.3 + uC*pow(m,4.0)*0.9;\n"
            + "  col += vec3(1.0)*stars(d,180.0,0.985);\n"
            + " } else if (uStyle==2) {\n"
            + "  float h = max(el,0.0); float ang = atan(d.z,d.x); float band = 0.0;\n"
            + "  for (int i=0;i<3;i++) { float fi=float(i);\n"
            + "   float y0 = 0.16+0.12*fi + 0.05*sin(ang*3.0+uTime*0.3+fi*2.0) + 0.06*(fbm(vec3(cos(ang)*2.0+fi, sin(ang)*2.0, uTime*0.08))-0.5);\n"
            + "   float c = smoothstep(y0-0.025,y0,h)*exp(-max(h-y0,0.0)*7.0);\n"
            + "   float rays = 0.55+0.45*noise(vec3(cos(ang)*30.0, sin(ang)*30.0+fi*7.0, uTime*0.6));\n"
            + "   band += c*rays*(0.8-0.18*fi); }\n"
            + "  col = mix(uA,uB,clamp(h*2.5,0.0,1.0))*band*1.5 + vec3(0.9)*stars(d,160.0,0.988)*smoothstep(0.0,0.2,el);\n"
            + " } else if (uStyle==3) {\n"
            + "  vec3 p = d*1.6; float t = uTime*0.05;\n"
            + "  float n = fbm(vec3(p.x+sin(t+p.y*2.0)*0.5, p.y, p.z+cos(t+p.x*2.0)*0.5)*2.0);\n"
            + "  float w = sin(n*12.0-uTime*0.4)*0.5+0.5;\n"
            + "  col = mix(uA,uB,n)*(0.4+0.6*w)*smoothstep(0.25,0.8,n) + uC*pow(w,8.0)*0.6*n + vec3(1.0)*stars(d,200.0,0.99);\n"
            + " } else if (uStyle==4) {\n"
            + "  vec3 p = d*3.0; float n = fbm(p+vec3(uTime*0.07)); float r = abs(sin((n+uTime*0.02)*18.0)); float l = pow(1.0-r,6.0);\n"
            + "  col = mix(uA,uB,n)*n*0.6 + uC*l*1.2*smoothstep(0.3,0.6,n);\n"
            + " } else if (uStyle==5) {\n"
            + "  float s = 0.0; for (int i=0;i<3;i++) { float fi=float(i); s += stars(d+vec3(0.0,0.0,fi*0.37),90.0+fi*80.0,0.975-fi*0.004)*(1.0-fi*0.25); }\n"
            + "  float g = fbm(d*4.0+uTime*0.01); col = uA*0.18*g + mix(uB,uC,g)*s*1.4;\n"
            + " } else if (uStyle==6) {\n"
            + "  float k = 1.0/(abs(el)+0.22); vec3 p = vec3(d.x*k, 0.0, d.z*k)*1.2 + vec3(uTime*0.02,0.0,uTime*0.01);\n"
            + "  float c = smoothstep(0.45,0.8,fbm(p*1.5)); float glow = exp(-abs(el)*5.0);\n"
            + "  col = uC*glow*0.45 + mix(uA,uB,glow)*c*step(0.0,el)*0.9;\n"
            + " } else if (uStyle==7 || uStyle==8) {\n"
            // cloud deck projected on a plane above the camera; domain-warped fbm for soft, billowy shapes
            + "  bool storm = uStyle==8; float h = max(el, 0.0);\n"
            + "  float k = 1.0/(h+0.06); vec2 q = d.xz*k*0.55 + vec2(uTime*0.012, uTime*0.006)*(storm?2.2:1.0);\n"
            + "  vec3 p = vec3(q.x, uTime*0.004, q.y);\n"
            + "  float w = fbm(p*0.9+vec3(3.1,0.0,1.7));\n"
            + "  float n = fbm(p*1.4 + w*1.6);\n"
            + "  float cov = smoothstep(storm?0.28:0.42, storm?0.62:0.78, n);\n"
            + "  float shade = fbm(p*2.6 + w*2.0 + vec3(0.0,5.0,0.0));\n"
            + "  vec3 lit = mix(uB, uA, smoothstep(0.25, 0.85, shade));\n"
            + "  if (storm) lit *= 0.75 + 0.25*smoothstep(0.4,0.8,shade);\n"
            + "  float horizon = smoothstep(0.0, 0.12, h);\n"
            + "  vec3 base = mix(uC, lit, horizon);\n"
            + "  float a = mix(storm?0.85:0.55, 1.0, cov) * (storm?1.0:cov*0.9+0.1);\n"
            + "  a *= mix(0.65, 1.0, horizon) * step(-0.02, el);\n"
            + "  if (storm) { float f = step(0.995, hash(vec3(floor(uTime*6.0)))); base += vec3(0.6,0.65,0.8)*f*smoothstep(0.5,0.9,n); }\n"
            + "  gl_FragColor = vec4(base, a*uAlpha); return;\n"
            + " }\n"
            + " gl_FragColor = vec4(col, uAlpha);\n"
            + "}\n";

    private static int program = -1;
    private static boolean failed;
    private static int uTime, uStyle, uA, uB, uC, uAlpha;

    private SkyShader() {}

    public static boolean available() {
        return !failed && OpenGlHelper.shadersSupported;
    }

    private static boolean ensure() {
        if (program > 0) return true;
        if (!available()) return false;
        try {
            int vs = compile(GL20.GL_VERTEX_SHADER, VERT), fs = compile(GL20.GL_FRAGMENT_SHADER, FRAG);
            if (vs == 0 || fs == 0) {
                failed = true;
                return false;
            }
            int p = GL20.glCreateProgram();
            GL20.glAttachShader(p, vs);
            GL20.glAttachShader(p, fs);
            GL20.glLinkProgram(p);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                Mod1227.LOGGER.warn("1227 sky shader link failed: " + GL20.glGetProgramInfoLog(p, 4096));
                failed = true;
                return false;
            }
            program = p;
            uTime = GL20.glGetUniformLocation(p, "uTime");
            uStyle = GL20.glGetUniformLocation(p, "uStyle");
            uA = GL20.glGetUniformLocation(p, "uA");
            uB = GL20.glGetUniformLocation(p, "uB");
            uC = GL20.glGetUniformLocation(p, "uC");
            uAlpha = GL20.glGetUniformLocation(p, "uAlpha");
            return true;
        } catch (Throwable t) {
            Mod1227.LOGGER.warn("1227 sky shader unavailable", t);
            failed = true;
            return false;
        }
    }

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            Mod1227.LOGGER.warn("1227 sky shader compile failed: " + GL20.glGetShaderInfoLog(s, 4096));
            return 0;
        }
        return s;
    }

    /** Draws the layer. style = index in STYLES (0 = off). Caller sets blending (additive). */
    public static void draw(int style, double time, int a, int b, int c, float alpha) {
        if (style <= 0 || alpha <= 0.001f || !ensure()) return;
        GL20.glUseProgram(program);
        GL20.glUniform1f(uTime, (float) (time % 3600.0));
        GL20.glUniform1i(uStyle, style);
        GL20.glUniform3f(uA, ((a >> 16) & 255) / 255f, ((a >> 8) & 255) / 255f, (a & 255) / 255f);
        GL20.glUniform3f(uB, ((b >> 16) & 255) / 255f, ((b >> 8) & 255) / 255f, (b & 255) / 255f);
        GL20.glUniform3f(uC, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
        GL20.glUniform1f(uAlpha, alpha);
        sphere(80f, 32, 20);
        GL20.glUseProgram(0);
    }

    private static void sphere(float r, int seg, int rings) {
        GL11.glBegin(GL11.GL_QUADS);
        for (int j = 0; j < rings; j++) {
            double e0 = -Math.PI / 2 + Math.PI * j / rings, e1 = -Math.PI / 2 + Math.PI * (j + 1) / rings;
            for (int i = 0; i < seg; i++) {
                double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
                v(r, e0, a0);
                v(r, e1, a0);
                v(r, e1, a1);
                v(r, e0, a1);
            }
        }
        GL11.glEnd();
    }

    private static void v(float r, double el, double az) {
        double c = Math.cos(el);
        GL11.glVertex3f((float) (c * Math.cos(az) * r), (float) (Math.sin(el) * r), (float) (c * Math.sin(az) * r));
    }
}
