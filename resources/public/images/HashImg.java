package mx.com.interware.robot.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import com.sun.image.codec.jpeg.ImageFormatException;
import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGImageEncoder;

import mx.com.interware.crypt.Password;

public class HashImg {

	private int tokenLength;
	private boolean numeric;

	private static BufferedImage tmp = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
	private static Graphics2D graphics = tmp.createGraphics();

	private static Font font[];

	static {
		useDefaultFonts();
	}

	public static void useDefaultFonts() {
		font = new Font[10];
		font[0] = new Font("Courier", Font.PLAIN, 15);
		font[1] = new Font("Courier", Font.PLAIN, 25);
		font[2] = new Font("Courier", Font.ITALIC, 18);
		font[3] = new Font("Helvetica", Font.ITALIC, 18);
		font[4] = new Font("Helvetica", Font.BOLD, 14);
		font[5] = new Font("Helvetica", Font.PLAIN, 16);
		font[6] = new Font("Courier", Font.BOLD, 15);
		font[7] = new Font("Times", Font.PLAIN, 15);
		font[8] = new Font("Times", Font.BOLD, 17);
		font[9] = new Font("Times", Font.PLAIN, 18);
	}

	public int getMaxImgWidth() {
		int maxWidth = 0;
		for (int i = 0; i < font.length; i++) {
			int width = graphics.getFontMetrics(font[i]).charWidth('W');
			if (width > maxWidth) {
				maxWidth = width;
			}
		}
		return maxWidth * tokenLength;
	}

	public int getMaxImgHeight() {
		int maxHeight = 0;
		for (int i = 0; i < font.length; i++) {
			int height = graphics.getFontMetrics(font[i]).getHeight();
			if (height > maxHeight) {
				maxHeight = height;
			}
		}
		return maxHeight;
	}

	public String getHashFor(String key) {
		String pas = isNumeric() ? ("" + Password.keyFromPassword(key)) : Password.digestPassword2(key);
		if (!isNumeric() && pas.length() > 2) {
			pas = pas.substring(0, pas.length() - 2);
		}
		StringBuffer result = new StringBuffer();
		int len = 0;
		for (int j = 0; j < pas.length(); j++) {
			if ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".indexOf(pas.charAt(j)) >= 0) {
				result.append(pas.charAt(j));
				len++;
				if (len >= tokenLength) {
					break;
				}
			}
		}
		return result.toString();
	}

	public BufferedImage createBufferedImage(String key) {
		String pas = getHashFor(key);
		int x = 2;
		int height = getMaxImgHeight() + 4;
		int width = getMaxImgWidth();
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = bi.createGraphics();
		graphics.setBackground(Color.LIGHT_GRAY);
		graphics.setColor(Color.BLACK);
		graphics.clearRect(0, 0, width, height);
		Random r = new Random();
		for (int i = 0; i < pas.length(); i++) {
			int index = r.nextInt(font.length);
			graphics.setFont(font[index]);
			FontMetrics fm = graphics.getFontMetrics();
			String s = pas.substring(i, i + 1);
			graphics.drawString(s, x, 20);
			x += fm.stringWidth(s) - 1;
		}
		return bi;
	}

	public byte[] createImage(String key) throws ImageFormatException, IOException {
		//System.out.println("salida de:" + key+"  "+pas);
		BufferedImage bi = createBufferedImage(key);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(baos);
		encoder.encode(bi);
		return baos.toByteArray();
	}

	/**
	 *
	 */

	public HashImg() {
		super();
		setNumeric(false);
		setTokenLength(8);
	}

	public static void main(String[] args) throws Exception {
		//Password.
		HashImg.useDefaultFonts();
		Random r = new Random();
		int id = r.nextInt(10000);
		HashImg img = new HashImg();
		img.setTokenLength(8);
		img.setNumeric(false);
		System.out.println("Hash for:" + id + " is:" + img.getHashFor("" + id));
		FileOutputStream fos = new FileOutputStream(new File("/tmp/img.gif"));
		fos.write(img.createImage("" + id));
		fos.close();
	}

	public static Font[] getFont() {
		return font;
	}

	public boolean isNumeric() {
		return numeric;
	}

	public int getTokenLength() {
		return tokenLength;
	}

	public static void setFont(Font[] fonts) {
		font = fonts;
	}

	public void setNumeric(boolean b) {
		numeric = b;
	}

	public void setTokenLength(int i) {
		tokenLength = i;
	}

}
