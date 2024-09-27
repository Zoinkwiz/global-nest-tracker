package com.globalnesttracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GlobalNestTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GlobalNestTrackerPlugin.class);
		RuneLite.main(args);
	}
}