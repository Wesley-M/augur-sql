package io.github.wesleym.augur.samples.gif;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * A minimal, dependency-free animated-GIF encoder built on the JDK's ImageIO
 * GIF writer. Frames are full, opaque images; each carries its own delay and
 * the stream loops forever.
 */
final class GifSequenceWriter implements AutoCloseable {
	private static final String FORMAT = "javax_imageio_gif_image_1.0";

	private final ImageWriter writer;
	private final ImageWriteParam params;
	private final ImageOutputStream output;
	private boolean first = true;

	GifSequenceWriter(File file) throws IOException {
		this.writer = ImageIO.getImageWritersBySuffix("gif").next();
		this.params = writer.getDefaultWriteParam();
		this.output = ImageIO.createImageOutputStream(file);
		writer.setOutput(output);
		writer.prepareWriteSequence(null);
	}

	/** Appends one frame shown for {@code delayMs} milliseconds. */
	void writeFrame(BufferedImage image, int delayMs) throws IOException {
		// Use the image's own type spec (not createFromRenderedImage, which loses the
		// IndexColorModel and makes the writer fall back to the default web palette).
		ImageTypeSpecifier type = new ImageTypeSpecifier(image);
		IIOMetadata metadata = writer.getDefaultImageMetadata(type, params);
		IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(FORMAT);

		IIOMetadataNode control = child(root, "GraphicControlExtension");
		control.setAttribute("disposalMethod", "none");
		control.setAttribute("userInputFlag", "FALSE");
		control.setAttribute("transparentColorFlag", "FALSE");
		control.setAttribute("delayTime", Integer.toString(Math.max(2, Math.round(delayMs / 10f))));
		control.setAttribute("transparentColorIndex", "0");

		if (first) {
			IIOMetadataNode extensions = child(root, "ApplicationExtensions");
			IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
			loop.setAttribute("applicationID", "NETSCAPE");
			loop.setAttribute("authenticationCode", "2.0");
			loop.setUserObject(new byte[] {0x1, 0x0, 0x0}); // 0 = loop forever
			extensions.appendChild(loop);
		}

		metadata.setFromTree(FORMAT, root);
		writer.writeToSequence(new IIOImage(image, null, metadata), params);
		first = false;
	}

	@Override
	public void close() throws IOException {
		writer.endWriteSequence();
		output.close();
		writer.dispose();
	}

	private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
		for (int i = 0; i < parent.getLength(); i++) {
			if (parent.item(i).getNodeName().equalsIgnoreCase(name)) {
				return (IIOMetadataNode) parent.item(i);
			}
		}
		IIOMetadataNode node = new IIOMetadataNode(name);
		parent.appendChild(node);
		return node;
	}
}
