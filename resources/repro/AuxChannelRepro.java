import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;

import java.io.OutputStream;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.time.Duration;

/**
 * Faithful reproduction of TermForge's voice bug: does opening + closing a second exec channel
 * tear down the primary shell session? MINA client -> this box's real OpenSSH, exactly as the app.
 */
public class AuxChannelRepro {
    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "app"; // "app" = out.close()+ch.close(false)
        Duration T = Duration.ofSeconds(10);

        SshClient client = SshClient.setUpDefaultClient();
        client.start();
        ClientSession session = client.connect("ubuntu", "localhost", 22).verify(T).getSession();

        FileKeyPairProvider keys = new FileKeyPairProvider(
                Paths.get(System.getProperty("user.home"), ".ssh", "id_rsa_repro"));
        for (KeyPair kp : keys.loadKeys(null)) session.addPublicKeyIdentity(kp);
        session.auth().verify(T);
        System.out.println("[repro] authenticated");

        // Primary shell channel (the terminal), with a close listener like the app's.
        ChannelShell shell = session.createShellChannel();
        shell.setOut(OutputStream.nullOutputStream());
        shell.setErr(OutputStream.nullOutputStream());
        shell.open().verify(T);
        shell.addCloseFutureListener(f -> System.out.println("### SHELL CLOSE LISTENER FIRED ###"));
        OutputStream shellIn = shell.getInvertedIn();
        System.out.println("[repro] shell open. isClosing=" + shell.isClosing() + " sessionClosed=" + session.isClosed());

        // Aux exec channel (the audio sink). "concurrent" mode = faithful to the app: a writer
        // thread streams continuously to a NO-READER FIFO (so writes block on a full channel
        // window), while the main thread closes the channel mid-write — exactly the app's race.
        String sink = mode.startsWith("concurrent") ? "cat > /tmp/tf_repro.fifo" : mode.equals("stream") ? "cat > /dev/null" : "cat > /tmp/tf_repro.sink";
        if (mode.startsWith("concurrent")) {
            new ProcessBuilder("sh", "-c", "rm -f /tmp/tf_repro.fifo; mkfifo /tmp/tf_repro.fifo").inheritIO().start().waitFor();
        }
        ChannelExec exec = session.createExecChannel(sink);
        exec.open().verify(T);
        OutputStream auxOut = exec.getInvertedIn();

        if (mode.equals("stream")) {
            // Faithful to the real app: sink DRAINS (/dev/null), so we stream a large continuous
            // volume (~32KB/s like real mic audio) for several seconds, while ALSO writing
            // keystrokes to the shell — both channels active at once — then close.
            Thread writer = new Thread(() -> {
                try {
                    for (int i = 0; i < 150; i++) { auxOut.write(new byte[2048]); auxOut.flush(); Thread.sleep(60); }
                } catch (Exception e) {
                    System.out.println("[repro] aux writer threw: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }, "aux-writer");
            writer.setDaemon(true);
            writer.start();
            for (int i = 0; i < 6; i++) { // concurrent shell keystrokes during streaming
                shellIn.write(("# tick " + i + "\n").getBytes()); shellIn.flush();
                System.out.println("[repro] shell write #" + i + " ok; session.isClosed=" + session.isClosed());
                Thread.sleep(500);
            }
            System.out.println("[repro] closing aux after sustained stream");
            auxOut.close();
            exec.close(false);
        } else if (mode.startsWith("concurrent")) {
            Thread writer = new Thread(() -> {
                try {
                    for (int i = 0; i < 10000; i++) { auxOut.write(new byte[2048]); auxOut.flush(); }
                } catch (Exception e) {
                    System.out.println("[repro] writer thread threw: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }, "aux-writer");
            writer.setDaemon(true);
            writer.start();
            Thread.sleep(800);
            System.out.println("[repro] closing aux WHILE writer thread is active, mode=" + mode);
            auxOut.close();
            exec.close(false);
        } else {
            for (int i = 0; i < 5; i++) { auxOut.write(new byte[4096]); auxOut.flush(); Thread.sleep(50); }
            System.out.println("[repro] aux wrote 20KB. Now closing aux with mode=" + mode);
            if (mode.equals("immediate")) exec.close(true);
            else { auxOut.close(); exec.close(false); }
        }

        Thread.sleep(2000);

        boolean shellAlive = !shell.isClosing() && !shell.isClosed() && !session.isClosed();
        System.out.println("[repro] after aux close: shell.isClosing=" + shell.isClosing()
                + " shell.isClosed=" + shell.isClosed() + " session.isClosed=" + session.isClosed());

        // The real test: can we still write to the shell (send a keystroke)?
        try {
            shellIn.write("echo STILL_ALIVE\n".getBytes());
            shellIn.flush();
            System.out.println("[repro] shell write SUCCEEDED -> session healthy");
        } catch (Exception e) {
            System.out.println("[repro] shell write FAILED -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        Thread.sleep(500);
        System.out.println("[repro] RESULT: shell " + (shellAlive ? "SURVIVED aux close (good)" : "DIED when aux closed (BUG REPRODUCED)"));

        session.close(false);
        client.stop();
    }
}
