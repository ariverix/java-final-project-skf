package dev.ariverix.shortener.infra;

import dev.ariverix.shortener.core.Browser;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

public final class DesktopBrowser implements Browser {
    @Override
    public void open(URI url) throws IOException {
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Открытие браузера не поддерживается в этой среде");
        }
        Desktop.getDesktop().browse(url);
    }
}
