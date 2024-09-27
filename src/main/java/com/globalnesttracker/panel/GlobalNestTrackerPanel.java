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

import com.globalnesttracker.networking.NestCrowdsourcingManager;
import com.globalnesttracker.data.NestItemData;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Panel for displaying and interacting with the Global Nest Tracker.
 * This panel includes a list of items, a details section, and controls for searching,
 * filtering, and pagination.
 */
public class GlobalNestTrackerPanel extends PluginPanel
{
	// Dependencies
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final NestCrowdsourcingManager nestCrowdsourcingManager;

	// UI Components
	private JLabel statusLabel;
	private JTextField searchField;
	private JButton searchButton;
	private JComboBox<String> filterComboBox;
	private JList<NestItemData> itemList;
	private DefaultListModel<NestItemData> listModel;
	private JButton prevButton;
	private JButton nextButton;
	private JButton randomItemsButton;
	private JPanel detailsPanel;
	private JTextPane detailsTextPane;
	private JLabel itemIconLabel;
	private JLabel paginationInfoLabel;

	// Data
	private final List<NestItemData> fullDataList = new ArrayList<>();

	// Pagination
	private int currentPage = 0;
	private final int pageSize = 28;

	/**
	 * Constructs the Global Nest Tracker Panel.
	 *
	 * @param clientThread          The client thread.
	 * @param itemManager           The item manager.
	 * @param nestCrowdsourcingManager  The crowdsourcing manager.
	 */
	public GlobalNestTrackerPanel(ClientThread clientThread, ItemManager itemManager, NestCrowdsourcingManager nestCrowdsourcingManager)
	{
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.nestCrowdsourcingManager = nestCrowdsourcingManager;

		setLayout(new BorderLayout(0, 5));
		setBorder(new EmptyBorder(10, 10, 10, 10));

		// Initialize UI components and build the UI
		initializeComponents();

		// Build and add panels to the main panel
		add(createTopPanel(), BorderLayout.NORTH);
		add(createMainContentPanel(), BorderLayout.CENTER);
		add(createControlsPanel(), BorderLayout.SOUTH);

		// Initialize details panel with default message
		resetDetailsPanel();
	}

	/**
	 * Initializes UI components and sets up action listeners.
	 */
	private void initializeComponents()
	{
		// Status label
		statusLabel = new JLabel("Loading data...");

		// Filter controls
		String[] filterOptions = { "All", "Unknown", "Transformed", "Not Transformed" };
		filterComboBox = new JComboBox<>(filterOptions);
		filterComboBox.addActionListener(e -> onFilterChanged());

		// Search controls
		searchField = new JTextField();
		searchButton = new JButton("Search");
		searchButton.addActionListener(e -> onSearch());

		// Details panel components
		detailsTextPane = new JTextPane();
		detailsTextPane.setEditable(false);
		itemIconLabel = new JLabel();
		itemIconLabel.setPreferredSize(new Dimension(36, 36));

		// Item list and model
		listModel = new DefaultListModel<>();
		itemList = new JList<>(listModel);
		itemList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
		itemList.setVisibleRowCount(-1);
		itemList.setFixedCellWidth(48);
		itemList.setFixedCellHeight(48);
		itemList.setCellRenderer(new ItemListCellRenderer());
		itemList.addListSelectionListener(this::onItemSelected);

		// Pagination buttons
		prevButton = new JButton("Previous");
		prevButton.addActionListener(e -> onPrevPage());

		nextButton = new JButton("Next");
		nextButton.addActionListener(e -> onNextPage());

		// Random items button
		randomItemsButton = new JButton("Get 28 Random Unknown Items");
		randomItemsButton.addActionListener(e -> onGetRandomItems());

		paginationInfoLabel = new JLabel();
	}

	/**
	 * Creates the top panel containing the status label, filter controls, and search controls.
	 *
	 * @return The top panel.
	 */
	private JPanel createTopPanel()
	{
		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.setBorder(new EmptyBorder(0, 0, 5, 0));

		// Filter panel
		JPanel filterPanel = new JPanel(new BorderLayout());
		filterPanel.add(new JLabel("Filter: "), BorderLayout.WEST);
		filterPanel.add(filterComboBox, BorderLayout.CENTER);

		// Search panel
		JPanel searchPanel = new JPanel(new BorderLayout());
		searchPanel.add(new JLabel("Search Item ID: "), BorderLayout.WEST);
		searchPanel.add(searchField, BorderLayout.CENTER);
		searchPanel.add(searchButton, BorderLayout.EAST);

		// Stack status label, filter, and search
		JPanel stackedPanel = new JPanel(new GridLayout(3, 1, 0, 5));
		stackedPanel.add(statusLabel);
		stackedPanel.add(filterPanel);
		stackedPanel.add(searchPanel);

		topPanel.add(stackedPanel, BorderLayout.NORTH);

		return topPanel;
	}

	/**
	 * Creates the main content panel containing the details panel and the item list.
	 *
	 * @return The main content panel.
	 */
	private JPanel createMainContentPanel()
	{
		JPanel mainContentPanel = new JPanel();
		mainContentPanel.setLayout(new BoxLayout(mainContentPanel, BoxLayout.Y_AXIS));

		// Details panel
		detailsPanel = new JPanel(new BorderLayout());
		detailsPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
		detailsPanel.setPreferredSize(new Dimension(222, 90));

		JPanel detailsContent = new JPanel(new BorderLayout(5, 0));
		detailsContent.add(itemIconLabel, BorderLayout.WEST);
		detailsContent.add(detailsTextPane, BorderLayout.CENTER);

		detailsPanel.add(detailsContent, BorderLayout.CENTER);

		// Add details panel to main content panel
		mainContentPanel.add(detailsPanel);

		// Item list scroll pane
		JScrollPane itemListScrollPane = new JScrollPane(itemList);

		// Add item list to main content panel
		mainContentPanel.add(itemListScrollPane);

		return mainContentPanel;
	}

	/**
	 * Creates the controls panel containing pagination and random items button.
	 *
	 * @return The controls panel.
	 */
	private JPanel createControlsPanel()
	{
		JPanel controlsPanel = new JPanel(new BorderLayout());

		// Pagination info panel
		JPanel paginationInfoPanel = new JPanel();
		paginationInfoPanel.add(paginationInfoLabel);

		// Pagination buttons panel
		JPanel paginationButtonsPanel = new JPanel();
		paginationButtonsPanel.add(prevButton);
		paginationButtonsPanel.add(nextButton);

		// Random items button panel
		JPanel randomItemsPanel = new JPanel();
		randomItemsPanel.add(randomItemsButton);

		// Add components to controls panel
		controlsPanel.add(randomItemsPanel, BorderLayout.NORTH);
		controlsPanel.add(paginationInfoPanel, BorderLayout.CENTER);
		controlsPanel.add(paginationButtonsPanel, BorderLayout.SOUTH);

		return controlsPanel;
	}

	/**
	 * Handles filter changes by fetching data with the selected filter.
	 */
	private void onFilterChanged()
	{
		String selectedFilter = (String) filterComboBox.getSelectedItem();
		statusLabel.setText("Loading data...");
		currentPage = 0; // Reset to the first page
		nestCrowdsourcingManager.makeGetRequestWithFilter(this, selectedFilter, currentPage, pageSize);
	}

	/**
	 * Handles the search action by fetching data for the entered item ID.
	 */
	private void onSearch()
	{
		String input = searchField.getText().trim();
		if (!input.isEmpty())
		{
			try
			{
				int itemId = Integer.parseInt(input);
				statusLabel.setText("Searching for item ID: " + itemId);
				nestCrowdsourcingManager.makeGetRequestById(this, itemId);
			}
			catch (NumberFormatException ex)
			{
				setError("Invalid Item ID");
			}
		}
	}

	/**
	 * Handles item selection in the list to update the details panel.
	 *
	 * @param e The list selection event.
	 */
	private void onItemSelected(ListSelectionEvent e)
	{
		if (!e.getValueIsAdjusting())
		{
			NestItemData selectedItem = itemList.getSelectedValue();
			if (selectedItem != null)
			{
				updateDetailsPanel(selectedItem);
			}
			else
			{
				resetDetailsPanel();
			}
		}
	}

	/**
	 * Handles the previous page action.
	 */
	private void onPrevPage()
	{
		if (currentPage > 0)
		{
			currentPage--;
			statusLabel.setText("Loading data...");
			String selectedFilter = (String) filterComboBox.getSelectedItem();
			nestCrowdsourcingManager.makeGetRequestWithFilter(this, selectedFilter, currentPage, pageSize);
		}
	}

	/**
	 * Handles the next page action.
	 */
	private void onNextPage()
	{
		currentPage++;
		statusLabel.setText("Loading data...");
		String selectedFilter = (String) filterComboBox.getSelectedItem();
		nestCrowdsourcingManager.makeGetRequestWithFilter(this, selectedFilter, currentPage, pageSize);
	}

	/**
	 * Handles fetching random unknown items.
	 */
	private void onGetRandomItems()
	{
		statusLabel.setText("Loading random items...");
		nestCrowdsourcingManager.getRandomUnknownItems(this, pageSize);
	}

	/**
	 * Updates the details panel with the information of the selected item.
	 *
	 * @param itemData The selected item data.
	 */
	private void updateDetailsPanel(NestItemData itemData)
	{
		// Fetch the item's image asynchronously
		AsyncBufferedImage itemImage = itemManager.getImage(itemData.getItemId());

		// Clear the icon while loading
		itemIconLabel.setIcon(null);

		itemImage.onLoaded(() ->
		{
			SwingUtilities.invokeLater(() ->
			{
				// Set the loaded image
				itemIconLabel.setIcon(new ImageIcon(itemImage));
				itemIconLabel.revalidate();
				itemIconLabel.repaint();
			});
		});

		// Ensure item name is available
		String itemName = itemData.getItemName();
		if (itemName == null || itemName.isEmpty())
		{
			itemName = "Unknown Item";
		}

		// Build the transformed text
		String transformedText = getTransformedText(itemData.getTransformedState());

		try
		{
			StyledDocument doc = detailsTextPane.getStyledDocument();
			doc.remove(0, doc.getLength());

			Style boldStyle = detailsTextPane.addStyle("Bold", null);
			StyleConstants.setBold(boldStyle, true);

			Style plainStyle = detailsTextPane.addStyle("Plain", null);
			StyleConstants.setBold(plainStyle, false);

			doc.insertString(doc.getLength(), "Name: ", boldStyle);
			doc.insertString(doc.getLength(), itemName + "\n", plainStyle);

			doc.insertString(doc.getLength(), "Item ID: ", boldStyle);
			doc.insertString(doc.getLength(), itemData.getItemId() + "\n", plainStyle);

			doc.insertString(doc.getLength(), "Status: ", boldStyle);
			doc.insertString(doc.getLength(), transformedText, plainStyle);

		}
		catch (BadLocationException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Resets the details panel to the default message.
	 */
	private void resetDetailsPanel()
	{
		itemIconLabel.setIcon(null);
		detailsTextPane.setText("Click on an item to get details!");
	}

	/**
	 * Updates the data displayed in the panel with the given list of items.
	 *
	 * @param data The list of item data.
	 */
	public void updateData(int total, List<NestItemData> data)
	{
		clientThread.invoke(() ->
		{
			loadItemNames(data);
			SwingUtilities.invokeLater(() ->
			{
				fullDataList.clear();
				fullDataList.addAll(data);

				listModel.clear();

				updateListModel(fullDataList);

				statusLabel.setText("Data loaded.");

				updatePaginationInfoLabel(total);

				// Update pagination buttons
				nextButton.setEnabled(data.size() == pageSize);
				prevButton.setEnabled(currentPage > 0);
			});
		});
	}

	private void updatePaginationInfoLabel(int total)
	{
		int startItem = currentPage * pageSize + 1;
		int endItem = currentPage * pageSize + listModel.getSize();

		paginationInfoLabel.setText("Showing " + startItem + "-" + endItem + " of " + total);
	}

	/**
	 * Loads item names for the given list of items.
	 *
	 * @param data The list of item data.
	 */
	private void loadItemNames(List<NestItemData> data)
	{
		for (NestItemData item : data)
		{
			item.setItemName(itemManager.getItemComposition(item.getItemId()).getName());
		}
	}

	/**
	 * Updates the list model with the given list of items.
	 *
	 * @param data The list of item data.
	 */
	private void updateListModel(List<NestItemData> data)
	{
		for (NestItemData item : data)
		{
			listModel.addElement(item);
			item.setImageIcon(null); // Start with no icon
			loadItemImage(item);
		}
	}

	/**
	 * Loads the image for the given item and updates the list model when done.
	 *
	 * @param item The item data.
	 */
	private void loadItemImage(NestItemData item)
	{
		AsyncBufferedImage abi = itemManager.getImage(item.getItemId());

		abi.onLoaded(() ->
		{
			SwingUtilities.invokeLater(() ->
			{
				// Create the ImageIcon with the loaded image
				ImageIcon itemIcon = new ImageIcon(abi);

				// Create the overlay icon
				ImageIcon overlayIcon = getOverlayIcon(itemIcon, item.getTransformedState());

				// Set the image in ItemData
				item.setImageIcon(overlayIcon);

				// Update the item in the list model
				int index = listModel.indexOf(item);
				if (index != -1)
				{
					// Replace the item to trigger a UI update
					listModel.set(index, item);
				}
			});
		});
	}

	/**
	 * Generates the overlay icon with the symbol representing the transformed state.
	 *
	 * @param baseIcon         The base item icon.
	 * @param transformedState The transformed state of the item.
	 * @return The ImageIcon with the overlay.
	 */
	private ImageIcon getOverlayIcon(ImageIcon baseIcon, String transformedState)
	{
		int width = baseIcon.getIconWidth();
		int height = baseIcon.getIconHeight();

		// Create a buffered image with transparency
		BufferedImage combinedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		// Draw the base image
		Graphics2D g = combinedImage.createGraphics();
		g.drawImage(baseIcon.getImage(), 0, 0, null);

		// Get symbol and color based on details state
		Map.Entry<String, Color> symbolAndColor = getSymbolAndColor(transformedState);
		String symbol = symbolAndColor.getKey();
		Color color = symbolAndColor.getValue();

		// Only draw the symbol if it's not empty
		if (!symbol.isEmpty())
		{
			// Enable anti-aliasing for text
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			// Set font and color
			g.setFont(new Font("Arial", Font.BOLD, 20));
			g.setColor(color);

			// Calculate position to center the symbol
			FontMetrics fm = g.getFontMetrics();
			int x = (width - fm.stringWidth(symbol)) / 2;
			int y = ((height - fm.getHeight()) / 2) + fm.getAscent();

			// Draw the symbol on top of the image
			g.drawString(symbol, x, y);
		}

		g.dispose();

		return new ImageIcon(combinedImage);
	}

	/**
	 * Retrieves the symbol and color associated with the transformed state.
	 *
	 * @param transformedState The transformed state.
	 * @return A Map.Entry containing the symbol and color.
	 */
	private Map.Entry<String, Color> getSymbolAndColor(String transformedState)
	{
		String symbol;
		Color color;

		switch (transformedState)
		{
			case "unknown":
				symbol = "?";
				color = Color.YELLOW;
				break;
			case "yes":
				symbol = "Y";
				color = Color.GREEN;
				break;
			case "no":
				symbol = "N";
				color = Color.RED;
				break;
			default:
				symbol = "?";
				color = Color.WHITE;
				break;
		}

		return new AbstractMap.SimpleEntry<>(symbol, color);
	}

	/**
	 * Retrieves the transformed state text description.
	 *
	 * @param transformedState The transformed state.
	 * @return The description text.
	 */
	private String getTransformedText(String transformedState)
	{
		switch (transformedState)
		{
			case "unknown":
				return "Unknown if item transforms.";
			case "yes":
				return "Item transforms.";
			case "no":
				return "Item does not transform.";
			default:
				return "Transformation state unknown.";
		}
	}

	/**
	 * Sets an error message in the status label.
	 *
	 * @param message The error message.
	 */
	public void setError(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText(message);
		});
	}
}