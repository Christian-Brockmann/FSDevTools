package com.espirit.moddev.serverrunner;

import de.espirit.common.base.Logger.LogLevel;
import de.espirit.firstspirit.access.Connection;
import de.espirit.firstspirit.access.ConnectionManager;
import de.espirit.firstspirit.common.MaximumNumberOfSessionsExceededException;
import de.espirit.firstspirit.server.authentication.AuthenticationException;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;

@Getter
public class ServerProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerProperties.class);
    private static final Pattern firstSpiritJarPattern = Pattern.compile("(fs-.*?\\.jar|wrapper.*?\\.jar)$");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(6);

    public enum ConnectionMode {
        SOCKET_MODE(1088),
        HTTP_MODE(8000);

        final int defaultPort;

        ConnectionMode(final int defaultPort) {
            this.defaultPort = defaultPort;
        }

        public int getDefaultPort() {
            return defaultPort;
        }
    }

    /**
     * root of the first spirit server
     */
    private final Path serverRoot;

    /**
     * HTTP port under which the FirstSpirit server will be reachable (leave empty to use defaults or set to 0 for random port)
     */
    private final int httpPort;

    /**
     * Socket port under which the FirstSpirit server will be reachable (leave empty to use defaults or set to 0 for random port)
     */
    private final int socketPort;

    /**
     * whether a GC log should be written or not
     */
    private final boolean serverGcLog;

    /**
     * whether first spirit should be installed or not
     */
    private final boolean serverInstall;

    /**
     * Additional server options
     */
    private final List<String> serverOps;

    /**
     * admin password for the first spirit server instance
     */
    private final String serverAdminPw;

    /**
     * host we bind the server on
     */
    private final String serverHost;

    /**
     * Timeout waiting for FirstSpirit Server start.
     */
    private final Duration timeout;

    /**
     * Where the FirstSpirit jars are stored. You will need at least these jars to successfully start a server:
     * <ul>
     * <li>server</li>
     * <li>wrapper</li>
     * </ul>
     * <p>
     * If you give one dependency, you need to give all. If you give none, they will be taken from the classpath (if possible).
     * If you try to start a FirstSpirit Server in isolated mode, you must not put fs-isolated-server.jar and fs-access.jar on the classpath.
     */
    private final List<File> firstSpiritJars;

    private final File lockFile;

    /**
     * A reference to a supplier for the license file. May come from the file system, or the class path, or anything else.
     * Is read from the class path if nothing is given.
     */
    private final Supplier<Optional<InputStream>> licenseFileSupplier;

    /**
     * In which mode to connect.
     */
    private final ConnectionMode mode;

    private final URL serverUrl;

    private final LogLevel logLevel;

    @SuppressWarnings("squid:S00107")
    @Builder
    ServerProperties(final Path serverRoot, final String serverHost, final Integer httpPort, Integer socketPort,
                     ConnectionMode connectionMode, final boolean serverGcLog,
                     final Boolean serverInstall,
                     @Singular final List<String> serverOps, final String serverAdminPw,
                     final Duration timeout, @Singular final List<File> firstSpiritJars,
                     final Supplier<Optional<InputStream>> licenseFileSupplier, final LogLevel logLevel) {
        assertThatOrNull(httpPort, "httpPort", allOf(greaterThanOrEqualTo(0), lessThanOrEqualTo(65536)));
        assertThatOrNull(socketPort, "socketPort", allOf(greaterThanOrEqualTo(0), lessThanOrEqualTo(65536)));
        if (timeout != null && timeout.isNegative()) {
            throw new IllegalArgumentException("timeout may not be negative.");
        }

        this.serverRoot = serverRoot == null ? Paths.get(System.getProperty("user.home"), "opt", "FirstSpirit") : serverRoot;
        this.serverGcLog = serverGcLog;
        this.serverInstall = serverInstall == null ? true : serverInstall;
        this.serverOps = serverOps == null ? Collections.emptyList() :
                serverOps.stream().filter(Objects::nonNull)
                        .collect(Collectors.toCollection(ArrayList::new));

        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.serverAdminPw = serverAdminPw == null ? "Admin" : serverAdminPw;
        this.serverHost = serverHost == null || serverHost.isEmpty() ? "localhost" : serverHost;
        mode = connectionMode != null ? connectionMode : ConnectionMode.HTTP_MODE;
        this.httpPort = httpPort == null ? ConnectionMode.HTTP_MODE.getDefaultPort() : port(httpPort);
        int targetSocketPort = this.httpPort;
        while (targetSocketPort == this.httpPort) {
            targetSocketPort = socketPort == null ? ConnectionMode.SOCKET_MODE.getDefaultPort() : port(socketPort);
        }
        this.socketPort = targetSocketPort;
        this.firstSpiritJars =
                firstSpiritJars == null || firstSpiritJars.isEmpty() ? Collections.emptyList() : firstSpiritJars.stream().filter(Objects::nonNull)
                        .collect(Collectors.toCollection(ArrayList::new));

        //generate lock file reference, which can be found in the server directory
        lockFile = this.serverRoot.resolve(".fs.lock").toFile();

        //when we do not have fs-license.jar on the class path, we will not find the fs-license.conf and getResourceAsStream will return null
        this.licenseFileSupplier =
                licenseFileSupplier == null ? () -> Optional.ofNullable(ServerProperties.class.getResourceAsStream("/fs-license.conf")) : licenseFileSupplier;

        //this value should be lazily calculated
        try {
            serverUrl = new URL("http://" + this.serverHost + ":" + this.httpPort + "/");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("either serverHost or httpPort had an illegal format", e);
        }

        this.logLevel = logLevel == null ? LogLevel.DEBUG : logLevel;
    }

    public static int port(final int portNumber) {
        if (portNumber != 0) {
            return portNumber;
        } else {
            try (final ServerSocket serverSocket = new ServerSocket(0)) {
                return serverSocket.getLocalPort();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static <T> void assertThat(final T obj, final String name, final Matcher<T> matcher) {
        if (!matcher.matches(obj)) {
            final StringDescription description = new StringDescription();
            description.appendText(name).appendText(" must fulfill this spec: ").appendDescriptionOf(matcher).appendText("\nbut: ");
            matcher.describeMismatch(obj, description);
            throw new IllegalArgumentException(description.toString());
        }
    }

    private static <T> void assertThatOrNull(final T obj, final String name, final Matcher<T> matcher) {
        if (obj != null) {
            assertThat(obj, name, matcher);
        }
    }

    /**
     * Tries to get the FirstSpirit jars from the classpath.
     * <p>
     * Maven uses https://codehaus-plexus.github.io/plexus-classworlds/.
     * Therefore UrlClassLoader.getUrls does _not_ return all urls of all dependencies (like fs-server.jar).
     * This is a problem for Tests in this project as well as in tests in other projects,
     * that use this library to start a FirstSpirit server.
     * <p>
     * Using Class.forName to search for startup classes isn't an option either,
     * because many classes can be found in multiple jars.
     * E. g. de.espirit.common.bootstrap.Bootstrap exists in fs-isolated-runtime.jar and in fs-isolated-server.jar,
     * but we need the one from the server jar to start a server.
     * Unfortunately, projects will have both on their classpath, because they need the runtime jar for their production code
     * and the server jar to start the FirstSpirit server.
     * <p>
     * Therefore we try to identify the jars needed to start a FirstSpirit server by searching for certain attribute values in the MANIFEST.MF files on the classpath.
     * <p>
     * Note, that this problem only occurs when running with maven.
     * You will usually not run into it, when running from within your IDE or when running the ServerStartCommand via command line.
     *
     * @return a list with one or two jar files or an empty list, if none of them can be found
     */
    public static List<File> getFirstSpiritJarsFromClasspath() {
        final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        final Map<Attributes.Name, List<String>> attributeValues = new HashMap<>();
        attributeValues.put(Attributes.Name.SPECIFICATION_TITLE, Collections.singletonList("Java Service Wrapper"));
        final List<String> startUpClassNames = Arrays.stream(ServerType.values()).map(serverType -> serverType.getStartUpClass().replaceAll("/", ".")).collect(Collectors.toList());
        attributeValues.put(Attributes.Name.MAIN_CLASS, startUpClassNames);
        return findJarsByManifestAttributeValues(systemClassLoader, attributeValues);
    }

    static List<File> findJarsByManifestAttributeValues(ClassLoader classLoader, Map<Attributes.Name, List<String>> attributeValues) {
        final List<File> files = new LinkedList<>();
        try {
            final Enumeration<URL> resources = classLoader.getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                final URL url = resources.nextElement();
                try (final InputStream inputStream = url.openStream()) {
                    if (manifestMainAttributesContainAnyOf(new Manifest(inputStream), attributeValues)) {
                        final String normalizedUri = url.toURI().toString().replaceAll("jar:file:", "file://").replace("!/META-INF/MANIFEST.MF", "");
                        files.add(new File(new URI(normalizedUri)));
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            LOGGER.error("Error while reading a MANIFEST.MF", e);
        }
        return files;
    }

    private static boolean manifestMainAttributesContainAnyOf(Manifest manifest, Map<Attributes.Name, List<String>> soughtAttributeValues) {
        final Attributes attributes = manifest.getMainAttributes();
        for (Map.Entry<Attributes.Name, List<String>> sought : soughtAttributeValues.entrySet()) {
            final String manifestAttributeValue = attributes.getValue(sought.getKey());
            if (manifestAttributeValue != null && sought.getValue().contains(manifestAttributeValue.replaceAll("/", "."))) {
                return true;
            }
        }
        return false;
    }

    static Optional<Class> tryFindStartUpClass(ClassLoader systemClassLoader) {
        return Arrays.stream(ServerType.values())
                .map(type -> tryClassForName(type.getStartUpClass(), systemClassLoader))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    @SuppressWarnings("squid:S1166")
    private static Optional<Class> tryClassForName(String className, ClassLoader classLoader) {
        try {
            LOGGER.debug("Trying to resolve {}.", className);
            return Optional.of(Class.forName(className, false, classLoader));
        } catch (ClassNotFoundException e) {
            LOGGER.error("Class " + className + " could not be resolved.");
            return Optional.empty();
        }
    }

    /**
     * Tries to open a connection to the FirstSpirit server described by this ServerProperties.
     *
     * @return An {@link Optional} object containing a {@link Connection} if it could be established.
     */
    public Optional<Connection> tryOpenAdminConnection() {
        final int port = mode == ConnectionMode.SOCKET_MODE ? socketPort : httpPort;
        final int connectionMode = mode == ConnectionMode.SOCKET_MODE ? ConnectionManager.SOCKET_MODE : ConnectionManager.HTTP_MODE;
        LOGGER.debug("Create connection for FirstSpirit server at '{}:{}' with user '{}' in {}...", getServerHost(), port, "Admin", mode);
        try {
            final Connection connection = ConnectionManager.getConnection(getServerHost(), port, connectionMode, "Admin", getServerAdminPw());
            connection.connect();
            return Optional.of(connection);
        } catch (IOException | AuthenticationException | MaximumNumberOfSessionsExceededException e) {
            LOGGER.error("Could not connect to the FirstSpirit server.", e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.debug("An unexpected exception occurred.", e);
            return Optional.empty();
        }
    }
}
