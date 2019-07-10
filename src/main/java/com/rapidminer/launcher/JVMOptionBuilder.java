package com.rapidminer.launcher;

import com.rapidminer.tools.FileSystemService;
import com.rapidminer.tools.LogService;
import com.rapidminer.tools.PlatformUtilities;
import com.rapidminer.tools.SystemInfoUtilities;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;






























public final class JVMOptionBuilder
{
    private static final String LAUNCHER_LOG = "launcher.log";
    private static final String ARGUMENTS_PROTOCOL_HANDLER = " -Xmx128m -XX:InitiatingHeapOccupancyPercent=55 -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true";
    private static final long MINIMUM_RM_MEMORY = 384L;
    private static final long MAX_32BIT_MEMORY = 1000L;
    private static final long MEMORY_TRESHOLD = 4069L;
    private static final double MEMORY_PORTION = 0.75D;
    private static final String WINDOWS_CLASSPATH_SEPARATOR = ";";
    private static final String UNIX_CLASSPATH_SEPARTOR = ":";
    private static final Logger LOGGER = Logger.getLogger(JVMOptionBuilder.class.getSimpleName());



    private static boolean verbose = false;



    private JVMOptionBuilder() { throw new AssertionError(); }


    private static void addSystemSpecificSettings(StringBuilder builder) {
        if (SystemInfoUtilities.getOperatingSystem() == SystemInfoUtilities.OperatingSystem.OSX) {
            String dockIconPath = null;

            String platformIndependent = PlatformUtilities.getRapidMinerHome() + "/RapidMiner Studio.app/Contents/Resources/rapidminer_frame_icon.icns";

            if (Files.exists(Paths.get(platformIndependent, new String[0]), new java.nio.file.LinkOption[0])) {
                dockIconPath = platformIndependent;
            } else {
                dockIconPath = PlatformUtilities.getRapidMinerHome() + "/../rapidminer_frame_icon.icns";
            }


            builder.append(" -Xdock:icon=");
            builder.append(escapeBlanks(dockIconPath));


            builder.append(" -Xdock:name=");
            builder.append(escapeBlanks("RapidMiner Studio"));


            builder.append(" -Dcom.apple.mrj.application.apple.menu.about.name=");
            builder.append(escapeBlanks("RapidMiner Studio"));


            builder.append(" -Dapple.laf.useScreenMenuBar=true");
            builder.append(" -Dcom.apple.mrj.application.growbox.intrudes=true");
            builder.append(" -Dapple.awt.antialiasing=true");
            builder.append(" -Dcom.apple.mrj.application.live-resize=true");
            builder.append(" -Dsun.java2d.opengl=true");
        } else if (SystemInfoUtilities.getOperatingSystem() == SystemInfoUtilities.OperatingSystem.WINDOWS) {

            builder.append(" -Djava.net.preferIPv4Stack=true");
            builder.append(" -Dsun.java2d.dpiaware=false");
        }
    }

    private static String escapeBlanks(String path) {
        if (SystemInfoUtilities.getOperatingSystem() == SystemInfoUtilities.OperatingSystem.OSX ||
                SystemInfoUtilities.getOperatingSystem() == SystemInfoUtilities.OperatingSystem.SOLARIS ||
                SystemInfoUtilities.getOperatingSystem() == SystemInfoUtilities.OperatingSystem.UNIX) {
            return path.replace(" ", "\\ ");
        }
        return "\"" + path + "\"";
    }


    private static void addMemorySettings(StringBuilder builder, long userMaxMemorySetting) {
        log("Calculating JVM memory settings...");

        long totalPhysicalMemorySize = 384L;
        try {
            totalPhysicalMemorySize = SystemInfoUtilities.getTotalPhysicalMemorySize().longValue();
            log("Total physical memory detected: " + totalPhysicalMemorySize);
        } catch (IOException e1) {
            log("Could not detect total physical memory.. assuming at least 384mb");
        }

        long memoryLimit = -1L;
        if (totalPhysicalMemorySize * 0.25D > 4069.0D) {

            memoryLimit = totalPhysicalMemorySize - 4069L;
        } else {

            memoryLimit = (long)(totalPhysicalMemorySize * 0.75D);
        }

        log("Calculating maximum usable memory for RapidMiner... ");
        log("Set maximum usable memory to " + memoryLimit + "mb");


        if (userMaxMemorySetting >= 384L) {
            if (userMaxMemorySetting <= totalPhysicalMemorySize) {
                log("Max allowed memory has been set in the RapidMiner preferences to " + userMaxMemorySetting + "mb. Using it instead of " + memoryLimit + "mb of memory.");

                memoryLimit = userMaxMemorySetting;
            } else {
                log("Max allowed memory has been set to more than the total memory " + totalPhysicalMemorySize + "mb. Ignoring it.");
            }
        } else {

            log("Max allowed memory has been set to less than 384mb. Ignoring because RapidMiner Studio must use at least 384mb.");
        }


        if (memoryLimit < 384L) {
            memoryLimit = 384L;
            log("Maximum usable memory is below minimum memory for RapidMiner! Set maximum usable memory to " + memoryLimit);
        }
        else if (SystemInfoUtilities.getJVMArchitecture() == SystemInfoUtilities.JVMArch.THIRTY_TWO) {

            if (memoryLimit > 1000L) {
                memoryLimit = 1000L;
                log("Maxmimum usable memory is above maximum memory for a 32bit JVM. Set maximum to " + memoryLimit);
            }
        }

        log("Using up to " + memoryLimit + "mb of memory.");


        if (SystemInfoUtilities.getJVMArchitecture() == SystemInfoUtilities.JVMArch.THIRTY_TWO &&
                SystemInfoUtilities.getOperatingSystem() == SystemInfoUtilities.OperatingSystem.WINDOWS) {


            long freePhysicalMemorySize = SystemInfoUtilities.getFreePhysicalMemorySize().longValue();
            if (freePhysicalMemorySize != -1L && freePhysicalMemorySize >= 384L &&
                    memoryLimit > freePhysicalMemorySize) {
                memoryLimit = freePhysicalMemorySize;
                log("Only " + freePhysicalMemorySize + "mb of free memory available, using " + memoryLimit + "mb of memory.");
            }
        }




        builder.append(" -Xms");
        builder.append(384L);
        builder.append("m");


        builder.append(" -Xmx");
        builder.append(memoryLimit);
        builder.append("m");
    }


    private static void addGarbageCollection(StringBuilder builder) {
        builder.append(" -XX:+UseG1GC");

        builder.append(" -XX:G1HeapRegionSize=32m");

        int cores = SystemInfoUtilities.getNumberOfProcessors();

        if (cores <= 16) {
            builder.append(" -XX:ParallelGCThreads=");
            builder.append(Math.max(2, cores / 2));
        }

        builder.append(" -XX:InitiatingHeapOccupancyPercent=55");
    }

    private static void addClassPath(StringBuilder builder) {
        builder.append("-cp ");


        builder.append("\"");

        String classPathSeperator = ";";
        if (SystemInfoUtilities.getOperatingSystem() != SystemInfoUtilities.OperatingSystem.WINDOWS) {
            classPathSeperator = ":";
        }

        log("Classpath seperator: " + classPathSeperator);

        File libFolder = new File(PlatformUtilities.getRapidMinerHome(), "lib");
        addLibsInFolder(libFolder, builder, classPathSeperator);

        File jdbcFolder = new File(libFolder, "jdbc");
        addLibsInFolder(jdbcFolder, builder, classPathSeperator);

        File freehepFolder = new File(libFolder, "freehep");
        addLibsInFolder(freehepFolder, builder, classPathSeperator);
        builder.append("\"");
    }


    private static void addLibsInFolder(File folder, StringBuilder builder, String classPathSeperator) {
        if (!folder.isDirectory()) {
            return;
        }
        for (File lib : folder.listFiles()) {
            if (lib.isFile() && lib.getName().contains(".jar")) {
                log("Adding lib to classpath: " + lib);
                builder.append(lib.getAbsolutePath());
                builder.append(classPathSeperator);
            }
        }
    }











    public static String getJVMOptions(boolean addClasspath, Long userMaxMemorySetting) {
        PlatformUtilities.ensureRapidMinerHomeSet(Level.OFF);

        StringBuilder builder = new StringBuilder();


        if (addClasspath) {
            addClassPath(builder);
        }


        addGarbageCollection(builder);


        addMemorySettings(builder, userMaxMemorySetting.longValue());


        addSystemSpecificSettings(builder);


        builder.append(" -Djava.net.useSystemProxies=true");

        return builder.toString();
    }









    public static void main(String[] args) {
        try {
            LogService.getRoot().setLevel(Level.OFF);
            for (Handler handler : LOGGER.getHandlers()) {
                LOGGER.removeHandler(handler);
            }


            try {
                FileHandler logFileHandler = new FileHandler((new File(FileSystemService.getUserRapidMinerDir(), "launcher.log")).getAbsolutePath(), false);
                logFileHandler.setLevel(Level.ALL);
                logFileHandler.setFormatter(new SimpleFormatter());
                LOGGER.addHandler(logFileHandler);
                LOGGER.setUseParentHandlers(false);
                LOGGER.setLevel(Level.ALL);
            } catch (IOException iOException) {}





            try {
                List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
                if (jvmArgs != null) {
                    StringBuilder sb = new StringBuilder();
                    for (String arg : jvmArgs) {
                        sb.append(arg);
                        sb.append(" ");
                    }
                    log("JVM arguments were: " + sb.toString());
                }
            } catch (Throwable t) {
                log("Failed to read JVM arguments!");
                t.printStackTrace();
            }



            try {
                Properties props = System.getProperties();
                if (props != null) {
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<Object, Object> entry : props.entrySet()) {
                        sb.append(entry.getKey());
                        sb.append(":'" + entry.getValue() + "'");
                        sb.append(" ");
                    }
                    log("System properties were: " + sb.toString());
                }
            } catch (Throwable t) {
                log("Failed to read system properties!");
                t.printStackTrace();
            }


            if (args.length > 0 && args[false] != null && args[0].startsWith("rapidminer://")) {

                Socket other = getOtherInstance();
                if (other != null) {
                    try {
                        other.close();
                    } catch (IOException iOException) {}


                    log("Running instance found. Starting minimal version.");

                    System.out.print(" -Xmx128m -XX:InitiatingHeapOccupancyPercent=55 -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true");
                    System.out.flush();
                    System.exit(0);
                } else {
                    log("No running instance found. Regular startup.");
                }
            }

            boolean addClasspath = false;

            for (String element : args) {
                if (element != null) {


                    if ("--verbose-startup".equals(element)) {
                        verbose = true;
                    }
                    if ("--addcp".equals(element)) {
                        addClasspath = true;
                    }
                }
            }
            if (verbose) {
                SystemInfoUtilities.logEnvironmentInfos();
            }

            String jvmOptions = getJVMOptions(addClasspath, readUserMaxMemorySetting());


            log("Launch settings are: '" + jvmOptions + "'");
            System.out.print(jvmOptions);

            System.out.flush();
        } finally {
            System.exit(0);
        }
    }


    private static void log(String toLog) { LOGGER.log(Level.INFO, toLog); }







    private static Long readUserMaxMemorySetting() {
        maxValue = Long.valueOf(Float.MAX_VALUE);


        Properties rmPreferences = new Properties();
        try (InputStream in = Files.newInputStream(FileSystemService.getMainUserConfigFile().toPath(), new java.nio.file.OpenOption[0])) {
            log("Trying to read user setting for maximum amount of memory.");
            rmPreferences.load(in);
            maxUserMemory = rmPreferences.getProperty("maxMemory");
            if (maxUserMemory != null && !maxUserMemory.isEmpty()) {
                maxValue = Long.valueOf(Long.parseLong(maxUserMemory));
                log("User setting for maximum amount of memory: " + maxValue);
            } else {
                log("No user setting for maximum amount of memory found.");
            }
        } catch (Exception e) {
            log("Failed to read RM preferences for user specified max memory: " + e.getMessage());
        }

        return maxValue;
    }






    private static Socket getOtherInstance() {
        int port;
        socketFile = FileSystemService.getUserConfigFile("rapidminer.lock");
        if (!socketFile.exists()) {
            return null;
        }


        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(socketFile), StandardCharsets.UTF_8))) {

            portStr = in.readLine();
            if (portStr == null) {
                log("Faild to retrieve port from socket file '" + socketFile + "'. File seems to be empty.");
                return null;
            }
            port = Integer.parseInt(portStr);
        } catch (Exception e) {
            log("Failed to read socket file '" + socketFile + "': " + e.getMessage());
            return null;
        }
        log("Checking for running instance on port " + port + ".");
        try {
            return new Socket(InetAddress.getLoopbackAddress(), port);
        } catch (IOException e) {
            log("Found lock file but no other instance running. Assuming unclean shutdown of previous launch.");
            return null;
        }
    }
}
