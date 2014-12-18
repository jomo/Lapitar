package blue.lapis.tar;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;

import com.google.common.base.MoreObjects;

public class TarRenderer {
	private static final int HEAD_TEXTURE = 1;
	private static final int HEAD2_TEXTURE = 2;
	
	private static final int TORSO_TEXTURE = 3;
	private static final int TORSO2_TEXTURE = 4;
	
	private static final int LEFTARM_TEXTURE = 5;
	private static final int LEFTARM2_TEXTURE = 6;
	
	private static final int RIGHTARM_TEXTURE = 7;
	private static final int RIGHTARM2_TEXTURE = 8;
	
	private float angle;
	private float tilt;
	
	private final int width, height, finalWidth, finalHeight;
	private final int superSampling;
	
	private final boolean helmet;
	private final boolean shadow;
	private final boolean lighting;
	private final boolean portrait;

	private final boolean useWindow;
	
	private Pbuffer buffer;
	private FloatBuffer lightPosition;
	private FloatBuffer lightAmbient;
	
	private boolean initialized = false;

	public TarRenderer(float angle, float tilt, // Angles
						int width, int height, int superSampling, // Size
						boolean helmet, boolean shadow, boolean lighting, // Flags A
						boolean portrait, boolean useWindow) { // Flags B
		this.angle = angle;
		this.tilt = tilt;
		this.width = width * superSampling;
		this.finalWidth = width;
		this.height = height * superSampling;
		this.finalHeight = height;
		this.superSampling = superSampling;
		this.helmet = helmet;
		this.shadow = shadow && !portrait;
		this.lighting = lighting;
		this.useWindow = useWindow;
		this.portrait = portrait;
	}

	public BufferedImage render(BufferedImage skin) throws Exception {
		init();
		if (useWindow) {
			GL11.glClearColor(1, 1, 1, 1);
		}
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glPushMatrix();
		GL11.glCullFace(GL11.GL_BACK);
		lightPosition = BufferUtils.createFloatBuffer(4);
		lightPosition.mark();
		lightPosition.put(-4f);
		lightPosition.put(2f);
		lightPosition.put(1f);
		lightPosition.put(100f);
		lightPosition.reset();

		lightAmbient = BufferUtils.createFloatBuffer(4);
		lightAmbient.mark();
		lightAmbient.put(3.0f);
		lightAmbient.put(3.0f);
		lightAmbient.put(3.0f);
		lightAmbient.put(1f);
		lightAmbient.reset();
		upload(skin.getSubimage(0, 0, 32, 16), HEAD_TEXTURE);
		if (helmet) {
			upload(skin.getSubimage(32, 0, 32, 16), HEAD2_TEXTURE);
		}
		if (portrait) {
			upload(skin.getSubimage(16, 16, 24, 16), TORSO_TEXTURE);
			/*upload(body2, TORSO2_TEXTURE);
			upload(larm, LEFTARM_TEXTURE);
			upload(larm, LEFTARM2_TEXTURE);
			upload(rarm2, RIGHTARM_TEXTURE);
			upload(rarm2, RIGHTARM2_TEXTURE);*/
		}

		GL11.glEnable(GL11.GL_BLEND);
		if (shadow) {
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glPushMatrix();
				GL11.glTranslatef(0f, -0.95f, -0.45f);
				float scale = 1.02f;
				int count = 10;
				for (int i = 0; i < count; i++) {
					scale += 0.01f;
					GL11.glTranslatef(0f, -0.001f, 0f);
					GL11.glColor4f(0, 0, 0, (1-(i/(float)count))/2f);
					draw(scale, 0.01f, scale, TextureType.NONE);
				}
			GL11.glPopMatrix();
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		if (lighting) {
			GL11.glEnable(GL11.GL_LIGHTING);
			GL11.glEnable(GL11.GL_LIGHT0);
		}
		if (portrait) {
			GL11.glTranslatef(0,0,-8.5f);
		} else {
			GL11.glTranslatef(0,0,-4.5f);
		}
		GL11.glRotatef(tilt,1.0f,0f,0.0f);
		GL11.glRotatef(angle,0f,1.0f,0f);
		GL11.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, lightPosition);
		GL11.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, lightAmbient);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor3f(1, 1, 1);
		if (portrait) {
			GL11.glPushMatrix();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, TORSO_TEXTURE);
			GL11.glTranslatef(0f, -2.5f, 0f);
			draw(1.0f, 1.5f, 0.5f, TextureType.TORSO);
			GL11.glPopMatrix();
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, HEAD_TEXTURE);
		draw(1.0f, 1.0f, 1.0f, TextureType.HEAD);
		if (helmet) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, HEAD2_TEXTURE);
			draw(1.05f, 1.05f, 1.05f, TextureType.HEAD);
		}

		GL11.glPopMatrix();
		if (useWindow) {
			return null;
		} else {
			BufferedImage img = readPixels();
			BufferedImage out = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D gout = out.createGraphics();
			gout.drawImage(img.getScaledInstance(finalWidth, finalHeight, Image.SCALE_SMOOTH), 0, 0, null);
			gout.dispose();
			cleanup();
			return out;
		}
	}

	private BufferedImage readPixels() {
		GL11.glReadBuffer(GL11.GL_FRONT);
		ByteBuffer buf = BufferUtils.createByteBuffer(width * height * 4);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for(int x = 0; x < width; x++)  {
			for(int y = 0; y < height; y++) {
				int i = (x + (width * y)) * 4;
				int r = buf.get(i) & 0xFF;
				int g = buf.get(i + 1) & 0xFF;
				int b = buf.get(i + 2) & 0xFF;
				int a = buf.get(i + 3) & 0xFF;
				img.setRGB(x, height - (y + 1), (a << 24) | (r << 16) | (g << 8) | b);
			}
		}
		return img;
	}
	//private static final 
	private void draw(float xScale, float yScale, float zScale, TextureType type) throws Exception {
		GL11.glPushMatrix();
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glNormal3f(0, 0, -1f);
		float[] vertices = {
				// Front
				-1.0f, -1.0f,  1.0f,
				 1.0f, -1.0f,  1.0f,
				 1.0f,  1.0f,  1.0f,
				-1.0f,  1.0f,  1.0f,
				// Back
				-1.0f, -1.0f, -1.0f,
				 1.0f, -1.0f, -1.0f,
				 1.0f,  1.0f, -1.0f,
				-1.0f,  1.0f, -1.0f,
				// Top
				-1.0f,  1.0f,  1.0f,
				 1.0f,  1.0f,  1.0f,
				 1.0f,  1.0f, -1.0f,
				-1.0f,  1.0f, -1.0f,
				// Bottom
				-1.0f, -1.0f, -1.0f,
				 1.0f, -1.0f, -1.0f,
				 1.0f, -1.0f,  1.0f,
				-1.0f, -1.0f,  1.0f,
				// Left
				 1.0f, -1.0f,  1.0f,
				 1.0f, -1.0f, -1.0f,
				 1.0f,  1.0f, -1.0f,
				 1.0f,  1.0f,  1.0f,
				// Right
				-1.0f, -1.0f, -1.0f,
				-1.0f, -1.0f,  1.0f,
				-1.0f,  1.0f,  1.0f,
				-1.0f,  1.0f, -1.0f
			};
		for (int i = 0; i < vertices.length/3; i++) {
			int idx = i*3;
			
			float vX = vertices[idx] * xScale;
			float vY = vertices[idx+1] * yScale;
			float vZ = vertices[idx+2] * zScale;
			
			float u = type.u[i];
			float v = type.v[i];
			
			GL11.glTexCoord2f(u, v);
			GL11.glVertex3f(vX, vY, vZ);
		}
		GL11.glEnd();
		GL11.glPopMatrix();
	}
	private static int upload(BufferedImage img, int id) {
		int[] pixels = new int[img.getWidth()*img.getHeight()];
		img.getRGB(0, 0, img.getWidth(), img.getHeight(), pixels, 0, img.getWidth());
		ByteBuffer buf = BufferUtils.createByteBuffer(img.getWidth() * img.getHeight() * 4);
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				int pixel = pixels[y*img.getWidth()+x];
				buf.put((byte) ((pixel >> 16) & 0xFF));
				buf.put((byte) ((pixel >> 8) & 0xFF));
				buf.put((byte) (pixel & 0xFF));
				buf.put((byte) ((pixel >> 24) & 0xFF));
			}
		}
		buf.flip();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, img.getWidth(), img.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		return id;
	}

	private void createBuffer() throws Exception {
		buffer = new Pbuffer(width, height, new PixelFormat(), null, null);
		buffer.makeCurrent();
		if (buffer.isBufferLost()) {
			cleanup();
			System.err.println("Failed to set up PBuffer.");
			System.exit(2);
		}
	}
	
	private void createWindow() {
		try {
			Display.setFullscreen(false);
			Display.setTitle("Lapitar");
			Display.setDisplayMode(new DisplayMode(width, height));
			Display.create();
		} catch (LWJGLException e) {
			cleanup();
			System.err.println("Failed to set up Display.");
			System.exit(2);
		}
	}
	
	private void init() throws Exception {
		if (initialized) return;
		if (useWindow) {
			createWindow();
		} else {
			createBuffer();
		}
		initGL();
		initialized = true;
	}

	private void initGL() {
		GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		GL11.glClearDepth(1.0);
		GL11.glShadeModel(GL11.GL_SMOOTH);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthFunc(GL11.GL_LEQUAL);

		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();

		GLU.gluPerspective(
				45.0f,
				(float) width / (float) height,
				0.1f,
				100.0f);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);

		GL11.glHint(GL11.GL_PERSPECTIVE_CORRECTION_HINT, GL11.GL_NICEST);
	}

	private void cleanup() {
		if (useWindow) {
			Display.destroy();
		} else {
			buffer.destroy();
		}
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("angle", angle)
				.add("width", finalWidth)
				.add("height", finalHeight)
				.add("superSampling", superSampling)
				.add("helmet", helmet)
				.add("shadow", shadow)
				.add("lighting", lighting)
				.toString();
	}

	public void modifyAngle(float angle, float tilt) {
		this.angle += angle;
		this.tilt += tilt;
	}

}
