package fr.universalserverloader.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public final class EulaManager {
    public void apply(Path root, boolean agreed) throws IOException {
        if (!agreed) return;
        Path eula = root.resolve("eula.txt");
        if (!Files.exists(eula)) {
            Files.write(eula, ("# Accepté explicitement via config/loader.json\n" +
                    "# " + new Date() + "\neula=true\n").getBytes(StandardCharsets.UTF_8));
            return;
        }
        List<String> lines = Files.readAllLines(eula, StandardCharsets.UTF_8);
        boolean alreadyTrue = false;
        for (String line : lines) if (line.trim().equalsIgnoreCase("eula=true")) alreadyTrue = true;
        if (alreadyTrue) return;
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        Files.copy(eula, root.resolve("eula.txt.backup-" + stamp), StandardCopyOption.COPY_ATTRIBUTES);
        Files.write(eula, ("# Copie de sauvegarde créée avant acceptation explicite\n" +
                "eula=true\n").getBytes(StandardCharsets.UTF_8));
    }
}
