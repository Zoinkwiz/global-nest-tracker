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
package com.globalnesttracker;

import com.globalnesttracker.data.NestItemData;
import com.globalnesttracker.networking.NestCrowdsourcingManager;
import com.globalnesttracker.panel.GlobalNestTrackerPanel;
import com.globalnesttracker.tools.Icon;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
	name = "Global Nest Tracker"
)
public class GlobalNestTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	ItemManager itemManager;

	@Inject
	ClientThread clientThread;

	@Inject
	NestCrowdsourcingManager nestCrowdsourcingManager;

	@Inject
	private ClientToolbar clientToolbar;

	GlobalNestTrackerPanel globalNestTrackerPanel;

	NavigationButton navButton;

	private boolean hasPlacedItemThisLogin;

	private int lastItemIdPlaced = -1;

	@Override
	protected void startUp() throws Exception
	{
		globalNestTrackerPanel = new GlobalNestTrackerPanel(clientThread, itemManager, nestCrowdsourcingManager);

		final BufferedImage icon = Icon.NEST.getImage();

		navButton = NavigationButton.builder()
			.tooltip("Global Nest Tracker")
			.icon(icon)
			.priority(7)
			.panel(globalNestTrackerPanel)
			.build();

		clientToolbar.addNavigation(navButton);

		nestCrowdsourcingManager.makeGetRequest(globalNestTrackerPanel);
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (chatMessage.getType() == ChatMessageType.MESBOX)
		{
			if (chatMessage.getMessage().equals("You place your item in the nest."))
			{
				hasPlacedItemThisLogin = true;
				clientThread.invokeLater(() -> {
					// Check widget
					Widget widget = client.getWidget(ComponentID.DIALOG_SPRITE_SPRITE);
					if (widget == null) return;
					int itemID = widget.getItemId();
					if (itemID <= 0) return;

					lastItemIdPlaced = itemID;
				});
				return;
			}

			if (chatMessage.getMessage().equals("That item is quite valuable. It's probably not a good idea to put it in a random nest."))
			{
				Widget widget = client.getWidget(ComponentID.DIALOG_SPRITE_SPRITE);
				if (widget == null) return;
				int itemID = widget.getItemId();
				if (itemID <= 0) return;

				NestItemData data = new NestItemData(itemID, false);
				nestCrowdsourcingManager.submitToAPI(data);

				hasPlacedItemThisLogin = false;
				lastItemIdPlaced = -1;
			}

			if (hasPlacedItemThisLogin) return;

			if (chatMessage.getMessage().equals("You retrieve your item from the nest.") ||
				chatMessage.getMessage().equals("You go to retrieve your item from the nest, but find that it has been replaced with something else."))
			{
				clientThread.invokeLater(() -> {
					// Check widget
					Widget widget = client.getWidget(ComponentID.DIALOG_SPRITE_SPRITE);
					if (widget == null) return;
					int itemID = widget.getItemId();
					if (itemID <= 0) return;

					boolean transformed = chatMessage.getMessage().contains("replaced with something else");

					if (transformed && lastItemIdPlaced == -1) return;

					int idToSend = transformed ? lastItemIdPlaced : itemID;

					NestItemData data = new NestItemData(idToSend, transformed);
					nestCrowdsourcingManager.submitToAPI(data);

					// Reset the flag
					hasPlacedItemThisLogin = false;
					lastItemIdPlaced = -1;
				});
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(final GameStateChanged event)
	{
		final GameState state = event.getGameState();

		if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN)
		{
			hasPlacedItemThisLogin = false;
		}
	}
}
