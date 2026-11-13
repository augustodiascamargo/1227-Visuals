package net.m1227.vfx;

import net.m1227.Mod1227;
import net.m1227.core.Setting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Animated materials for cosmetics (china hat, halo, wings, aura shell, rings): plasma, aurora,
 * galaxy, fire, electric, hologram, rainbow, iridescent, lava, frost and void. One small GLSL 120
 * program; meshes pass texture coordinates (u around, v from centre/root to edge) and normals.
 * If the driver cannot compile it, cosmetics fall back to their plain gradient colours.
 */
public final class MaterialShader {
    public static final String[] MATERIALS = {"Gradient", "Plasma", "Aurora", "Galaxy", "Fire", "Electric", "Hologram",
            "Rainbow", "Iridescent", "Lava", "Frost", "Void"};

    private static final String VERT = "#version 120\n"
            + "uniform vec3 uOrigin;\n"
            + "varying vec2 vUv; varying vec3 vPos; varying vec4 vCol; varying vec3 vN; varying vec3 vV;\n"
            + "void main(){ vUv = gl_MultiTexCoord0.xy; vPos = gl_Vertex.xyz - uOrigin; vCol = gl_Color;\n"
            + " vN = gl_NormalMatrix * gl_Normal; vV = -(gl_ModelViewMatrix * gl_Vertex).xyz;\n"
            + " gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex; }\n";

    static final String FRAG_BODY =
            "varying vec2 vUv; varying vec3 vPos; varying vec4 vCol; varying vec3 vN; varying vec3 vV;\n"
            + "uniform float uTime; uniform int uStyle; uniform vec3 uA; uniform vec3 uB; uniform float uScale; uniform float uFres; uniform float uEdge;\n"
            + "float hash(vec3 p){ p = fract(p*0.3183099+0.1); p *= 17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }\n"
            + "float noise(vec3 x){ vec3 i=floor(x); vec3 f=fract(x); f=f*f*(3.0-2.0*f);\n"
            + " return mix(mix(mix(hash(i),hash(i+vec3(1.0,0.0,0.0)),f.x), mix(hash(i+vec3(0.0,1.0,0.0)),hash(i+vec3(1.0,1.0,0.0)),f.x),f.y),\n"
            + "            mix(mix(hash(i+vec3(0.0,0.0,1.0)),hash(i+vec3(1.0,0.0,1.0)),f.x), mix(hash(i+vec3(0.0,1.0,1.0)),hash(i+vec3(1.0,1.0,1.0)),f.x),f.y), f.z); }\n"
            + "float fbm(vec3 p){ float s=0.0; float a=0.5; for(int i=0;i<4;i++){ s+=a*noise(p); p=p*2.03+vec3(1.7,9.2,3.1); a*=0.5; } return s; }\n"
            + "vec3 hsv(float h){ return clamp(abs(mod(h*6.0+vec3(0.0,4.0,2.0),6.0)-3.0)-1.0,0.0,1.0); }\n"
            + "void main(){\n"
            + " vec3 p = vPos*uScale; float t = uTime; vec3 c = uA; float a = 1.0;\n"
            + " vec3 n = normalize(vN + vec3(0.0,0.0,1e-4)); vec3 v = normalize(vV + vec3(0.0,0.0,1e-4));\n"
            + " float facing = abs(dot(n, v));\n"
            + " if(uStyle==1){\n" // plasma
            + "  float q = sin(p.x*3.0+t*1.7)+sin(p.y*4.0-t*1.3)+sin((p.x+p.z)*3.0+t)+sin(length(p.xz)*5.0-t*2.0);\n"
            + "  q = q*0.25+0.5; c = mix(uA,uB,q) + vec3(1.0)*pow(abs(sin(q*6.2831+t)),12.0)*0.6; a = 0.7+0.3*q;\n"
            + " } else if(uStyle==2){\n" // aurora
            + "  float w = fbm(vec3(p.x*1.5, p.z*1.5, t*0.25));\n"
            + "  float band = sin(vUv.x*6.2831*3.0 + w*6.0 + t*0.8)*0.5+0.5;\n"
            + "  float cur = pow(band,3.0)*(0.4+0.6*fbm(p*3.0+vec3(0.0,-t*0.6,0.0)));\n"
            + "  c = mix(uA,uB, smoothstep(0.0,1.0,vUv.y+w*0.4))*(0.35+1.7*cur); a = 0.35+0.65*cur;\n"
            + " } else if(uStyle==3){\n" // galaxy
            + "  float q = fbm(p*2.0+vec3(0.0,0.0,t*0.1));\n"
            + "  c = mix(vec3(0.02,0.0,0.06), mix(uA,uB,q), clamp(q*q*1.6,0.0,1.0));\n"
            + "  float s = hash(floor(p*40.0)); c += vec3(step(0.985,s)*(0.5+0.5*sin(t*5.0+s*80.0))); a = 0.92;\n"
            + " } else if(uStyle==4){\n" // fire
            + "  float q = fbm(vec3(p.x*3.0, p.y*3.0-t*2.2, p.z*3.0)+vec3(0.0,0.0,vUv.y*2.0-t*1.4));\n"
            + "  float h = clamp(q*1.7 - vUv.y*0.55, 0.0, 1.0);\n"
            + "  c = mix(uB, uA, h); c = mix(c, vec3(1.0,0.95,0.7), smoothstep(0.75,1.0,h)); a = smoothstep(0.12,0.55,h);\n"
            + " } else if(uStyle==5){\n" // electric
            + "  float q = fbm(p*0.9+vec3(t*0.7,t*0.4,-t*0.5)); float r = pow(1.0-abs(q*2.0-1.0),10.0);\n"
            + "  float q2 = fbm(p*1.6-vec3(t*0.9)); float r2 = pow(1.0-abs(q2*2.0-1.0),16.0);\n"
            + "  c = uA*0.3 + uB*r*1.8 + vec3(1.0)*r2*0.9; a = 0.2+0.8*max(r,r2);\n"
            + " } else if(uStyle==6){\n" // hologram
            + "  float scan = 0.6+0.4*sin(vPos.y*120.0 - t*8.0); float fl = 0.85+0.15*sin(t*37.0)*sin(t*13.0);\n"
            + "  c = mix(uA,uB,vUv.y)*scan*fl*1.2; a = (0.35+0.5*(1.0-facing))*scan*fl;\n"
            + " } else if(uStyle==7){\n" // rainbow
            + "  c = hsv(fract(vUv.x + vUv.y*0.3 - t*0.15))*1.1; a = 0.85;\n"
            + " } else if(uStyle==8){\n" // iridescent
            + "  float q = fbm(p*1.5+vec3(t*0.2)); c = hsv(fract(q*1.5 + facing*0.6 + t*0.05))*0.6 + mix(uA,uB,q)*0.5; a = 0.85;\n"
            + " } else if(uStyle==9){\n" // lava
            + "  float q = fbm(p*2.0+vec3(0.0,t*0.15,0.0)); float cr = smoothstep(0.46,0.54,q);\n"
            + "  float glow = 1.0-smoothstep(0.0,0.08,abs(q-0.5));\n"
            + "  c = mix(uA*1.3+vec3(0.3,0.15,0.0), uB*0.12, cr) + uA*glow*0.8; a = 0.95;\n"
            + " } else if(uStyle==10){\n" // frost
            + "  float q = fbm(p*4.0); float sp = pow(hash(floor(p*30.0)+vec3(floor(t*2.0))),30.0);\n"
            + "  c = mix(uA*0.8, vec3(1.0), q*q*0.7) + vec3(sp); a = 0.45+0.35*q;\n"
            + " } else {\n" // void
            + "  float q = fbm(p*2.0 - vec3(0.0,t*0.3,0.0)); c = uA*0.05 + uB*pow(q,3.0)*2.5; a = 0.92;\n"
            + " }\n"
            + " if(uFres>0.0){ float f = pow(1.0-facing,2.0); a *= mix(1.0, 0.12+1.3*f, uFres); c += uB*f*0.5*uFres; }\n"
            + " if(uEdge>0.0){ float e = smoothstep(1.0-uEdge, 1.0, vUv.y); c = mix(c, c*0.5+vec3(0.5)+uB*0.5, e); a = max(a, e*0.9); }\n"
            + " gl_FragColor = vec4(c*vCol.rgb, clamp(a,0.0,1.0)*vCol.a);\n"
            + "}\n";

    private static final String FRAG = "#version 120\n" + FRAG_BODY;

    private static int program = -1;
    private static boolean failed;
    private static int uTime, uStyle, uA, uB, uScale, uFres, uEdge, uOrigin;
    private static int active;

    private MaterialShader() {}

    /** Index of a material setting's value in MATERIALS (0 = plain gradient). */
    public static int index(Setting.Mode m) {
        String v = m.get();
        for (int i = 0; i < MATERIALS.length; i++) if (MATERIALS[i].equals(v)) return i;
        return 0;
    }

    public static boolean available() {
        return !failed && OpenGlHelper.shadersSupported;
    }

    /** Fire, Electric and Hologram look best added on top of the scene (glow); the rest blend normally. */
    public static boolean additive(int style) {
        return style == 4 || style == 5 || style == 6;
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
                Mod1227.LOGGER.warn("1227 material shader link failed: " + GL20.glGetProgramInfoLog(p, 4096));
                failed = true;
                return false;
            }
            program = p;
            uTime = GL20.glGetUniformLocation(p, "uTime");
            uStyle = GL20.glGetUniformLocation(p, "uStyle");
            uA = GL20.glGetUniformLocation(p, "uA");
            uB = GL20.glGetUniformLocation(p, "uB");
            uScale = GL20.glGetUniformLocation(p, "uScale");
            uFres = GL20.glGetUniformLocation(p, "uFres");
            uEdge = GL20.glGetUniformLocation(p, "uEdge");
            uOrigin = GL20.glGetUniformLocation(p, "uOrigin");
            return true;
        } catch (Throwable t) {
            Mod1227.LOGGER.warn("1227 material shader unavailable", t);
            failed = true;
            return false;
        }
    }

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            Mod1227.LOGGER.warn("1227 material shader compile failed: " + GL20.glGetShaderInfoLog(s, 4096));
            return 0;
        }
        return s;
    }

    /**
     * Starts drawing with a material. Returns false (and changes nothing) for "Gradient" or when
     * shaders are unavailable, so the caller keeps its normal colours. scale = noise frequency per
     * block, fresnel 0..1 = glow at grazing angles, edge 0..1 = bright rim near v = 1.
     */
    public static boolean begin(int style, double time, int a, int b, float scale, float fresnel, float edge) {
        if (style <= 0 || !ensure()) return false;
        GL20.glUseProgram(program);
        GL20.glUniform1f(uTime, (float) (time % 3600.0));
        GL20.glUniform1i(uStyle, style);
        GL20.glUniform3f(uA, ((a >> 16) & 255) / 255f, ((a >> 8) & 255) / 255f, (a & 255) / 255f);
        GL20.glUniform3f(uB, ((b >> 16) & 255) / 255f, ((b >> 8) & 255) / 255f, (b & 255) / 255f);
        GL20.glUniform1f(uScale, scale);
        GL20.glUniform1f(uFres, fresnel);
        GL20.glUniform1f(uEdge, edge);
        GL20.glUniform3f(uOrigin, 0f, 0f, 0f);
        if (additive(style)) GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        active = style;
        return true;
    }

    /** Point the noise is anchored to (so the pattern moves with the cosmetic, not the camera). */
    public static void origin(double x, double y, double z) {
        if (active > 0) GL20.glUniform3f(uOrigin, (float) x, (float) y, (float) z);
    }

    public static void end() {
        if (active <= 0) return;
        GL20.glUseProgram(0);
        if (additive(active)) GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        active = 0;
    }
}
