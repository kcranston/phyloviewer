package org.iplantc.phyloviewer.server.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class ImageGraphics extends Java2DGraphics
{
	private BufferedImage image;

	public ImageGraphics(int width, int height)
	{
		super();

		image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g2d = image.createGraphics();

		// Turn on anti-aliasing.
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g2d.setClip(0, 0, width, height);
		g2d.setBackground(new Color(1.0f, 1.0f, 1.0f, 1.0f));
		g2d.clearRect(0, 0, width, height);
		this.setGraphics2D(g2d);

		this.setSize(width, height);
	}

	public BufferedImage getImage()
	{
		return image;
	}
}
