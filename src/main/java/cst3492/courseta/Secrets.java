package cst3492.courseta;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;

public final class Secrets {
    private Secrets() {
    }

    public static void write(String key) {
        PasswordSafe.getInstance().set(attributes(), new Credentials("folio", key == null ? "" : key));
    }

    public static String read() {
        Credentials credentials = PasswordSafe.getInstance().get(attributes());
        if (credentials == null || credentials.getPasswordAsString() == null) {
            return "";
        }
        return credentials.getPasswordAsString();
    }

    private static CredentialAttributes attributes() {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("Folio", "apiKey"));
    }
}
