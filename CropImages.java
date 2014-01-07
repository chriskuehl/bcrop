import java.io.*;
import java.lang.*;
import java.util.*;
import javax.imageio.*;
import java.awt.*;
import java.awt.image.*;

/**
 * Crop border off of images by testing the color at position
 * (COLOR_TEST_X, COLOR_TEST_Y) and cropping the color from each side of the image.
 */
class CropImages {
	private final static int COLOR_TEST_X = 2;
	private final static int COLOR_TEST_Y = 2;
	private final static int COLOR_THRESHOLD = 100;
	private final static int ALLOWABLE_ERRORS = 20;
	private final static double CROP_GREED_FACTOR = 1.0;

	public static void main(String[] args) throws IOException {
		File sourceDir = null, outputDir = null;

		try {
			sourceDir = getDirectory(args[0]);
			outputDir = getDirectory(args[1]);
		} catch (ArrayIndexOutOfBoundsException ex) {
			System.err.println("usage: java CropImages source_dir output_dir");
			System.exit(1);
		} catch (IOException ex) {
			System.err.println(ex.getMessage());
			System.exit(1);
		}

		cropImages(sourceDir, outputDir);
	}

	private static void cropImages(File sourceDir, File outputDir) throws IOException {
		for (File file : sourceDir.listFiles()) {
			cropImage(file, getOutputFile(file, outputDir));

			// TODO: remove this or at least clean it up
			String name = file.getName();
			System.out.println("<p><img src=\"orig/" + name + "\" /> <img src=\"output/" + name + "\" /></p>");
		}
	}

	private static File getOutputFile(File file, File outputDir) {
		return new File(outputDir, file.getName());
	}

	private static void cropImage(File file, File outputFile) throws IOException {
		BufferedImage image = ImageIO.read(file);
		int w = image.getWidth();
		int h = image.getHeight();
		int[][] colors = getColorsFromImage(image);
		int[] color = colors[COLOR_TEST_Y * w + COLOR_TEST_X];

		System.err.println("Cropping file \"" + file.getName() + "\"...");
		System.err.println("\tDimensions: " + w + "x" + h);
		System.err.println("\tCrop Color: [" + color[0] + ", " + color[1] + ", " + color[2] + "]");
		
		// terribly ugly hack to make handling sides easier
		FixedInteger zero = new FixedInteger(0);
		FixedInteger fw = new FixedInteger(w);
		FixedInteger fh = new FixedInteger(h);

		// left, right, top, bottom
		FixedInteger[][] sides = {
			// x1, x2, y1, y2
			{zero, new IncreasingInteger(1, w), zero, fh},
			{new DecreasingInteger(w - 1, 0), fw, zero, fh},
			{zero, fw, zero, new IncreasingInteger(1, h)},
			{zero, fw, new DecreasingInteger(h - 1, 0), fh}
		};

		int[] resultIndex = {1, 0, 3, 2};
		int[] results = new int[4];
		int i = 0;

		for (FixedInteger[] side : sides) {
			// System.err.println("\tTest Side #" + i);

			findSideDepth(colors, color, side, w);
			results[i] = side[resultIndex[i]].num;

			i ++;
		}

		int x1 = results[0];
		int x2 = results[1];
		int y1 = results[2];
		int y2 = results[3];

		System.err.println("\tCropping " + x1 + ", " + y1 + " :: " + (w - x2) + ", " + (h - y2));

		Rectangle bounds = new Rectangle(x1, y1, x2 - x1, y2 - y1);
		BufferedImage cropped = crop(image, bounds);
		ImageIO.write(cropped, "jpg", outputFile);
	}

	private static BufferedImage crop(BufferedImage src, Rectangle rect) {
		return src.getSubimage(rect.x, rect.y, rect.width, rect.height);
	}

	private static int[][] getColorsFromImage(BufferedImage image) {
		int w = image.getWidth();
		int h = image.getHeight();

		int[] rgbs = image.getRGB(0, 0, w, h, null, 0, w);
		int[][] colors = new int[rgbs.length][0];

		for (int i = 0; i < rgbs.length; i ++) {
			colors[i] = getRGBElements(rgbs[i]);
		}

		return colors;
	}

	/**
	 * Increments each dimension until the image shouldn't be cropped anymore.
	 *
	 * Since 3 dimensions will be FixedIntegers and 1 dimension will be an
	 * Increasing/DecreasingInteger, this function lets us test a single
	 * dimension at a time.
	 */
	private static void findSideDepth(int[][] colors, int[] color, FixedInteger[] side, int imageWidth) {
		while (true) {
			try {
				if (! shouldCrop(colors, color, getIntArray(side), imageWidth)) {
					prevAll(side);
					break;
				}

				nextAll(side);
			} catch (ReachedMaxException ex) {
				break;
			}
		}
	}

	private static boolean shouldCrop(int[][] colors, int[] color, int[] side, int imageWidth) {
		int x = side[0];
		int y = side[2];
		int width = side[1] - side[0];
		int height = side[3] - side[2];

		// System.err.println("test: (" + x + ", " + y + ") dim: " + width + "x" + height);
		int c = 0;

		// TODO: this can be optimized a lot (although it's fast enough as-is for the task at hand)
		for (int xx = x; xx < x + width; xx ++) {
			for (int yy = y; yy < y + height; yy ++) {
				int[] pColor = colors[yy * imageWidth + xx];

				if (! sameColor(color, pColor)) {
					c ++;
				}
			}
		}

		if (c >= ALLOWABLE_ERRORS) {
			System.err.println("\t\t" + c);
		}

		return c < ALLOWABLE_ERRORS;
	}

	private static boolean sameColor(int[] color1, int[] color2) {
		int delta = 0;

		for (int i = 0; i < 3; i ++) {
			delta += Math.abs(color1[0] - color2[0]);
		}

		return delta <= COLOR_THRESHOLD;
	}

	private static int[] getIntArray(FixedInteger[] side) {
		int[] iside = new int[side.length];

		for (int i = 0; i < side.length; i ++) {
			iside[i] = side[i].num;
		}

		return iside;
	}

	// yuck
	private static void prevAll(FixedInteger[] ints) throws ReachedMaxException {
		for (FixedInteger i : ints) {
			i.prev();
		}
	}

	private static void nextAll(FixedInteger[] ints) throws ReachedMaxException {
		for (FixedInteger i : ints) {
			i.next();
		}
	}

	private static int[] getRGBElements(int color) {
		// http://stackoverflow.com/a/15972640
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = (color & 0xFF);

		int[] rgb = {r, g, b};
		return rgb;
	}

	private static File getDirectory(String path) throws IOException {
		File targetDirectory = new File(path);

		if (! targetDirectory.exists() || ! targetDirectory.isDirectory()) {
			throw new IOException("Bad arguments; \"" + path + "\" isn't a directory.");
		}

		return targetDirectory;
	}
}

/*
 * This is so ugly. I'm sorry.
 * I miss lambdas.
 */
class FixedInteger {
	public int num;

	public FixedInteger(int num) {
		this.num = num;
	}

	public void next() throws ReachedMaxException {}
	public void prev() {}
}

// TODO: combine (Increasing|Decreasing)Integer into a single class
// with a "step"... or just find a better way to do this whole thing
class IncreasingInteger extends FixedInteger {
	private int max;

	public IncreasingInteger(int num, int max) {
		super(num);
		this.max = max;
	}

	@Override
	public void next() throws ReachedMaxException {
		this.num ++;

		if (this.num > this.max) {
			throw new ReachedMaxException();
		}
	}

	@Override
	public void prev() {
		this.num --;
	}
}

class DecreasingInteger extends FixedInteger {
	private int min;

	public DecreasingInteger(int num, int min) {
		super(num);
		this.min = min;
	}

	@Override
	public void next() throws ReachedMaxException {
		this.num --;

		if (this.num < this.min) {
			throw new ReachedMaxException();
		}
	}

	@Override
	public void prev() {
		this.num ++;
	}
}

class ReachedMaxException extends Exception {}
