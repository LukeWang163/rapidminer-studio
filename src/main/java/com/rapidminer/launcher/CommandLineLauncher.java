package com.rapidminer.launcher;

import com.rapidminer.BreakpointListener;
import com.rapidminer.Process;
import com.rapidminer.RapidMiner;
import com.rapidminer.RepositoryProcessLocation;
import com.rapidminer.core.license.DatabaseConstraintViolationException;
import com.rapidminer.core.license.LicenseViolationException;
import com.rapidminer.license.LicenseManager;
import com.rapidminer.license.LicenseManagerRegistry;
import com.rapidminer.license.internal.DefaultLicenseManager;
import com.rapidminer.license.verification.JarVerifier;
import com.rapidminer.operator.IOContainer;
import com.rapidminer.operator.Operator;
import com.rapidminer.parameter.UndefinedParameterError;
import com.rapidminer.repository.RepositoryLocation;
import com.rapidminer.security.PluginSandboxPolicy;
import com.rapidminer.security.PluginSecurityManager;
import com.rapidminer.tools.I18N;
import com.rapidminer.tools.LogService;
import com.rapidminer.tools.ParameterService;
import com.rapidminer.tools.PlatformUtilities;
import com.rapidminer.tools.SystemInfoUtilities;
import com.rapidminer.tools.Tools;
import com.rapidminer.tools.container.Pair;
import com.rapidminer.tools.net.UserProvidedTLSCertificateLoader;
import com.rapidminer.tools.usagestats.ActionStatisticsCollector;
import com.rapidminer.tools.usagestats.UsageStatistics;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Policy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;









public class CommandLineLauncher
        extends RapidMiner
        implements BreakpointListener
{
    private static final String LICENSE = "RapidMiner Studio version " + RapidMiner.getLongVersion() + ", Copyright (C) 2001-2019 RapidMiner GmbH" +
            Tools.getLineSeparator() + "See End User License Agreement information in the file named EULA.";


    private String repositoryLocation = null;

    private boolean readFromFile = false;
    private final List<Pair<String, String>> macros = new ArrayList();



    private static class WaitForKeyThread
            extends Thread
    {
        private final Process process;



        public WaitForKeyThread(Process process) { this.process = process; }



        public void run() {
            try {
                System.in.read();
            } catch (IOException e) {

                LogService.getRoot().log(Level.WARNING, I18N.getMessage(LogService.getRoot().getResourceBundle(), "com.rapidminer.RapidMinerCommandLine.waiting_for_user_input_error", new Object[] { e
                        .getMessage() }), e);
            }
            this.process.resume();
        }
    }



    public void breakpointReached(Process process, Operator operator, IOContainer container, int location) {
        System.out.println("Results in application " + operator.getApplyCount() + " of " + operator.getName() + ":" +
                Tools.getLineSeparator() + container);
        System.out.println("Breakpoint reached " + ((location == 0) ? "before " : "after ") + operator
                .getName() + ", press enter...");
        (new WaitForKeyThread(process)).start();
    }



    public void resume() {}



    private void parseArguments(String[] argv) {
        this.repositoryLocation = null;

        for (String element : argv) {
            if (element != null)
            {

                if ("-f".equals(element)) {
                    this.readFromFile = true;
                } else if (element.startsWith("-M")) {
                    String elementSubstring = element.substring(2);
                    String[] split = elementSubstring.split("=", 2);
                    String value = (split.length == 2) ? split[1] : "";
                    this.macros.add(new Pair(split[0], value));
                } else if (this.repositoryLocation == null) {
                    this.repositoryLocation = element;
                }
            }
        }
        if (this.repositoryLocation == null) {
            printUsage();
        }
    }

    private static void printUsage() {
        System.err.println("Usage: " + CommandLineLauncher.class.getName() + " [-f] PROCESS [-Mname=value]\n  PROCESS       a repository location containing a process\n  -f            interpret PROCESS as a file rather than a repository location (deprecated)\n  -Mname=value  sets the macro 'name' with the value 'value'");



        System.exit(1);
    }

    private void run() {
        PlatformUtilities.initialize();

        UserProvidedTLSCertificateLoader.INSTANCE.init();


        RapidMiner.init();


        RapidMiner.addShutdownHook(() -> UsageStatistics.getInstance().save());

        process = null;
        try {
            if (this.readFromFile) {
                process = RapidMiner.readProcessFile(new File(this.repositoryLocation));
            } else {
                RepositoryProcessLocation loc = new RepositoryProcessLocation(new RepositoryLocation(this.repositoryLocation));
                process = loc.load(null);
            }
        } catch (Exception e) {
            LogService.getRoot().log(Level.WARNING, I18N.getMessage(LogService.getRoot().getResourceBundle(), "com.rapidminer.RapidMinerCommandLine.reading_process_setup_error", new Object[] { this.repositoryLocation, e
                    .getMessage() }), e);

            RapidMiner.quit(RapidMiner.ExitMode.ERROR);
        }

        if (process != null) {
            try {
                for (Pair<String, String> macro : this.macros) {
                    process.getContext().addMacro(macro);
                }
                process.addBreakpointListener(this);
                IOContainer results = process.run();
                process.getRootOperator().sendEmail(results, null);
                LogService.getRoot().log(Level.INFO, "com.rapidminer.RapidMinerCommandLine.process_finished");
                RapidMiner.quit(RapidMiner.ExitMode.NORMAL);
            } catch (OutOfMemoryError e) {
                LogService.getRoot().log(Level.SEVERE, "com.rapidminer.RapidMinerCommandLine.out_of_memory");
                ActionStatisticsCollector.getInstance().log("error", "out_of_memory",
                        String.valueOf(SystemInfoUtilities.getMaxHeapMemorySize()));
                process.getLogger().log(Level.SEVERE, "Here: " + process
                        .getRootOperator().createMarkedProcessTree(10, "==>", process.getCurrentOperator()));
                try {
                    process.getRootOperator().sendEmail(null, e);
                } catch (UndefinedParameterError undefinedParameterError) {}

            }
            catch (DatabaseConstraintViolationException ex) {
                if (ex.getOperatorName() != null) {
                    LogService.getRoot().log(Level.SEVERE, "com.rapidminer.RapidMinerCommandLine.database_constraint_violation_exception_in_operator", new Object[] { ex

                            .getDatabaseURL(), ex.getOperatorName() });
                } else {
                    LogService.getRoot().log(Level.SEVERE, "com.rapidminer.RapidMinerCommandLine.database_constraint_violation_exception", new Object[] { ex

                            .getDatabaseURL() });
                }
            } catch (LicenseViolationException e) {
                LogService.getRoot().log(Level.SEVERE, "com.rapidminer.RapidMinerCommandLine.operator_constraint_violation_exception", new Object[] { e

                        .getOperatorName() });
            } catch (Throwable e) {
                String debugProperty = ParameterService.getParameterValue("rapidminer.general.debugmode");
                boolean debugMode = Tools.booleanValue(debugProperty, false);
                String message = e.getMessage();
                if (!debugMode &&
                        e instanceof RuntimeException) {
                    if (e.getMessage() != null) {
                        message = "operator cannot be executed (" + e.getMessage() + "). Check the log messages...";
                    } else {
                        message = "operator cannot be executed. Check the log messages...";
                    }
                }

                process.getLogger().log(Level.SEVERE, "Process failed: " + message, e);
                process.getLogger().log(Level.SEVERE, "Here: " + process
                        .getRootOperator().createMarkedProcessTree(10, "==>", process.getCurrentOperator()));
                try {
                    process.getRootOperator().sendEmail(null, e);
                } catch (UndefinedParameterError undefinedParameterError) {}
            }
            finally {

                ActionStatisticsCollector.getInstance().log(process.getCurrentOperator(), "FAILURE");

                ActionStatisticsCollector.getInstance().log(process.getCurrentOperator(), "RUNTIME_EXCEPTION");

                LogService.getRoot().severe("Process not successful");
                RapidMiner.quit(RapidMiner.ExitMode.ERROR);
            }
        }
    }







    public static void main(String[] argv) {
        Policy.setPolicy(new PluginSandboxPolicy());
        System.setSecurityManager(new PluginSecurityManager());

        setExecutionMode(RapidMiner.ExecutionMode.COMMAND_LINE);


        LicenseManagerRegistry.INSTANCE.set(new DefaultLicenseManager());
        LicenseManager manager = LicenseManagerRegistry.INSTANCE.get();
        try {
            JarVerifier.verify(new Class[] { manager.getClass(), RapidMiner.class, CommandLineLauncher.class });
        } catch (GeneralSecurityException e) {
            System.err.println("Failed to verify RapidMiner Studio installation: " + e.getMessage());
            System.exit(1);
        }

        System.out.println(LICENSE);
        CommandLineLauncher main = new CommandLineLauncher();
        main.parseArguments(argv);
        main.run();
    }
}
