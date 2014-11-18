// ============================================================================
//
// Copyright (C) 2006-2014 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.jobscript;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class DriveCmdLine {

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        List<Column> columns = new ArrayList<Column>();
        columns.add(new Column("Prenom", "id_String"));
        columns.add(new Column("Nom", "id_String"));
        columns.add(new Column("Arrivee", "id_Date"));
        JobScriptBuilder jobScriptBuilder = new JobScriptBuilder().addHeader().addContexts().addParameters()
                .addInputComponents(columns).addConnection(columns).addSubJob();

        File jobScript = File.createTempFile("JS_", ".jobscript", new File("/tmp"));
        // File jobScript = new File("/tmp", "JS_generated.txt");
        Files.write(jobScriptBuilder.toString(), jobScript, Charsets.UTF_8);

        long end = System.currentTimeMillis();
        System.out.println("Generated <" + jobScript.getAbsolutePath() + "> in " + (end - start) + " ms");

        String jobName = "Job_from_script";
        String commandInit = "initLocal";
        String commandLogon = "logonProject --project-name P1 --user-login stef@talend.com --user-password p";
        String commandCreate = "createJob " + jobName + " --script_file " + jobScript.getAbsolutePath()
                + " --over_write";
        // commandCreate = "createJob " + jobName + " --script_file " + "/tmp/reference.txt" + " --over_write";
        // commandCreate = "createJob " + jobName + " --script_file " + "/tmp/generated.txt" + " --over_write";
        String commandExecute = "executeJob " + jobName + " --interpreter /usr/bin/java";

        boolean executed = true;
        // if (executed)
        // waitCommand(8002, commandInit);
        // if (executed)
        // executed = waitCommand(8002, commandLogon);
        if (executed)
            executed = waitCommand(8002, commandCreate);
        if (executed)
            executed = waitCommand(8002, commandExecute);
    }

    public static boolean waitCommand(int port, String command) throws Exception {
        int id = sendCommand(port, command);
        long start = System.currentTimeMillis();
        boolean waitCommand = waitCommand(port, id);
        long end = System.currentTimeMillis();
        System.out.println("Processed command <" + command + "> in " + (end - start) + " ms");
        return waitCommand;
    }

    private static int waitTime = 100;

    private static int sendCommand(int port, String command) throws Exception {
        Socket socket = null;
        PrintWriter printWriter = null;
        BufferedReader reader = null;
        try {
            socket = new Socket("localhost", port);
            printWriter = new PrintWriter(socket.getOutputStream());
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            printWriter.println(command);
            printWriter.flush();

            String returnCmd = null;
            int t = -1;
            while (t < 0) {
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                }
                returnCmd = reader.readLine();
                if (returnCmd != null) {
                    if (returnCmd.matches("^\\|Unexpected .+ while processing .*")) {
                        throw new Exception(returnCmd);
                    }
                    t = returnCmd.indexOf("ADDED_COMMAND");
                }
            }
            int id = Integer.parseInt(returnCmd.substring(t + 14));
            return id;
        } finally {
            try {
                printWriter.close();
            } catch (Exception e) {
            }
            try {
                reader.close();
            } catch (Exception e) {
            }
            try {
                socket.close();
            } catch (Exception e) {
            }
        }
    }

    protected static boolean waitCommand(int port, int commandId) throws IOException {
        Socket socket = null;
        PrintWriter printWriter = null;
        BufferedReader reader = null;
        try {
            String response = "RUNNING";
            while (response.contains("RUNNING") || response.contains("WAITING")) {
                socket = new Socket("localhost", port);
                printWriter = new PrintWriter(socket.getOutputStream());
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                printWriter.println("getCommandStatus " + commandId);
                printWriter.flush();
                try {
                    response = reader.readLine();
                } catch (IOException e) {
                    throw e;
                }

                try {
                    printWriter.close();
                } catch (Exception e) {
                }
                try {
                    reader.close();
                } catch (Exception e) {
                }
                try {
                    socket.close();
                } catch (Exception e) {
                }

                if (response.contains("RUNNING") || response.contains("WAITING")) {
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException e) {
                    }
                }
            }

            return response.contains("COMPLETED");
        } finally {
            try {
                printWriter.close();
            } catch (Exception e) {
            }
            try {
                reader.close();
            } catch (Exception e) {
            }
            try {
                socket.close();
            } catch (Exception e) {
            }
        }
    }
}
