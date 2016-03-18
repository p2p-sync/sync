package org.rmatil.sync.core.init;

import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.model.ApplicationConfig;
import org.rmatil.sync.core.model.RemoteClientLocation;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates application configurations
 */
public final class ApplicationConfigFactory {

    /**
     * Returns all default path ignore patterns.
     * The glob patterns follow the specification of <a href="https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-">Glob Patterns</a>
     * (resp. {@link FileSystem#getPathMatcher(String)}):
     * <p>
     * <ul>
     * <li><p> The {@code *} character matches zero or more {@link Character
     * characters} of a {@link Path#getName(int) name} component without
     * crossing directory boundaries. </p></li>
     * <p>
     * <li><p> The {@code **} characters matches zero or more {@link Character
     * characters} crossing directory boundaries. </p></li>
     * <p>
     * <li><p> The {@code ?} character matches exactly one character of a
     * name component.</p></li>
     * <p>
     * <li><p> The backslash character ({@code \}) is used to escape characters
     * that would otherwise be interpreted as special characters. The expression
     * {@code \\} matches a single backslash and "\{" matches a left brace
     * for example.  </p></li>
     * <p>
     * <li><p> The {@code [ ]} characters are a <i>bracket expression</i> that
     * match a single character of a name component out of a set of characters.
     * For example, {@code [abc]} matches {@code "a"}, {@code "b"}, or {@code "c"}.
     * The hyphen ({@code -}) may be used to specify a range so {@code [a-z]}
     * specifies a range that matches from {@code "a"} to {@code "z"} (inclusive).
     * These forms can be mixed so [abce-g] matches {@code "a"}, {@code "b"},
     * {@code "c"}, {@code "e"}, {@code "f"} or {@code "g"}. If the character
     * after the {@code [} is a {@code !} then it is used for negation so {@code
     * [!a-c]} matches any character except {@code "a"}, {@code "b"}, or {@code
     * "c"}.
     * <p> Within a bracket expression the {@code *}, {@code ?} and {@code \}
     * characters match themselves. The ({@code -}) character matches itself if
     * it is the first character within the brackets, or the first character
     * after the {@code !} if negating.</p></li>
     * <p>
     * <li><p> The {@code { }} characters are a group of subpatterns, where
     * the group matches if any subpattern in the group matches. The {@code ","}
     * character is used to separate the subpatterns. Groups cannot be nested.
     * </p></li>
     * <p>
     * <li><p> Leading period<tt>&#47;</tt>dot characters in file name are
     * treated as regular characters in match operations. For example,
     * the {@code "*"} glob pattern matches file name {@code ".login"}.
     * The {@link Files#isHidden} method may be used to test whether a file
     * is considered hidden.
     * </p></li>
     * <p>
     * <li><p> All other characters match themselves in an implementation
     * dependent manner. This includes characters representing any {@link
     * FileSystem#getSeparator name-separators}. </p></li>
     * <p>
     * <li><p> The matching of {@link Path#getRoot root} components is highly
     * implementation-dependent and is not specified. </p></li>
     * <p>
     * </ul>
     *
     * @return A list of default ignore patterns
     */
    public static List<String> getDefaultIgnorePatterns() {
        List<String> ignorePatterns = new ArrayList<>();
        ignorePatterns.add("**.DS_Store");
        ignorePatterns.add("**.ds_store");
        ignorePatterns.add("**.swp");
        ignorePatterns.add("**.swx");
        ignorePatterns.add("**.fseventd");
        ignorePatterns.add("**.apidisk");
        ignorePatterns.add("**.apdisk");
        ignorePatterns.add("**.htaccess");
        ignorePatterns.add("**.directory");
        ignorePatterns.add("**.part");
        ignorePatterns.add("**.filepart");
        ignorePatterns.add("**.crdownload");
        ignorePatterns.add("**.kate-swp");
        ignorePatterns.add("**.gnucash.tmp-*");
        ignorePatterns.add("**.synkron.*");
        ignorePatterns.add("**.sync.ffs_db");
        ignorePatterns.add("**.symform");
        ignorePatterns.add("**.symform-store");
        ignorePatterns.add("**.fuse_hidden*");
        ignorePatterns.add("**.unison");
        ignorePatterns.add("**.nfs*");
        ignorePatterns.add("**.Win7.vdi");
        ignorePatterns.add("**~");
        ignorePatterns.add("**~$*");
        ignorePatterns.add("**.~lock.*");
        ignorePatterns.add("**~*.tmp");
        ignorePatterns.add("**~.~*");
        ignorePatterns.add("**Icon\r*");
        ignorePatterns.add("**._*");
        ignorePatterns.add("**Thumbs.db");
        ignorePatterns.add("**desktop.ini");
        ignorePatterns.add("**.*.sw?");
        ignorePatterns.add("**.*.*sw?");
        ignorePatterns.add("**.TemporaryItems");
        ignorePatterns.add("**.Trashes");
        ignorePatterns.add("**.DocumentRevisions-V100");

        return ignorePatterns;
    }

    /**
     * Create a new application configuration filled with
     * default configuration values and a new RSA key pair.
     *
     * @return The default application configuration
     *
     * @throws NoSuchAlgorithmException If no provider supports RSA
     */
    public static ApplicationConfig createBootstrapApplicationConfig()
            throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator;
        keyPairGenerator = KeyPairGenerator.getInstance("RSA");

        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        RSAPublicKey rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) keyPair.getPrivate();

        return ApplicationConfigFactory.createBootstrapApplicationConfig(
                null,
                null,
                null,
                rsaPublicKey,
                rsaPrivateKey,
                ApplicationConfigFactory.getDefaultIgnorePatterns()
        );
    }

    /**
     * Create a new application configuration containing default
     * configuration values for the user parameters specified.
     * This method specifically creates a configuration for a bootstrap node,
     * i.e. no remote is used.
     *
     * @param username       The name of the user for which the config should be created
     * @param password       The password of that user
     * @param salt           The salt of that user
     * @param rsaPublicKey   A RSA public key to use
     * @param rsaPrivateKey  A RSA private key to use
     * @param ignorePatterns Ignore Patterns to use
     *
     * @return The created application configuration
     */
    public static ApplicationConfig createBootstrapApplicationConfig(String username, String password, String salt, RSAPublicKey rsaPublicKey, RSAPrivateKey rsaPrivateKey, List<String> ignorePatterns) {
        return ApplicationConfigFactory.createDefaultApplicationConfig(
                username,
                password,
                salt,
                rsaPublicKey,
                rsaPrivateKey,
                null,
                ignorePatterns
        );
    }

    /**
     * Create a new application configuration containing default
     * configuration values for the user parameters specified.
     *
     * @param username       The name of the user for which the config should be created
     * @param password       The password of that user
     * @param salt           The salt of that user
     * @param rsaPublicKey   A RSA public key to use
     * @param rsaPrivateKey  A RSA private key to use
     * @param remote         The remote node location of a node already joined the network
     * @param ignorePatterns Ignore Patterns to use
     *
     * @return The created application configuration
     */
    public static ApplicationConfig createDefaultApplicationConfig(String username, String password, String salt, RSAPublicKey rsaPublicKey, RSAPrivateKey rsaPrivateKey, RemoteClientLocation remote, List<String> ignorePatterns) {
        return new ApplicationConfig(
                username,
                password,
                salt,
                Config.DEFAULT.getCacheTtl(),
                Config.DEFAULT.getPeerDiscoveryTimeout(),
                Config.DEFAULT.getPeerBootstrapTimeout(),
                Config.DEFAULT.getShutdownAnnounceTimeout(),
                Config.DEFAULT.getDefaultPort(),
                rsaPublicKey,
                rsaPrivateKey,
                remote,
                ignorePatterns
        );
    }

}
