/*
 * Copyright (c) 2024, Zoinkwiz <https://github.com/Zoinkwiz>
 * Copyright (c) 2019, Weird Gloop <admin@weirdgloop.org>
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
package com.globalnesttracker.networking;

import com.globalnesttracker.data.NestItemData;
import com.globalnesttracker.panel.GlobalNestTrackerPanel;
import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the communication with the Nest Crowdsourcing API.
 * Handles submitting data and fetching data with various filters.
 */
@Slf4j
@Singleton
public class NestCrowdsourcingManager
{
	// Base URL for the crowdsourcing API
	private static final String API_BASE_URL = "https://global-nest-tracker-6e3101149673.herokuapp.com/";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	// Injected dependencies
	@Inject
	private OkHttpClient httpClient;

	@Inject
	private Gson gson;

	@Inject
	private ClientThread clientThread;

	/**
	 * Submits the given item data to the API via a POST request.
	 *
	 * @param data The NestItemData to submit.
	 */
	public void submitToAPI(NestItemData data)
	{
		RequestBody body = RequestBody.create(JSON, gson.toJson(data));
		Request request = new Request.Builder()
			.url(API_BASE_URL)
			.post(body)
			.build();

		executeRequest(request, null);
	}

	/**
	 * Fetches all item data from the API and updates the panel.
	 *
	 * @param nestPanel The panel to update with the data.
	 */
	public void loadItems(GlobalNestTrackerPanel nestPanel)
	{
		Request request = new Request.Builder()
			.url(API_BASE_URL)
			.build();

		executeRequest(request, nestPanel);
	}

	/**
	 * Fetches item data by item ID from the API and updates the panel.
	 *
	 * @param nestPanel The panel to update with the data.
	 * @param itemId    The item ID to search for.
	 */
	public void loadItemsById(GlobalNestTrackerPanel nestPanel, int itemId)
	{
		HttpUrl url = HttpUrl.parse(API_BASE_URL).newBuilder()
			.addQueryParameter("itemId", String.valueOf(itemId))
			.build();

		Request request = new Request.Builder()
			.url(url)
			.build();

		executeRequest(request, nestPanel);
	}

	/**
	 * Fetches item data with the specified filter, page, and size, then updates the panel.
	 *
	 * @param nestPanel The panel to update with the data.
	 * @param filter    The filter to apply ("All", "Unknown", "Transformed", "Not Transformed").
	 * @param page      The page number for pagination.
	 * @param size      The number of items per page.
	 */
	public void loadItemsWithFilter(GlobalNestTrackerPanel nestPanel, String filter, int page, int size)
	{
		HttpUrl.Builder urlBuilder = HttpUrl.parse(API_BASE_URL).newBuilder()
			.addQueryParameter("page", String.valueOf(page))
			.addQueryParameter("size", String.valueOf(size));

		// Map filter option to transformedState parameter
		String transformedState = mapFilterToTransformedState(filter);
		if (transformedState != null)
		{
			urlBuilder.addQueryParameter("transformedState", transformedState);
		}

		HttpUrl url = urlBuilder.build();
		Request request = new Request.Builder()
			.url(url)
			.build();

		executeRequest(request, nestPanel);
	}

	/**
	 * Fetches a specified number of random unknown items and updates the panel.
	 *
	 * @param nestPanel The panel to update with the data.
	 * @param count     The number of random items to fetch.
	 */
	public void loadRandomUnknownItems(GlobalNestTrackerPanel nestPanel, int count)
	{
		HttpUrl url = HttpUrl.parse(API_BASE_URL + "random").newBuilder()
			.addQueryParameter("count", String.valueOf(count))
			.addQueryParameter("transformedState", "unknown")
			.build();

		Request request = new Request.Builder()
			.url(url)
			.build();

		executeRequest(request, nestPanel);
	}

	/**
	 * Executes the given HTTP request and handles the response, updating the panel if provided.
	 *
	 * @param request   The HTTP request to execute.
	 * @param nestPanel The panel to update with the data; can be null if not needed.
	 */
	private void executeRequest(Request request, GlobalNestTrackerPanel nestPanel)
	{
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.error("Error executing request: {}", request.url(), e);
				if (nestPanel != null)
				{
					nestPanel.setError("Failed to load data.");
				}
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try
				{
					if (response.isSuccessful())
					{
						if (nestPanel != null)
						{
							handleSuccessfulResponse(response, nestPanel);
						}
						else
						{
							log.debug("Request successful: {}", request.url());
						}
					}
					else
					{
						log.error("Server returned error code {} for URL: {}", response.code(), request.url());
						if (nestPanel != null)
						{
							nestPanel.setError("Failed to load data.");
						}
					}
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	/**
	 * Handles a successful HTTP response, parsing the data and updating the panel.
	 *
	 * @param response  The HTTP response.
	 * @param nestPanel The panel to update with the data.
	 * @throws IOException If an I/O error occurs.
	 */
	private void handleSuccessfulResponse(Response response, GlobalNestTrackerPanel nestPanel) throws IOException
	{
		String responseBody = response.body().string();

		// Parse total count from headers or JSON, depending on API implementation
		String totalHeader = response.header("X-Total-Count");
		int total = totalHeader != null ? Integer.parseInt(totalHeader) : 0;

		// Parse the JSON response
		Type listType = new com.google.gson.reflect.TypeToken<ArrayList<NestItemData>>(){}.getType();
		List<NestItemData> data = gson.fromJson(responseBody, listType);

		// Update the panel with the data and total count
		nestPanel.updateData(total, data);
	}

	/**
	 * Maps the filter option from the UI to the corresponding transformedState value.
	 *
	 * @param filter The filter option selected in the UI.
	 * @return The corresponding transformedState value, or null if "All" is selected.
	 */
	private String mapFilterToTransformedState(String filter)
	{
		switch (filter)
		{
			case "Unknown":
				return "unknown";
			case "Transformed":
				return "yes";
			case "Not Transformed":
				return "no";
			case "All":
			default:
				return null; // No filter applied
		}
	}
}