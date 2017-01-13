package io.descoped.plugins.devmode.util;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.lang.Boolean.TRUE;

/**
 * Created by oranheim on 04/01/2017.
 */
public class CommonUtil {

    // http://patorjk.com/software/taag/#p=display&f=Graffiti&t=Descoped
    public static final String DESCOPED_LOGO = //"\n" +
            "\t________                                               .___\n" +
                    "\t\\______ \\   ____   ______ ____  ____ ______   ____   __| _/\n" +
                    "\t |    |  \\_/ __ \\ /  ___// ___\\/  _ \\\\____ \\_/ __ \\ / __ | \n" +
                    "\t |    `   \\  ___/ \\___ \\\\  \\__(  <_> )  |_> >  ___// /_/ | \n" +
                    "\t/_______  /\\___  >____  >\\___  >____/|   __/ \\___  >____ | \n" +
                    "\t        \\/     \\/     \\/     \\/      |__|        \\/     \\/ ";
    private static final Log LOGGER = Logger.INSTANCE;
    private static ThreadLocal<OutputStream> outputLocal = new ThreadLocal<OutputStream>() {
        private OutputStream output = null;

        @Override
        protected OutputStream initialValue() {
            if (output == null) {
                output = newOutputStream();
            }
            return output;
        }

        @Override
        public void remove() {
            try {
                output.flush();
                output.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            super.remove();
        }
    };

    public static void closeOutputStream(OutputStream output) throws IOException {
        output.flush();
        output.close();
    }

    public static OutputStream closeAndCreateNewOutputStream(OutputStream output) throws IOException {
        closeOutputStream(output);
        return newOutputStream();
    }

    public static OutputStream getConsoleOutputStream() {
        return outputLocal.get();
    }

    public static OutputStream newOutputStream() {
        return new OutputStream() {
            private StringBuilder string = new StringBuilder();

            @Override
            public void write(int b) throws IOException {
                this.string.append((char) b);
            }

            @Override
            public synchronized void write(byte[] b, int off, int len) {
                try {
                    this.string.append(new String(b, 0, len, "UTF-8"));
                } catch (Exception e) {

                }
            }


            public String toString() {
                return this.string.toString();
            }
        };
    }

    public static OutputStream writeInputToOutputStream(InputStream in) throws IOException {
        OutputStream out = newOutputStream();
        byte[] buffer = new byte[1024];
        int len = in.read(buffer);
        while (len != -1) {
            out.write(buffer, 0, len);
            len = in.read(buffer);
        }
        out.close();
        return out;
    }

    public static OutputStream writeInputToOutputStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len = in.read(buffer);
        while (len != -1) {
            out.write(buffer, 0, len);
            len = in.read(buffer);
        }
        out.close();
        return out;
    }

    public static void printStream(String msg, InputStream in, Log log) throws IOException {
        OutputStream out = CommonUtil.newOutputStream();
        CommonUtil.writeInputToOutputStream(in, out);
        log.info(msg + ": " + out);
    }

    public static void printEnvVars() {
        LOGGER.info("------------> Environment Varaibles <------------");
        Map<String, String> env = System.getenv();
        for (Map.Entry<String, String> e : env.entrySet()) {
            if (e.getKey().contains("CI_NEXUS")) continue;
            LOGGER.info(String.format("%s=%s", e.getKey(), e.getValue()));
        }
        LOGGER.info("------------> System Properties <------------");
        Properties props = System.getProperties();
        for (Map.Entry<Object, Object> e : props.entrySet()) {
            LOGGER.info(String.format("%s=%s", e.getKey(), e.getValue()));
        }
        LOGGER.info("------------> -o-o-o-o-o-o-o <------------");
    }

    public static String trimLeft(String string) {
        return string.replaceAll("^\\s+", "");
    }

    public static String trimRight(String string) {
        return string.replaceAll("\\s+$", "");
    }

    public static String getOSString() {
        return System.getProperties().get("os.name") + " " + System.getProperties().get("os.version");
    }

    public static boolean isLinux() {
        return (getOSString().contains("Linux"));
    }

    public static boolean isMacOS() {
        return ((getOSString().contains("MacOS")) || (getOSString().contains("OS X")));
    }

    public static boolean isWindows() {
        return getOSString().contains("Windows");
    }

    public static boolean isMojoRunningInTestingHarness() {
        try {
            Class<?> mojo = Class.forName("org.apache.maven.plugin.testing.AbstractMojoTestCase");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isMojoRunningStandalone(MavenProject project) {
        boolean ok = Boolean.valueOf(project.getProperties().getProperty("test.mojo"));
        return ok;
    }

    public static boolean isTravisCI() {
        return TRUE.equals(Boolean.valueOf(System.getenv("TRAVIS")));
    }

    public static String printList(List<?> list) {
        if (list == null || list.isEmpty()) return "(empty)";
        StringBuffer buf = new StringBuffer();
        for (Object obj : list) {
            buf.append(obj.toString() + "\n");
        }
        return buf.toString();
    }

    public static synchronized long getPidOfProcess(Process p) {
        long pid = -1;

        try {
            if (p.getClass().getName().equals("java.lang.UNIXProcess")) {
                Field f = p.getClass().getDeclaredField("pid");
                f.setAccessible(true);
                pid = f.getLong(p);
                f.setAccessible(false);
            }
        } catch (Exception e) {
            pid = -1;
        }
        return pid;
    }

    public static Thread consoleProgressThread(File file, long contentLength) {
        Thread progressThread = new Thread(new ConsoleProgress(file, contentLength));
        progressThread.start();
        return progressThread;
    }

    // http://stackoverflow.com/questions/1001290/console-based-progress-in-java
    public static void printProgress(long startTime, long total, long current) {
        long eta = current == 0 ? 0 :
                (total - current) * (System.currentTimeMillis() - startTime) / current;

        String etaHms = current == 0 ? "N/A" :
                String.format("%02d:%02d:%01d", TimeUnit.MILLISECONDS.toHours(eta),
                        TimeUnit.MILLISECONDS.toMinutes(eta) % TimeUnit.HOURS.toMinutes(1),
                        TimeUnit.MILLISECONDS.toSeconds(eta) % TimeUnit.MINUTES.toSeconds(1));

        StringBuilder string = new StringBuilder(140);
        int percent = (int) (current * 100 / total);
        string
                .append('\r')
                .append(String.join("", Collections.nCopies(percent == 0 ? 2 : 2 - (int) (Math.log10(percent)), " ")))
                .append(String.format(" %d%% [", percent))
                .append(String.join("", Collections.nCopies(percent, "=")))
                .append('>')
                .append(String.join("", Collections.nCopies(100 - percent, " ")))
                .append(']')
                .append(String.join("", Collections.nCopies((int) (Math.log10(total)) - (int) (Math.log10(current)), " ")))
                .append(String.format(" %d/%d, ETA: %s", current, total, etaHms));

        System.out.print(string);
    }

    private static class ConsoleProgress implements Runnable {
        private final File file;
        private final long contentLength;

        public ConsoleProgress(File file, long contentLength) {
            this.file = file;
            this.contentLength = contentLength;
        }

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            while (true) {
                try {
                    CommonUtil.printProgress(startTime, contentLength, (file.length() == 0 ? 1 : file.length()));
                    Thread.currentThread().sleep(50);
                } catch (InterruptedException e) {
                    CommonUtil.printProgress(startTime, contentLength, (file.length() == 0 ? 1 : file.length()));
                    System.out.println();
                    break;
                }
            }
        }
    }

    public static void interruptProgress(Thread progressThread) {
        progressThread.interrupt();
        try {
            Thread.currentThread().sleep(50);
        } catch (InterruptedException e) {
        }
    }


}
