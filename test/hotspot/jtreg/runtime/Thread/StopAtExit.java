/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 8167108 8266130
 * @summary Stress test java.lang.Thread.stop() at thread exit.
 * @run main/othervm StopAtExit
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class StopAtExit extends Thread {
    private final static int DEF_TIME_MAX = 30;  // default max # secs to test
    private final static String PROG_NAME = "StopAtExit";

    public CountDownLatch exitSyncObj = new CountDownLatch(1);
    public CountDownLatch startSyncObj = new CountDownLatch(1);

    @Override
    public void run() {
        try {
            // Tell main thread we have started.
            startSyncObj.countDown();
            try {
                // Wait for main thread to tell us to race to the exit.
                exitSyncObj.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpected: " + e);
            }
        } catch (ThreadDeath td) {
            // ignore because we're testing Thread.stop() which throws it
        } catch (NoClassDefFoundError ncdfe) {
            // ignore because we're testing Thread.stop() which can cause it
        }
    }

    public static void main(String[] args) {
        int timeMax = 0;
        if (args.length == 0) {
            timeMax = DEF_TIME_MAX;
        } else {
            try {
                timeMax = Integer.parseUnsignedInt(args[0]);
            } catch (NumberFormatException nfe) {
                System.err.println("'" + args[0] + "': invalid timeMax value.");
                usage();
            }
        }

        System.out.println("About to execute for " + timeMax + " seconds.");

        long count = 0;
        long start_time = System.currentTimeMillis();
        while (System.currentTimeMillis() < start_time + (timeMax * 1000)) {
            count++;

            StopAtExit thread = new StopAtExit();
            thread.start();
            try {
                // Wait for the worker thread to get going.
                thread.startSyncObj.await();
                // Tell the worker thread to race to the exit and the
                // Thread.stop() calls will come in during thread exit.
                thread.exitSyncObj.countDown();
                while (true) {
                    thread.stop();

                    if (!thread.isAlive()) {
                        // Done with Thread.stop() calls since
                        // thread is not alive.
                        break;
                    }
                }
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            } catch (NoClassDefFoundError ncdfe) {
                // Ignore because we're testing Thread.stop() which can
                // cause it. Yes, a NoClassDefFoundError that happens
                // in a worker thread can subsequently be seen in the
                // main thread.
            }

            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }
            thread.stop();
        }

        System.out.println("Executed " + count + " loops in " + timeMax +
                           " seconds.");

        String cmd = System.getProperty("sun.java.command");
        if (cmd != null && !cmd.startsWith("com.sun.javatest.regtest.agent.MainWrapper")) {
            // Exit with success in a non-JavaTest environment:
            System.exit(0);
        }
    }

    public static void usage() {
        System.err.println("Usage: " + PROG_NAME + " [time_max]");
        System.err.println("where:");
        System.err.println("    time_max  max looping time in seconds");
        System.err.println("              (default is " + DEF_TIME_MAX +
                           " seconds)");
        System.exit(1);
    }
}
