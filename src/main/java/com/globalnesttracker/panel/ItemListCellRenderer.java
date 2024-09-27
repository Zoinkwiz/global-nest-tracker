/*
 * Copyright (c) 2024, Zoinkwiz <https://github.com/Zoinkwiz>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.globalnesttracker.panel;

import com.globalnesttracker.data.NestItemData;
import javax.swing.*;
import java.awt.*;


/**
 * Custom cell renderer for the item list, displays item icons with overlays.
 */
public class ItemListCellRenderer extends DefaultListCellRenderer
{
	@Override
	public Component getListCellRendererComponent(JList<?> list, Object value, int index,
												  boolean isSelected, boolean cellHasFocus)
	{
		NestItemData itemData = (NestItemData) value;

		JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

		// Set the icon from ItemData
		ImageIcon overlayIcon = itemData.getImageIcon();
		label.setIcon(overlayIcon);

		// Clear any text to prevent unwanted characters
		label.setText(null); // Ensure no text is displayed

		// Set label properties
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setVerticalAlignment(SwingConstants.CENTER);
		label.setOpaque(true); // Ensure the label is opaque

		// Optionally, set background and foreground colors
		if (isSelected)
		{
			label.setBackground(list.getSelectionBackground());
			label.setForeground(list.getSelectionForeground());
		}
		else
		{
			label.setBackground(list.getBackground());
			label.setForeground(list.getForeground());
		}

		// Disable focus border if not needed
		label.setBorder(null);

		return label;
	}
}