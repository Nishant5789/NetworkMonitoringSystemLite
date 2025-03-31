package com.motadata.NMSLiteUsingVertex.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


public class AppLogger
{
    private static volatile Logger logger = null;

    public static Logger getLogger()
    {
        if (logger==null)
        {
            synchronized (AppLogger.class)
            {
                if (logger==null)
                {
                    logger = Logger.getLogger(AppLogger.class.getName()); // Use class name for clarity
                    logger.setUseParentHandlers(false);
                    setupFileHandler();
                }
            }
        }
        return logger;
    }

    private static void setupFileHandler()
    {
        try
        {
            Files.createDirectories(Paths.get("./logs"));

            FileHandler fileHandler = new FileHandler("./logs/application.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
        }
        catch (IOException e)
        {
            System.err.println("Failed to initialize FileHandler: " + e.getMessage());
        }
    }

    // New method to log messages without lambda method names
    public static void logInfo(String message)
    {
        getLogger().info(() -> Thread.currentThread().getStackTrace()[2].getMethodName() + " - " + message);
    }
}
